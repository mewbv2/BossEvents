package io.mewb.bossEventManager;

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
import com.sk89q.worldedit.session.ClipboardHolder;


import io.mewb.bossEventManager.commands.BossEventCommand;
import io.mewb.bossEventManager.listeners.BossDeathListener;
import io.mewb.bossEventManager.managers.ArenaManager;
import io.mewb.bossEventManager.managers.BossManager;
import io.mewb.bossEventManager.managers.ConfigManager;
import io.mewb.bossEventManager.managers.GuiManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import io.lumine.mythic.api.MythicProvider;
import io.lumine.mythic.api.MythicPlugin;
import com.ticxo.modelengine.api.ModelEngineAPI;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BossEventManagerPlugin extends JavaPlugin {

    private static BossEventManagerPlugin instance;
    private static final Logger log = Logger.getLogger("Minecraft");

    // API Hooks
    private Economy vaultEconomy = null;
    private MythicPlugin mythicMobsApi = null;
    private ModelEngineAPI modelEngineApi = null;
    private WorldEdit faweApi = null;

    // Managers
    private ConfigManager configManager;
    private BossManager bossManager;
    private GuiManager guiManager;
    private ArenaManager arenaManager;
    // private PartyIntegrationManager partyIntegrationManager;


    @Override
    public void onEnable() {
        instance = this;
        log.info("--------------------------------------");
        log.info(this.getDescription().getName() + " is enabling...");
        log.info("Version: " + this.getDescription().getVersion());
        log.info("Author: " + this.getDescription().getAuthors().toString());
        log.info("--------------------------------------");

        // 1. Load Configuration
        configManager = new ConfigManager(this);

        // 2. Setup Dependencies
        // ... (dependency setup remains the same) ...
        if (!setupEconomy()) { log.severe("Vault not found or no Economy provider! Disabling BossEventManager."); getServer().getPluginManager().disablePlugin(this); return; }
        log.info("Successfully hooked into Vault!");
        if (!setupMythicMobs()) { log.severe("MythicMobs not found! Boss functionality will be severely limited."); } else { log.info("Successfully hooked into MythicMobs!"); }
        if (!setupModelEngine()) { log.severe("ModelEngine not found or API could not be hooked! Custom models will not work."); } else { log.info("Successfully hooked into ModelEngine!"); }
        if (!setupFAWE()) { log.severe("FastAsyncWorldEdit (FAWE) not found or API could not be hooked! Arena creation will fail."); } else { log.info("Successfully hooked into FastAsyncWorldEdit (FAWE)!"); }
        if (!setupPartyAndFriends()) { log.warning("Party & Friends not found! Party features will be disabled."); } else { log.info("Successfully hooked into Party & Friends!"); }


        // 3. Initialize Other Managers
        bossManager = new BossManager(this);
        guiManager = new GuiManager(this);
        if (faweApi != null && Bukkit.getWorld(configManager.getConfig().getString("arena-manager.arena-world-name", "BossEventArenas")) != null) {
            arenaManager = new ArenaManager(this);
        } else {
            log.severe("ArenaManager could not be initialized due to missing FAWE or arena world. Arena features disabled.");
        }
        // partyIntegrationManager = new PartyIntegrationManager(this);


        // 4. Register Commands
        BossEventCommand bossEventCommandExecutor = new BossEventCommand(this);
        PluginCommand bossEventPluginCommand = getCommand("bossevent");
        if (bossEventPluginCommand != null) {
            bossEventPluginCommand.setExecutor(bossEventCommandExecutor);
            bossEventPluginCommand.setTabCompleter(bossEventCommandExecutor);
            log.info("'/bossevent' command registered.");
        } else {
            log.severe("Could not register '/bossevent' command! Check plugin.yml.");
        }

        // 5. Register Event Listeners
        if (arenaManager != null && mythicMobsApi != null) { // Only register if needed APIs/Managers are present
            getServer().getPluginManager().registerEvents(new BossDeathListener(this), this);
            log.info("BossDeathListener registered.");
        } else {
            log.warning("BossDeathListener NOT registered due to missing ArenaManager or MythicMobs API.");
        }


        log.info(this.getDescription().getName() + " has been enabled successfully!");
        if (configManager.isDebugMode()) {
            log.info("Debug messages will be shown.");
        }
    }

    @Override
    public void onDisable() {
        log.info("--------------------------------------");
        log.info(this.getDescription().getName() + " is disabling...");
        if (arenaManager != null) {
            arenaManager.shutdown();
        }
        log.info(this.getDescription().getName() + " has been disabled.");
        log.info("--------------------------------------");
        instance = null;
        vaultEconomy = null;
        mythicMobsApi = null;
        modelEngineApi = null;
        faweApi = null;
        configManager = null;
        bossManager = null;
        guiManager = null;
        arenaManager = null;
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        vaultEconomy = rsp.getProvider();
        return vaultEconomy != null;
    }

    private boolean setupMythicMobs() {
        if (getServer().getPluginManager().getPlugin("MythicMobs") == null) return false;
        try {
            mythicMobsApi = MythicProvider.get();
            return mythicMobsApi != null;
        } catch (Exception e) {
            log.log(Level.SEVERE, "Could not retrieve MythicMobs API", e);
            return false;
        }
    }

    private boolean setupModelEngine() {
        if (getServer().getPluginManager().getPlugin("ModelEngine") == null) return false;
        try {
            modelEngineApi = ModelEngineAPI.getAPI();
            return modelEngineApi != null;
        } catch (NoClassDefFoundError | Exception e) {
            log.log(Level.SEVERE, "Could not retrieve ModelEngine API", e);
            return false;
        }
    }

    private boolean setupFAWE() {
        if (getServer().getPluginManager().getPlugin("FastAsyncWorldEdit") == null) {
            log.warning("FastAsyncWorldEdit plugin not found.");
            return false;
        }
        try {
            faweApi = WorldEdit.getInstance();
            return faweApi != null;
        } catch (NoClassDefFoundError ncdfe) {
            log.severe("FAWE API class (WorldEdit) not found: " + ncdfe.getMessage());
            return false;
        }
        catch (Exception e) {
            log.log(Level.SEVERE, "Could not retrieve FAWE (WorldEdit) API instance", e);
            return false;
        }
    }


    private boolean setupPartyAndFriends() { // Placeholder
        if (getServer().getPluginManager().getPlugin("PartyAndFriends") == null) return false;
        return Bukkit.getPluginManager().isPluginEnabled("PartyAndFriends");
    }


    // --- Getter Methods ---
    public static BossEventManagerPlugin getInstance() { return instance; }
    public Economy getVaultEconomy() { return vaultEconomy; }
    public MythicPlugin getMythicMobsApi() { return mythicMobsApi; }
    public ModelEngineAPI getModelEngineApi() { return modelEngineApi; }
    public WorldEdit getFAWEApi() { return faweApi; }
    public ConfigManager getConfigManager() { return configManager; }
    public BossManager getBossManager() { return bossManager; }
    public GuiManager getGuiManager() { return guiManager; }
    public ArenaManager getArenaManager() { return arenaManager; }

    // --- Schematic Helper Methods ---
    public Clipboard loadSchematic(File schematicFile) {
        if (faweApi == null) { log.severe("FAWE API not available..."); return null; }
        if (!schematicFile.exists()) { log.severe("Schematic file does not exist..."); return null; }
        ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
        if (format == null) {
            if (schematicFile.getName().toLowerCase().endsWith(".schem")) { format = ClipboardFormats.findByAlias("schem"); }
            else if (schematicFile.getName().toLowerCase().endsWith(".schematic")) { format = ClipboardFormats.findByAlias("schematic"); }
            if (format == null) { log.severe("Could not determine clipboard format..."); return null; }
        }
        try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile))) { return reader.read(); }
        catch (IOException | WorldEditException e) { log.log(Level.SEVERE, "Failed to load schematic...", e); return null; }
    }

    public void pasteSchematic(Clipboard clipboard, org.bukkit.Location targetLocation, boolean ignoreAirBlocks) {
        if (faweApi == null || clipboard == null || targetLocation == null || targetLocation.getWorld() == null) { log.severe("Cannot paste schematic..."); return; }
        com.sk89q.worldedit.world.World worldEditWorld = BukkitAdapter.adapt(targetLocation.getWorld());
        try (EditSession editSession = faweApi.newEditSession(worldEditWorld)) {
            Operation operation = new ClipboardHolder(clipboard).createPaste(editSession).to(BlockVector3.at(targetLocation.getX(), targetLocation.getY(), targetLocation.getZ())).ignoreAirBlocks(ignoreAirBlocks).build();
            Operations.complete(operation);
        } catch (WorldEditException e) { log.log(Level.SEVERE, "Failed to paste schematic...", e); }
    }
}