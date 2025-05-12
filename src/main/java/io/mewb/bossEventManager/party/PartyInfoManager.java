package io.mewb.bossEventManager.party;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import io.mewb.bossEventManager.BossEventManagerPlugin;
import io.mewb.bossEventManager.party.PartyInfo;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Manages requests for party information via BungeeCord plugin messaging
 * and handles the asynchronous responses.
 */
public class PartyInfoManager {

    private final BossEventManagerPlugin plugin;
    // Map to store pending requests: Player UUID -> Future that will hold the PartyInfo
    private final Map<UUID, CompletableFuture<PartyInfo>> pendingRequests;

    public PartyInfoManager(BossEventManagerPlugin plugin) {
        this.plugin = plugin;
        this.pendingRequests = new ConcurrentHashMap<>(); // Use ConcurrentHashMap for thread safety
    }

    /**
     * Sends a request to the BungeeCord extension to get party info for a player.
     * Returns a CompletableFuture that will be completed when the response arrives.
     *
     * @param player The player whose party info is needed.
     * @return A CompletableFuture<PartyInfo>.
     */
    public CompletableFuture<PartyInfo> requestPartyInfo(Player player) {
        if (player == null || !player.isOnline()) {
            plugin.getLogger().warning("Attempted to request party info for null or offline player.");
            return CompletableFuture.completedFuture(new PartyInfo(null)); // Return failed future
        }

        UUID playerUUID = player.getUniqueId();
        CompletableFuture<PartyInfo> future = new CompletableFuture<>();

        // Store the future, waiting for the response listener to complete it
        pendingRequests.put(playerUUID, future);

        // Construct the plugin message payload
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("GET_PARTY_INFO"); // Sub-channel
        out.writeUTF(playerUUID.toString()); // Player UUID

        // Send the message to BungeeCord via the player's connection
        player.sendPluginMessage(plugin, BossEventManagerPlugin.BUNGEE_CHANNEL, out.toByteArray());
        // plugin.getLogger().info("Sent GET_PARTY_INFO request for player " + player.getName() + " (UUID: " + playerUUID + ")"); // Commented out

        // Add a timeout for the request
        future.completeOnTimeout(new PartyInfo(playerUUID), 5, TimeUnit.SECONDS); // Timeout after 5 seconds

        // Clean up the map when the future completes (either successfully or via timeout/exception)
        future.whenComplete((result, throwable) -> {
            pendingRequests.remove(playerUUID);
            if (throwable != null) {
                plugin.getLogger().log(Level.WARNING, "Party info request future completed exceptionally for " + playerUUID, throwable);
            } else if (result != null && !result.isSuccess()) {
                plugin.getLogger().warning("Party info request failed or timed out for " + playerUUID);
            }
        });


        return future;
    }

    /**
     * Called by the PluginMessageListener when a response is received from BungeeCord.
     * Completes the corresponding CompletableFuture.
     *
     * @param playerUUID The UUID of the player the response is for.
     * @param isLeader   Party leader status.
     * @param partySize  Size of the party.
     * @param memberUUIDs List of member UUIDs.
     */
    public void handlePartyInfoResponse(UUID playerUUID, boolean isLeader, int partySize, List<UUID> memberUUIDs) {
        CompletableFuture<PartyInfo> future = pendingRequests.get(playerUUID);

        if (future != null) {
            PartyInfo info = new PartyInfo(playerUUID, isLeader, partySize, memberUUIDs);
            future.complete(info); // Complete the future with the received data
            // plugin.getLogger().info("Received and processed PARTY_INFO_RESPONSE for " + playerUUID); // Commented out
        } else {
            plugin.getLogger().warning("Received PARTY_INFO_RESPONSE for UUID " + playerUUID + " but no pending request was found (or it timed out).");
        }
    }


    public void handlePartyInfoFailure(UUID playerUUID) {
        CompletableFuture<PartyInfo> future = pendingRequests.get(playerUUID);
        if (future != null) {
            PartyInfo failedInfo = new PartyInfo(playerUUID); // Create failure object
            future.complete(failedInfo);
            // plugin.getLogger().info("Processed failed PARTY_INFO_RESPONSE for " + playerUUID); // Commented out
        } else {
            plugin.getLogger().warning("Received failed PARTY_INFO_RESPONSE for UUID " + playerUUID + " but no pending request was found (or it timed out).");
        }
    }
}