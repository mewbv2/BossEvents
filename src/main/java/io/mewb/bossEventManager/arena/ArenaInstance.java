package io.mewb.bossEventManager.arena;


import io.mewb.bossEventManager.bosses.BossDefinition;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ArenaInstance {

    public enum ArenaState {
        AVAILABLE, PREPARING, IN_USE, CLEANING_UP, UNLOADING
    }

    private static final Logger log = Bukkit.getLogger(); // Keep for potential non-debug logs

    private final UUID instanceId;
    private final ArenaTheme arenaTheme;
    private final Location plotOrigin;
    private List<UUID> partyMemberUUIDs;
    private BossDefinition currentBoss;
    private UUID bossEntityUUID;
    private ArenaState state;
    private long creationTime;
    private long lastActivityTime;


    public ArenaInstance(ArenaTheme arenaTheme, Location plotOrigin) {
        this.instanceId = UUID.randomUUID();
        this.arenaTheme = arenaTheme;
        this.plotOrigin = plotOrigin;
        this.state = ArenaState.AVAILABLE;
        this.creationTime = System.currentTimeMillis();
        this.lastActivityTime = this.creationTime;
        this.bossEntityUUID = null;
    }

    // --- Getters ---
    public UUID getInstanceId() { return instanceId; }
    public ArenaTheme getArenaTheme() { return arenaTheme; }
    public Location getPlotOrigin() { return plotOrigin; }
    public List<UUID> getPartyMemberUUIDs() { return partyMemberUUIDs; }
    public BossDefinition getCurrentBoss() { return currentBoss; }
    public UUID getBossEntityUUID() { return bossEntityUUID; }
    public ArenaState getState() { return state; }
    public long getCreationTime() { return creationTime; }
    public long getLastActivityTime() { return lastActivityTime; }

    // --- Setters / Modifiers ---
    public void setParty(List<Player> players) {
        if (players != null) {
            this.partyMemberUUIDs = players.stream().map(Player::getUniqueId).collect(Collectors.toList());
        } else {
            this.partyMemberUUIDs = null;
        }
        updateLastActivity();
    }
    public void setCurrentBoss(BossDefinition bossDefinition) { this.currentBoss = bossDefinition; updateLastActivity(); }
    public void setBossEntityUUID(UUID bossEntityUUID) { this.bossEntityUUID = bossEntityUUID; }
    public void setState(ArenaState state) {
        this.state = state;
        updateLastActivity();
        if (state == ArenaState.CLEANING_UP || state == ArenaState.UNLOADING) {
            this.bossEntityUUID = null;
        }
    }
    public void updateLastActivity() { this.lastActivityTime = System.currentTimeMillis(); }

    // --- Helper methods for spawn locations ---

    public Location getPlayerSpawnLocation(int playerIndex, int partySize) {
        if (arenaTheme == null || plotOrigin == null) {
            // log.warning("[BossEventManager] Cannot get player spawn: ArenaTheme or PlotOrigin is null for instance " + instanceId); // Keep as warning
            return null;
        }
        ArenaLocation relativeSpawn = arenaTheme.getPlayerSpawnPoint(playerIndex, partySize);
        if (relativeSpawn == null) {
            // log.warning("[BossEventManager] Cannot get player spawn: No valid relative spawn found for player index " + playerIndex + " in theme " + arenaTheme.getId()); // Keep as warning
            return null;
        }

        Location finalLocation = relativeSpawn.toBukkitLocation(plotOrigin, plotOrigin.getWorld().getName());

        // Debug Logging (Commented out)
        // log.info("[BossEventManager DEBUG] Player Spawn Calc for Instance " + instanceId + ":");
        // log.info("  - Plot Origin: " + locationToString(plotOrigin));
        // log.info("  - Relative Spawn (Index " + playerIndex + "): " + relativeSpawn.toString());
        // log.info("  - Calculated Final Location: " + locationToString(finalLocation));

        return finalLocation;
    }

    public Location getBossSpawnLocation() {
        if (arenaTheme == null || plotOrigin == null || arenaTheme.getBossSpawnPoint() == null) {
            // log.warning("[BossEventManager] Cannot get boss spawn: ArenaTheme, PlotOrigin, or relative boss spawn is null for instance " + instanceId); // Keep as warning
            return null;
        }
        ArenaLocation relativeSpawn = arenaTheme.getBossSpawnPoint();
        Location finalLocation = relativeSpawn.toBukkitLocation(plotOrigin, plotOrigin.getWorld().getName());

        // Debug Logging (Commented out)
        // log.info("[BossEventManager DEBUG] Boss Spawn Calc for Instance " + instanceId + ":");
        // log.info("  - Plot Origin: " + locationToString(plotOrigin));
        // log.info("  - Relative Spawn: " + relativeSpawn.toString());
        // log.info("  - Calculated Final Location: " + locationToString(finalLocation));

        return finalLocation;
    }

    private String locationToString(Location loc) {
        if (loc == null) return "null";
        return String.format("World: %s, X: %.2f, Y: %.2f, Z: %.2f, Yaw: %.1f, Pitch: %.1f",
                loc.getWorld() != null ? loc.getWorld().getName() : "null",
                loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArenaInstance that = (ArenaInstance) o;
        return instanceId.equals(that.instanceId);
    }

    @Override
    public int hashCode() { return instanceId.hashCode(); }

    @Override
    public String toString() {
        return "ArenaInstance{" +
                "instanceId=" + instanceId +
                ", theme=" + (arenaTheme != null ? arenaTheme.getId() : "null") +
                ", origin=" + locationToString(plotOrigin) +
                ", state=" + state +
                ", partySize=" + (partyMemberUUIDs != null ? partyMemberUUIDs.size() : 0) +
                ", bossUUID=" + bossEntityUUID +
                '}';
    }
}