package io.mewb.bossEventManager.managers;


import io.mewb.bossEventManager.BossEventManagerPlugin;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap; // Import HashMap
import java.util.List;
import java.util.Map; // Import Map
import java.util.logging.Level;

public class ConfigManager {

    private final BossEventManagerPlugin plugin;
    private FileConfiguration config;
    private File configFile;

    // Config values
    private String prefix;
    private int minPartySize;
    private int maxPartySize;
    private boolean debugMode;
    private Map<String, String> messages; // Store loaded messages

    public ConfigManager(BossEventManagerPlugin plugin) {
        this.plugin = plugin;
        this.messages = new HashMap<>(); // Initialize map
        setup();
        loadConfigValues();
    }

    private void setup() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdir();
        }
        configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
            plugin.getLogger().info("config.yml not found, created a new one.");
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    private void loadConfigValues() {
        prefix = ChatColor.translateAlternateColorCodes('&', config.getString("plugin-prefix", "&8[&bBossEvents&8] &r"));
        minPartySize = config.getInt("party.min-size", 2);
        maxPartySize = config.getInt("party.max-size", 5);
        debugMode = config.getBoolean("debug-mode", false);

        // Load messages
        messages.clear();
        ConfigurationSection msgSection = config.getConfigurationSection("messages");
        if (msgSection != null) {
            for (String key : msgSection.getKeys(false)) {
                messages.put(key, msgSection.getString(key));
            }
            plugin.getLogger().info("Loaded " + messages.size() + " custom messages.");
        } else {
            plugin.getLogger().warning("No 'messages' section found in config.yml. Using default messages where applicable.");
        }

        plugin.getLogger().info("Configuration values loaded.");
        if (debugMode) {
            plugin.getLogger().info("Debug mode is ENABLED.");
        }
    }

    public FileConfiguration getConfig() {
        if (config == null) { reloadConfig(); }
        return config;
    }

    public void saveConfig() { /* ... */
        if (config == null || configFile == null) { return; }
        try { getConfig().save(configFile); }
        catch (IOException ex) { plugin.getLogger().log(Level.SEVERE, "Could not save config to " + configFile, ex); }
    }

    public void reloadConfig() {
        if (configFile == null) { configFile = new File(plugin.getDataFolder(), "config.yml"); }
        config = YamlConfiguration.loadConfiguration(configFile);
        InputStream defaultConfigStream = plugin.getResource("config.yml");
        if (defaultConfigStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultConfigStream));
            config.setDefaults(defaultConfig);
        }
        loadConfigValues(); // Reload values into memory
        plugin.getLogger().info("Configuration reloaded.");
    }

    // --- Getters for specific configuration values ---
    public String getPrefix() { return prefix; }
    public int getMinPartySize() { return minPartySize; }
    public int getMaxPartySize() { return maxPartySize; }
    public boolean isDebugMode() { return debugMode; }

    // --- Message Getter ---
    /**
     * Gets a configured message, translates color codes, and replaces placeholders.
     * Returns the key itself if the message is not found.
     *
     * @param key          The key of the message in the config.yml messages section.
     * @param replacements Optional map of placeholders (like "%player%") to their values.
     * @return The formatted message string.
     */
    public String getMessage(String key, Map<String, String> replacements) {
        String message = messages.getOrDefault(key, "&cMissing message for key: " + key); // Get message or default
        message = message.replace("%prefix%", prefix); // Always replace prefix

        // Replace other placeholders if provided
        if (replacements != null) {
            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                message = message.replace(entry.getKey(), entry.getValue());
            }
        }

        return ChatColor.translateAlternateColorCodes('&', message);
    }

    // Overload for messages without extra placeholders
    public String getMessage(String key) {
        return getMessage(key, null);
    }


    public String getColoredString(String path, String defaultValue) {
        return ChatColor.translateAlternateColorCodes('&', config.getString(path, defaultValue));
    }
    public List<String> getColoredStringList(String path) {
        List<String> list = config.getStringList(path);
        list.replaceAll(s -> ChatColor.translateAlternateColorCodes('&', s));
        return list;
    }
}