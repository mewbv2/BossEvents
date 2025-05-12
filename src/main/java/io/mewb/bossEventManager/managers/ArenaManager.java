package io.mewb.bossEventManager.managers;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
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

import io.lumine.mythic.api.adapters.AbstractLocation;
import io.lumine.mythic.bukkit.MythicBukkit;


import io.lumine.mythic.core.mobs.ActiveMob;
import io.mewb.bossEventManager.BossEventManagerPlugin;
import io.mewb.bossEventManager.arena.ArenaInstance;
import io.mewb.bossEventManager.arena.ArenaTheme;
import io.mewb.bossEventManager.arena.PlotInfo;
import io.mewb.bossEventManager.bosses.BossDefinition;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
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
    private final Set<Integer> usedPlotIds = new HashSet<>();

    private final String arenaWorldName;
    private final World arenaWorld;
    private final int startX, startY, startZ;
    private final int plotSeparationX, plotSeparationZ;
    private final int plotsPerRow;
    private final int maxConcurrentArenas;
    private final File faweSchematicsDir;

    private final float musicDefaultVolume;
    private final float musicDefaultPitch;
    private final List<String> availableMusicTracks;
    private final Random random = new Random();

    public ArenaManager(BossEventManagerPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.faweApi = plugin.getFAWEApi();
        this.mythicMobsApi = plugin.getMythicMobsApi();

        this.arenaThemes = new HashMap<>();
        this.activeArenaInstances = Collections.synchronizedList(new ArrayList<>());

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
        this.plotsPerRow = Math.max(1, configManager.getConfig().getInt("arena-manager.plots-per-row", 10));
        this.maxConcurrentArenas = configManager.getConfig().getInt("arena-manager.max-concurrent-arenas", 20);

        this.musicDefaultVolume = (float) configManager.getConfig().getDouble("arena-manager.music.volume", 0.7);
        this.musicDefaultPitch = (float) configManager.getConfig().getDouble("arena-manager.music.pitch", 1.0);
        this.availableMusicTracks = configManager.getConfig().getStringList("arena-manager.music.available-tracks");
        if (this.availableMusicTracks.isEmpty()) {
            plugin.getLogger().info("No available boss music tracks configured.");
        } else {
            plugin.getLogger().info("Loaded " + this.availableMusicTracks.size() + " available boss music tracks.");
        }

        File fawePluginDir = null;
        org.bukkit.plugin.Plugin fawePluginInstance = plugin.getServer().getPluginManager().getPlugin("FastAsyncWorldEdit");
        if (fawePluginInstance != null) {
            fawePluginDir = fawePluginInstance.getDataFolder();
            this.faweSchematicsDir = new File(fawePluginDir, "schematics");
            if (!this.faweSchematicsDir.exists()) {
                if (this.faweSchematicsDir.mkdirs()) {
                    plugin.getLogger().info("Created FAWE schematics directory: " + this.faweSchematicsDir.getAbsolutePath());
                } else {
                    plugin.getLogger().warning("Failed to create FAWE schematics directory: " + this.faweSchematicsDir.getAbsolutePath());
                }
            }
        } else {
            this.faweSchematicsDir = new File(plugin.getDataFolder().getParentFile(), "FastAsyncWorldEdit/schematics"); // Fallback
            plugin.getLogger().warning("FastAsyncWorldEdit plugin not found, using fallback schematics path: " + this.faweSchematicsDir.getAbsolutePath());
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
                String schematicFileName = currentThemeSection.getString("schematic-file");
                List<String> playerSpawns = currentThemeSection.getStringList("player-spawn-points");
                String bossSpawn = currentThemeSection.getString("boss-spawn-point");

                if (schematicFileName == null || schematicFileName.isEmpty()) {
                    plugin.getLogger().warning("Arena theme '" + themeId + "' is missing 'schematic-file'. Skipping.");
                    continue;
                }

                File schematicFile = new File(faweSchematicsDir, schematicFileName);
                if (!schematicFile.exists()) {
                    schematicFile = new File(plugin.getDataFolder(), "schematics" + File.separator + schematicFileName);
                }

                if (!schematicFile.exists()) {
                    plugin.getLogger().severe("Schematic file '" + schematicFileName + "' for theme '" + themeId + "' not found. Skipping theme.");
                    continue;
                }

                Clipboard clipboard = loadSchematicFromFile(schematicFile);
                if (clipboard == null) {
                    plugin.getLogger().severe("Failed to load schematic '" + schematicFileName + "' for theme '" + themeId + "' to get dimensions. Skipping theme.");
                    continue;
                }

                BlockVector3 dimensions = clipboard.getDimensions();
                BlockVector3 originOffset = clipboard.getRegion().getMinimumPoint().subtract(clipboard.getOrigin());

                ArenaTheme theme = new ArenaTheme(themeId, displayName, schematicFileName, playerSpawns, bossSpawn, dimensions, originOffset);
                arenaThemes.put(themeId.toLowerCase(), theme);
            }
        }
        plugin.getLogger().info("Finished loading " + arenaThemes.size() + " arena themes.");
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

    public ArenaInstance getActiveArenaInstanceByBossUUID(UUID bossBukkitUUID) {
        if (bossBukkitUUID == null) return null;
        synchronized (activeArenaInstances) {
            for (ArenaInstance instance : activeArenaInstances) {
                if (instance.getState() == ArenaInstance.ArenaState.IN_USE && bossBukkitUUID.equals(instance.getBossEntityUUID())) {
                    return instance;
                }
            }
        }
        return null;
    }

    private PlotInfo findAndReservePlot() {
        if (arenaWorld == null) {
            plugin.getLogger().severe("Arena world is not loaded, cannot find or reserve a plot.");
            return null;
        }
        synchronized (usedPlotIds) {
            if (maxConcurrentArenas > 0 && usedPlotIds.size() >= maxConcurrentArenas) {
                plugin.getLogger().warning("Maximum number of concurrent arenas (" + maxConcurrentArenas + ") reached. No plot available.");
                return null;
            }

            // Define searchLimit here
            int searchLimit = (maxConcurrentArenas > 0) ? (maxConcurrentArenas + plotsPerRow + 5) : 1000;
            for (int currentPlotId = 0; currentPlotId < searchLimit; currentPlotId++) {
                if (!usedPlotIds.contains(currentPlotId)) {
                    usedPlotIds.add(currentPlotId);

                    int plotXIndex = currentPlotId % plotsPerRow;
                    int plotZIndex = currentPlotId / plotsPerRow;

                    double plotX = startX + (plotXIndex * plotSeparationX);
                    double plotZ = startZ + (plotZIndex * plotSeparationZ);
                    Location plotOrigin = new Location(arenaWorld, plotX, startY, plotZ);

                    plugin.getLogger().info("Reserved plot ID: " + currentPlotId + " at location: " + plotOrigin);
                    return new PlotInfo(currentPlotId, plotOrigin);
                }
            }
        }
        plugin.getLogger().severe("Could not find an available plot ID after searching up to " + ((maxConcurrentArenas > 0) ? (maxConcurrentArenas + plotsPerRow + 5) : 1000) + " plots."); // Use the same calculation for logging
        return null;
    }

    private void releasePlot(int plotId) {
        if (plotId >= 0) {
            synchronized (usedPlotIds) {
                boolean removed = usedPlotIds.remove(plotId);
                if (removed) {
                    plugin.getLogger().info("Released plot ID: " + plotId + ". It is now available.");
                } else {
                    plugin.getLogger().warning("Attempted to release plot ID: " + plotId + ", but it was not in the used set.");
                }
            }
        } else {
            plugin.getLogger().warning("Attempted to release an invalid plot ID: " + plotId);
        }
    }

    public CompletableFuture<ArenaInstance> requestArena(String themeId) {
        CompletableFuture<ArenaInstance> future = new CompletableFuture<>();
        ArenaTheme theme = getArenaTheme(themeId);

        if (theme == null) { plugin.getLogger().severe("Cannot request arena: Theme '" + themeId + "' not found."); future.complete(null); return future; }
        if (faweApi == null) { plugin.getLogger().severe("FAWE API not available. Cannot create arena."); future.complete(null); return future; }
        if (arenaWorld == null) { plugin.getLogger().severe("Arena world '" + arenaWorldName + "' is not loaded."); future.complete(null); return future; }
        if (theme.getSchematicDimensions() == null || theme.getSchematicOriginOffset() == null) {
            plugin.getLogger().severe("Arena theme '" + themeId + "' is missing pre-loaded schematic dimension/offset data!");
            future.complete(null);
            return future;
        }

        PlotInfo reservedPlot = findAndReservePlot();
        if (reservedPlot == null) {
            future.complete(null);
            return future;
        }
        final int plotId = reservedPlot.getPlotId();
        final Location plotOrigin = reservedPlot.getPlotOrigin();

        File schematicFile = new File(faweSchematicsDir, theme.getSchematicFile());
        if (!schematicFile.exists()) {
            schematicFile = new File(plugin.getDataFolder(), "schematics" + File.separator + theme.getSchematicFile());
        }

        if (!schematicFile.exists()) {
            plugin.getLogger().severe("Schematic file not found during request: " + theme.getSchematicFile());
            releasePlot(plotId);
            future.complete(null);
            return future;
        }
        final File finalSchematicFile = schematicFile;

        new BukkitRunnable() {
            @Override
            public void run() {
                Clipboard clipboard = loadSchematicFromFile(finalSchematicFile);
                if (clipboard == null) {
                    releasePlot(plotId);
                    future.complete(null);
                    return;
                }
                pasteSchematicToLocation(clipboard, plotOrigin, true);
                ArenaInstance instance = new ArenaInstance(theme, plotOrigin, plotId);
                instance.setState(ArenaInstance.ArenaState.PREPARING);
                synchronized (activeArenaInstances) {
                    activeArenaInstances.add(instance);
                }
                plugin.getLogger().info("Arena instance " + instance.getInstanceId() + " (Plot ID: " + plotId + ") created at " + plotOrigin + " with theme " + theme.getDisplayName());
                future.complete(instance);
            }
        }.runTaskAsynchronously(plugin);
        return future;
    }

    public void startEvent(ArenaInstance instance, List<Player> partyPlayers, BossDefinition bossDef) {
        if (instance == null || instance.getState() != ArenaInstance.ArenaState.PREPARING) { plugin.getLogger().warning("Attempted to start event in an invalid arena instance."); return; }
        if (mythicMobsApi == null) { plugin.getLogger().severe("MythicMobs API not available."); instance.setState(ArenaInstance.ArenaState.CLEANING_UP); endEvent(instance); return; }
        if (partyPlayers == null || partyPlayers.isEmpty()) { plugin.getLogger().warning("Attempted to start event with no players."); instance.setState(ArenaInstance.ArenaState.CLEANING_UP); endEvent(instance); return; }

        instance.storePartyOriginalLocations(partyPlayers);
        instance.setParty(partyPlayers);
        instance.setCurrentBoss(bossDef);

        if (!availableMusicTracks.isEmpty()) {
            String musicTrackToPlay = availableMusicTracks.get(random.nextInt(availableMusicTracks.size()));
            instance.setActiveMusicTrack(musicTrackToPlay);
            for (Player player : partyPlayers) {
                if (player != null && player.isOnline()) {
                    player.playSound(player.getLocation(), musicTrackToPlay, SoundCategory.MUSIC, musicDefaultVolume, musicDefaultPitch);
                }
            }
        }

        for (int i = 0; i < partyPlayers.size(); i++) {
            Player player = partyPlayers.get(i);
            Location spawnLoc = instance.getPlayerSpawnLocation(i, partyPlayers.size());
            if (spawnLoc != null) {
                player.teleportAsync(spawnLoc).thenAccept(success -> {
                    if (success) player.sendMessage(ChatColor.GREEN + "Teleported to the arena for: " + ChatColor.translateAlternateColorCodes('&', bossDef.getDisplayName()));
                    else player.sendMessage(ChatColor.RED + "Failed to teleport to the arena.");
                });
            } else player.sendMessage(ChatColor.RED + "Could not determine your spawn point in the arena.");
        }
        Location bossSpawnLoc = instance.getBossSpawnLocation();
        if (bossSpawnLoc == null) { plugin.getLogger().severe("Boss spawn location is null for arena " + instance.getInstanceId()); partyPlayers.forEach(p -> p.sendMessage(ChatColor.RED + "Error: Could not determine boss spawn location.")); endEvent(instance); return; }
        Optional<MythicMob> mythicMobOpt = mythicMobsApi.getMobManager().getMythicMob(bossDef.getMythicMobId());
        if (!mythicMobOpt.isPresent()) { plugin.getLogger().severe("MythicMob '" + bossDef.getMythicMobId() + "' not found."); partyPlayers.forEach(p -> p.sendMessage(ChatColor.RED + "Error: Boss definition '" + bossDef.getDisplayName() + "' is misconfigured.")); endEvent(instance); return; }
        MythicMob mythicMobToSpawn = mythicMobOpt.get();
        AbstractLocation abstractBossSpawnLoc = io.lumine.mythic.bukkit.BukkitAdapter.adapt(bossSpawnLoc);
        int partySize = partyPlayers.size(); Map<String, Double> scalingParams = bossDef.getPartySizeScaling();
        double baseLevel = 1.0; double levelPerMember = 0.0; double maxLevel = Double.MAX_VALUE;
        if (scalingParams != null) { baseLevel = scalingParams.getOrDefault("base-level", 1.0); levelPerMember = scalingParams.getOrDefault("level-per-member-above-one", 0.0); if (scalingParams.containsKey("max-level")) { maxLevel = scalingParams.get("max-level"); } }
        double calculatedLevel = baseLevel; if (partySize > 1) { calculatedLevel += (partySize - 1) * levelPerMember; }
        calculatedLevel = Math.min(calculatedLevel, maxLevel); calculatedLevel = Math.max(1.0, calculatedLevel);
        int finalBossLevel = (int) Math.round(calculatedLevel); if (finalBossLevel < 1) finalBossLevel = 1;
        plugin.getLogger().info("Calculated boss level for " + bossDef.getDisplayName() + ": " + finalBossLevel);
        try {
            ActiveMob spawnedActiveMob = mythicMobToSpawn.spawn(abstractBossSpawnLoc, finalBossLevel);
            if (spawnedActiveMob != null && spawnedActiveMob.getEntity() != null && spawnedActiveMob.getEntity().isLiving()) {
                Entity spawnedBossEntity = spawnedActiveMob.getEntity().getBukkitEntity();
                instance.setBossEntityUUID(spawnedBossEntity.getUniqueId()); instance.setState(ArenaInstance.ArenaState.IN_USE);
                plugin.getLogger().info("Successfully spawned boss " + bossDef.getMythicMobId() + " (Level " + finalBossLevel + ") in arena " + instance.getInstanceId());
            } else { plugin.getLogger().severe("Failed to spawn boss " + bossDef.getMythicMobId()); partyPlayers.forEach(p -> p.sendMessage(ChatColor.RED + "Error: Failed to spawn the boss.")); endEvent(instance); }
        } catch (Exception e) { plugin.getLogger().log(Level.SEVERE, "Exception while spawning MythicMob " + bossDef.getMythicMobId(), e); partyPlayers.forEach(p -> p.sendMessage(ChatColor.RED + "Critical Error spawning boss.")); endEvent(instance); }
        if (instance.getState() == ArenaInstance.ArenaState.IN_USE) { plugin.getLogger().info("Event started in arena " + instance.getInstanceId()); }
    }

    public void endEvent(ArenaInstance instance) {
        if (instance == null || (instance.getState() != ArenaInstance.ArenaState.IN_USE && instance.getState() != ArenaInstance.ArenaState.PREPARING)) { plugin.getLogger().warning("Attempted to end event in an invalid arena instance."); return; }
        boolean wasInUse = instance.getState() == ArenaInstance.ArenaState.IN_USE;
        Map<UUID, Location> originalLocations = instance.getOriginalPlayerLocations();
        String activeMusic = instance.getActiveMusicTrack();
        UUID bossUUID = instance.getBossEntityUUID();

        instance.setState(ArenaInstance.ArenaState.CLEANING_UP);
        plugin.getLogger().info("Event ended in arena " + instance.getInstanceId() + " (Plot ID: " + instance.getPlotId() + "). Scheduling for cleanup.");

        if (activeMusic != null && !activeMusic.isEmpty()) {
            if (!originalLocations.isEmpty()) {
                for (UUID playerUUID : originalLocations.keySet()) {
                    Player player = Bukkit.getPlayer(playerUUID);
                    if (player != null && player.isOnline()) {
                        player.stopSound(activeMusic, SoundCategory.MUSIC);
                    }
                }
            }
        }
        if (wasInUse && bossUUID != null) {
            Optional<ActiveMob> activeMobOpt = MythicBukkit.inst().getMobManager().getActiveMob(bossUUID);
            if (activeMobOpt.isPresent()) { ActiveMob activeMob = activeMobOpt.get(); if (!activeMob.isDead()) { activeMob.remove(); }
            } else { Entity bossBukkitEntity = Bukkit.getEntity(bossUUID); if (bossBukkitEntity != null && !bossBukkitEntity.isDead()) { bossBukkitEntity.remove(); } }
        }
        if (!originalLocations.isEmpty()) {
            Location defaultFallbackLocation = Bukkit.getWorlds().get(0).getSpawnLocation();
            String configuredLobbyWorldName = configManager.getConfig().getString("lobby.world-name", defaultFallbackLocation.getWorld().getName());
            World lobbyWorld = Bukkit.getWorld(configuredLobbyWorldName);
            if (lobbyWorld == null) { lobbyWorld = defaultFallbackLocation.getWorld(); }
            Location configuredLobbyLocation = new Location(lobbyWorld,
                    configManager.getConfig().getDouble("lobby.x", lobbyWorld.getSpawnLocation().getX()),
                    configManager.getConfig().getDouble("lobby.y", lobbyWorld.getSpawnLocation().getY()),
                    configManager.getConfig().getDouble("lobby.z", lobbyWorld.getSpawnLocation().getZ()),
                    (float) configManager.getConfig().getDouble("lobby.yaw", lobbyWorld.getSpawnLocation().getYaw()),
                    (float) configManager.getConfig().getDouble("lobby.pitch", lobbyWorld.getSpawnLocation().getPitch())
            );
            for (Map.Entry<UUID, Location> entry : originalLocations.entrySet()) {
                UUID playerUUID = entry.getKey();
                // Location returnLocation = entry.getValue(); // Reverted to always send to lobby
                Player player = Bukkit.getPlayer(playerUUID);
                if (player != null && player.isOnline()) {
                    if (player.getGameMode() == GameMode.SPECTATOR) { player.setGameMode(GameMode.SURVIVAL); }
                    final Location finalReturnLocation = configuredLobbyLocation; // Use the lobby location
                    player.teleportAsync(finalReturnLocation).thenAccept(success -> {
                        if(success) player.sendMessage(configManager.getPrefix() + ChatColor.GOLD + "The boss event has ended. Teleporting you to the lobby...");
                        else { player.sendMessage(configManager.getPrefix() + ChatColor.RED + "Failed to teleport you to the lobby. Please use /spawn or /lobby."); Bukkit.getScheduler().runTask(plugin, () -> player.teleport(finalReturnLocation)); }
                    });
                }
            }
        }
        instance.setParty(null); instance.setCurrentBoss(null);
        final int plotIdToRelease = instance.getPlotId();
        cleanupArena(instance).thenAccept(success -> {
            if (success) {
                plugin.getLogger().info("Arena " + instance.getInstanceId() + " (Plot ID: " + plotIdToRelease + ") cleaned up successfully.");
                releasePlot(plotIdToRelease);
            } else {
                plugin.getLogger().severe("Failed to cleanup arena " + instance.getInstanceId() + " (Plot ID: " + plotIdToRelease + "). Plot may remain marked as used.");
            }
            synchronized (activeArenaInstances) { activeArenaInstances.remove(instance); }
        });
    }

    public CompletableFuture<Boolean> cleanupArena(ArenaInstance instance) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        if (faweApi == null || instance.getPlotOrigin() == null || instance.getArenaTheme() == null || arenaWorld == null) { future.complete(false); return future; }
        ArenaTheme theme = instance.getArenaTheme();
        BlockVector3 dimensions = theme.getSchematicDimensions();
        BlockVector3 originOffset = theme.getSchematicOriginOffset();
        if (dimensions == null || originOffset == null) { future.complete(false); return future; }
        new BukkitRunnable() {
            @Override
            public void run() {
                BlockVector3 pasteOriginBV = BukkitAdapter.asBlockVector(instance.getPlotOrigin());
                BlockVector3 minPoint = pasteOriginBV.add(originOffset);
                BlockVector3 maxPoint = minPoint.add(dimensions).subtract(BlockVector3.ONE);
                CuboidRegion regionToClear = new CuboidRegion(BukkitAdapter.adapt(arenaWorld), minPoint, maxPoint);
                try (EditSession editSession = faweApi.newEditSession(BukkitAdapter.adapt(arenaWorld))) {
                    editSession.setBlocks((Region) regionToClear, BlockTypes.AIR);
                    Operations.complete(editSession.commit());
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
        if (faweApi == null) { plugin.getLogger().severe("FAWE API not available, cannot load schematic: " + schematicFile.getName()); return null; }
        if (!schematicFile.exists()) { plugin.getLogger().severe("Schematic file does not exist: " + schematicFile.getAbsolutePath()); return null; }
        ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
        if (format == null) {
            if (schematicFile.getName().toLowerCase().endsWith(".schem")) { format = ClipboardFormats.findByAlias("schem"); }
            else if (schematicFile.getName().toLowerCase().endsWith(".schematic")) { format = ClipboardFormats.findByAlias("schematic"); }
            if (format == null) { plugin.getLogger().severe("Could not determine clipboard format for schematic: " + schematicFile.getName()); return null; }
        }
        try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile))) { return reader.read(); }
        catch (IOException | WorldEditException e) { plugin.getLogger().log(Level.SEVERE, "Failed to load schematic: " + schematicFile.getName(), e); return null; }
    }

    private void pasteSchematicToLocation(Clipboard clipboard, Location targetLocation, boolean ignoreAirBlocks) {
        if (faweApi == null || clipboard == null || targetLocation == null || targetLocation.getWorld() == null) { plugin.getLogger().severe("Cannot paste schematic due to missing FAWE API, clipboard, or valid target location."); return; }
        com.sk89q.worldedit.world.World worldEditWorld = BukkitAdapter.adapt(targetLocation.getWorld());
        try (EditSession editSession = faweApi.newEditSession(worldEditWorld)) {
            Operation operation = new ClipboardHolder(clipboard).createPaste(editSession).to(BukkitAdapter.asBlockVector(targetLocation)).ignoreAirBlocks(ignoreAirBlocks).build();
            Operations.complete(operation);
        } catch (WorldEditException e) { plugin.getLogger().log(Level.SEVERE, "Failed to paste schematic at " + targetLocation, e); }
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
                plugin.getLogger().info("Force cleaning arena: " + instance.getInstanceId() + " (Plot ID: " + instance.getPlotId() + ")");
                final int plotIdToRelease = instance.getPlotId();
                cleanupArena(instance).thenAccept(success -> {
                    if (success) {
                        plugin.getLogger().info("Arena " + instance.getInstanceId() + " cleaned during shutdown process.");
                        releasePlot(plotIdToRelease);
                    } else {
                        plugin.getLogger().warning("Arena " + instance.getInstanceId() + " failed to clean during shutdown process. Plot ID " + plotIdToRelease + " may need manual review if it wasn't released.");
                    }
                });
            }
        }
        synchronized (activeArenaInstances) {
            activeArenaInstances.clear();
        }
        synchronized (usedPlotIds) {
            usedPlotIds.clear();
        }
    }
}