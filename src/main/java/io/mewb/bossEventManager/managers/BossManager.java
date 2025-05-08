package io.mewb.bossEventManager.managers;


import io.mewb.bossEventManager.BossEventManagerPlugin;
import io.mewb.bossEventManager.bosses.BossDefinition;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class BossManager {

    private final BossEventManagerPlugin plugin;
    private final Map<String, BossDefinition> bossDefinitions;

    public BossManager(BossEventManagerPlugin plugin) {
        this.plugin = plugin;
        this.bossDefinitions = new HashMap<>();
        loadBosses();
    }

    @SuppressWarnings("unchecked") // For casting partySizeScaling map
    private void loadBosses() {
        bossDefinitions.clear(); // Clear any existing definitions if reloading
        ConfigurationSection bossesSection = plugin.getConfigManager().getConfig().getConfigurationSection("bosses");

        if (bossesSection == null) {
            plugin.getLogger().warning("No 'bosses' section found in config.yml. No bosses will be loaded.");
            return;
        }

        for (String bossId : bossesSection.getKeys(false)) {
            ConfigurationSection currentBossSection = bossesSection.getConfigurationSection(bossId);
            if (currentBossSection == null) {
                plugin.getLogger().warning("Skipping invalid boss configuration for ID: " + bossId);
                continue;
            }

            try {
                String displayName = currentBossSection.getString("display-name", "&cUnnamed Boss");
                String mythicMobId = currentBossSection.getString("mythicmob-id");
                if (mythicMobId == null || mythicMobId.isEmpty()) {
                    plugin.getLogger().severe("Boss '" + bossId + "' is missing a 'mythicmob-id'. Skipping.");
                    continue;
                }

                String modelEngineId = currentBossSection.getString("modelengine-id"); // Optional
                List<String> description = currentBossSection.getStringList("description");
                int gemCost = currentBossSection.getInt("gem-cost", plugin.getConfigManager().getConfig().getInt("economy.default-gem-cost", 100));
                int requiredLevel = currentBossSection.getInt("required-level", 0); // 0 or less means no requirement
                String arenaTheme = currentBossSection.getString("arena-theme"); // Optional
                List<String> rewardsCommands = currentBossSection.getStringList("rewards");

                Map<String, Double> partySizeScaling = new HashMap<>();
                ConfigurationSection scalingSection = currentBossSection.getConfigurationSection("party-size-scaling");
                if (scalingSection != null) {
                    for (String key : scalingSection.getKeys(false)) {
                        if (scalingSection.isDouble(key) || scalingSection.isInt(key)) {
                            partySizeScaling.put(key, scalingSection.getDouble(key));
                        }
                    }
                }

                BossDefinition definition = new BossDefinition(
                        bossId, displayName, mythicMobId, modelEngineId,
                        description, gemCost, requiredLevel, arenaTheme,
                        rewardsCommands, partySizeScaling.isEmpty() ? null : partySizeScaling
                );

                bossDefinitions.put(bossId.toLowerCase(), definition); // Store with lowercase ID for case-insensitive lookup
                if (plugin.getConfigManager().isDebugMode()) {
                    plugin.getLogger().info("Loaded boss definition: " + bossId);
                }

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load boss definition for ID: " + bossId, e);
            }
        }
        plugin.getLogger().info("Successfully loaded " + bossDefinitions.size() + " boss definitions.");
    }

    /**
     * Gets a boss definition by its unique ID.
     *
     * @param id The case-insensitive ID of the boss.
     * @return The BossDefinition, or null if not found.
     */
    public BossDefinition getBossDefinition(String id) {
        if (id == null) return null;
        return bossDefinitions.get(id.toLowerCase());
    }

    /**
     * Gets all loaded boss definitions.
     *
     * @return An unmodifiable collection of all boss definitions.
     */
    public Collection<BossDefinition> getAllBossDefinitions() {
        return Collections.unmodifiableCollection(bossDefinitions.values());
    }

    /**
     * Reloads boss definitions from the configuration.
     */
    public void reloadBosses() {
        plugin.getLogger().info("Reloading boss definitions...");
        loadBosses();
    }
}
