package io.mewb.bossEventManager.arena;

import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ArenaLocation {
    private final double x, y, z;
    private final float yaw, pitch;
    private final boolean relative;

    public ArenaLocation(double x, double y, double z, float yaw, float pitch, boolean relative) {
        this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch; this.relative = relative;
    }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public boolean isRelative() { return relative; }
    public Location toBukkitLocation(Location baseLocation, String worldName) {
        if (relative) {
            if (baseLocation == null) { Bukkit.getLogger().warning("[BossEventManager] Cannot calculate relative ArenaLocation without a baseLocation."); return null; }
            Location newLoc = baseLocation.clone(); newLoc.add(x, y, z); newLoc.setYaw(yaw); newLoc.setPitch(pitch); return newLoc;
        } else {
            World world = (baseLocation != null) ? baseLocation.getWorld() : Bukkit.getWorld(worldName);
            if (world == null) { Bukkit.getLogger().warning("[BossEventManager] World '" + worldName + "' not found for absolute ArenaLocation."); return null; }
            return new Location(world, x, y, z, yaw, pitch);
        }
    }
    public Vector toVector() { return new Vector(x,y,z); }
    public static ArenaLocation parseRelative(String input) {
        if (input == null || input.isEmpty()) return null;
        String[] parts = input.split(",");
        try {
            double x = Double.parseDouble(parts[0].trim()); double y = Double.parseDouble(parts[1].trim()); double z = Double.parseDouble(parts[2].trim());
            float yaw = 0; float pitch = 0;
            if (parts.length >= 5) { yaw = Float.parseFloat(parts[3].trim()); pitch = Float.parseFloat(parts[4].trim()); }
            return new ArenaLocation(x, y, z, yaw, pitch, true);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            Bukkit.getLogger().warning("[BossEventManager] Failed to parse relative location string: '" + input + "'. Error: " + e.getMessage()); return null;
        }
    }
    @Override public String toString() { return (relative ? "Relative(" : "Absolute(") + x + "," + y + "," + z + "," + yaw + "," + pitch + ")"; }
}

