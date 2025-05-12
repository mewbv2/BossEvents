package io.mewb.bossEventManager.managers;


import io.mewb.bossEventManager.BossEventManagerPlugin;
import io.mewb.bossEventManager.bosses.BossDefinition;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemorySection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class BossManager {

    private final BossEventManagerPlugin plugin;
    private final Map<String, BossDefinition> bossDefinitions;
    private final Map<String, List<BossDefinition>> bossesByDifficulty;

    public BossManager(BossEventManagerPlugin plugin) {
        this.plugin = plugin;
        this.bossDefinitions = new HashMap<>();
        this.bossesByDifficulty = new HashMap<>();
        loadBosses();
    }

    private void loadBosses() {
        bossDefinitions.clear();
        bossesByDifficulty.clear();
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
                String difficulty = currentBossSection.getString("difficulty");
                if (difficulty == null || difficulty.isEmpty()) {
                    plugin.getLogger().severe("Boss '" + bossId + "' is missing a 'difficulty'. Skipping.");
                    continue;
                }
                String mythicMobId = currentBossSection.getString("mythicmob-id");
                if (mythicMobId == null || mythicMobId.isEmpty()) {
                    plugin.getLogger().severe("Boss '" + bossId + "' is missing a 'mythicmob-id'. Skipping.");
                    continue;
                }
                String finalPhaseId = currentBossSection.getString("final-phase-mythicmob-id", mythicMobId);
                String modelEngineId = currentBossSection.getString("modelengine-id");
                List<String> description = currentBossSection.getStringList("description");
                int gemCost = currentBossSection.getInt("gem-cost", plugin.getConfigManager().getConfig().getInt("economy.default-gem-cost", 100));
                int requiredLevel = currentBossSection.getInt("required-level", 0);

                List<Map<?, ?>> rewardConfigMaps = currentBossSection.getMapList("rewards");
                if (rewardConfigMaps.isEmpty() && currentBossSection.isList("rewards")) {
                    List<String> oldRewardStrings = currentBossSection.getStringList("rewards");
                    rewardConfigMaps = new ArrayList<>();
                    for (String cmd : oldRewardStrings) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("command", cmd);
                        map.put("chance", 1.0);
                        rewardConfigMaps.add(map);
                    }
                    if (!oldRewardStrings.isEmpty()) {
                        plugin.getLogger().info("Boss '" + bossId + "' uses old reward string format. Converted to new format with 100% chance.");
                    }
                }

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
                        bossId, displayName, difficulty, mythicMobId, finalPhaseId, modelEngineId,
                        description, gemCost, requiredLevel,
                        rewardConfigMaps, partySizeScaling.isEmpty() ? null : partySizeScaling
                );

                bossDefinitions.put(bossId.toLowerCase(), definition);
                bossesByDifficulty.computeIfAbsent(difficulty.toLowerCase(), k -> new ArrayList<>()).add(definition);

                // plugin.getLogger().info("Loaded boss definition: " + bossId + " (Difficulty: " + difficulty + ")"); // Optional debug

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

    public Collection<String> getAvailableDifficulties() {
        return bossesByDifficulty.values().stream()
                .filter(list -> !list.isEmpty())
                .map(list -> list.get(0).getDifficulty())
                .collect(Collectors.toSet());
    }

    public List<BossDefinition> getBossesByDifficulty(String difficulty) {
        if (difficulty == null) return Collections.emptyList();
        return Collections.unmodifiableList(
                bossesByDifficulty.getOrDefault(difficulty.toLowerCase(), Collections.emptyList())
        );
    }

    public void reloadBosses() {
        plugin.getLogger().info("Reloading boss definitions...");
        loadBosses();
    }
}
