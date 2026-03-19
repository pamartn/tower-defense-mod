package com.towerdefense.game;

/**
 * Minimal interface used by {@link com.towerdefense.tower.TowerManager} to
 * report structure destruction back to the game session without depending on
 * the full {@link GameManager} class.
 */
public interface StructureEventSink {
    boolean isActive();
    void onStructureDestroyed(int victimTeamId);
}
