package io.mewb.bossEventManager.arena;


import io.mewb.bossEventManager.bosses.BossDefinition;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.ArrayList; // Ensure ArrayList is imported

public class ArenaInstance {

    public enum ArenaState {
        AVAILABLE, PREPARING, IN_USE, CLEANING_UP, UNLOADING
    }

    private static final Logger log = Bukkit.getLogger();

    private final UUID instanceId;
    private final ArenaTheme arenaTheme;
    private final Location plotOrigin; // The actual world location where the schematic was pasted
    private int plotId = -1; // The logical ID of the plot this instance occupies, -1 if not set
    private List<UUID> partyMemberUUIDs;
    private BossDefinition currentBoss;
    private UUID bossEntityUUID;
    private String activeMusicTrack;
    private ArenaState state;
    private long creationTime;
    private long lastActivityTime;
    private Map<UUID, Location> originalPlayerLocations;

    public ArenaInstance(ArenaTheme arenaTheme, Location plotOrigin, int plotId) { // Added plotId to constructor
        this.instanceId = UUID.randomUUID();
        this.arenaTheme = arenaTheme;
        this.plotOrigin = plotOrigin;
        this.plotId = plotId; // Set plotId
        this.state = ArenaState.AVAILABLE;
        this.creationTime = System.currentTimeMillis();
        this.lastActivityTime = this.creationTime;
        this.bossEntityUUID = null;
        this.originalPlayerLocations = new HashMap<>();
        this.activeMusicTrack = null;
    }

    // --- Getters ---
    public UUID getInstanceId() { return instanceId; }
    public ArenaTheme getArenaTheme() { return arenaTheme; }
    public Location getPlotOrigin() { return plotOrigin; }

    /**
     * Gets the logical ID of the plot this arena instance occupies.
     * @return The plot ID, or -1 if not set.
     */
    public int getPlotId() { return plotId; }

    public List<UUID> getPartyMemberUUIDs() {
        return partyMemberUUIDs == null ? Collections.emptyList() : new ArrayList<>(partyMemberUUIDs);
    }
    public BossDefinition getCurrentBoss() { return currentBoss; }
    public UUID getBossEntityUUID() { return bossEntityUUID; }
    public String getActiveMusicTrack() { return activeMusicTrack; }
    public ArenaState getState() { return state; }
    public long getCreationTime() { return creationTime; }
    public long getLastActivityTime() { return lastActivityTime; }

    public Location getOriginalPlayerLocation(UUID playerUUID) {
        return originalPlayerLocations.get(playerUUID);
    }

    public Map<UUID, Location> getOriginalPlayerLocations() {
        return new HashMap<>(originalPlayerLocations);
    }


    // --- Setters / Modifiers ---
    public void setPlotId(int plotId) { this.plotId = plotId; }
    public void setParty(List<Player> players) {
        if (players != null) {
            this.partyMemberUUIDs = players.stream().map(Player::getUniqueId).collect(Collectors.toList());
            // there's probably a better way to do this. but this works.
        } else {
            this.partyMemberUUIDs = null;
            this.originalPlayerLocations.clear();
        }
        updateLastActivity();
    }

    public void storePartyOriginalLocations(List<Player> players) {
        originalPlayerLocations.clear();
        if (players != null) {
            for (Player player : players) {
                if (player != null && player.isOnline()) {
                    originalPlayerLocations.put(player.getUniqueId(), player.getLocation().clone());
                }
            }
        }
    }

    public void setCurrentBoss(BossDefinition bossDefinition) { this.currentBoss = bossDefinition; updateLastActivity(); }
    public void setBossEntityUUID(UUID bossEntityUUID) { this.bossEntityUUID = bossEntityUUID; }
    public void setActiveMusicTrack(String musicTrack) { this.activeMusicTrack = musicTrack; }
    public void setState(ArenaState state) {
        this.state = state;
        updateLastActivity();
        if (state == ArenaState.CLEANING_UP || state == ArenaState.UNLOADING || state == ArenaState.AVAILABLE) {
            this.bossEntityUUID = null;
            this.partyMemberUUIDs = null;
            this.originalPlayerLocations.clear();
            this.activeMusicTrack = null;
        }
    }
    public void updateLastActivity() { this.lastActivityTime = System.currentTimeMillis(); }

    // --- Helper methods for spawn locations ---
    public Location getPlayerSpawnLocation(int playerIndex, int partySize) {
        if (arenaTheme == null || plotOrigin == null) return null;
        ArenaLocation relativeSpawn = arenaTheme.getPlayerSpawnPoint(playerIndex, partySize);
        if (relativeSpawn == null) return null;
        return relativeSpawn.toBukkitLocation(plotOrigin, plotOrigin.getWorld().getName());
    }
    public Location getBossSpawnLocation() {
        if (arenaTheme == null || plotOrigin == null || arenaTheme.getBossSpawnPoint() == null) return null;
        ArenaLocation relativeSpawn = arenaTheme.getBossSpawnPoint();
        return relativeSpawn.toBukkitLocation(plotOrigin, plotOrigin.getWorld().getName());
    }
    private String locationToString(Location loc) { if (loc == null) return "null"; return String.format("World: %s, X: %.2f, Y: %.2f, Z: %.2f, Yaw: %.1f, Pitch: %.1f", loc.getWorld() != null ? loc.getWorld().getName() : "null", loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch()); }
    @Override public boolean equals(Object o) { if (this == o) return true; if (o == null || getClass() != o.getClass()) return false; ArenaInstance that = (ArenaInstance) o; return instanceId.equals(that.instanceId); }
    @Override public int hashCode() { return instanceId.hashCode(); }
    @Override public String toString() { return "ArenaInstance{instanceId=" + instanceId + ", plotId=" + plotId + ", theme=" + (arenaTheme != null ? arenaTheme.getId() : "null") + ", origin=" + locationToString(plotOrigin) + ", state=" + state + ", partySize=" + (partyMemberUUIDs != null ? partyMemberUUIDs.size() : 0) + ", bossUUID=" + bossEntityUUID + ", music=" + activeMusicTrack + '}'; }
}