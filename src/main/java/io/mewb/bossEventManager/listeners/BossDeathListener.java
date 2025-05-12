package io.mewb.bossEventManager.listeners;


import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import io.mewb.bossEventManager.BossEventManagerPlugin;
import io.mewb.bossEventManager.arena.ArenaInstance;
import io.mewb.bossEventManager.bosses.BossDefinition;
import io.mewb.bossEventManager.managers.ArenaManager;
import io.mewb.bossEventManager.managers.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.HashMap; // Keep HashMap if used for placeholders
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class BossDeathListener implements Listener {

    private final BossEventManagerPlugin plugin;
    private final ArenaManager arenaManager;
    private final ConfigManager configManager;
    private final Random random = new Random(); // For chance-based rewards

    public BossDeathListener(BossEventManagerPlugin plugin) {
        this.plugin = plugin;
        this.arenaManager = plugin.getArenaManager();
        this.configManager = plugin.getConfigManager();
    }

    @EventHandler
    public void onMythicMobDeath(MythicMobDeathEvent event) {
        if (arenaManager == null) {
            return;
        }

        Entity deadEntity = event.getEntity();
        UUID deadMobUUID = deadEntity.getUniqueId();
        String deadMobTypeName = event.getMobType().getInternalName(); // Get the MythicMob type name

        ArenaInstance arenaInstance = null;

        ArenaInstance instanceByUUID = arenaManager.getActiveArenaInstanceByBossUUID(deadMobUUID);
        if (instanceByUUID != null && instanceByUUID.getState() == ArenaInstance.ArenaState.IN_USE) {
            arenaInstance = instanceByUUID;
        } else {
            Location deathLocation = deadEntity.getLocation();
            for (ArenaInstance activeInstance : new ArrayList<>(arenaManager.getActiveArenaInstances())) {
                if (activeInstance.getState() == ArenaInstance.ArenaState.IN_USE && activeInstance.getCurrentBoss() != null) {
                    BossDefinition bossDef = activeInstance.getCurrentBoss();
                    boolean isInitialPhase = deadMobTypeName.equalsIgnoreCase(bossDef.getMythicMobId());
                    boolean isFinalPhase = deadMobTypeName.equalsIgnoreCase(bossDef.getFinalPhaseMythicMobId());

                    if (isInitialPhase || isFinalPhase) {
                        Location plotOrigin = activeInstance.getPlotOrigin();
                        if (plotOrigin != null && deathLocation.getWorld().equals(plotOrigin.getWorld())) {
                            double maxDistanceSquared = 250 * 250;
                            if (deathLocation.distanceSquared(plotOrigin) < maxDistanceSquared) {
                                arenaInstance = activeInstance;
                                // plugin.getLogger().info("Found matching arena instance " + arenaInstance.getInstanceId() + " for mob type " + deadMobTypeName + " by location."); // Commented out
                                break;
                            }
                        }
                    }
                }
            }
        }

        if (arenaInstance == null) {
            return;
        }

        BossDefinition currentBossDef = arenaInstance.getCurrentBoss();
        if (currentBossDef == null) {
            plugin.getLogger().warning("Tracked boss " + deadMobUUID + " died in arena " + arenaInstance.getInstanceId() + " but BossDefinition was null in instance.");
            arenaManager.endEvent(arenaInstance);
            return;
        }

        // plugin.getLogger().info("Processing death of MythicMob type '" + deadMobTypeName + "' (Entity UUID: " + deadMobUUID + ") in arena instance: " + arenaInstance.getInstanceId()); // Commented out

        String finalPhaseId = currentBossDef.getFinalPhaseMythicMobId();

        if (!deadMobTypeName.equalsIgnoreCase(finalPhaseId)) {
            // plugin.getLogger().info("Boss phase '" + deadMobTypeName + "' defeated in arena " + arenaInstance.getInstanceId() + ". This is not the final phase ('" + finalPhaseId + "'). Event continues, waiting for next phase."); // Commented out
            if (deadMobUUID.equals(arenaInstance.getBossEntityUUID())) {
                arenaInstance.setBossEntityUUID(null);
                // plugin.getLogger().info("Cleared tracked boss UUID for instance " + arenaInstance.getInstanceId() + " as it was not the final phase."); // Commented out
            }
            return;
        }

        // plugin.getLogger().info("Final phase '" + deadMobTypeName + "' defeated for boss " + currentBossDef.getDisplayName() + " in arena " + arenaInstance.getInstanceId() + ". Processing rewards and ending event."); // Commented out

        List<UUID> partyMemberUUIDs = arenaInstance.getPartyMemberUUIDs();
        if (currentBossDef.getRewards() != null && partyMemberUUIDs != null && !partyMemberUUIDs.isEmpty()) {
            List<BossDefinition.RewardItem> rewardItems = currentBossDef.getRewards();
            // plugin.getLogger().info("Processing " + rewardItems.size() + " potential reward items for " + partyMemberUUIDs.size() + " players."); // Commented out

            List<String> playerNamesForBroadcast = new ArrayList<>();

            for (UUID playerUUID : partyMemberUUIDs) {
                Player player = Bukkit.getPlayer(playerUUID);
                String playerName = "A_Brave_Adventurer";
                if (player != null && player.isOnline()) {
                    playerName = player.getName();
                    if (arenaInstance.getOriginalPlayerLocations().containsKey(playerUUID)) {
                        playerNamesForBroadcast.add(playerName);
                    }
                }

                for (BossDefinition.RewardItem reward : rewardItems) {
                    if (random.nextDouble() <= reward.getChance()) {
                        String processedCommand = reward.getCommand()
                                .replace("%player%", playerName)
                                .replace("%player_uuid%", playerUUID.toString())
                                .replace("%boss_name%", ChatColor.stripColor(currentBossDef.getDisplayName()))
                                .replace("%arena_id%", arenaInstance.getInstanceId().toString());
                        try {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand);
                        } catch (Exception e) {
                            plugin.getLogger().log(Level.SEVERE, "Error dispatching reward command: " + processedCommand, e);
                        }
                    }
                }
                if (player != null && player.isOnline()) {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("%boss_name%", currentBossDef.getDisplayName());
                    placeholders.put("%player_list%", "Your party");
                    player.sendMessage(configManager.getMessage("boss-defeated-broadcast", placeholders));
                }
            }

            String playerListString = String.join(", ", playerNamesForBroadcast);
            if (playerListString.isEmpty() && !partyMemberUUIDs.isEmpty()) {
                playerListString = "A brave party";
            }

            if (!playerListString.isEmpty()) {
                Map<String, String> broadcastPlaceholders = new HashMap<>();
                broadcastPlaceholders.put("%player_list%", playerListString);
                broadcastPlaceholders.put("%boss_name%", currentBossDef.getDisplayName());
                String globalBroadcastMsg = configManager.getMessage("boss-defeated-broadcast-global", broadcastPlaceholders);
                if (!globalBroadcastMsg.contains("Missing message")) {
                    Bukkit.broadcastMessage(globalBroadcastMsg);
                }
            }

        } else {
            plugin.getLogger().warning("Could not process rewards for arena " + arenaInstance.getInstanceId() + ": Missing boss rewards or party members.");
        }

        final ArenaInstance finalInstanceToClose = arenaInstance;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (arenaManager != null) {
                arenaManager.endEvent(finalInstanceToClose);
            }
        }, 60L);
    }
}