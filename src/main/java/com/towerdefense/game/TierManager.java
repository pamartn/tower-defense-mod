package com.towerdefense.game;

import com.towerdefense.TowerDefenseMod;
import com.towerdefense.arena.NexusManager;
import com.towerdefense.config.ConfigManager;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages per-player tier unlocks. Each player starts at tier 1.
 * Buying tier 2 or 3 costs money and triggers a 30-second countdown before unlock.
 * On unlock: Nexus gains +50 HP.
 */
public class TierManager {

    private static final int UNLOCK_DELAY_TICKS = 30 * 20; // 30 seconds

    private final Map<Integer, Integer> currentTier = new HashMap<>();
    private final Map<Integer, Integer> pendingUnlockTier = new HashMap<>();
    private final Map<Integer, Integer> unlockTicksRemaining = new HashMap<>();

    public void reset() {
        currentTier.clear();
        pendingUnlockTier.clear();
        unlockTicksRemaining.clear();
    }

    public void initTeam(int teamId) {
        currentTier.put(teamId, 1);
        pendingUnlockTier.remove(teamId);
        unlockTicksRemaining.remove(teamId);
    }

    public void initPlayer(UUID uuid) {
        initTeam(1);
        initTeam(2);
    }

    public int getCurrentTier(int teamId) {
        return currentTier.getOrDefault(teamId, 1);
    }

    public int getCurrentTier(UUID uuid) {
        return 1;
    }

    public int getPendingUnlockTier(int teamId) {
        return pendingUnlockTier.getOrDefault(teamId, 0);
    }

    public int getUnlockTicksRemaining(int teamId) {
        return unlockTicksRemaining.getOrDefault(teamId, 0);
    }

    public boolean canBuyTier(int teamId, int targetTier) {
        if (targetTier < 2 || targetTier > 3) return false;
        int current = getCurrentTier(teamId);
        if (targetTier <= current) return false;
        if (getPendingUnlockTier(teamId) > 0) return false;
        return true;
    }

    public boolean canBuyTier(UUID uuid, int targetTier) {
        return canBuyTier(1, targetTier) || canBuyTier(2, targetTier);
    }

    public int getTierCost(int targetTier) {
        return switch (targetTier) {
            case 2 -> ConfigManager.getInstance().getTier2Cost();
            case 3 -> ConfigManager.getInstance().getTier3Cost();
            default -> Integer.MAX_VALUE;
        };
    }

    public boolean buyTierUpgrade(ServerPlayer player, MoneyManager moneyManager, int targetTier, int teamId) {
        if (!canBuyTier(teamId, targetTier)) return false;

        int cost = getTierCost(targetTier);
        if (!moneyManager.canAfford(cost)) return false;

        moneyManager.spend(cost);
        pendingUnlockTier.put(teamId, targetTier);
        unlockTicksRemaining.put(teamId, UNLOCK_DELAY_TICKS);
        return true;
    }

    public boolean buyTierUpgrade(ServerPlayer player, MoneyManager moneyManager, int targetTier) {
        var gm = TowerDefenseMod.getInstance().getGameManager();
        PlayerState ps = gm != null ? gm.getPlayerState(player) : null;
        int teamId = ps != null ? ps.getSide() : 1;
        return buyTierUpgrade(player, moneyManager, targetTier, teamId);
    }

    public void tick() {
        var gm = TowerDefenseMod.getInstance().getGameManager();
        if (gm == null || gm.getTeam1() == null || gm.getTeam2() == null) return;

        for (int teamId : new int[]{1, 2}) {
            int remaining = unlockTicksRemaining.getOrDefault(teamId, 0);
            if (remaining <= 0) continue;

            remaining--;
            unlockTicksRemaining.put(teamId, remaining);

            if (remaining <= 0) {
                int tier = pendingUnlockTier.remove(teamId);
                unlockTicksRemaining.remove(teamId);
                currentTier.put(teamId, tier);

                TeamState team = teamId == 1 ? gm.getTeam1() : gm.getTeam2();
                if (team != null) {
                    NexusManager nexus = team.getNexusManager();
                    nexus.addMaxHpBonus(50);
                    nexus.heal(50);
                }
            }
        }
    }

}
