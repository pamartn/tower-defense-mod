package com.towerdefense.tower;

import com.towerdefense.config.ConfigManager;

public enum TowerType {
    BASIC,
    ARCHER,
    CANNON,
    LASER,
    FIRE,
    SLOW,
    POISON,
    SNIPER,
    CHAIN_LIGHTNING,
    AOE;

    public int getDefaultTier() {
        return switch (this) {
            case BASIC, ARCHER, POISON, SLOW, FIRE -> 1;
            case CANNON, CHAIN_LIGHTNING, AOE -> 2;
            case SNIPER, LASER -> 3;
        };
    }

    public int getTier() {
        return ConfigManager.getInstance().getTowerTier(this);
    }
}
