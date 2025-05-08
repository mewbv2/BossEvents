package io.mewb.bossEventManager.bosses; // New subpackage for boss-related classes

import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BossDefinition {

    private final String id; // Internal ID, e.g., "skeleton_king"
    private final String displayName;
    private final String mythicMobId;
    private final String modelEngineId; // Optional
    private final List<String> description;
    private final int gemCost;
    private final int requiredLevel; // Optional
    private final String arenaTheme; // Optional, links to an arena configuration
    private final List<String> rewardsCommands; // Commands to run as rewards
    private final Map<String, Double> partySizeScaling; // e.g., "health-per-member": 500.0

    public BossDefinition(String id, String displayName, String mythicMobId, String modelEngineId,
                          List<String> description, int gemCost, int requiredLevel, String arenaTheme,
                          List<String> rewardsCommands, Map<String, Double> partySizeScaling) {
        this.id = id;
        this.displayName = ChatColor.translateAlternateColorCodes('&', displayName);
        this.mythicMobId = mythicMobId;
        this.modelEngineId = modelEngineId;
        this.description = new ArrayList<>();
        if (description != null) {
            description.forEach(line -> this.description.add(ChatColor.translateAlternateColorCodes('&', line)));
        }
        this.gemCost = gemCost;
        this.requiredLevel = requiredLevel;
        this.arenaTheme = arenaTheme;
        this.rewardsCommands = rewardsCommands != null ? new ArrayList<>(rewardsCommands) : new ArrayList<>();
        this.partySizeScaling = partySizeScaling;
    }

    // --- Getters ---
    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getMythicMobId() {
        return mythicMobId;
    }

    public String getModelEngineId() {
        return modelEngineId;
    }

    public List<String> getDescription() {
        return description;
    }

    public int getGemCost() {
        return gemCost;
    }

    public int getRequiredLevel() {
        return requiredLevel;
    }

    public String getArenaTheme() {
        return arenaTheme;
    }

    public List<String> getRewardsCommands() {
        return rewardsCommands;
    }

    public Map<String, Double> getPartySizeScaling() {
        return partySizeScaling;
    }

    // --- Utility methods (optional) ---
    public double getScaledHealth(int partySize) {
        if (partySizeScaling != null && partySizeScaling.containsKey("health-per-member")) {
            // This is a simplistic example. MythicMobs might handle scaling directly.
            // Or you might apply this to a base health value from MythicMobs.
            // For now, let's assume this is an *additional* value per member beyond the first.
            if (partySize > 1) {
                return (partySize -1) * partySizeScaling.get("health-per-member");
            }
        }
        return 0; // No additional health or base health is handled by MythicMobs
    }

    public double getDamageMultiplier(int partySize) {
        if (partySizeScaling != null && partySizeScaling.containsKey("damage-multiplier-per-member")) {
            // Example: 0.1 means 10% extra damage per member beyond the first.
            // Multiplier is 1.0 + (partySize - 1) * value
            if (partySize > 1) {
                return 1.0 + ((partySize - 1) * partySizeScaling.get("damage-multiplier-per-member"));
            }
        }
        return 1.0; // Default damage multiplier
    }
}