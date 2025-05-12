package io.mewb.bossEventManager.listeners;

import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;

import io.mewb.bossEventManager.BossEventManagerPlugin;
import io.mewb.bossEventManager.arena.ArenaInstance;
import io.mewb.bossEventManager.bosses.BossDefinition;
import io.mewb.bossEventManager.managers.ArenaManager;
import io.mewb.bossEventManager.managers.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors; // Added for player list in broadcast

public class BossDeathListener implements Listener {

    private final BossEventManagerPlugin plugin;
    private final ArenaManager arenaManager;
    private final ConfigManager configManager;
    private final Random random = new Random();

    public BossDeathListener(BossEventManagerPlugin plugin) {
        this.plugin = plugin;
        this.arenaManager = plugin.getArenaManager();
        this.configManager = plugin.getConfigManager();
    }

    @EventHandler
    public void onMythicMobDeath(MythicMobDeathEvent event) {
        if (arenaManager == null) {
            // plugin.getLogger().fine("BossDeathListener: ArenaManager is null.");
            return;
        }

        UUID deadMobUUID = event.getEntity().getUniqueId();
        String deadMobTypeName = event.getMobType().getInternalName();

        ArenaInstance arenaInstance = arenaManager.getActiveArenaInstanceByBossUUID(deadMobUUID);

        if (arenaInstance == null || arenaInstance.getState() != ArenaInstance.ArenaState.IN_USE) {
            return;
        }

        BossDefinition currentBossDef = arenaInstance.getCurrentBoss();
        if (currentBossDef == null) {
            plugin.getLogger().warning("Tracked boss " + deadMobUUID + " died in arena " + arenaInstance.getInstanceId() + " but BossDefinition was null.");
            arenaManager.endEvent(arenaInstance);
            return;
        }

        plugin.getLogger().info("Detected death of MythicMob type '" + deadMobTypeName + "' (Entity UUID: " + deadMobUUID + ") in arena instance: " + arenaInstance.getInstanceId());

        // Multi-Phase Boss Check:
        String finalPhaseId = currentBossDef.getFinalPhaseMythicMobId(); // Defaults to initial ID if not set

        if (!deadMobTypeName.equalsIgnoreCase(finalPhaseId)) {
            plugin.getLogger().info("Boss phase '" + deadMobTypeName + "' defeated in arena " + arenaInstance.getInstanceId() + ". This is not the final phase ('" + finalPhaseId + "'). Event continues.");
            // If MythicMobs spawns a new entity for the next phase, we need a way to update
            // ArenaInstance.bossEntityUUID to track the new entity.
            // For now, this listener will only trigger the end if the *final phase* mob dies.
            // If the next phase is a different entity, its death won't be caught by getActiveArenaInstanceByBossUUID
            // unless the UUID in ArenaInstance is updated.
            // A more advanced system might involve listening to MythicMobSpawnEvent within the arena context.
            return;
        }

        plugin.getLogger().info("Final phase '" + deadMobTypeName + "' defeated for boss " + currentBossDef.getDisplayName() + " in arena " + arenaInstance.getInstanceId() + ". Processing rewards and ending event.");

        // Prevent default MythicMobs drops if desired (can be a config option per boss)
        // event.setDrops(new ArrayList<>());

        List<UUID> partyMemberUUIDs = arenaInstance.getPartyMemberUUIDs();
        if (currentBossDef.getRewards() != null && partyMemberUUIDs != null && !partyMemberUUIDs.isEmpty()) {
            List<BossDefinition.RewardItem> rewardItems = currentBossDef.getRewards();
            plugin.getLogger().info("Processing " + rewardItems.size() + " potential reward items for " + partyMemberUUIDs.size() + " players.");

            List<String> playerNamesForBroadcast = new ArrayList<>();

            for (UUID playerUUID : partyMemberUUIDs) {
                Player player = Bukkit.getPlayer(playerUUID);
                String playerName = "A brave adventurer"; // Default for offline
                if (player != null && player.isOnline()) {
                    playerName = player.getName();
                    playerNamesForBroadcast.add(playerName); // Add to list for broadcast
                }

                for (BossDefinition.RewardItem reward : rewardItems) {
                    if (random.nextDouble() <= reward.getChance()) {
                        String processedCommand = reward.getCommand()
                                .replace("%player%", playerName)
                                .replace("%player_uuid%", playerUUID.toString())
                                .replace("%boss_name%", ChatColor.stripColor(currentBossDef.getDisplayName()))
                                .replace("%arena_id%", arenaInstance.getInstanceId().toString());
                        // plugin.getLogger().info("Dispatching reward command (Chance: " + reward.getChance()*100 + "%) for " + playerName + ": " + processedCommand);
                        try {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand);
                        } catch (Exception e) {
                            plugin.getLogger().log(Level.SEVERE, "Error dispatching reward command: " + processedCommand, e);
                        }
                    }
                }
                if (player != null && player.isOnline()) {
                    // Send individual confirmation, broadcast is separate
                    player.sendMessage(configManager.getMessage("boss-defeated-broadcast", // This message key might need adjustment
                            Map.of("%boss_name%", currentBossDef.getDisplayName(), "%player_list%", "Your party")
                    ));
                }
            }

            // Global broadcast if configured (using a specific message key)
            String playerListString = String.join(", ", playerNamesForBroadcast);
            if (playerListString.isEmpty()) playerListString = "A brave party";
            Map<String, String> broadcastPlaceholders = new HashMap<>();
            broadcastPlaceholders.put("%player_list%", playerListString);
            broadcastPlaceholders.put("%boss_name%", currentBossDef.getDisplayName());
            // Assuming you have a message like "boss-defeated-broadcast-global" in your config
            // String globalBroadcastMsg = configManager.getMessage("boss-defeated-broadcast-global", broadcastPlaceholders);
            // if (!globalBroadcastMsg.contains("Missing message")) { // Check if message key exists
            //    Bukkit.broadcastMessage(globalBroadcastMsg);
            // }


        } else {
            plugin.getLogger().warning("Could not process rewards for arena " + arenaInstance.getInstanceId() + ": Missing boss rewards or party members.");
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (arenaManager != null) { // Re-check arenaManager
                arenaManager.endEvent(arenaInstance);
            }
        }, 60L); // 3 second delay
    }
}