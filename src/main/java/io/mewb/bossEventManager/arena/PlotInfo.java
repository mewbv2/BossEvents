package io.mewb.bossEventManager.arena;

import org.bukkit.Location;
import io.mewb.bossEventManager.managers.ArenaManager;

/**
 * A simple data class to hold information about a reserved plot,
 * including its logical ID and its actual world location (origin).
 */
public class PlotInfo {
    private final int plotId;
    private final Location plotOrigin;

    /**
     * Constructs a new PlotInfo object.
     * @param plotId The logical ID of the plot.
     * @param plotOrigin The Bukkit Location representing the origin (e.g., pasting point) of this plot.
     */
    public PlotInfo(int plotId, Location plotOrigin) {
        this.plotId = plotId;
        this.plotOrigin = plotOrigin;
    }

    /**
     * Gets the logical ID of the plot.
     * @return The plot ID.
     */
    public int getPlotId() {
        return plotId;
    }

    /**
     * Gets the Bukkit Location representing the origin of this plot in the world.
     * This is typically where a schematic would be pasted.
     * @return The plot's origin Location.
     */
    public Location getPlotOrigin() {
        return plotOrigin;
    }

    @Override
    public String toString() {
        return "PlotInfo{" +
                "plotId=" + plotId +
                ", plotOrigin=" + (plotOrigin != null ? plotOrigin.toString() : "null") +
                '}';
    }
}