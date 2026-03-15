package com.towerdefense.tower;

import com.towerdefense.config.ConfigManager;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class TowerUpgradeManager {

    private final Map<Integer, Map<TowerType, Integer>> levels = new HashMap<>();

    public int getLevel(int teamId, TowerType type) {
        return levels
                .getOrDefault(teamId, Collections.emptyMap())
                .getOrDefault(type, 0);
    }

    public void addLevel(int teamId, TowerType type) {
        levels
                .computeIfAbsent(teamId, k -> new EnumMap<>(TowerType.class))
                .merge(type, 1, Integer::sum);
    }

    public int getUpgradeCost(int teamId, TowerType type) {
        int level = getLevel(teamId, type);
        int baseCost = ConfigManager.getInstance().getTowerUpgradeBaseCost();
        return baseCost + (level * baseCost);
    }

    public int getEffectivePower(int basePower, int teamId, TowerType type) {
        int level = getLevel(teamId, type);
        double mult = 1.0 + level * ConfigManager.getInstance().getTowerPowerMultiplierPerLevel();
        return (int) Math.round(basePower * mult);
    }

    public int getEffectiveFireRate(int baseFireRate, int teamId, TowerType type) {
        int level = getLevel(teamId, type);
        double mult = 1.0 - level * ConfigManager.getInstance().getTowerFireRateMultiplierPerLevel();
        int rate = (int) Math.round(baseFireRate * Math.max(0.1, mult));
        return Math.max(1, rate);
    }

    public int getEffectiveEffectDuration(int baseDuration, int teamId, TowerType type) {
        int level = getLevel(teamId, type);
        double mult = 1.0 + level * ConfigManager.getInstance().getTowerEffectDurationMultiplierPerLevel();
        return (int) Math.round(baseDuration * mult);
    }

    public void reset() {
        levels.clear();
    }
}
