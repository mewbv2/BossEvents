package io.mewb.bossEventManager;

import com.sk89q.worldedit.WorldEdit;


import io.mewb.bossEventManager.commands.BossEventCommand;
import io.mewb.bossEventManager.listeners.BossDeathListener;
import io.mewb.bossEventManager.listeners.PlayerArenaDeathListener;
import io.mewb.bossEventManager.listeners.SpigotPluginMessageListener;
import io.mewb.bossEventManager.managers.ArenaManager;
import io.mewb.bossEventManager.managers.BossManager;
import io.mewb.bossEventManager.managers.ConfigManager;
import io.mewb.bossEventManager.managers.GuiManager;
import io.mewb.bossEventManager.party.PartyInfoManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager; // Import PluginManager
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import io.lumine.mythic.api.MythicProvider;
import io.lumine.mythic.api.MythicPlugin;
import com.ticxo.modelengine.api.ModelEngineAPI;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection; // Import Collection
import java.util.logging.Level;
import java.util.logging.Logger;

public class BossEventManagerPlugin extends JavaPlugin {

    private static BossEventManagerPlugin instance;
    private static final Logger log = Logger.getLogger("Minecraft");

    public static final String BUNGEE_CHANNEL = "bossevent:party";

    private Economy vaultEconomy = null;
    private MythicPlugin mythicMobsApi = null;
    private ModelEngineAPI modelEngineApi = null;
    private WorldEdit faweApi = null;

    private ConfigManager configManager;
    private BossManager bossManager;
    private GuiManager guiManager;
    private ArenaManager arenaManager;
    private PartyInfoManager partyInfoManager;


    @Override
    public void onEnable() {
        instance = this;
        log.info("--------------------------------------");
        log.info(this.getDescription().getName() + " is enabling...");
        log.info("Version: " + this.getDescription().getVersion());
        log.info("Author: " + this.getDescription().getAuthors().toString());
        log.info("--------------------------------------");

        configManager = new ConfigManager(this);

        if (!setupEconomy()) { log.severe("Vault/Economy setup failed! Disabling BossEventManager."); getServer().getPluginManager().disablePlugin(this); return; }
        // Success message is now inside setupEconomy
        if (!setupMythicMobs()) { log.severe("MythicMobs not found! Boss functionality will be severely limited."); } else { log.info("Successfully hooked into MythicMobs!"); }
        if (!setupModelEngine()) { log.severe("ModelEngine not found or API could not be hooked! Custom models will not work."); } else { log.info("Successfully hooked into ModelEngine!"); }
        if (!setupFAWE()) { log.severe("FastAsyncWorldEdit (FAWE) not found or API could not be hooked! Arena creation will fail."); } else { log.info("Successfully hooked into FastAsyncWorldEdit (FAWE)!"); }
        // setupPartyAndFriends() is now just a placeholder, real check is Bungee-side
        setupPartyAndFriends();


        bossManager = new BossManager(this);
        partyInfoManager = new PartyInfoManager(this);
        guiManager = new GuiManager(this);

        log.info("Scheduling ArenaManager initialization (Delay: 20 ticks)...");
        Bukkit.getScheduler().runTaskLater(this, () -> {
            log.info("Attempting to initialize ArenaManager...");
            String worldName = configManager.getConfig().getString("arena-manager.arena-world-name", "BossEventArenas");
            if (faweApi != null && Bukkit.getWorld(worldName) != null) {
                arenaManager = new ArenaManager(this);
                log.info("ArenaManager initialized successfully!");
                registerListeners();
            } else {
                log.severe("ArenaManager could NOT be initialized (FAWE Hook: " + (faweApi != null) + ", World '" + worldName + "' Loaded: " + (Bukkit.getWorld(worldName) != null) + "). Arena features disabled.");
            }
        }, 20L);

        BossEventCommand bossEventCommandExecutor = new BossEventCommand(this);
        PluginCommand bossEventPluginCommand = getCommand("bossevent");
        if (bossEventPluginCommand != null) {
            bossEventPluginCommand.setExecutor(bossEventCommandExecutor);
            bossEventPluginCommand.setTabCompleter(bossEventCommandExecutor);
            log.info("'/bossevent' command registered.");
        } else {
            log.severe("Could not register '/bossevent' command! Check plugin.yml.");
        }

        this.getServer().getMessenger().registerOutgoingPluginChannel(this, BUNGEE_CHANNEL);
        this.getServer().getMessenger().registerIncomingPluginChannel(this, BUNGEE_CHANNEL, new SpigotPluginMessageListener(this));
        log.info("Registered BungeeCord plugin message channel: " + BUNGEE_CHANNEL);

        log.info(this.getDescription().getName() + " enable sequence initiated.");
        if (configManager.isDebugMode()) {
            log.info("Debug messages will be shown.");
        }
    }

    private void registerListeners() {
        if (mythicMobsApi != null) {
            getServer().getPluginManager().registerEvents(new BossDeathListener(this), this);
            log.info("BossDeathListener registered.");
        } else {
            log.warning("BossDeathListener registration skipped (MythicMobs API missing).");
        }
        getServer().getPluginManager().registerEvents(new PlayerArenaDeathListener(this), this);
        log.info("PlayerArenaDeathListener registered.");
    }


    @Override
    public void onDisable() {
        log.info("--------------------------------------");
        log.info(this.getDescription().getName() + " is disabling...");
        this.getServer().getMessenger().unregisterOutgoingPluginChannel(this, BUNGEE_CHANNEL);
        this.getServer().getMessenger().unregisterIncomingPluginChannel(this, BUNGEE_CHANNEL);
        if (arenaManager != null) {
            arenaManager.shutdown();
        }
        Bukkit.getScheduler().cancelTasks(this);
        log.info(this.getDescription().getName() + " has been disabled.");
        log.info("--------------------------------------");
        instance = null; vaultEconomy = null; mythicMobsApi = null; modelEngineApi = null;
        faweApi = null; configManager = null; bossManager = null; guiManager = null;
        arenaManager = null; partyInfoManager = null;
    }

    private boolean setupEconomy() {
        PluginManager pm = getServer().getPluginManager();
        if (pm.getPlugin("Vault") == null) {
            log.warning("Vault plugin not found. Economy features will be disabled.");
            return false;
        }

        // Check if PlayerPoints plugin is present
        if (pm.getPlugin("PlayerPoints") != null) {
            log.info("PlayerPoints plugin found. Attempting to hook PlayerPoints through Vault.");
            Collection<RegisteredServiceProvider<Economy>> providers = getServer().getServicesManager().getRegistrations(Economy.class);
            for (RegisteredServiceProvider<Economy> provider : providers) {
                // Check the name of the plugin providing the economy service
                // PlayerPoints usually registers with "PlayerPoints" or a similar name.
                // This check might need adjustment if PlayerPoints uses a different internal name for its Vault hook.
                if (provider.getPlugin().getName().equalsIgnoreCase("PlayerPoints")) {
                    vaultEconomy = provider.getProvider();
                    log.info("Successfully hooked into PlayerPoints as the economy provider via Vault!");
                    return true; // Successfully hooked PlayerPoints
                }
            }
            log.warning("PlayerPoints plugin is present, but could not specifically hook its economy service through Vault. Will attempt to use default Vault provider.");
        } else {
            log.info("PlayerPoints plugin not found. Will attempt to use default Vault economy provider.");
        }

        // Fallback to default Vault provider if PlayerPoints isn't found or couldn't be specifically hooked
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            log.warning("No economy provider found through Vault (neither PlayerPoints specifically nor any default).");
            return false;
        }
        vaultEconomy = rsp.getProvider();
        log.info("Successfully hooked into Vault with default economy provider: " + vaultEconomy.getName());
        return true; // vaultEconomy will be non-null if rsp wasn't null
    }

    private boolean setupMythicMobs() {
        if (getServer().getPluginManager().getPlugin("MythicMobs") == null) { log.warning("MythicMobs plugin not found."); return false; }
        try { mythicMobsApi = MythicProvider.get(); return mythicMobsApi != null; }
        catch (Exception e) { log.log(Level.SEVERE, "Could not retrieve MythicMobs API", e); return false; }
    }
    private boolean setupModelEngine() {
        if (getServer().getPluginManager().getPlugin("ModelEngine") == null) { log.warning("ModelEngine plugin not found."); return false; }
        try { modelEngineApi = ModelEngineAPI.getAPI(); return modelEngineApi != null; }
        catch (NoClassDefFoundError | Exception e) { log.log(Level.SEVERE, "Could not retrieve ModelEngine API", e); return false; }
    }
    private boolean setupFAWE() {
        if (getServer().getPluginManager().getPlugin("FastAsyncWorldEdit") == null) { log.warning("FastAsyncWorldEdit plugin not found."); return false; }
        try { faweApi = WorldEdit.getInstance(); return faweApi != null; }
        catch (NoClassDefFoundError ncdfe) { log.severe("FAWE API class (WorldEdit) not found: " + ncdfe.getMessage()); return false; }
        catch (Exception e) { log.log(Level.SEVERE, "Could not retrieve FAWE (WorldEdit) API instance", e); return false; }
    }
    private boolean setupPartyAndFriends() {
        // This method is now less critical as Bungee extension handles PAF.
        // We can keep a basic check or log that Bungee interaction is expected.
        log.info("Party & Friends integration relies on the BungeeCord extension (PAFBE).");
        return true;
    }

    public static BossEventManagerPlugin getInstance() { return instance; }
    public Economy getVaultEconomy() { return vaultEconomy; }
    public MythicPlugin getMythicMobsApi() { return mythicMobsApi; }
    public ModelEngineAPI getModelEngineApi() { return modelEngineApi; }
    public WorldEdit getFAWEApi() { return faweApi; }
    public ConfigManager getConfigManager() { return configManager; }
    public BossManager getBossManager() { return bossManager; }
    public GuiManager getGuiManager() { return guiManager; }
    public ArenaManager getArenaManager() { return arenaManager; }
    public PartyInfoManager getPartyInfoManager() { return partyInfoManager; }

}
