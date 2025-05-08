package io.mewb.bossEventManager.managers; // Assuming managers will be in a subpackage

import io.mewb.bossEventManager.BossEventManagerPlugin;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.logging.Level;

public class ConfigManager {

    private final BossEventManagerPlugin plugin;
    private FileConfiguration config;
    private File configFile;

    // Example configuration values (we'll add more as needed)
    private String prefix;
    private int minPartySize;
    private int maxPartySize;
    private boolean debugMode;

    public ConfigManager(BossEventManagerPlugin plugin) {
        this.plugin = plugin;
        setup();
        loadConfigValues();
    }

    /**
     * Sets up the config file and loads it.
     * If the config file doesn't exist, it creates it from the default in the JAR.
     */
    private void setup() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdir();
        }
        configFile = new File(plugin.getDataFolder(), "config.yml");

        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false); // Copies from src/main/resources/config.yml
            plugin.getLogger().info("config.yml not found, created a new one.");
        }

        config = YamlConfiguration.loadConfiguration(configFile);
        // Load defaults from JAR if any are missing (optional, saveResource usually handles this for new files)
        // YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(plugin.getResource("config.yml")));
        // config.setDefaults(defaultConfig);
        // config.options().copyDefaults(true);
        // saveConfig(); // Save to include any newly added defaults
    }

    /**
     * Loads values from the configuration file into memory.
     */
    private void loadConfigValues() {
        prefix = ChatColor.translateAlternateColorCodes('&', config.getString("plugin-prefix", "&8[&bBossEvents&8] &r"));
        minPartySize = config.getInt("party.min-size", 2);
        maxPartySize = config.getInt("party.max-size", 5);
        debugMode = config.getBoolean("debug-mode", false);

        // Example of loading a list
        // List<String> exampleList = config.getStringList("some.list.of.strings");

        // Example of loading a section (for boss definitions, arena definitions, etc.)
        // if (config.isConfigurationSection("bosses")) {
        //     for (String bossKey : config.getConfigurationSection("bosses").getKeys(false)) {
        //         String name = config.getString("bosses." + bossKey + ".name");
        //         int cost = config.getInt("bosses." + bossKey + ".cost");
        //         // ... load other boss properties
        //     }
        // }
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
            reloadConfig();
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
