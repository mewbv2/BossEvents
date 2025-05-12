package io.mewb.bossEventManager.listeners;


import io.mewb.bossEventManager.BossEventManagerPlugin;
import io.mewb.bossEventManager.arena.ArenaInstance;
import io.mewb.bossEventManager.managers.ArenaManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.ArrayList;
import java.util.HashMap; // Import HashMap
import java.util.HashSet;
import java.util.List;
import java.util.Map; // Import Map
import java.util.Set;
import java.util.UUID;

public class PlayerArenaDeathListener implements Listener {

    private final BossEventManagerPlugin plugin;
    private final ArenaManager arenaManager;
    private static final Set<UUID> playersToMakeSpectatorInArena = new HashSet<>();
    private static final Map<UUID, Location> deathLocations = new HashMap<>();


    public PlayerArenaDeathListener(BossEventManagerPlugin plugin) {
        this.plugin = plugin;
        this.arenaManager = plugin.getArenaManager();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeathInArena(PlayerDeathEvent event) {
        if (arenaManager == null) {
            return;
        }

        Player deceasedPlayer = event.getEntity();
        UUID deceasedPlayerUUID = deceasedPlayer.getUniqueId();
        ArenaInstance playerArenaInstance = null;

        for (ArenaInstance instance : new ArrayList<>(arenaManager.getActiveArenaInstances())) { // Iterate copy
            if (instance.getState() == ArenaInstance.ArenaState.IN_USE &&
                    instance.getPartyMemberUUIDs() != null &&
                    instance.getPartyMemberUUIDs().contains(deceasedPlayerUUID)) {
                playerArenaInstance = instance;
                break;
            }
        }

        if (playerArenaInstance != null) {
            plugin.getLogger().info("Player " + deceasedPlayer.getName() + " died in active boss arena " + playerArenaInstance.getInstanceId() + ". Preparing for spectator mode on respawn.");

            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setKeepLevel(true);
            event.setDroppedExp(0);

            deathLocations.put(deceasedPlayerUUID, deceasedPlayer.getLocation().clone());
            playersToMakeSpectatorInArena.add(deceasedPlayerUUID);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerArenaRespawn(PlayerRespawnEvent event) {
        Player respawnedPlayer = event.getPlayer();
        UUID respawnedPlayerUUID = respawnedPlayer.getUniqueId();

        if (playersToMakeSpectatorInArena.contains(respawnedPlayerUUID)) {
            playersToMakeSpectatorInArena.remove(respawnedPlayerUUID);
            Location originalDeathLocation = deathLocations.remove(respawnedPlayerUUID);

            if (arenaManager == null) {
                plugin.getLogger().warning("ArenaManager became null before " + respawnedPlayer.getName() + " could be set to spectator.");
                return;
            }

            ArenaInstance playerArenaInstance = null;
            for (ArenaInstance instance : new ArrayList<>(arenaManager.getActiveArenaInstances())) { // Iterate copy
                if (instance.getState() == ArenaInstance.ArenaState.IN_USE &&
                        instance.getPartyMemberUUIDs() != null &&
                        instance.getPartyMemberUUIDs().contains(respawnedPlayerUUID)) {
                    playerArenaInstance = instance;
                    break;
                }
            }

            if (playerArenaInstance == null) {
                plugin.getLogger().warning("Player " + respawnedPlayer.getName() + " was marked for spectator, but their arena instance was no longer active/found.");
                return;
            }

            plugin.getLogger().info("Processing respawn for " + respawnedPlayer.getName() + " in arena " + playerArenaInstance.getInstanceId() + " to set spectator mode.");

            Location currentRespawnLocation = event.getRespawnLocation();
            if (originalDeathLocation != null && originalDeathLocation.getWorld().equals(playerArenaInstance.getPlotOrigin().getWorld())) {
                currentRespawnLocation = originalDeathLocation;
                event.setRespawnLocation(originalDeathLocation);
            }

            Location spectatorTeleportTarget = currentRespawnLocation.clone();
            Player closestLivingMember = null;
            double minDistanceSq = Double.MAX_VALUE;
            List<UUID> partyMembersInArena = playerArenaInstance.getPartyMemberUUIDs(); // Get the list of UUIDs for this specific arena instance

            if (partyMembersInArena != null) {
                for (UUID memberUUID : partyMembersInArena) {
                    if (!memberUUID.equals(respawnedPlayerUUID)) {
                        Player member = Bukkit.getPlayer(memberUUID);
                        if (member != null && member.isOnline() && member.getGameMode() != GameMode.SPECTATOR && !member.isDead()) {
                            if (member.getWorld().equals(currentRespawnLocation.getWorld())) {
                                double distSq = member.getLocation().distanceSquared(currentRespawnLocation);
                                if (distSq < minDistanceSq) {
                                    minDistanceSq = distSq;
                                    closestLivingMember = member;
                                }
                            }
                        }
                    }
                }
            }

            if (closestLivingMember != null) {
                spectatorTeleportTarget = closestLivingMember.getLocation().clone().add(0, 0.5, 0);
                plugin.getLogger().info("Found living party member " + closestLivingMember.getName() + " for " + respawnedPlayer.getName() + " to spectate near.");
            } else {
                spectatorTeleportTarget = currentRespawnLocation.clone().add(0, 1, 0);
                plugin.getLogger().info("No other living party members found for " + respawnedPlayer.getName() + " to spectate near. Using elevated death location.");
            }

            if (spectatorTeleportTarget.getBlock().getType().isSolid() ||
                    spectatorTeleportTarget.clone().add(0,1,0).getBlock().getType().isSolid()) {
                plugin.getLogger().warning("Calculated spectator teleport for " + respawnedPlayer.getName() + " was obstructed. Using higher fallback from respawn location.");
                spectatorTeleportTarget = currentRespawnLocation.clone().add(0, 1.5, 0);
            }

            final Location finalTeleportTarget = spectatorTeleportTarget;
            final Player finalRespawnedPlayer = respawnedPlayer;
            final ArenaInstance finalPlayerArenaInstance = playerArenaInstance; // Effectively final for lambda

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (finalRespawnedPlayer.isOnline()) {
                    finalRespawnedPlayer.setGameMode(GameMode.SPECTATOR);
                    finalRespawnedPlayer.teleportAsync(finalTeleportTarget).thenAccept(success -> {
                        if (success) {
                            finalRespawnedPlayer.sendMessage(plugin.getConfigManager().getPrefix() + ChatColor.YELLOW + "You have fallen! You are now spectating the rest of the fight.");
                            plugin.getLogger().info("Set " + finalRespawnedPlayer.getName() + " to spectator mode and teleported to " + finalTeleportTarget);

                            // --- Check if all party members are now spectators ---
                            checkPartyWipe(finalPlayerArenaInstance);
                            // --- End Check ---

                        } else {
                            finalRespawnedPlayer.sendMessage(plugin.getConfigManager().getPrefix() + ChatColor.RED + "Failed to position you correctly for spectating.");
                            plugin.getLogger().warning("Failed to teleport spectator " + finalRespawnedPlayer.getName() + " to target location " + finalTeleportTarget + ". They are spectator at their respawn location.");
                            // Still check for party wipe even if teleport failed, as they are spectator
                            checkPartyWipe(finalPlayerArenaInstance);
                        }
                    });
                }
            }, 2L);
        }
    }

    /**
     * Checks if all players in the given arena instance's party are in spectator mode or offline.
     * If so, ends the event.
     * @param arenaInstance The arena instance to check.
     */
    private void checkPartyWipe(ArenaInstance arenaInstance) {
        if (arenaInstance == null || arenaInstance.getState() != ArenaInstance.ArenaState.IN_USE) {
            return; // Arena not active or doesn't exist
        }

        List<UUID> partyMemberUUIDs = arenaInstance.getPartyMemberUUIDs();
        if (partyMemberUUIDs == null || partyMemberUUIDs.isEmpty()) {
            plugin.getLogger().info("No party members found in arena " + arenaInstance.getInstanceId() + " during wipe check. Ending event.");
            arenaManager.endEvent(arenaInstance);
            return;
        }

        boolean allSpectatorsOrOffline = true;
        for (UUID memberUUID : partyMemberUUIDs) {
            Player member = Bukkit.getPlayer(memberUUID);
            if (member != null && member.isOnline()) {
                if (member.getGameMode() != GameMode.SPECTATOR) {
                    allSpectatorsOrOffline = false; // Found an active player
                    break;
                }
            }
            // If player is offline, they are considered "out" of the fight for this check.
        }

        if (allSpectatorsOrOffline) {
            plugin.getLogger().info("All players in arena " + arenaInstance.getInstanceId() + " are spectators or offline. Ending event due to party wipe.");
            // Send a message to any online spectators from that party
            for (UUID memberUUID : partyMemberUUIDs) {
                Player member = Bukkit.getPlayer(memberUUID);
                if (member != null && member.isOnline() && member.getGameMode() == GameMode.SPECTATOR) {
                    member.sendMessage(plugin.getConfigManager().getPrefix() + ChatColor.RED + "Your party has been defeated! The event is ending.");
                }
            }
            arenaManager.endEvent(arenaInstance);
        } else {
            plugin.getLogger().info("Party wipe check for arena " + arenaInstance.getInstanceId() + ": Active players still present.");
        }
    }
}