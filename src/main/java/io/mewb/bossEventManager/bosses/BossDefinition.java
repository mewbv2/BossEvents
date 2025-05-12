package io.mewb.bossEventManager.bosses;

import org.bukkit.ChatColor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class BossDefinition {

    public static class RewardItem {
        private final String command;
        private final double chance;

        public RewardItem(String command, double chance) {
            this.command = command;
            this.chance = Math.max(0.0, Math.min(1.0, chance));
        }
        public String getCommand() { return command; }
        public double getChance() { return chance; }
    }

    private final String id;
    private final String displayName;
    private final String difficulty;
    private final String mythicMobId;
    private final String finalPhaseMythicMobId;
    private final String modelEngineId;
    private final List<String> description;
    private final int gemCost;
    private final int requiredLevel;
    private final List<RewardItem> rewards;
    private final Map<String, Double> partySizeScaling;

    public BossDefinition(String id, String displayName, String difficulty, String mythicMobId,
                          String finalPhaseMythicMobId, String modelEngineId,
                          List<String> description, int gemCost, int requiredLevel,
                          List<Map<?, ?>> rewardConfigMaps, Map<String, Double> partySizeScaling) {
        this.id = id;
        this.displayName = ChatColor.translateAlternateColorCodes('&', displayName);
        this.difficulty = difficulty;
        this.mythicMobId = mythicMobId;
        this.finalPhaseMythicMobId = (finalPhaseMythicMobId == null || finalPhaseMythicMobId.isEmpty()) ? mythicMobId : finalPhaseMythicMobId;
        this.modelEngineId = modelEngineId;
        this.description = new ArrayList<>();
        if (description != null) {
            description.forEach(line -> this.description.add(ChatColor.translateAlternateColorCodes('&', line)));
        }
        this.gemCost = gemCost;
        this.requiredLevel = requiredLevel;
        this.rewards = new ArrayList<>();
        if (rewardConfigMaps != null) {
            for (Map<?, ?> rewardMap : rewardConfigMaps) {
                Object commandObj = rewardMap.get("command");
                Object chanceObj = rewardMap.get("chance");
                if (commandObj instanceof String) {
                    String command = (String) commandObj;
                    double chance = 1.0;
                    if (chanceObj instanceof Number) {
                        chance = ((Number) chanceObj).doubleValue();
                    }
                    this.rewards.add(new RewardItem(command, chance));
                }
            }
        }
        this.partySizeScaling = partySizeScaling;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getDifficulty() { return difficulty; }
    public String getMythicMobId() { return mythicMobId; }
    public String getFinalPhaseMythicMobId() { return finalPhaseMythicMobId; }
    public String getModelEngineId() { return modelEngineId; }
    public List<String> getDescription() { return Collections.unmodifiableList(description); }
    public int getGemCost() { return gemCost; }
    public int getRequiredLevel() { return requiredLevel; }
    public List<RewardItem> getRewards() { return Collections.unmodifiableList(rewards); }
    public Map<String, Double> getPartySizeScaling() { return partySizeScaling; }

    public double getScaledHealth(int partySize) {
        if (partySizeScaling != null && partySizeScaling.containsKey("health-per-member")) {
            if (partySize > 1) { return (partySize -1) * partySizeScaling.get("health-per-member"); }
        }
        return 0;
    }
    public double getDamageMultiplier(int partySize) {
        if (partySizeScaling != null && partySizeScaling.containsKey("damage-multiplier-per-member")) {
            if (partySize > 1) { return 1.0 + ((partySize - 1) * partySizeScaling.get("damage-multiplier-per-member")); }
        }
        return 1.0;
    }
}