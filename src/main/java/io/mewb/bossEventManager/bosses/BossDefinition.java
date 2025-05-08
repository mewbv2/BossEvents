package io.mewb.bossEventManager.bosses;

import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BossDefinition {

    private final String id;
    private final String displayName;
    private final String difficulty; // Added difficulty field
    private final String mythicMobId;
    private final String modelEngineId;
    private final List<String> description;
    private final int gemCost;
    private final int requiredLevel;
    // Removed final String arenaTheme;
    private final List<String> rewardsCommands;
    private final Map<String, Double> partySizeScaling;

    public BossDefinition(String id, String displayName, String difficulty, String mythicMobId, String modelEngineId,
                          List<String> description, int gemCost, int requiredLevel, /* Removed arenaTheme */
                          List<String> rewardsCommands, Map<String, Double> partySizeScaling) {
        this.id = id;
        this.displayName = ChatColor.translateAlternateColorCodes('&', displayName);
        this.difficulty = difficulty; // Store difficulty
        this.mythicMobId = mythicMobId;
        this.modelEngineId = modelEngineId;
        this.description = new ArrayList<>();
        if (description != null) {
            description.forEach(line -> this.description.add(ChatColor.translateAlternateColorCodes('&', line)));
        }
        this.gemCost = gemCost;
        this.requiredLevel = requiredLevel;
        // this.arenaTheme = arenaTheme; // Removed
        this.rewardsCommands = rewardsCommands != null ? new ArrayList<>(rewardsCommands) : new ArrayList<>();
        this.partySizeScaling = partySizeScaling;
    }

    // --- Getters ---
    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getDifficulty() { return difficulty; } // Getter for difficulty
    public String getMythicMobId() { return mythicMobId; }
    public String getModelEngineId() { return modelEngineId; }
    public List<String> getDescription() { return description; }
    public int getGemCost() { return gemCost; }
    public int getRequiredLevel() { return requiredLevel; }
    // public String getArenaTheme() { return arenaTheme; } // Removed
    public List<String> getRewardsCommands() { return rewardsCommands; }
    public Map<String, Double> getPartySizeScaling() { return partySizeScaling; }

    // --- Utility methods (scaling examples) ---
    // ... (scaling methods remain the same) ...
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