package io.mewb.bossEventManager.listeners;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;

import io.mewb.bossEventManager.BossEventManagerPlugin;
import io.mewb.bossEventManager.party.PartyInfoManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Listens for plugin messages incoming from BungeeCord on the designated channel.
 */
public class SpigotPluginMessageListener implements PluginMessageListener {

    private final BossEventManagerPlugin plugin;
    private final PartyInfoManager partyInfoManager;

    public SpigotPluginMessageListener(BossEventManagerPlugin plugin) {
        this.plugin = plugin;
        // Get the PartyInfoManager instance - ensure it's initialized before this listener is registered
        this.partyInfoManager = plugin.getPartyInfoManager();
        if (this.partyInfoManager == null) {
            plugin.getLogger().severe("SpigotPluginMessageListener could not get PartyInfoManager instance! Responses will not be processed.");
        }
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, @NotNull byte[] message) {
        // We don't strictly need the 'player' argument here as Bungee sends to the server,
        // but Bukkit requires it in the signature. The relevant player UUID is inside the message.
        if (!channel.equals(BossEventManagerPlugin.BUNGEE_CHANNEL)) {
            return; // Ignore messages not on our channel
        }
        if (partyInfoManager == null) {
            plugin.getLogger().warning("Received plugin message on channel " + channel + " but PartyInfoManager is null. Cannot process.");
            return;
        }

        ByteArrayDataInput input = ByteStreams.newDataInput(message);

        try {
            String subChannel = input.readUTF(); // Read the sub-channel

            if ("PARTY_INFO_RESPONSE".equalsIgnoreCase(subChannel)) {
                // Parse the response data according to the format sent by PAFBE
                UUID requestedPlayerUUID = UUID.fromString(input.readUTF());
                boolean isLeader = input.readBoolean();
                int partySize = input.readInt();
                int memberCount = input.readInt();
                List<UUID> memberUUIDs = new ArrayList<>();
                for (int i = 0; i < memberCount; i++) {
                    memberUUIDs.add(UUID.fromString(input.readUTF()));
                }

                plugin.getLogger().info("Received PARTY_INFO_RESPONSE for " + requestedPlayerUUID + " (Leader: " + isLeader + ", Size: " + partySize + ")");
                // Pass the data to the PartyInfoManager to complete the future
                partyInfoManager.handlePartyInfoResponse(requestedPlayerUUID, isLeader, partySize, memberUUIDs);

            }
            // Add handling for other potential sub-channels/responses if needed
            // else if ("PARTY_INFO_FAILURE".equalsIgnoreCase(subChannel)) {
            //    UUID requestedPlayerUUID = UUID.fromString(input.readUTF());
            //    partyInfoManager.handlePartyInfoFailure(requestedPlayerUUID);
            // }

        } catch (IllegalStateException | IllegalArgumentException e) {
            // IllegalStateException if not enough bytes, IllegalArgumentException for UUID parsing
            plugin.getLogger().log(Level.SEVERE, "Error parsing PARTY_INFO_RESPONSE plugin message.", e);
        }
    }
}