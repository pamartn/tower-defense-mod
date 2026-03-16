package com.towerdefense.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.towerdefense.TowerDefenseMod;
import com.towerdefense.game.IncomeGeneratorType;
import com.towerdefense.shop.WallShopItem;
import com.towerdefense.shop.WeaponShopItem;
import com.towerdefense.spell.SpellType;
import com.towerdefense.tower.TowerType;
import com.towerdefense.wave.MobType;
import com.towerdefense.wave.MobUpgradeManager;
import com.towerdefense.wave.SpawnerType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe configuration manager. Loads from JSON, provides getters with defaults.
 */
public class ConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int WEB_PORT = 8765;

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile TDConfig config;
    private Path configPath;

    private static ConfigManager instance;

    public static ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    public void init(Path configDir) {
        configPath = configDir.resolve("towerdefense.json");
        lock.writeLock().lock();
        try {
            if (Files.exists(configPath)) {
                try {
                    String json = Files.readString(configPath);
                    config = GSON.fromJson(json, TDConfig.class);
                    if (config == null) config = createDefaults();
                    else mergeDefaults(config);
                } catch (Exception e) {
                    TowerDefenseMod.LOGGER.warn("Failed to load config, using defaults: {}", e.getMessage());
                    config = createDefaults();
                }
            } else {
                config = createDefaults();
                saveUnlocked();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getWebPort() {
        return WEB_PORT;
    }

    public TDConfig getConfig() {
        lock.readLock().lock();
        try {
            return config;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void updateConfig(TDConfig newConfig) {
        lock.writeLock().lock();
        try {
            mergeDefaults(newConfig);
            this.config = newConfig;
            saveUnlocked();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void patchConfig(TDConfig patch) {
        lock.writeLock().lock();
        try {
            config = merge(config, patch);
            saveUnlocked();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Apply server config on client for display (shop, etc.). Does NOT save to disk.
     */
    public void applyServerConfig(TDConfig serverConfig) {
        lock.writeLock().lock();
        try {
            mergeDefaults(serverConfig);
            this.config = serverConfig;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void save() {
        lock.writeLock().lock();
        try {
            saveUnlocked();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void saveUnlocked() {
        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, GSON.toJson(config));
        } catch (IOException e) {
            TowerDefenseMod.LOGGER.error("Failed to save config: {}", e.getMessage());
        }
    }

    private TDConfig createDefaults() {
        TDConfig c = new TDConfig();
        // Mobs
        for (MobType m : MobType.values()) {
            TDConfig.MobSection s = new TDConfig.MobSection();
            s.baseHp = m.getDefaultBaseHp();
            s.speed = m.getDefaultSpeed();
            s.nexusDamage = m.getDefaultNexusDamage();
            s.moneyReward = m.getDefaultMoneyReward();
            c.mobs.put(m.name(), s);
        }
        // Spawners
        for (SpawnerType s : SpawnerType.values()) {
            TDConfig.SpawnerSection sec = new TDConfig.SpawnerSection();
            sec.price = s.getDefaultPrice();
            sec.spawnIntervalTicks = s.getDefaultSpawnIntervalTicks();
            sec.tier = s.getDefaultTier();
            c.spawners.put(s.name(), sec);
        }
        // Towers (from TowerRegistry defaults)
        c.towers.put("BASIC", tower(2, 8, 40, 10));
        c.towers.put("ARCHER", tower(2, 12, 35, 25));
        c.towers.put("CANNON", tower(8, 16, 60, 50));
        c.towers.put("LASER", tower(6, 20, 25, 100));
        c.towers.put("FIRE", tower(1, 10, 40, 40));
        c.towers.put("SLOW", tower(0, 12, 60, 35));
        c.towers.put("POISON", tower(0, 8, 50, 30));
        c.towers.put("SNIPER", tower(15, 25, 80, 75));
        c.towers.put("CHAIN_LIGHTNING", tower(3, 12, 45, 60));
        c.towers.put("AOE", tower(4, 10, 55, 55));
        // Towers (set tier)
        for (TowerType t : TowerType.values()) {
            TDConfig.TowerSection sec = c.towers.get(t.name());
            if (sec != null) sec.tier = t.getDefaultTier();
        }
        // Generators
        for (IncomeGeneratorType g : IncomeGeneratorType.values()) {
            TDConfig.GeneratorSection sec = new TDConfig.GeneratorSection();
            sec.price = g.getDefaultPrice();
            sec.incomeAmount = g.getDefaultIncomeAmount();
            sec.incomeIntervalTicks = g.getDefaultIncomeIntervalTicks();
            sec.tier = g.getDefaultTier();
            c.generators.put(g.name(), sec);
        }
        // Spells
        for (SpellType s : SpellType.values()) {
            TDConfig.SpellSection sec = new TDConfig.SpellSection();
            sec.price = s.getDefaultPrice();
            sec.tier = s.getDefaultTier();
            c.spells.put(s.name(), sec);
        }
        // Weapons
        for (WeaponShopItem w : WeaponShopItem.getAll()) {
            c.weapons.put(w.name(), w.defaultPrice());
            c.weaponTiers.put(w.name(), w.getDefaultTier());
        }
        // Walls
        for (WallShopItem w : WallShopItem.getAll()) {
            c.walls.put(w.name(), w.defaultPrice());
            c.wallTiers.put(w.name(), w.getDefaultTier());
        }
        return c;
    }

    private TDConfig.TowerSection tower(int power, double range, int rate, int price) {
        TDConfig.TowerSection t = new TDConfig.TowerSection();
        t.power = power;
        t.range = range;
        t.fireRateInTicks = rate;
        t.price = price;
        return t;
    }

    private void mergeDefaults(TDConfig c) {
        if (c.game == null) c.game = new TDConfig.GameSection();
        if (c.game.tier2Cost == 0) c.game.tier2Cost = 200;
        if (c.game.tier3Cost == 0) c.game.tier3Cost = 500;
        if (c.game.soloModeStartingMultiplier <= 0) c.game.soloModeStartingMultiplier = 1.5;
        if (c.game.soloModeIncomeMultiplier <= 0) c.game.soloModeIncomeMultiplier = 1.5;
        if (c.game.soloModeGeneratorMultiplier <= 0) c.game.soloModeGeneratorMultiplier = 1.5;
        if (c.arena == null) c.arena = new TDConfig.ArenaSection();
        if (c.mobs == null) c.mobs = new java.util.HashMap<>();
        if (c.spawners == null) c.spawners = new java.util.HashMap<>();
        removeDeprecatedKeys(c);
        if (c.towers == null) c.towers = new java.util.HashMap<>();
        if (c.generators == null) c.generators = new java.util.HashMap<>();
        if (c.spells == null) c.spells = new java.util.HashMap<>();
        if (c.weapons == null) c.weapons = new java.util.HashMap<>();
        if (c.walls == null) c.walls = new java.util.HashMap<>();
        for (WallShopItem w : WallShopItem.getAll()) {
            c.walls.putIfAbsent(w.name(), w.defaultPrice());
        }
        // Backfill tier defaults for entries that existed before the tier field was added
        for (SpawnerType s : SpawnerType.values()) {
            TDConfig.SpawnerSection sec = c.spawners.get(s.name());
            if (sec != null && sec.tier == 0) sec.tier = s.getDefaultTier();
        }
        for (TowerType t : TowerType.values()) {
            TDConfig.TowerSection sec = c.towers.get(t.name());
            if (sec != null && sec.tier == 0) sec.tier = t.getDefaultTier();
        }
        for (IncomeGeneratorType g : IncomeGeneratorType.values()) {
            TDConfig.GeneratorSection sec = c.generators.get(g.name());
            if (sec != null && sec.tier == 0) sec.tier = g.getDefaultTier();
        }
        for (SpellType s : SpellType.values()) {
            TDConfig.SpellSection sec = c.spells.get(s.name());
            if (sec != null && sec.tier == 0) sec.tier = s.getDefaultTier();
        }
        if (c.weaponTiers == null) c.weaponTiers = new java.util.HashMap<>();
        for (WeaponShopItem w : WeaponShopItem.getAll()) {
            c.weaponTiers.putIfAbsent(w.name(), w.getDefaultTier());
        }
        if (c.wallTiers == null) c.wallTiers = new java.util.HashMap<>();
        for (WallShopItem w : WallShopItem.getAll()) {
            c.wallTiers.putIfAbsent(w.name(), w.getDefaultTier());
        }
        if (c.upgrades == null) c.upgrades = new TDConfig.UpgradesSection();
        if (c.spellEffects == null) c.spellEffects = new TDConfig.SpellsEffectSection();
        if (c.towerEffects == null) c.towerEffects = new TDConfig.TowerEffectSection();
        if (c.spawnerEffects == null) c.spawnerEffects = new TDConfig.SpawnerEffectSection();
        if (c.gameEvents == null) c.gameEvents = new TDConfig.GameEventSection();
    }

    private void removeDeprecatedKeys(TDConfig c) {
        if (c.mobs != null) c.mobs.remove("PHANTOM");
        if (c.spawners != null) c.spawners.remove("PHANTOM_SPAWNER");
    }

    private TDConfig merge(TDConfig base, TDConfig patch) {
        TDConfig result = GSON.fromJson(GSON.toJson(base), TDConfig.class);
        if (patch.game != null) result.game = patch.game;
        if (patch.arena != null) result.arena = patch.arena;
        if (patch.mobs != null && !patch.mobs.isEmpty()) result.mobs.putAll(patch.mobs);
        if (patch.spawners != null && !patch.spawners.isEmpty()) result.spawners.putAll(patch.spawners);
        if (patch.towers != null && !patch.towers.isEmpty()) result.towers.putAll(patch.towers);
        if (patch.generators != null && !patch.generators.isEmpty()) result.generators.putAll(patch.generators);
        if (patch.spells != null && !patch.spells.isEmpty()) result.spells.putAll(patch.spells);
        if (patch.weapons != null && !patch.weapons.isEmpty()) result.weapons.putAll(patch.weapons);
        if (patch.walls != null && !patch.walls.isEmpty()) result.walls.putAll(patch.walls);
        if (patch.weaponTiers != null && !patch.weaponTiers.isEmpty()) result.weaponTiers.putAll(patch.weaponTiers);
        if (patch.wallTiers != null && !patch.wallTiers.isEmpty()) result.wallTiers.putAll(patch.wallTiers);
        if (patch.upgrades != null) result.upgrades = patch.upgrades;
        if (patch.spellEffects != null) result.spellEffects = patch.spellEffects;
        if (patch.towerEffects != null) result.towerEffects = patch.towerEffects;
        if (patch.spawnerEffects != null) result.spawnerEffects = patch.spawnerEffects;
        if (patch.gameEvents != null) result.gameEvents = patch.gameEvents;
        removeDeprecatedKeys(result);
        return result;
    }

    // ─── Getters (game section) ───
    public int getStartingMoney() {
        lock.readLock().lock();
        try {
            return config.game.startingMoney;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getNexusMaxHp() {
        lock.readLock().lock();
        try {
            return config.game.nexusMaxHp;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getTier2Cost() {
        lock.readLock().lock();
        try {
            return config.game.tier2Cost;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getTier3Cost() {
        lock.readLock().lock();
        try {
            return config.game.tier3Cost;
        } finally {
            lock.readLock().unlock();
        }
    }

    public double getNexusExplosionRadius() {
        lock.readLock().lock();
        try {
            return config.game.nexusExplosionRadius;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getPrepPhaseTicks() {
        lock.readLock().lock();
        try {
            return config.game.prepPhaseTicks;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getBasePassiveIncome() {
        lock.readLock().lock();
        try {
            return config.game.basePassiveIncome;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getBasePassiveInterval() {
        lock.readLock().lock();
        try {
            return config.game.basePassiveInterval;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getDefeatDelayTicks() {
        lock.readLock().lock();
        try {
            return config.game.defeatDelayTicks;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getChainExplosionDelay() {
        lock.readLock().lock();
        try {
            return config.game.chainExplosionDelay;
        } finally {
            lock.readLock().unlock();
        }
    }

    public double getSoloModeStartingMultiplier() {
        lock.readLock().lock();
        try {
            return config.game.soloModeStartingMultiplier > 0 ? config.game.soloModeStartingMultiplier : 1.5;
        } finally {
            lock.readLock().unlock();
        }
    }

    public double getSoloModeIncomeMultiplier() {
        lock.readLock().lock();
        try {
            return config.game.soloModeIncomeMultiplier > 0 ? config.game.soloModeIncomeMultiplier : 1.5;
        } finally {
            lock.readLock().unlock();
        }
    }

    public double getSoloModeGeneratorMultiplier() {
        lock.readLock().lock();
        try {
            return config.game.soloModeGeneratorMultiplier > 0 ? config.game.soloModeGeneratorMultiplier : 1.5;
        } finally {
            lock.readLock().unlock();
        }
    }

    // ─── Arena ───
    public int getArenaSize() {
        lock.readLock().lock();
        try {
            return config.arena.size;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getArenaWallHeight() {
        lock.readLock().lock();
        try {
            return config.arena.wallHeight;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getArenaY() {
        lock.readLock().lock();
        try {
            return config.arena.arenaY;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getStandDepth() {
        lock.readLock().lock();
        try {
            return config.arena.standDepth;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getStandHeight() {
        lock.readLock().lock();
        try {
            return config.arena.standHeight;
        } finally {
            lock.readLock().unlock();
        }
    }

    // ─── Mobs ───
    public double getMobBaseHp(MobType type) {
        lock.readLock().lock();
        try {
            TDConfig.MobSection s = config.mobs.get(type.name());
            return s != null ? s.baseHp : type.getDefaultBaseHp();
        } finally {
            lock.readLock().unlock();
        }
    }

    public double getMobSpeed(MobType type) {
        lock.readLock().lock();
        try {
            TDConfig.MobSection s = config.mobs.get(type.name());
            return s != null ? s.speed : type.getDefaultSpeed();
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getMobNexusDamage(MobType type) {
        lock.readLock().lock();
        try {
            TDConfig.MobSection s = config.mobs.get(type.name());
            return s != null ? s.nexusDamage : type.getDefaultNexusDamage();
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getMobMoneyReward(MobType type) {
        lock.readLock().lock();
        try {
            TDConfig.MobSection s = config.mobs.get(type.name());
            return s != null ? s.moneyReward : type.getDefaultMoneyReward();
        } finally {
            lock.readLock().unlock();
        }
    }

    // ─── Spawners ───
    public int getSpawnerPrice(SpawnerType type) {
        lock.readLock().lock();
        try {
            TDConfig.SpawnerSection s = config.spawners.get(type.name());
            return s != null ? s.price : type.getDefaultPrice();
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getSpawnerInterval(SpawnerType type) {
        lock.readLock().lock();
        try {
            TDConfig.SpawnerSection s = config.spawners.get(type.name());
            return s != null ? s.spawnIntervalTicks : type.getDefaultSpawnIntervalTicks();
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getSpawnerTier(SpawnerType type) {
        lock.readLock().lock();
        try {
            TDConfig.SpawnerSection s = config.spawners.get(type.name());
            return (s != null && s.tier > 0) ? s.tier : type.getDefaultTier();
        } finally {
            lock.readLock().unlock();
        }
    }

    // ─── Towers ───
    public int getTowerPower(TowerType type) {
        lock.readLock().lock();
        try {
            TDConfig.TowerSection s = config.towers.get(type.name());
            return s != null ? s.power : 0;
        } finally {
            lock.readLock().unlock();
        }
    }

    public double getTowerRange(TowerType type) {
        lock.readLock().lock();
        try {
            TDConfig.TowerSection s = config.towers.get(type.name());
            return s != null ? s.range : 10;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getTowerFireRate(TowerType type) {
        lock.readLock().lock();
        try {
            TDConfig.TowerSection s = config.towers.get(type.name());
            return s != null ? s.fireRateInTicks : 40;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getTowerPrice(TowerType type) {
        lock.readLock().lock();
        try {
            TDConfig.TowerSection s = config.towers.get(type.name());
            return s != null ? s.price : 10;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getTowerTier(TowerType type) {
        lock.readLock().lock();
        try {
            TDConfig.TowerSection s = config.towers.get(type.name());
            return (s != null && s.tier > 0) ? s.tier : type.getDefaultTier();
        } finally {
            lock.readLock().unlock();
        }
    }

    // ─── Generators ───
    public int getGeneratorPrice(IncomeGeneratorType type) {
        lock.readLock().lock();
        try {
            TDConfig.GeneratorSection s = config.generators.get(type.name());
            return s != null ? s.price : type.getPrice();
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getGeneratorIncomeAmount(IncomeGeneratorType type) {
        lock.readLock().lock();
        try {
            TDConfig.GeneratorSection s = config.generators.get(type.name());
            return s != null ? s.incomeAmount : type.getDefaultIncomeAmount();
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getGeneratorIncomeInterval(IncomeGeneratorType type) {
        lock.readLock().lock();
        try {
            TDConfig.GeneratorSection s = config.generators.get(type.name());
            return s != null ? s.incomeIntervalTicks : type.getDefaultIncomeIntervalTicks();
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getGeneratorTier(IncomeGeneratorType type) {
        lock.readLock().lock();
        try {
            TDConfig.GeneratorSection s = config.generators.get(type.name());
            return (s != null && s.tier > 0) ? s.tier : type.getDefaultTier();
        } finally {
            lock.readLock().unlock();
        }
    }

    // ─── Spells ───
    public int getSpellPrice(SpellType type) {
        lock.readLock().lock();
        try {
            TDConfig.SpellSection s = config.spells.get(type.name());
            return s != null ? s.price : type.getDefaultPrice();
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getSpellTier(SpellType type) {
        lock.readLock().lock();
        try {
            TDConfig.SpellSection s = config.spells.get(type.name());
            return (s != null && s.tier > 0) ? s.tier : type.getDefaultTier();
        } finally {
            lock.readLock().unlock();
        }
    }

    // ─── Weapons ───
    public int getWeaponPrice(String name) {
        lock.readLock().lock();
        try {
            Integer p = config.weapons.get(name);
            return p != null ? p : 15;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getWeaponTier(WeaponShopItem item) {
        lock.readLock().lock();
        try {
            Integer t = config.weaponTiers.get(item.name());
            return (t != null && t > 0) ? t : item.getDefaultTier();
        } finally {
            lock.readLock().unlock();
        }
    }

    // ─── Walls ───
    public int getWallPrice(String name) {
        lock.readLock().lock();
        try {
            Integer p = config.walls.get(name);
            return p != null ? p : 3;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getWallTier(WallShopItem item) {
        lock.readLock().lock();
        try {
            Integer t = config.wallTiers.get(item.name());
            return (t != null && t > 0) ? t : item.getDefaultTier();
        } finally {
            lock.readLock().unlock();
        }
    }

    // ─── Upgrades ───
    public int getUpgradeBaseCost(MobUpgradeManager.UpgradeType type) {
        lock.readLock().lock();
        try {
            return switch (type) {
                case HP -> config.upgrades.hpBaseCost;
                case SPEED -> config.upgrades.speedBaseCost;
                case DAMAGE -> config.upgrades.damageBaseCost;
            };
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getSpawnerExtraCostPerUpgrade() {
        lock.readLock().lock();
        try {
            return config.upgrades.spawnerExtraCostPerUpgrade;
        } finally {
            lock.readLock().unlock();
        }
    }

    public double getHpMultiplierPerLevel() {
        lock.readLock().lock();
        try {
            return config.upgrades.hpMultiplierPerLevel;
        } finally {
            lock.readLock().unlock();
        }
    }

    public double getSpeedMultiplierPerLevel() {
        lock.readLock().lock();
        try {
            return config.upgrades.speedMultiplierPerLevel;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getTowerUpgradeBaseCost() {
        lock.readLock().lock();
        try {
            int v = config.upgrades.towerUpgradeBaseCost;
            return v > 0 ? v : 25;
        } finally {
            lock.readLock().unlock();
        }
    }

    public double getTowerPowerMultiplierPerLevel() {
        lock.readLock().lock();
        try {
            return config.upgrades.towerPowerMultiplierPerLevel;
        } finally {
            lock.readLock().unlock();
        }
    }

    public double getTowerFireRateMultiplierPerLevel() {
        lock.readLock().lock();
        try {
            return config.upgrades.towerFireRateMultiplierPerLevel;
        } finally {
            lock.readLock().unlock();
        }
    }

    public double getTowerEffectDurationMultiplierPerLevel() {
        lock.readLock().lock();
        try {
            return config.upgrades.towerEffectDurationMultiplierPerLevel;
        } finally {
            lock.readLock().unlock();
        }
    }

    // ─── Spell effects ───
    public int getFireballLifetime() {
        lock.readLock().lock();
        try {
            return config.spellEffects.fireballLifetime;
        } finally {
            lock.readLock().unlock();
        }
    }

    public double getFireballExplosionRadius() {
        lock.readLock().lock();
        try {
            return config.spellEffects.fireballExplosionRadius;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getFreezeDurationTicks() {
        lock.readLock().lock();
        try {
            return config.spellEffects.freezeDurationTicks;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getHealNexusAmount() {
        lock.readLock().lock();
        try {
            return config.spellEffects.healNexusAmount;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getLightningBoxSize() {
        lock.readLock().lock();
        try {
            return config.spellEffects.lightningBoxSize;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getLightningDamage() {
        lock.readLock().lock();
        try {
            return config.spellEffects.lightningDamage;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getShieldDurationTicks() {
        lock.readLock().lock();
        try {
            return config.spellEffects.shieldDurationTicks;
        } finally {
            lock.readLock().unlock();
        }
    }

    // ─── Tower effects ───
    public int getFireTicks() {
        lock.readLock().lock();
        try {
            return config.towerEffects.fireTicks;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getSlowDurationTicks() {
        lock.readLock().lock();
        try {
            return config.towerEffects.slowDurationTicks;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getSlowAmplifier() {
        lock.readLock().lock();
        try {
            return config.towerEffects.slowAmplifier;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getPoisonDurationTicks() {
        lock.readLock().lock();
        try {
            return config.towerEffects.poisonDurationTicks;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getPoisonAmplifier() {
        lock.readLock().lock();
        try {
            return config.towerEffects.poisonAmplifier;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getChainLightningBoxSize() {
        lock.readLock().lock();
        try {
            return config.towerEffects.chainLightningBoxSize;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getChainLightningMaxTargets() {
        lock.readLock().lock();
        try {
            return config.towerEffects.chainLightningMaxTargets;
        } finally {
            lock.readLock().unlock();
        }
    }

    public double getAoeBoxSize() {
        lock.readLock().lock();
        try {
            return config.towerEffects.aoeBoxSize;
        } finally {
            lock.readLock().unlock();
        }
    }

    public double getAoeDamageRadius() {
        lock.readLock().lock();
        try {
            return config.towerEffects.aoeDamageRadius;
        } finally {
            lock.readLock().unlock();
        }
    }

    // ─── Spawner effects ───
    public double getSpawnSpread() {
        lock.readLock().lock();
        try {
            return config.spawnerEffects.spawnSpread;
        } finally {
            lock.readLock().unlock();
        }
    }

    public double getEndermanTeleportRange() {
        lock.readLock().lock();
        try {
            return config.spawnerEffects.endermanTeleportRange;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getWitchHealBoxSize() {
        lock.readLock().lock();
        try {
            return config.spawnerEffects.witchHealBoxSize;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getWitchHealDurationTicks() {
        lock.readLock().lock();
        try {
            return config.spawnerEffects.witchHealDurationTicks;
        } finally {
            lock.readLock().unlock();
        }
    }

    public double getWitchHealAmount() {
        lock.readLock().lock();
        try {
            return config.spawnerEffects.witchHealAmount;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getWitchHealIntervalTicks() {
        lock.readLock().lock();
        try {
            int v = config.spawnerEffects.witchHealIntervalTicks;
            return v > 0 ? v : 120;
        } finally {
            lock.readLock().unlock();
        }
    }

    public double getFollowRange() {
        lock.readLock().lock();
        try {
            return config.spawnerEffects.followRange;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getSpecialMobTickInterval() {
        lock.readLock().lock();
        try {
            return config.spawnerEffects.specialMobTickInterval;
        } finally {
            lock.readLock().unlock();
        }
    }

    // ─── Game events ───
    public int getWaveEventIntervalTicks() {
        lock.readLock().lock();
        try {
            return config.gameEvents.waveEventIntervalTicks;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getBonusMoney() {
        lock.readLock().lock();
        try {
            return config.gameEvents.bonusMoney;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getDoubleIncomeMultiplier() {
        lock.readLock().lock();
        try {
            return config.gameEvents.doubleIncomeMultiplier;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getSpeedBoostDurationTicks() {
        lock.readLock().lock();
        try {
            return config.gameEvents.speedBoostDurationTicks;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getStructureDestroyedBounty() {
        lock.readLock().lock();
        try {
            return config.gameEvents.structureDestroyedBounty;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getOreSpawnIntervalTicks() {
        lock.readLock().lock();
        try {
            return config.gameEvents.oreSpawnIntervalTicks;
        } finally {
            lock.readLock().unlock();
        }
    }
}
