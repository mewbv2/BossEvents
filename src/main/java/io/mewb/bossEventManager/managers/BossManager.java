package io.mewb.bossEventManager.managers;


import io.mewb.bossEventManager.BossEventManagerPlugin;
import io.mewb.bossEventManager.bosses.BossDefinition;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors; // Added for stream operations

public class BossManager {

    private final BossEventManagerPlugin plugin;
    private final Map<String, BossDefinition> bossDefinitions;
    private final Map<String, List<BossDefinition>> bossesByDifficulty; // Added map for quick difficulty lookup

    public BossManager(BossEventManagerPlugin plugin) {
        this.plugin = plugin;
        this.bossDefinitions = new HashMap<>();
        this.bossesByDifficulty = new HashMap<>(); // Initialize map
        loadBosses();
    }

    private void loadBosses() {
        bossDefinitions.clear();
        bossesByDifficulty.clear(); // Clear difficulty map on reload
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
                String difficulty = currentBossSection.getString("difficulty"); // Load difficulty string
                if (difficulty == null || difficulty.isEmpty()) {
                    plugin.getLogger().severe("Boss '" + bossId + "' is missing a 'difficulty'. Skipping.");
                    continue; // Make difficulty mandatory
                }
                String mythicMobId = currentBossSection.getString("mythicmob-id");
                if (mythicMobId == null || mythicMobId.isEmpty()) {
                    plugin.getLogger().severe("Boss '" + bossId + "' is missing a 'mythicmob-id'. Skipping.");
                    continue;
                }

                String modelEngineId = currentBossSection.getString("modelengine-id");
                List<String> description = currentBossSection.getStringList("description");
                int gemCost = currentBossSection.getInt("gem-cost", plugin.getConfigManager().getConfig().getInt("economy.default-gem-cost", 100));
                int requiredLevel = currentBossSection.getInt("required-level", 0);
                // String arenaTheme = currentBossSection.getString("arena-theme"); // REMOVED
                List<String> rewardsCommands = currentBossSection.getStringList("rewards");

                Map<String, Double> partySizeScaling = new HashMap<>();
                ConfigurationSection scalingSection = currentBossSection.getConfigurationSection("party-size-scaling");
                if (scalingSection != null) {
                    scalingSection.getKeys(false).forEach(key -> {
                        if (scalingSection.isDouble(key) || scalingSection.isInt(key)) {
                            partySizeScaling.put(key, scalingSection.getDouble(key));
                        }
                    });
                }

                BossDefinition definition = new BossDefinition(
                        bossId, displayName, difficulty, mythicMobId, modelEngineId,
                        description, gemCost, requiredLevel, /* Removed arenaTheme */
                        rewardsCommands, partySizeScaling.isEmpty() ? null : partySizeScaling
                );

                bossDefinitions.put(bossId.toLowerCase(), definition);

                // Add to difficulty map (case-insensitive difficulty key)
                bossesByDifficulty.computeIfAbsent(difficulty.toLowerCase(), k -> new ArrayList<>()).add(definition);

                if (plugin.getConfigManager().isDebugMode()) {
                    plugin.getLogger().info("Loaded boss definition: " + bossId + " (Difficulty: " + difficulty + ")");
                }

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load boss definition for ID: " + bossId, e);
            }
        }
        plugin.getLogger().info("Successfully loaded " + bossDefinitions.size() + " boss definitions across " + bossesByDifficulty.size() + " difficulties.");
    }

    public BossDefinition getBossDefinition(String id) {
        if (id == null) return null;
        return bossDefinitions.get(id.toLowerCase());
    }

    public Collection<BossDefinition> getAllBossDefinitions() {
        return Collections.unmodifiableCollection(bossDefinitions.values());
    }

    /**
     * Gets all unique difficulty levels defined for loaded bosses.
     * @return A collection of difficulty strings (case-preserved from first encountered).
     */
    public Collection<String> getAvailableDifficulties() {
        // Return keys from the difficulty map, preserving original casing if needed
        // This gives unique keys used. For original casing, we might need another approach during loading.
        // Returning the keys from the bossesByDifficulty map (which are lowercased) might be sufficient for internal use.
        // For display, we might want to retrieve the original casing from the first boss loaded for that difficulty.
        return bossesByDifficulty.values().stream()
                .filter(list -> !list.isEmpty())
                .map(list -> list.get(0).getDifficulty()) // Get original casing from first boss in list
                .collect(Collectors.toSet()); // Use Set for uniqueness
    }

    /**
     * Gets all bosses matching a specific difficulty level (case-insensitive).
     * @param difficulty The difficulty string.
     * @return A list of BossDefinitions for that difficulty, or an empty list if none found.
     */
    public List<BossDefinition> getBossesByDifficulty(String difficulty) {
        if (difficulty == null) return Collections.emptyList();
        // Return an unmodifiable list or a copy to prevent external modification
        return Collections.unmodifiableList(
                bossesByDifficulty.getOrDefault(difficulty.toLowerCase(), Collections.emptyList())
        );
    }


    public void reloadBosses() {
        plugin.getLogger().info("Reloading boss definitions...");
        loadBosses();
    }
}