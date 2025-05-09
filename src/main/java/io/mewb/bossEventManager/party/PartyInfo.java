package io.mewb.bossEventManager.party; // New package for party related classes

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Simple Plain Old Java Object (POJO) to hold party information
 * received from the BungeeCord extension.
 */
public class PartyInfo {

    private final UUID requestedPlayerUUID;
    private final boolean isLeader;
    private final int partySize;
    private final List<UUID> memberUUIDs;
    private final boolean success; // Indicates if data was successfully retrieved

    // Constructor for successful retrieval
    public PartyInfo(UUID requestedPlayerUUID, boolean isLeader, int partySize, List<UUID> memberUUIDs) {
        this.requestedPlayerUUID = requestedPlayerUUID;
        this.isLeader = isLeader;
        this.partySize = partySize;
        this.memberUUIDs = Collections.unmodifiableList(new ArrayList<>(memberUUIDs)); // Store immutable copy
        this.success = true;
    }

    // Constructor for failed retrieval (e.g., player not found, API error)
    public PartyInfo(UUID requestedPlayerUUID) {
        this.requestedPlayerUUID = requestedPlayerUUID;
        this.isLeader = false;
        this.partySize = 0;
        this.memberUUIDs = Collections.emptyList();
        this.success = false;
    }

    public UUID getRequestedPlayerUUID() {
        return requestedPlayerUUID;
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isLeader() {
        return isLeader;
    }

    public int getPartySize() {
        return partySize;
    }

    public List<UUID> getMemberUUIDs() {
        return memberUUIDs;
    }

    public boolean isInParty() {
        return partySize > 0; // Simple check if player is in any party
    }
}