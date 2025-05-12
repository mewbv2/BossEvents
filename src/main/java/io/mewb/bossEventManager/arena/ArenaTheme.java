package io.mewb.bossEventManager.arena;

import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors; /**
 * Represents an Arena Theme, typically loaded from config, defining a schematic and spawn points.
 * Changed to public visibility.
 */
public class ArenaTheme {
    private final String id;
    private final String displayName;
    private final String schematicFile;
    private final List<ArenaLocation> playerSpawnPoints;
    private final ArenaLocation bossSpawnPoint;
    private final BlockVector3 schematicDimensions;
    private final BlockVector3 schematicOriginOffset;
    // private final String backgroundMusic; // REMOVED field for music

    public ArenaTheme(String id, String displayName, String schematicFile,
                      List<String> playerSpawnStrings, String bossSpawnString,
                      BlockVector3 schematicDimensions, BlockVector3 schematicOriginOffset) { // REMOVED backgroundMusic from constructor
        this.id = id;
        this.displayName = ChatColor.translateAlternateColorCodes('&', displayName);
        this.schematicFile = schematicFile;
        this.playerSpawnPoints = playerSpawnStrings.stream().map(ArenaLocation::parseRelative).filter(Objects::nonNull).collect(Collectors.toList());
        this.bossSpawnPoint = ArenaLocation.parseRelative(bossSpawnString);
        this.schematicDimensions = schematicDimensions;
        this.schematicOriginOffset = schematicOriginOffset;
        // this.backgroundMusic = backgroundMusic; // REMOVED music assignment

        if (this.playerSpawnPoints.isEmpty()) Bukkit.getLogger().warning("[BossEventManager] ArenaTheme '" + id + "' has no valid player spawn points!");
        if (this.bossSpawnPoint == null) Bukkit.getLogger().warning("[BossEventManager] ArenaTheme '" + id + "' has no valid boss spawn point!");
        if (this.schematicDimensions == null || this.schematicOriginOffset == null) Bukkit.getLogger().severe("[BossEventManager] ArenaTheme '" + id + "' is missing schematic dimension/offset data!");
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getSchematicFile() { return schematicFile; }
    public List<ArenaLocation> getPlayerSpawnPoints() { return playerSpawnPoints; }
    public ArenaLocation getBossSpawnPoint() { return bossSpawnPoint; }
    public BlockVector3 getSchematicDimensions() { return schematicDimensions; }
    public BlockVector3 getSchematicOriginOffset() { return schematicOriginOffset; }
    // public String getBackgroundMusic() { return backgroundMusic; } // REMOVED getter for music

    public ArenaLocation getPlayerSpawnPoint(int partyMemberIndex, int partySize) {
        if (playerSpawnPoints.isEmpty()) return null;
        return playerSpawnPoints.get(partyMemberIndex % playerSpawnPoints.size());
    }
}