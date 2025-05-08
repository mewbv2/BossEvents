package io.mewb.bossEventManager.listeners; // New listeners subpackage

import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;

import io.mewb.bossEventManager.BossEventManagerPlugin;
import io.mewb.bossEventManager.arena.ArenaInstance;
import io.mewb.bossEventManager.managers.ArenaManager;
import io.mewb.bossEventManager.managers.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class BossDeathListener implements Listener {

    private final BossEventManagerPlugin plugin;
    private final ArenaManager arenaManager;
    private final ConfigManager configManager;

    public BossDeathListener(BossEventManagerPlugin plugin) {
        this.plugin = plugin;
        this.arenaManager = plugin.getArenaManager();
        this.configManager = plugin.getConfigManager();
    }

    @EventHandler
    public void onMythicMobDeath(MythicMobDeathEvent event) {
        // Ensure ArenaManager is available
        if (arenaManager == null) {
            return;
        }

        // Get the UUID of the entity that died
        UUID deadMobUUID = event.getEntity().getUniqueId();

        // Find the ArenaInstance associated with this boss UUID
        ArenaInstance arenaInstance = arenaManager.getActiveArenaInstanceByBossUUID(deadMobUUID);

        // If no matching arena instance is found, or it's not in use, ignore the death
        if (arenaInstance == null || arenaInstance.getState() != ArenaInstance.ArenaState.IN_USE) {
            // Optional: Log if a MythicMob died that we thought was a boss but wasn't tracked correctly
            // plugin.getLogger().fine("MythicMobDeathEvent for UUID " + deadMobUUID + " did not match an active boss arena.");
            return;
        }

        plugin.getLogger().info("Detected death of tracked boss (UUID: " + deadMobUUID + ") in arena instance: " + arenaInstance.getInstanceId());

        // Prevent default MythicMobs drops if needed (can be configured per-boss later)
        // event.setDrops(new ArrayList<>()); // Example: Clear default drops

        // --- Process Rewards ---
        List<UUID> partyMemberUUIDs = arenaInstance.getPartyMemberUUIDs();
        if (arenaInstance.getCurrentBoss() != null && partyMemberUUIDs != null && !partyMemberUUIDs.isEmpty()) {
            List<String> rewardCommands = arenaInstance.getCurrentBoss().getRewardsCommands();

            if (rewardCommands != null && !rewardCommands.isEmpty()) {
                plugin.getLogger().info("Processing " + rewardCommands.size() + " reward commands for " + partyMemberUUIDs.size() + " players.");

                for (UUID playerUUID : partyMemberUUIDs) {
                    Player player = Bukkit.getPlayer(playerUUID);
                    String playerName = (player != null) ? player.getName() : "OfflinePlayer_" + playerUUID.toString().substring(0, 5); // Handle offline players

                    for (String command : rewardCommands) {
                        // Replace placeholders
                        String processedCommand = command.replace("%player%", playerName)
                                .replace("%player_uuid%", playerUUID.toString())
                                .replace("%boss_name%", ChatColor.stripColor(arenaInstance.getCurrentBoss().getDisplayName()))
                                .replace("%arena_id%", arenaInstance.getInstanceId().toString());
                        // Execute command from console
                        plugin.getLogger().info("Dispatching reward command for " + playerName + ": " + processedCommand);
                        try {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand);
                        } catch (Exception e) {
                            plugin.getLogger().log(Level.SEVERE, "Error dispatching reward command: " + processedCommand, e);
                        }
                    }
                    if (player != null && player.isOnline()) {
                        player.sendMessage(configManager.getPrefix() + ChatColor.GOLD + "Your party defeated " + arenaInstance.getCurrentBoss().getDisplayName() + "! Rewards granted!");
                    }
                }
            } else {
                plugin.getLogger().info("No reward commands configured for boss: " + arenaInstance.getCurrentBoss().getDisplayName());
            }
        } else {
            plugin.getLogger().warning("Could not process rewards for arena " + arenaInstance.getInstanceId() + ": Missing boss definition or party members.");
        }

        // --- End the Event ---
        // Add a small delay before ending the event to allow players to see messages/effects
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            arenaManager.endEvent(arenaInstance);
        }, 60L); // 3 second delay (20 ticks per second)

    }
}