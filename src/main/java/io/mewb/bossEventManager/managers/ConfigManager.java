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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private String errorPrefix; // Store the resolved error prefix

    public ConfigManager(BossEventManagerPlugin plugin) {
        this.plugin = plugin;
        this.messages = new HashMap<>(); // Initialize map
        setup();
        loadConfigValues();
    }

    /**
     * Sets up the config file and loads it.
     * If the config file doesn't exist, it creates it from the default in the JAR.
     */
    private void setup() {
        if (!plugin.getDataFolder().exists()) {
            boolean created = plugin.getDataFolder().mkdir();
            if (!created) {
                plugin.getLogger().severe("Could not create plugin data folder!");
            }
        }
        configFile = new File(plugin.getDataFolder(), "config.yml");

        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false); // Copies from src/main/resources/config.yml
            plugin.getLogger().info("config.yml not found, created a new one.");
        }

        config = YamlConfiguration.loadConfiguration(configFile);
        // Ensure defaults are loaded if the file was just created or is missing sections
        InputStream defaultConfigStream = plugin.getResource("config.yml");
        if (defaultConfigStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultConfigStream));
            config.setDefaults(defaultConfig);
            config.options().copyDefaults(true); // Copy defaults for any missing paths
            saveConfig(); // Save to write defaults to disk if any were added
        }
    }

    /**
     * Loads values from the configuration file into memory.
     */
    private void loadConfigValues() {
        prefix = ChatColor.translateAlternateColorCodes('&', config.getString("plugin-prefix", "&8[&bEvents&8] &r"));
        minPartySize = config.getInt("party.min-size", 2);
        maxPartySize = config.getInt("party.max-size", 5);
        debugMode = config.getBoolean("debug-mode", false);

        // Load messages
        messages.clear();
        // Load error prefix first, as other messages might use it
        errorPrefix = ChatColor.translateAlternateColorCodes('&', config.getString("messages.error-prefix", "&c&lError: &r"));

        ConfigurationSection msgSection = config.getConfigurationSection("messages");
        if (msgSection != null) {
            for (String key : msgSection.getKeys(false)) {
                if (!key.equals("error-prefix")) { // Don't re-add error-prefix itself to messages map
                    String message = msgSection.getString(key);
                    if (message != null) {
                        // Replace %error-prefix% placeholder within each message string
                        message = message.replace("%error-prefix%", errorPrefix);
                        messages.put(key, message);
                    }
                }
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

    /**
     * Gets the main configuration object.
     *
     * @return The FileConfiguration object.
     */
    public FileConfiguration getConfig() {
        if (config == null) {
            reloadConfig(); // Should not happen if setup is called in constructor
        }
        return config;
    }

    /**
     * Saves the current configuration to disk.
     */
    public void saveConfig() {
        if (config == null || configFile == null) {
            return;
        }
        try {
            getConfig().save(configFile);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save config to " + configFile, ex);
        }
    }

    /**
     * Reloads the configuration from disk.
     */
    public void reloadConfig() {
        if (configFile == null) {
            configFile = new File(plugin.getDataFolder(), "config.yml");
        }
        config = YamlConfiguration.loadConfiguration(configFile);

        // Look for defaults in the jar
        InputStream defaultConfigStream = plugin.getResource("config.yml");
        if (defaultConfigStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultConfigStream));
            config.setDefaults(defaultConfig);
        }
        loadConfigValues(); // Reload values into memory
        plugin.getLogger().info("Configuration reloaded.");
    }

    // --- Getters for specific configuration values ---

    public String getPrefix() {
        return prefix;
    }

    public int getMinPartySize() {
        return minPartySize;
    }

    public int getMaxPartySize() {
        return maxPartySize;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    // --- Message Getter ---
    /**
     * Gets a configured message, translates color codes, and replaces placeholders.
     * Returns the key itself (colored red) if the message is not found.
     *
     * @param key          The key of the message in the config.yml messages section.
     * @param replacements Optional map of placeholders (like "%player%") to their values.
     * @return The formatted message string.
     */
    public String getMessage(String key, Map<String, String> replacements) {
        // Retrieve the raw message template (which already has %error-prefix% resolved)
        String messageTemplate = messages.get(key);

        if (messageTemplate == null) {
            plugin.getLogger().warning("Missing message in config.yml for key: '" + key + "'");
            return ChatColor.RED + "Missing message: " + key;
        }

        // Apply the main plugin prefix
        String message = messageTemplate.replace("%prefix%", prefix);

        // Apply any other dynamic replacements
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


    /**
     * Gets a string from the config, with color codes translated.
     * @param path The path to the string.
     * @param defaultValue The default value if the path is not found.
     * @return The colored string.
     */
    public String getColoredString(String path, String defaultValue) {
        return ChatColor.translateAlternateColorCodes('&', config.getString(path, defaultValue));
    }

    /**
     * Gets a list of strings from the config, with color codes translated.
     * @param path The path to the list of strings.
     * @return The list of colored strings.
     */
    public List<String> getColoredStringList(String path) {
        List<String> list = config.getStringList(path);
        list.replaceAll(s -> ChatColor.translateAlternateColorCodes('&', s));
        return list;
    }
}
