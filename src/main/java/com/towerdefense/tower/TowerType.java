package com.towerdefense.tower;

import com.towerdefense.config.ConfigManager;

public enum TowerType {
    ARCHER,
    CANNON,
    LASER,
    FIRE,
    SLOW,
    POISON,
    SNIPER,
    CHAIN_LIGHTNING,
    AOE,
    SHOTGUN;

    public int getDefaultTier() {
        return switch (this) {
            case ARCHER, POISON, SLOW, FIRE, SHOTGUN -> 1;
            case CANNON, CHAIN_LIGHTNING, AOE -> 2;
            case SNIPER, LASER -> 3;
        };
    }

    public int getTier() {
        return ConfigManager.getInstance().getTowerTier(this);
    }
}
