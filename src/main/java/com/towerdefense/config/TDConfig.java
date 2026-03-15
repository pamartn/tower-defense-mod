package com.towerdefense.config;

import java.util.HashMap;
import java.util.Map;

/**
 * Root configuration POJO for JSON serialization.
 * All fields are nullable; ConfigManager provides defaults.
 */
public class TDConfig {

    public GameSection game = new GameSection();
    public ArenaSection arena = new ArenaSection();
    public Map<String, MobSection> mobs = new HashMap<>();
    public Map<String, SpawnerSection> spawners = new HashMap<>();
    public Map<String, TowerSection> towers = new HashMap<>();
    public Map<String, GeneratorSection> generators = new HashMap<>();
    public Map<String, SpellSection> spells = new HashMap<>();
    public Map<String, Integer> weapons = new HashMap<>();
    public Map<String, Integer> walls = new HashMap<>();
    public UpgradesSection upgrades = new UpgradesSection();
    public SpellsEffectSection spellEffects = new SpellsEffectSection();
    public TowerEffectSection towerEffects = new TowerEffectSection();
    public SpawnerEffectSection spawnerEffects = new SpawnerEffectSection();
    public GameEventSection gameEvents = new GameEventSection();

    public static class GameSection {
        public int startingMoney = 500;
        public int nexusMaxHp = 100;
        public int tier2Cost = 200;
        public int tier3Cost = 500;
        public double nexusExplosionRadius = 3.5;
        public int prepPhaseTicks = 600;
        public int basePassiveIncome = 2;
        public int basePassiveInterval = 200;
        public int defeatDelayTicks = 100;
        public int chainExplosionDelay = 5;
    }

    public static class ArenaSection {
        public int size = 64;
        public int wallHeight = 15;
        public int arenaY = 64;
        public int standDepth = 12;
        public int standHeight = 20;
    }

    public static class MobSection {
        public double baseHp;
        public double speed;
        public int nexusDamage;
        public int moneyReward;
    }

    public static class SpawnerSection {
        public int price;
        public int spawnIntervalTicks;
    }

    public static class TowerSection {
        public int power;
        public double range;
        public int fireRateInTicks;
        public int price;
    }

    public static class GeneratorSection {
        public int price;
        public int incomeAmount;
        public int incomeIntervalTicks;
    }

    public static class SpellSection {
        public int price;
    }

    public static class UpgradesSection {
        public int hpBaseCost = 20;
        public int speedBaseCost = 25;
        public int damageBaseCost = 30;
        public int spawnerExtraCostPerUpgrade = 5;
        public double hpMultiplierPerLevel = 0.2;
        public double speedMultiplierPerLevel = 0.1;
        public int towerUpgradeBaseCost = 25;
        public double towerPowerMultiplierPerLevel = 0.15;
        public double towerFireRateMultiplierPerLevel = 0.1;
        public double towerEffectDurationMultiplierPerLevel = 0.2;
    }

    public static class SpellsEffectSection {
        public int fireballLifetime = 200;
        public double fireballExplosionRadius = 3.0;
        public int freezeDurationTicks = 100;
        public int healNexusAmount = 15;
        public int lightningBoxSize = 6;
        public int lightningDamage = 1000;
        public int shieldDurationTicks = 200;
    }

    public static class TowerEffectSection {
        public int fireTicks = 200;
        public int slowDurationTicks = 100;
        public int slowAmplifier = 1;
        public int poisonDurationTicks = 120;
        public int poisonAmplifier = 1;
        public int chainLightningBoxSize = 8;
        public int chainLightningMaxTargets = 2;
        public double aoeBoxSize = 8;
        public double aoeDamageRadius = 4.0;
    }

    public static class SpawnerEffectSection {
        public double spawnSpread = 2.0;
        public double endermanTeleportRange = 8;
        public int witchHealBoxSize = 6;
        public int witchHealDurationTicks = 60;
        public int witchHealIntervalTicks = 120;
        public double witchHealAmount = 0.2;
        public double followRange = 128.0;
        public int specialMobTickInterval = 60;
    }

    public static class GameEventSection {
        public int waveEventIntervalTicks = 2400;
        public int bonusMoney = 50;
        public int doubleIncomeMultiplier = 3;
        public int speedBoostDurationTicks = 400;
        public int structureDestroyedBounty = 10;
        public int oreSpawnIntervalTicks = 400;
    }
}
