package com.towerdefense.game;

import com.towerdefense.tower.TowerUpgradeManager;
import com.towerdefense.wave.MobUpgradeManager;

import java.util.UUID;

/**
 * Minimal interface exposing the game-session data that {@link com.towerdefense.shop.ShopScreenHandler}
 * needs. Implemented by {@link GameManager}; tests inject a lightweight stub.
 */
public interface IGameSession {
    TierManager getTierManager();
    TowerUpgradeManager getTowerUpgradeManager();
    MobUpgradeManager getMobUpgradeManager();
    /** Returns the team side (1 or 2) for the given player UUID, or -1 if unknown. */
    int getTeamSide(UUID playerUUID);
    boolean isActive();
}
