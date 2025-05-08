package io.mewb.bossEventManager.managers;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter; // WorldEdit's BukkitAdapter
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockTypes;

import io.lumine.mythic.api.MythicPlugin;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.core.mobs.ActiveMob;
import io.lumine.mythic.api.adapters.AbstractLocation;


import io.mewb.bossEventManager.BossEventManagerPlugin;
import io.mewb.bossEventManager.arena.ArenaInstance;
import io.mewb.bossEventManager.arena.ArenaTheme;
import io.mewb.bossEventManager.bosses.BossDefinition;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class ArenaManager {

    private final BossEventManagerPlugin plugin;
    private final ConfigManager configManager;
    private final WorldEdit faweApi;
    private final MythicPlugin mythicMobsApi;

    private final Map<String, ArenaTheme> arenaThemes;
    private final List<ArenaInstance> activeArenaInstances;
    private final Map<Integer, ArenaInstance> plotIdToInstanceMap;
    private int nextPlotId = 0;

    private final String arenaWorldName;
    private final World arenaWorld;
    private final int startX, startY, startZ;
    private final int plotSeparationX, plotSeparationZ;
    private final int maxConcurrentArenas;
    private final File faweSchematicsDir;

    public ArenaManager(BossEventManagerPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.faweApi = plugin.getFAWEApi();
        this.mythicMobsApi = plugin.getMythicMobsApi();

        this.arenaThemes = new HashMap<>();
        this.activeArenaInstances = Collections.synchronizedList(new ArrayList<>());
        this.plotIdToInstanceMap = new HashMap<>();

        this.arenaWorldName = configManager.getConfig().getString("arena-manager.arena-world-name", "BossEventArenas");
        this.arenaWorld = Bukkit.getWorld(arenaWorldName);
        if (this.arenaWorld == null) {
            plugin.getLogger().severe("Arena world '" + arenaWorldName + "' not found or not loaded! Arena functionality will be disabled.");
        }

        this.startX = configManager.getConfig().getInt("arena-manager.start-x", 0);
        this.startY = configManager.getConfig().getInt("arena-manager.start-y", 100);
        this.startZ = configManager.getConfig().getInt("arena-manager.start-z", 0);
        this.plotSeparationX = configManager.getConfig().getInt("arena-manager.plot-separation-x", 1000);
        this.plotSeparationZ = configManager.getConfig().getInt("arena-manager.plot-separation-z", 1000);
        this.maxConcurrentArenas = configManager.getConfig().getInt("arena-manager.max-concurrent-arenas", 10);

        File fawePluginDir = null;
        if (plugin.getServer().getPluginManager().getPlugin("FastAsyncWorldEdit") != null) {
            fawePluginDir = plugin.getServer().getPluginManager().getPlugin("FastAsyncWorldEdit").getDataFolder();
            this.faweSchematicsDir = new File(fawePluginDir, "schematics");
            if (!this.faweSchematicsDir.exists()) {
                this.faweSchematicsDir.mkdirs();
                plugin.getLogger().info("FAWE schematics directory did not exist, created: " + this.faweSchematicsDir.getAbsolutePath());
            }
        } else {
            this.faweSchematicsDir = new File("plugins/FastAsyncWorldEdit/schematics");
            plugin.getLogger().warning("FastAsyncWorldEdit plugin not found by manager, using default schematics path: " + this.faweSchematicsDir.getAbsolutePath());
        }
        loadArenaThemes();
    }

    private void loadArenaThemes() {
        arenaThemes.clear();
        ConfigurationSection themesSection = configManager.getConfig().getConfigurationSection("arena-themes");
        if (themesSection == null) {
            plugin.getLogger().warning("No 'arena-themes' section found in config.yml. No arena themes will be available.");
            return;
        }
        for (String themeId : themesSection.getKeys(false)) {
            ConfigurationSection currentThemeSection = themesSection.getConfigurationSection(themeId);
            if (currentThemeSection != null) {
                String displayName = currentThemeSection.getString("display-name", "Unnamed Theme");
                String schematicFile = currentThemeSection.getString("schematic-file");
                List<String> playerSpawns = currentThemeSection.getStringList("player-spawn-points");
                String bossSpawn = currentThemeSection.getString("boss-spawn-point");

                if (schematicFile == null || schematicFile.isEmpty()) {
                    plugin.getLogger().warning("Arena theme '" + themeId + "' is missing 'schematic-file'. Skipping.");
                    continue;
                }
                ArenaTheme theme = new ArenaTheme(themeId, displayName, schematicFile, playerSpawns, bossSpawn);
                arenaThemes.put(themeId.toLowerCase(), theme);
            }
        }
        plugin.getLogger().info("Loaded " + arenaThemes.size() + " arena themes.");
    }

    public ArenaTheme getArenaTheme(String themeId) {
        if (themeId == null) return null;
        return arenaThemes.get(themeId.toLowerCase());
    }

    public Collection<ArenaTheme> getAllArenaThemes() {
        return Collections.unmodifiableCollection(arenaThemes.values());
    }

    public List<ArenaInstance> getActiveArenaInstances() {
        return new ArrayList<>(activeArenaInstances);
    }

    public ArenaInstance getActiveArenaInstance(UUID instanceId) {
        synchronized (activeArenaInstances) {
            for (ArenaInstance instance : activeArenaInstances) {
                if (instance.getInstanceId().equals(instanceId)) {
                    return instance;
                }
            }
        }
        return null;
    }

    // Added helper to find instance by boss UUID
    public ArenaInstance getActiveArenaInstanceByBossUUID(UUID bossUUID) {
        if (bossUUID == null) return null;
        synchronized (activeArenaInstances) {
            for (ArenaInstance instance : activeArenaInstances) {
                // Check state and compare UUIDs
                if (instance.getState() == ArenaInstance.ArenaState.IN_USE && bossUUID.equals(instance.getBossEntityUUID())) {
                    return instance;
                }
            }
        }
        return null;
    }


    private Location findNextAvailablePlotOrigin() {
        if (arenaWorld == null) {
            plugin.getLogger().severe("Arena world is not loaded, cannot find plot origin.");
            return null;
        }
        if (maxConcurrentArenas > 0 && activeArenaInstances.size() >= maxConcurrentArenas) {
            plugin.getLogger().warning("Maximum number of concurrent arenas (" + maxConcurrentArenas + ") reached.");
            return null;
        }
        int currentPlotId = nextPlotId++;
        double plotX = startX + (currentPlotId * plotSeparationX);
        double plotZ = startZ;
        return new Location(arenaWorld, plotX, startY, plotZ);
    }

    public CompletableFuture<ArenaInstance> requestArena(String themeId) {
        CompletableFuture<ArenaInstance> future = new CompletableFuture<>();
        ArenaTheme theme = getArenaTheme(themeId);

        if (theme == null || faweApi == null || arenaWorld == null) {
            plugin.getLogger().severe("Pre-check failed for requesting arena: Theme ("+themeId+"), FAWE API, or Arena World is null.");
            future.complete(null);
            return future;
        }
        Location plotOrigin = findNextAvailablePlotOrigin();
        if (plotOrigin == null) {
            plugin.getLogger().warning("No available plot for new arena with theme: " + theme.getDisplayName());
            future.complete(null);
            return future;
        }

        File schematicFile = new File(faweSchematicsDir, theme.getSchematicFile());
        if (!schematicFile.exists()) {
            schematicFile = new File(plugin.getDataFolder(), "schematics" + File.separator + theme.getSchematicFile());
            if (!schematicFile.exists()) {
                plugin.getLogger().severe("Schematic file not found: " + theme.getSchematicFile() + " (Checked FAWE dir and plugin dir)");
                future.complete(null);
                return future;
            }
        }
        final File finalSchematicFile = schematicFile;

        new BukkitRunnable() {
            @Override
            public void run() {
                Clipboard clipboard = loadSchematicFromFile(finalSchematicFile);
                if (clipboard == null) {
                    future.complete(null);
                    return;
                }
                pasteSchematicToLocation(clipboard, plotOrigin, true);
                ArenaInstance instance = new ArenaInstance(theme, plotOrigin);
                instance.setState(ArenaInstance.ArenaState.PREPARING);
                synchronized (activeArenaInstances) {
                    activeArenaInstances.add(instance);
                }
                plugin.getLogger().info("Arena instance " + instance.getInstanceId() + " created at " + plotOrigin + " with theme " + theme.getDisplayName());
                future.complete(instance);
            }
        }.runTaskAsynchronously(plugin);
        return future;
    }

    public void startEvent(ArenaInstance instance, List<Player> partyPlayers, BossDefinition bossDef) {
        if (instance == null || instance.getState() != ArenaInstance.ArenaState.PREPARING) {
            plugin.getLogger().warning("Attempted to start event in an invalid or non-prepared arena instance: " + (instance != null ? instance.getInstanceId() : "null"));
            return;
        }
        if (mythicMobsApi == null) {
            plugin.getLogger().severe("MythicMobs API not available. Cannot spawn boss for event in arena: " + instance.getInstanceId());
            instance.setState(ArenaInstance.ArenaState.CLEANING_UP);
            endEvent(instance);
            return;
        }

        instance.setParty(partyPlayers);
        instance.setCurrentBoss(bossDef);
        // State is set later after successful boss spawn

        for (int i = 0; i < partyPlayers.size(); i++) {
            Player player = partyPlayers.get(i);
            Location spawnLoc = instance.getPlayerSpawnLocation(i, partyPlayers.size());
            if (spawnLoc != null) {
                player.teleportAsync(spawnLoc).thenAccept(success -> {
                    if (success) {
                        player.sendMessage(ChatColor.GREEN + "Teleported to the arena for: " + ChatColor.translateAlternateColorCodes('&', bossDef.getDisplayName()));
                    } else {
                        player.sendMessage(ChatColor.RED + "Failed to teleport to the arena.");
                    }
                });
            } else {
                player.sendMessage(ChatColor.RED + "Could not determine your spawn point in the arena.");
            }
        }

        Location bossSpawnLoc = instance.getBossSpawnLocation();
        if (bossSpawnLoc == null) {
            plugin.getLogger().severe("Boss spawn location is null for arena " + instance.getInstanceId() + " and theme " + instance.getArenaTheme().getId());
            partyPlayers.forEach(p -> p.sendMessage(ChatColor.RED + "Error: Could not determine boss spawn location. Event cancelled."));
            endEvent(instance);
            return;
        }

        Optional<MythicMob> mythicMobOpt = mythicMobsApi.getMobManager().getMythicMob(bossDef.getMythicMobId());
        if (!mythicMobOpt.isPresent()) {
            plugin.getLogger().severe("MythicMob '" + bossDef.getMythicMobId() + "' not found for boss " + bossDef.getDisplayName());
            partyPlayers.forEach(p -> p.sendMessage(ChatColor.RED + "Error: Boss definition '" + bossDef.getDisplayName() + "' is misconfigured. Event cancelled."));
            endEvent(instance);
            return;
        }

        MythicMob mythicMob = mythicMobOpt.get();
        AbstractLocation abstractBossSpawnLoc = io.lumine.mythic.bukkit.BukkitAdapter.adapt(bossSpawnLoc);
        int bossLevel = 1; // Placeholder

        try {
            ActiveMob spawnedActiveMob = mythicMob.spawn(abstractBossSpawnLoc, bossLevel);

            if (spawnedActiveMob != null && spawnedActiveMob.getEntity() != null && spawnedActiveMob.getEntity().isLiving()) {
                Entity spawnedBossEntity = spawnedActiveMob.getEntity().getBukkitEntity();
                instance.setBossEntityUUID(spawnedBossEntity.getUniqueId()); // Store the boss UUID
                instance.setState(ArenaInstance.ArenaState.IN_USE); // Set state to IN_USE only after successful spawn
                plugin.getLogger().info("Successfully spawned boss " + bossDef.getMythicMobId() + " (UUID: " + spawnedBossEntity.getUniqueId() + ") in arena " + instance.getInstanceId());
                // TODO: Apply ModelEngine model
            } else {
                plugin.getLogger().severe("Failed to spawn boss " + bossDef.getMythicMobId() + " in arena " + instance.getInstanceId() + ". spawn() returned null or invalid mob.");
                partyPlayers.forEach(p -> p.sendMessage(ChatColor.RED + "Error: Failed to spawn the boss. Event cancelled."));
                endEvent(instance); // Cleanup if spawn failed
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Exception while spawning MythicMob " + bossDef.getMythicMobId(), e);
            partyPlayers.forEach(p -> p.sendMessage(ChatColor.RED + "Critical Error: An exception occurred while spawning the boss. Event cancelled."));
            endEvent(instance); // Cleanup on exception
        }
        // Only log event started if spawn was successful
        if (instance.getState() == ArenaInstance.ArenaState.IN_USE) {
            plugin.getLogger().info("Event started in arena " + instance.getInstanceId() + " for boss " + bossDef.getDisplayName());
        }
    }


    public void endEvent(ArenaInstance instance) {
        if (instance == null || (instance.getState() != ArenaInstance.ArenaState.IN_USE && instance.getState() != ArenaInstance.ArenaState.PREPARING)) {
            plugin.getLogger().warning("Attempted to end event in an invalid or already cleaning arena instance: " + (instance != null ? instance.getInstanceId() : "null"));
            return;
        }
        // If the event was preparing but never started (e.g., spawn failed), just clean up
        boolean wasInUse = instance.getState() == ArenaInstance.ArenaState.IN_USE;

        instance.setState(ArenaInstance.ArenaState.CLEANING_UP);
        plugin.getLogger().info("Event ended in arena " + instance.getInstanceId() + ". Scheduling for cleanup.");

        // TODO: Despawn any remaining MythicMobs associated with this instance (if not already dead)
        // This might involve iterating through entities in the area or using MythicMobs API if it tracks mobs by instance/metadata

        if (instance.getPartyMemberUUIDs() != null) {
            Location lobbyLocation = Bukkit.getWorlds().get(0).getSpawnLocation(); // TODO: Make lobby configurable
            for (UUID playerUUID : instance.getPartyMemberUUIDs()) {
                Player player = Bukkit.getPlayer(playerUUID);
                if (player != null && player.isOnline()) {
                    // Only teleport if they were actually in the fight
                    if (wasInUse || player.getWorld().getName().equals(arenaWorldName)) {
                        player.teleportAsync(lobbyLocation);
                        player.sendMessage(configManager.getPrefix() + ChatColor.GOLD + "The boss event has ended. Teleporting you out...");
                    }
                }
            }
        }
        instance.setParty(null);
        instance.setCurrentBoss(null);
        // instance.setBossEntityUUID(null); // Already cleared by setState(CLEANING_UP)

        cleanupArena(instance).thenAccept(success -> {
            if (success) {
                plugin.getLogger().info("Arena " + instance.getInstanceId() + " cleaned up successfully.");
            } else {
                plugin.getLogger().severe("Failed to cleanup arena " + instance.getInstanceId() + ".");
            }
            synchronized (activeArenaInstances) {
                activeArenaInstances.remove(instance);
            }
        });
    }

    public CompletableFuture<Boolean> cleanupArena(ArenaInstance instance) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        if (faweApi == null || instance.getPlotOrigin() == null || instance.getArenaTheme() == null || arenaWorld == null) {
            plugin.getLogger().severe("Cannot cleanup arena: Pre-check failed for instance " + instance.getInstanceId());
            future.complete(false);
            return future;
        }
        File schematicFile = new File(faweSchematicsDir, instance.getArenaTheme().getSchematicFile());
        if (!schematicFile.exists()) {
            schematicFile = new File(plugin.getDataFolder(), "schematics" + File.separator + instance.getArenaTheme().getSchematicFile());
        }
        if (!schematicFile.exists()) {
            plugin.getLogger().severe("Schematic file for cleanup not found: " + instance.getArenaTheme().getSchematicFile());
            future.complete(false);
            return future;
        }
        final File finalSchematicFile = schematicFile;

        new BukkitRunnable() {
            @Override
            public void run() {
                Clipboard clipboard = loadSchematicFromFile(finalSchematicFile);
                if (clipboard == null) {
                    future.complete(false);
                    return;
                }
                BlockVector3 pasteOriginBV = BukkitAdapter.asBlockVector(instance.getPlotOrigin());
                BlockVector3 clipboardOffset = clipboard.getRegion().getMinimumPoint().subtract(clipboard.getOrigin());
                BlockVector3 minPoint = pasteOriginBV.add(clipboardOffset);
                BlockVector3 maxPoint = minPoint.add(clipboard.getDimensions()).subtract(BlockVector3.ONE);
                CuboidRegion regionToClear = new CuboidRegion(BukkitAdapter.adapt(arenaWorld), minPoint, maxPoint);

                try (EditSession editSession = faweApi.newEditSession(BukkitAdapter.adapt(arenaWorld))) {
                    editSession.setBlocks((Region) regionToClear, BlockTypes.AIR);
                    Operations.complete(editSession.commit());
                    plugin.getLogger().info("Successfully cleared region for arena " + instance.getInstanceId());
                    instance.setState(ArenaInstance.ArenaState.AVAILABLE);
                    future.complete(true);
                } catch (WorldEditException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to clear arena region for instance " + instance.getInstanceId(), e);
                    future.complete(false);
                }
            }
        }.runTaskAsynchronously(plugin);
        return future;
    }

    private Clipboard loadSchematicFromFile(File schematicFile) {
        if (!schematicFile.exists()) {
            plugin.getLogger().severe("Schematic file does not exist: " + schematicFile.getAbsolutePath());
            return null;
        }
        ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
        if (format == null) {
            if (schematicFile.getName().toLowerCase().endsWith(".schem")) {
                format = ClipboardFormats.findByAlias("schem");
            } else if (schematicFile.getName().toLowerCase().endsWith(".schematic")) {
                format = ClipboardFormats.findByAlias("schematic");
            }
            if (format == null) {
                plugin.getLogger().severe("Could not determine clipboard format for schematic: " + schematicFile.getName());
                return null;
            }
        }
        try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile))) {
            return reader.read();
        } catch (IOException | WorldEditException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load schematic: " + schematicFile.getName(), e);
            return null;
        }
    }

    private void pasteSchematicToLocation(Clipboard clipboard, Location targetLocation, boolean ignoreAirBlocks) {
        if (clipboard == null || targetLocation == null || targetLocation.getWorld() == null) {
            plugin.getLogger().severe("Cannot paste schematic: Clipboard, target location, or world is null.");
            return;
        }
        com.sk89q.worldedit.world.World worldEditWorld = BukkitAdapter.adapt(targetLocation.getWorld());
        try (EditSession editSession = faweApi.newEditSession(worldEditWorld)) {
            Operation operation = new ClipboardHolder(clipboard)
                    .createPaste(editSession)
                    .to(BukkitAdapter.asBlockVector(targetLocation))
                    .ignoreAirBlocks(ignoreAirBlocks)
                    .build();
            Operations.complete(operation);
        } catch (WorldEditException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to paste schematic at " + targetLocation, e);
        }
    }

    public void reloadArenaThemes() {
        plugin.getLogger().info("Reloading arena themes...");
        loadArenaThemes();
    }

    public void shutdown() {
        plugin.getLogger().info("Shutting down ArenaManager, attempting to clean up " + activeArenaInstances.size() + " active arenas...");
        List<ArenaInstance> instancesToClean = getActiveArenaInstances();
        for (ArenaInstance instance : instancesToClean) {
            if (instance.getState() == ArenaInstance.ArenaState.IN_USE || instance.getState() == ArenaInstance.ArenaState.PREPARING) {
                plugin.getLogger().info("Force cleaning arena: " + instance.getInstanceId());
                cleanupArena(instance).thenAccept(success -> {
                    if (success) plugin.getLogger().info("Arena " + instance.getInstanceId() + " cleaned during shutdown process.");
                    else plugin.getLogger().warning("Arena " + instance.getInstanceId() + " failed to clean during shutdown process.");
                });
            }
        }
        synchronized (activeArenaInstances) {
            activeArenaInstances.clear();
        }
        plotIdToInstanceMap.clear();
    }
}