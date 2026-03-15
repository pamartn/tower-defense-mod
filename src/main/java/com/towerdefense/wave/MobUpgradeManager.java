package com.towerdefense.wave;

import com.towerdefense.config.ConfigManager;

import java.util.*;

public class MobUpgradeManager {

    public enum UpgradeType {
        HP("HP", "+20% HP per level"),
        SPEED("Speed", "+10% speed per level"),
        DAMAGE("Damage", "+1 nexus damage per level");

        private final String name;
        private final String description;

        UpgradeType(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
    }

    private final Map<Integer, Map<MobType, Map<UpgradeType, Integer>>> upgrades = new HashMap<>();

    public int getLevel(int teamId, MobType mob, UpgradeType upgrade) {
        return upgrades
                .getOrDefault(teamId, Collections.emptyMap())
                .getOrDefault(mob, Collections.emptyMap())
                .getOrDefault(upgrade, 0);
    }

    public void addLevel(int teamId, MobType mob, UpgradeType upgrade) {
        upgrades
                .computeIfAbsent(teamId, k -> new EnumMap<>(MobType.class))
                .computeIfAbsent(mob, k -> new EnumMap<>(UpgradeType.class))
                .merge(upgrade, 1, Integer::sum);
    }

    public int getTotalUpgrades(int teamId, MobType mob) {
        Map<UpgradeType, Integer> map = upgrades
                .getOrDefault(teamId, Collections.emptyMap())
                .getOrDefault(mob, Collections.emptyMap());
        return map.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int getUpgradeCost(int teamId, MobType mob, UpgradeType upgrade) {
        int level = getLevel(teamId, mob, upgrade);
        int baseCost = ConfigManager.getInstance().getUpgradeBaseCost(upgrade);
        return baseCost + (level * baseCost);
    }

    public int getSpawnerExtraCost(int teamId, MobType mob) {
        return getTotalUpgrades(teamId, mob) * ConfigManager.getInstance().getSpawnerExtraCostPerUpgrade();
    }

    public double getHpMultiplier(int teamId, MobType mob) {
        return 1.0 + getLevel(teamId, mob, UpgradeType.HP) * ConfigManager.getInstance().getHpMultiplierPerLevel();
    }

    public double getSpeedMultiplier(int teamId, MobType mob) {
        return 1.0 + getLevel(teamId, mob, UpgradeType.SPEED) * ConfigManager.getInstance().getSpeedMultiplierPerLevel();
    }

    public int getDamageBonus(int teamId, MobType mob) {
        return getLevel(teamId, mob, UpgradeType.DAMAGE);
    }

    public void reset() {
        upgrades.clear();
    }

    public static List<UpgradeEntry> getAllUpgradeEntries() {
        List<UpgradeEntry> entries = new ArrayList<>();
        for (MobType mob : MobType.values()) {
            for (UpgradeType upgrade : UpgradeType.values()) {
                entries.add(new UpgradeEntry(mob, upgrade));
            }
        }
        return entries;
    }

    public record UpgradeEntry(MobType mob, UpgradeType upgrade) {}
}
