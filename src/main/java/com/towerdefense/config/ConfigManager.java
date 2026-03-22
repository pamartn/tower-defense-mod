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
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe configuration manager. Loads from JSON, provides getters with defaults.
 */
public class ConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int WEB_PORT = 8765;

    // Initialised with a plain TDConfig so that TDConfig field-level defaults (tier costs, nexus HP, etc.)
    // are available immediately, even before init() is called (e.g. in unit tests without a MC server).
    // createDefaults() populates MC-dependent sections (mob/spawner/generator maps) and is only called
    // from init() once Minecraft registries are bootstrapped.
    private final AtomicReference<TDConfig> configRef = new AtomicReference<>(new TDConfig());
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
        TDConfig loaded;
        if (Files.exists(configPath)) {
            try {
                String json = Files.readString(configPath);
                loaded = GSON.fromJson(json, TDConfig.class);
                if (loaded == null) loaded = createDefaults();
                else mergeDefaults(loaded);
            } catch (Exception e) {
                TowerDefenseMod.LOGGER.warn("Failed to load config, using defaults: {}", e.getMessage());
                loaded = createDefaults();
            }
        } else {
            loaded = createDefaults();
            configRef.set(loaded);
            saveUnlocked();
            return;
        }
        configRef.set(loaded);
    }

    public int getWebPort() {
        return WEB_PORT;
    }

    public TDConfig getConfig() {
        return configRef.get();
    }

    public void updateConfig(TDConfig newConfig) {
        mergeDefaults(newConfig);
        configRef.set(newConfig);
        saveUnlocked();
    }

    public void patchConfig(TDConfig patch) {
        configRef.set(merge(configRef.get(), patch));
        saveUnlocked();
    }

    /**
     * Apply server config on client for display (shop, etc.). Does NOT save to disk.
     */
    public void applyServerConfig(TDConfig serverConfig) {
        mergeDefaults(serverConfig);
        configRef.set(serverConfig);
    }

    public void save() {
        saveUnlocked();
    }

    private void saveUnlocked() {
        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, GSON.toJson(configRef.get()));
        } catch (IOException e) {
            TowerDefenseMod.LOGGER.error("Failed to save config: {}", e.getMessage());
        }
    }

    private static TDConfig createDefaults() {
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
        c.towers.put("BASIC", tower(1, 8, 40, 10));
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

    private static TDConfig.TowerSection tower(int power, double range, int rate, int price) {
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
        // BABY_ZOMBIE_SPAWNER promoted from tier 1 to tier 2
        if (c.spawners != null) {
            TDConfig.SpawnerSection bz = c.spawners.get("BABY_ZOMBIE_SPAWNER");
            if (bz != null && bz.tier == 1) bz.tier = 2;
        }
        // v1.5.0: basic tower damage reduced 2 -> 1
        if (c.towers != null) {
            TDConfig.TowerSection basic = c.towers.get("BASIC");
            if (basic != null && basic.power == 2) basic.power = 1;
        }
        // v1.6.0: starting money adjusted to 200
        if (c.game != null && (c.game.startingMoney == 500 || c.game.startingMoney == 100)) c.game.startingMoney = 200;
        // v1.6.0: baby zombie hp 8->5, nexus damage 3->1
        if (c.mobs != null) {
            TDConfig.MobSection bz = c.mobs.get("BABY_ZOMBIE");
            if (bz != null) {
                if (bz.baseHp == 8.0) bz.baseHp = 5.0;
                if (bz.nexusDamage == 3) bz.nexusDamage = 1;
            }
        }
        // v1.5.0: generator income halved
        if (c.generators != null) {
            migrateGeneratorIncome(c.generators, "BASIC",    2, 1);
            migrateGeneratorIncome(c.generators, "ADVANCED", 5, 2);
            migrateGeneratorIncome(c.generators, "ELITE",   15, 7);
        }
        // v1.5.0: mob money rewards halved; ravager speed/nexus damage reduced
        if (c.mobs != null) {
            migrateMobReward(c.mobs, "ZOMBIE",      5,  2);
            migrateMobReward(c.mobs, "SKELETON",    8,  4);
            migrateMobReward(c.mobs, "SPIDER",     10,  5);
            migrateMobReward(c.mobs, "RAVAGER",    50, 25);
            migrateMobReward(c.mobs, "BABY_ZOMBIE", 3,  1);
            migrateMobReward(c.mobs, "CREEPER",    15,  7);
            migrateMobReward(c.mobs, "ENDERMAN",   12,  6);
            migrateMobReward(c.mobs, "WITCH",      10,  5);
            migrateMobReward(c.mobs, "IRON_GOLEM", 60, 30);
            migrateMobReward(c.mobs, "BOSS",      100, 50);
            TDConfig.MobSection rav = c.mobs.get("RAVAGER");
            if (rav != null) {
                if (rav.speed == 0.15) rav.speed = 0.12;
                if (rav.nexusDamage == 20) rav.nexusDamage = 12;
            }
        }
    }

    private static void migrateGeneratorIncome(java.util.Map<String, TDConfig.GeneratorSection> map, String key, int oldVal, int newVal) {
        TDConfig.GeneratorSection s = map.get(key);
        if (s != null && s.incomeAmount == oldVal) s.incomeAmount = newVal;
    }

    private static void migrateMobReward(java.util.Map<String, TDConfig.MobSection> map, String key, int oldVal, int newVal) {
        TDConfig.MobSection s = map.get(key);
        if (s != null && s.moneyReward == oldVal) s.moneyReward = newVal;
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
    public int getStartingMoney() { return configRef.get().game.startingMoney; }
    public int getNexusMaxHp() { return configRef.get().game.nexusMaxHp; }
    public int getTier2Cost() { return configRef.get().game.tier2Cost; }
    public int getTier3Cost() { return configRef.get().game.tier3Cost; }
    public double getNexusExplosionRadius() { return configRef.get().game.nexusExplosionRadius; }
    public int getPrepPhaseTicks() { return configRef.get().game.prepPhaseTicks; }
    public int getBasePassiveIncome() { return configRef.get().game.basePassiveIncome; }
    public int getBasePassiveInterval() { return configRef.get().game.basePassiveInterval; }
    public int getDefeatDelayTicks() { return configRef.get().game.defeatDelayTicks; }
    public int getChainExplosionDelay() { return configRef.get().game.chainExplosionDelay; }

    public double getSoloModeStartingMultiplier() {
        double v = configRef.get().game.soloModeStartingMultiplier;
        return v > 0 ? v : 1.5;
    }
    public double getSoloModeIncomeMultiplier() {
        double v = configRef.get().game.soloModeIncomeMultiplier;
        return v > 0 ? v : 1.5;
    }
    public double getSoloModeGeneratorMultiplier() {
        double v = configRef.get().game.soloModeGeneratorMultiplier;
        return v > 0 ? v : 1.5;
    }

    // ─── Arena ───
    public int getArenaSize() { return configRef.get().arena.size; }
    public int getArenaWallHeight() { return configRef.get().arena.wallHeight; }
    public int getArenaY() { return configRef.get().arena.arenaY; }
    public int getStandDepth() { return configRef.get().arena.standDepth; }
    public int getStandHeight() { return configRef.get().arena.standHeight; }

    // ─── Mobs ───
    public double getMobBaseHp(MobType type) {
        TDConfig.MobSection s = configRef.get().mobs.get(type.name());
        return s != null ? s.baseHp : type.getDefaultBaseHp();
    }
    public double getMobSpeed(MobType type) {
        TDConfig.MobSection s = configRef.get().mobs.get(type.name());
        return s != null ? s.speed : type.getDefaultSpeed();
    }
    public int getMobNexusDamage(MobType type) {
        TDConfig.MobSection s = configRef.get().mobs.get(type.name());
        return s != null ? s.nexusDamage : type.getDefaultNexusDamage();
    }
    public int getMobMoneyReward(MobType type) {
        TDConfig.MobSection s = configRef.get().mobs.get(type.name());
        return s != null ? s.moneyReward : type.getDefaultMoneyReward();
    }

    // ─── Spawners ───
    public int getSpawnerPrice(SpawnerType type) {
        TDConfig.SpawnerSection s = configRef.get().spawners.get(type.name());
        return s != null ? s.price : type.getDefaultPrice();
    }
    public int getSpawnerInterval(SpawnerType type) {
        TDConfig.SpawnerSection s = configRef.get().spawners.get(type.name());
        return s != null ? s.spawnIntervalTicks : type.getDefaultSpawnIntervalTicks();
    }
    public int getSpawnerTier(SpawnerType type) {
        TDConfig.SpawnerSection s = configRef.get().spawners.get(type.name());
        return (s != null && s.tier > 0) ? s.tier : type.getDefaultTier();
    }

    // ─── Towers ───
    public int getTowerPower(TowerType type) {
        TDConfig.TowerSection s = configRef.get().towers.get(type.name());
        return s != null ? s.power : 0;
    }
    public double getTowerRange(TowerType type) {
        TDConfig.TowerSection s = configRef.get().towers.get(type.name());
        return s != null ? s.range : 10;
    }
    public int getTowerFireRate(TowerType type) {
        TDConfig.TowerSection s = configRef.get().towers.get(type.name());
        return s != null ? s.fireRateInTicks : 40;
    }
    public int getTowerPrice(TowerType type) {
        TDConfig.TowerSection s = configRef.get().towers.get(type.name());
        return s != null ? s.price : 10;
    }
    public int getTowerTier(TowerType type) {
        TDConfig.TowerSection s = configRef.get().towers.get(type.name());
        return (s != null && s.tier > 0) ? s.tier : type.getDefaultTier();
    }

    // ─── Generators ───
    public int getGeneratorPrice(IncomeGeneratorType type) {
        TDConfig.GeneratorSection s = configRef.get().generators.get(type.name());
        return s != null ? s.price : type.getDefaultPrice();
    }
    public int getGeneratorIncomeAmount(IncomeGeneratorType type) {
        TDConfig.GeneratorSection s = configRef.get().generators.get(type.name());
        return s != null ? s.incomeAmount : type.getDefaultIncomeAmount();
    }
    public int getGeneratorIncomeInterval(IncomeGeneratorType type) {
        TDConfig.GeneratorSection s = configRef.get().generators.get(type.name());
        return s != null ? s.incomeIntervalTicks : type.getDefaultIncomeIntervalTicks();
    }
    public int getGeneratorTier(IncomeGeneratorType type) {
        TDConfig.GeneratorSection s = configRef.get().generators.get(type.name());
        return (s != null && s.tier > 0) ? s.tier : type.getDefaultTier();
    }

    // ─── Spells ───
    public int getSpellPrice(SpellType type) {
        TDConfig.SpellSection s = configRef.get().spells.get(type.name());
        return s != null ? s.price : type.getDefaultPrice();
    }
    public int getSpellTier(SpellType type) {
        TDConfig.SpellSection s = configRef.get().spells.get(type.name());
        return (s != null && s.tier > 0) ? s.tier : type.getDefaultTier();
    }

    // ─── Weapons ───
    public int getWeaponPrice(String name) {
        Integer p = configRef.get().weapons.get(name);
        return p != null ? p : 15;
    }
    public int getWeaponTier(WeaponShopItem item) {
        Integer t = configRef.get().weaponTiers.get(item.name());
        return (t != null && t > 0) ? t : item.getDefaultTier();
    }

    // ─── Walls ───
    public int getWallPrice(String name) {
        Integer p = configRef.get().walls.get(name);
        return p != null ? p : 3;
    }
    public int getWallTier(WallShopItem item) {
        Integer t = configRef.get().wallTiers.get(item.name());
        return (t != null && t > 0) ? t : item.getDefaultTier();
    }

    // ─── Upgrades ───
    public int getUpgradeBaseCost(MobUpgradeManager.UpgradeType type) {
        TDConfig.UpgradesSection u = configRef.get().upgrades;
        return switch (type) {
            case HP -> u.hpBaseCost;
            case SPEED -> u.speedBaseCost;
            case DAMAGE -> u.damageBaseCost;
        };
    }
    public int getSpawnerExtraCostPerUpgrade() { return configRef.get().upgrades.spawnerExtraCostPerUpgrade; }
    public double getHpMultiplierPerLevel() { return configRef.get().upgrades.hpMultiplierPerLevel; }
    public double getSpeedMultiplierPerLevel() { return configRef.get().upgrades.speedMultiplierPerLevel; }
    public int getTowerUpgradeBaseCost() {
        int v = configRef.get().upgrades.towerUpgradeBaseCost;
        return v > 0 ? v : 25;
    }
    public double getTowerPowerMultiplierPerLevel() { return configRef.get().upgrades.towerPowerMultiplierPerLevel; }
    public double getTowerFireRateMultiplierPerLevel() { return configRef.get().upgrades.towerFireRateMultiplierPerLevel; }
    public double getTowerEffectDurationMultiplierPerLevel() { return configRef.get().upgrades.towerEffectDurationMultiplierPerLevel; }

    // ─── Spell effects ───
    public int getFireballLifetime() { return configRef.get().spellEffects.fireballLifetime; }
    public double getFireballExplosionRadius() { return configRef.get().spellEffects.fireballExplosionRadius; }
    public int getFreezeDurationTicks() { return configRef.get().spellEffects.freezeDurationTicks; }
    public int getHealNexusAmount() { return configRef.get().spellEffects.healNexusAmount; }
    public int getLightningBoxSize() { return configRef.get().spellEffects.lightningBoxSize; }
    public int getLightningDamage() { return configRef.get().spellEffects.lightningDamage; }
    public int getShieldDurationTicks() { return configRef.get().spellEffects.shieldDurationTicks; }

    // ─── Tower effects ───
    public int getFireTicks() { return configRef.get().towerEffects.fireTicks; }
    public int getSlowDurationTicks() { return configRef.get().towerEffects.slowDurationTicks; }
    public int getSlowAmplifier() { return configRef.get().towerEffects.slowAmplifier; }
    public int getPoisonDurationTicks() { return configRef.get().towerEffects.poisonDurationTicks; }
    public int getPoisonAmplifier() { return configRef.get().towerEffects.poisonAmplifier; }
    public int getChainLightningBoxSize() { return configRef.get().towerEffects.chainLightningBoxSize; }
    public int getChainLightningMaxTargets() { return configRef.get().towerEffects.chainLightningMaxTargets; }
    public double getAoeBoxSize() { return configRef.get().towerEffects.aoeBoxSize; }
    public double getAoeDamageRadius() { return configRef.get().towerEffects.aoeDamageRadius; }

    // ─── Spawner effects ───
    public double getSpawnSpread() { return configRef.get().spawnerEffects.spawnSpread; }
    public double getEndermanTeleportRange() { return configRef.get().spawnerEffects.endermanTeleportRange; }
    public int getWitchHealBoxSize() { return configRef.get().spawnerEffects.witchHealBoxSize; }
    public int getWitchHealIntervalTicks() {
        int v = configRef.get().spawnerEffects.witchHealIntervalTicks;
        return v > 0 ? v : 120;
    }
    public double getWitchHealPercent() {
        double v = configRef.get().spawnerEffects.witchHealPercent;
        return v > 0 ? v : 0.05;
    }
    public double getWitchHealMinPercent() {
        double v = configRef.get().spawnerEffects.witchHealMinPercent;
        return v > 0 ? v : 0.01;
    }
    public double getWitchHealDecayFactor() {
        double v = configRef.get().spawnerEffects.witchHealDecayFactor;
        return (v > 0 && v < 1.0) ? v : 0.8;
    }
    public double getFollowRange() { return configRef.get().spawnerEffects.followRange; }
    public int getSpecialMobTickInterval() { return configRef.get().spawnerEffects.specialMobTickInterval; }

    // ─── Game events ───
    public int getWaveEventIntervalTicks() { return configRef.get().gameEvents.waveEventIntervalTicks; }
    public int getBonusMoney() { return configRef.get().gameEvents.bonusMoney; }
    public int getDoubleIncomeMultiplier() { return configRef.get().gameEvents.doubleIncomeMultiplier; }
    public int getSpeedBoostDurationTicks() { return configRef.get().gameEvents.speedBoostDurationTicks; }
    public int getStructureDestroyedBounty() { return configRef.get().gameEvents.structureDestroyedBounty; }
    public int getOreSpawnIntervalTicks() { return configRef.get().gameEvents.oreSpawnIntervalTicks; }

    // ═══════════════════════════════════════════════════════════════════════
    //  Grouped accessor classes — new code can use these instead of the flat
    //  getters above.  Existing callers are unaffected.
    // ═══════════════════════════════════════════════════════════════════════

    /** Grouped accessors for game-level settings. */
    public final class GameSettings {
        private GameSettings() {}
        public int  startingMoney()              { return getStartingMoney(); }
        public int  nexusMaxHp()                 { return getNexusMaxHp(); }
        public int  tier2Cost()                  { return getTier2Cost(); }
        public int  tier3Cost()                  { return getTier3Cost(); }
        public double nexusExplosionRadius()     { return getNexusExplosionRadius(); }
        public int  prepPhaseTicks()             { return getPrepPhaseTicks(); }
        public int  basePassiveIncome()          { return getBasePassiveIncome(); }
        public int  basePassiveInterval()        { return getBasePassiveInterval(); }
        public int  defeatDelayTicks()           { return getDefeatDelayTicks(); }
        public int  chainExplosionDelay()        { return getChainExplosionDelay(); }
        public double soloModeStartingMultiplier()  { return getSoloModeStartingMultiplier(); }
        public double soloModeIncomeMultiplier()    { return getSoloModeIncomeMultiplier(); }
        public double soloModeGeneratorMultiplier() { return getSoloModeGeneratorMultiplier(); }
    }

    /** Grouped accessors for arena geometry settings. */
    public final class ArenaConfig {
        private ArenaConfig() {}
        public int arenaSize()    { return getArenaSize(); }
        public int wallHeight()   { return getArenaWallHeight(); }
        public int arenaY()       { return getArenaY(); }
        public int standDepth()   { return getStandDepth(); }
        public int standHeight()  { return getStandHeight(); }
    }

    /** Grouped accessors for mob stats. */
    public final class MobConfig {
        private MobConfig() {}
        public double baseHp(com.towerdefense.wave.MobType t)     { return getMobBaseHp(t); }
        public double speed(com.towerdefense.wave.MobType t)      { return getMobSpeed(t); }
        public int nexusDamage(com.towerdefense.wave.MobType t)   { return getMobNexusDamage(t); }
        public int moneyReward(com.towerdefense.wave.MobType t)   { return getMobMoneyReward(t); }
    }

    /** Grouped accessors for tower stats and effects. */
    public final class TowerConfig {
        private TowerConfig() {}
        public int    power(com.towerdefense.tower.TowerType t)     { return getTowerPower(t); }
        public double range(com.towerdefense.tower.TowerType t)     { return getTowerRange(t); }
        public int    fireRate(com.towerdefense.tower.TowerType t)  { return getTowerFireRate(t); }
        public int    price(com.towerdefense.tower.TowerType t)     { return getTowerPrice(t); }
        public int    tier(com.towerdefense.tower.TowerType t)      { return getTowerTier(t); }
        // effects
        public int    fireTicks()              { return getFireTicks(); }
        public int    slowDurationTicks()      { return getSlowDurationTicks(); }
        public int    slowAmplifier()          { return getSlowAmplifier(); }
        public int    poisonDurationTicks()    { return getPoisonDurationTicks(); }
        public int    poisonAmplifier()        { return getPoisonAmplifier(); }
        public int    chainLightningBoxSize()  { return getChainLightningBoxSize(); }
        public int    chainLightningMaxTargets() { return getChainLightningMaxTargets(); }
        public double aoeBoxSize()             { return getAoeBoxSize(); }
        public double aoeDamageRadius()        { return getAoeDamageRadius(); }
    }

    /** Grouped accessors for spawner stats and effects. */
    public final class SpawnerConfig {
        private SpawnerConfig() {}
        public int    price(com.towerdefense.wave.SpawnerType t)    { return getSpawnerPrice(t); }
        public int    interval(com.towerdefense.wave.SpawnerType t) { return getSpawnerInterval(t); }
        public int    tier(com.towerdefense.wave.SpawnerType t)     { return getSpawnerTier(t); }
        // effects
        public double spawnSpread()             { return getSpawnSpread(); }
        public double endermanTeleportRange()   { return getEndermanTeleportRange(); }
        public int    witchHealBoxSize()        { return getWitchHealBoxSize(); }
        public int    witchHealIntervalTicks()  { return getWitchHealIntervalTicks(); }
        public double witchHealPercent()        { return getWitchHealPercent(); }
        public double witchHealMinPercent()     { return getWitchHealMinPercent(); }
        public double witchHealDecayFactor()    { return getWitchHealDecayFactor(); }
        public double followRange()             { return getFollowRange(); }
        public int    specialMobTickInterval()  { return getSpecialMobTickInterval(); }
    }

    /** Grouped accessors for income generator stats. */
    public final class GeneratorConfig {
        private GeneratorConfig() {}
        public int price(com.towerdefense.game.IncomeGeneratorType t)          { return getGeneratorPrice(t); }
        public int incomeAmount(com.towerdefense.game.IncomeGeneratorType t)   { return getGeneratorIncomeAmount(t); }
        public int incomeInterval(com.towerdefense.game.IncomeGeneratorType t) { return getGeneratorIncomeInterval(t); }
        public int tier(com.towerdefense.game.IncomeGeneratorType t)           { return getGeneratorTier(t); }
    }

    /** Grouped accessors for spell stats and effects. */
    public final class SpellConfig {
        private SpellConfig() {}
        public int price(com.towerdefense.spell.SpellType t) { return getSpellPrice(t); }
        public int tier(com.towerdefense.spell.SpellType t)  { return getSpellTier(t); }
        // effects
        public int    fireballLifetime()         { return getFireballLifetime(); }
        public double fireballExplosionRadius()  { return getFireballExplosionRadius(); }
        public int    freezeDurationTicks()      { return getFreezeDurationTicks(); }
        public int    healNexusAmount()          { return getHealNexusAmount(); }
        public int    lightningBoxSize()         { return getLightningBoxSize(); }
        public int    lightningDamage()          { return getLightningDamage(); }
        public int    shieldDurationTicks()      { return getShieldDurationTicks(); }
    }

    /** Grouped accessors for upgrade costs and multipliers. */
    public final class UpgradeConfig {
        private UpgradeConfig() {}
        public int    baseCost(com.towerdefense.wave.MobUpgradeManager.UpgradeType t) { return getUpgradeBaseCost(t); }
        public int    spawnerExtraCostPerUpgrade()           { return getSpawnerExtraCostPerUpgrade(); }
        public double hpMultiplierPerLevel()                 { return getHpMultiplierPerLevel(); }
        public double speedMultiplierPerLevel()              { return getSpeedMultiplierPerLevel(); }
        public int    towerUpgradeBaseCost()                 { return getTowerUpgradeBaseCost(); }
        public double towerPowerMultiplierPerLevel()         { return getTowerPowerMultiplierPerLevel(); }
        public double towerFireRateMultiplierPerLevel()      { return getTowerFireRateMultiplierPerLevel(); }
        public double towerEffectDurationMultiplierPerLevel(){ return getTowerEffectDurationMultiplierPerLevel(); }
    }

    /** Grouped accessors for game-event settings. */
    public final class EventConfig {
        private EventConfig() {}
        public int waveEventIntervalTicks()   { return getWaveEventIntervalTicks(); }
        public int bonusMoney()               { return getBonusMoney(); }
        public int doubleIncomeMultiplier()   { return getDoubleIncomeMultiplier(); }
        public int speedBoostDurationTicks()  { return getSpeedBoostDurationTicks(); }
        public int structureDestroyedBounty() { return getStructureDestroyedBounty(); }
        public int oreSpawnIntervalTicks()    { return getOreSpawnIntervalTicks(); }
    }

    // ─── Typed group accessors ────────────────────────────────────────────────
    // Usage: ConfigManager.getInstance().game().startingMoney()

    public GameSettings   game()       { return new GameSettings(); }
    public ArenaConfig    arena()      { return new ArenaConfig(); }
    public MobConfig      mobs()       { return new MobConfig(); }
    public TowerConfig    towers()     { return new TowerConfig(); }
    public SpawnerConfig  spawners()   { return new SpawnerConfig(); }
    public GeneratorConfig generators(){ return new GeneratorConfig(); }
    public SpellConfig    spells()     { return new SpellConfig(); }
    public UpgradeConfig  upgrades()   { return new UpgradeConfig(); }
    public EventConfig    events()     { return new EventConfig(); }
}
