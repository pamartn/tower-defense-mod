package com.towerdefense.wave;

import com.towerdefense.config.ConfigManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import com.towerdefense.shop.Purchasable;
import java.util.List;

public enum SpawnerType implements Purchasable {

    ZOMBIE_SPAWNER("Zombie Spawner", MobType.ZOMBIE, 15, 10 * 20, Blocks.MOSSY_COBBLESTONE),
    SKELETON_SPAWNER("Skeleton Spawner", MobType.SKELETON, 25, 12 * 20, Blocks.BONE_BLOCK),
    SPIDER_SPAWNER("Spider Spawner", MobType.SPIDER, 35, 8 * 20, Blocks.COBWEB),
    RAVAGER_SPAWNER("Ravager Spawner", MobType.RAVAGER, 100, 30 * 20, Blocks.CRYING_OBSIDIAN),
    BABY_ZOMBIE_SPAWNER("Baby Zombie Spawner", MobType.BABY_ZOMBIE, 75, 6 * 20, Blocks.MOSS_BLOCK),
    CREEPER_SPAWNER("Creeper Spawner", MobType.CREEPER, 40, 15 * 20, Blocks.GREEN_WOOL),
    ENDERMAN_SPAWNER("Enderman Spawner", MobType.ENDERMAN, 45, 12 * 20, Blocks.END_STONE),
    WITCH_SPAWNER("Witch Spawner", MobType.WITCH, 50, 15 * 20, Blocks.CAULDRON),
    IRON_GOLEM_SPAWNER("Iron Golem Spawner", MobType.IRON_GOLEM, 120, 35 * 20, Blocks.ANVIL),
    BOSS_SPAWNER("Boss Spawner", MobType.BOSS, 500, 60 * 20, Blocks.SOUL_SAND);

    private final String name;
    private final MobType mobType;
    private final int defaultPrice;
    private final int defaultSpawnIntervalTicks;
    private final Block triggerBlock;

    SpawnerType(String name, MobType mobType, int price, int spawnIntervalTicks, Block triggerBlock) {
        this.name = name;
        this.mobType = mobType;
        this.defaultPrice = price;
        this.defaultSpawnIntervalTicks = spawnIntervalTicks;
        this.triggerBlock = triggerBlock;
    }

    public String getName() { return name; }
    public MobType getMobType() { return mobType; }
    public int getDefaultPrice() { return defaultPrice; }
    public int getDefaultSpawnIntervalTicks() { return defaultSpawnIntervalTicks; }
    public int getPrice() { return ConfigManager.getInstance().getSpawnerPrice(this); }
    public int getSpawnIntervalTicks() { return ConfigManager.getInstance().getSpawnerInterval(this); }
    public Block getTriggerBlock() { return triggerBlock; }

    public static List<SpawnerType> getAll() {
        return List.of(values());
    }

    public static List<SpawnerType> getAllSortedByPrice() {
        return java.util.Arrays.stream(values()).sorted((a, b) -> Integer.compare(a.getPrice(), b.getPrice())).toList();
    }

    public int getDefaultTier() {
        return switch (this) {
            case ZOMBIE_SPAWNER, SKELETON_SPAWNER, SPIDER_SPAWNER -> 1;
            case BABY_ZOMBIE_SPAWNER, CREEPER_SPAWNER, ENDERMAN_SPAWNER, WITCH_SPAWNER, RAVAGER_SPAWNER -> 2;
            case IRON_GOLEM_SPAWNER, BOSS_SPAWNER -> 3;
        };
    }

    public int getTier() {
        return ConfigManager.getInstance().getSpawnerTier(this);
    }

    public static SpawnerType findByBlock(Block block) {
        for (SpawnerType type : values()) {
            if (type.triggerBlock == block) return type;
        }
        return null;
    }
}
