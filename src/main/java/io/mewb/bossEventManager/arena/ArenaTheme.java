package io.mewb.bossEventManager.arena;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors; /**
 * Represents an Arena Theme, typically loaded from config, defining a schematic and spawn points.
 * Changed to public visibility.
 */
public class ArenaTheme { // <<<< Made this class public bcos its the big dumb
    private final String id;
    private final String displayName;
    private final String schematicFile;
    private final List<ArenaLocation> playerSpawnPoints; // Relative to schematic origin
    private final ArenaLocation bossSpawnPoint;       // Relative to schematic origin

    public ArenaTheme(String id, String displayName, String schematicFile, List<String> playerSpawnStrings, String bossSpawnString) {
        this.id = id;
        this.displayName = ChatColor.translateAlternateColorCodes('&', displayName);
        this.schematicFile = schematicFile;

        this.playerSpawnPoints = playerSpawnStrings.stream()
                .map(ArenaLocation::parseRelative)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        this.bossSpawnPoint = ArenaLocation.parseRelative(bossSpawnString);

        if (this.playerSpawnPoints.isEmpty()) {
            Bukkit.getLogger().severe("[BossEventManager] ArenaTheme '" + id + "' has no valid player spawn points defined!");
        }
        if (this.bossSpawnPoint == null) {
            Bukkit.getLogger().severe("[BossEventManager] ArenaTheme '" + id + "' has no valid boss spawn point defined!");
        }
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getSchematicFile() {
        return schematicFile;
    }

    public List<ArenaLocation> getPlayerSpawnPoints() {
        return playerSpawnPoints;
    }

    public ArenaLocation getBossSpawnPoint() {
        return bossSpawnPoint;
    }

    public ArenaLocation getPlayerSpawnPoint(int partyMemberIndex, int partySize) {
        if (playerSpawnPoints.isEmpty()) {
            return null;
        }
        return playerSpawnPoints.get(partyMemberIndex % playerSpawnPoints.size());
    }
}
