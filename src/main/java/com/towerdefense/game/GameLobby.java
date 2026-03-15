package com.towerdefense.game;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Holds lobby state when host has done td start but arena not yet generated.
 * Manages invites: host invites players, they accept with td join.
 */
public class GameLobby {

    private final UUID hostUUID;
    /** invitedPlayer -> host who invited (most recent) */
    private final Map<UUID, UUID> pendingInvites = new HashMap<>();

    public GameLobby(UUID hostUUID) {
        this.hostUUID = hostUUID;
    }

    public UUID getHostUUID() { return hostUUID; }

    /** Host invites target. Returns true if successful. */
    public boolean invite(UUID inviter, UUID target) {
        if (!inviter.equals(hostUUID)) return false;
        pendingInvites.put(target, inviter);
        return true;
    }

    /** Returns host UUID if player was invited, null otherwise. */
    public UUID getMostRecentInviteFor(UUID player) {
        return pendingInvites.get(player);
    }

    /** Remove invite after player joins. */
    public void removeInvite(UUID player) {
        pendingInvites.remove(player);
    }

    /** Can transition to prep phase: at least 2 players across both teams. */
    public boolean canStart(int team1Count, int team2Count) {
        return team1Count + team2Count >= 2;
    }
}
