package com.towerdefense.wave;

import com.towerdefense.config.ConfigManager;
import net.minecraft.world.entity.EntityType;

public enum MobType {

    ZOMBIE(EntityType.ZOMBIE, 20.0, 0.18, 5, 2),
    SKELETON(EntityType.SKELETON, 15.0, 0.23, 3, 4),
    SPIDER(EntityType.SPIDER, 10.0, 0.35, 2, 5),
    RAVAGER(EntityType.RAVAGER, 80.0, 0.12, 12, 25),
    BABY_ZOMBIE(EntityType.ZOMBIE, 8.0, 0.30, 3, 1),
    CREEPER(EntityType.CREEPER, 15.0, 0.15, 30, 7),
    ENDERMAN(EntityType.ENDERMAN, 25.0, 0.25, 5, 6),
    WITCH(EntityType.WITCH, 18.0, 0.18, 4, 5),
    IRON_GOLEM(EntityType.IRON_GOLEM, 150.0, 0.10, 25, 30),
    BOSS(EntityType.WITHER_SKELETON, 300.0, 0.08, 50, 50);

    private final EntityType<?> entityType;
    private final double defaultBaseHp;
    private final double defaultSpeed;
    private final int defaultNexusDamage;
    private final int defaultMoneyReward;

    MobType(EntityType<?> entityType, double baseHp, double speed, int nexusDamage, int moneyReward) {
        this.entityType = entityType;
        this.defaultBaseHp = baseHp;
        this.defaultSpeed = speed;
        this.defaultNexusDamage = nexusDamage;
        this.defaultMoneyReward = moneyReward;
    }

    public EntityType<?> getEntityType() { return entityType; }
    public double getDefaultBaseHp() { return defaultBaseHp; }
    public double getDefaultSpeed() { return defaultSpeed; }
    public int getDefaultNexusDamage() { return defaultNexusDamage; }
    public int getDefaultMoneyReward() { return defaultMoneyReward; }
    public double getBaseHp() { return ConfigManager.getInstance().getMobBaseHp(this); }
    public double getSpeed() { return ConfigManager.getInstance().getMobSpeed(this); }
    public int getNexusDamage() { return ConfigManager.getInstance().getMobNexusDamage(this); }
    public int getMoneyReward() { return ConfigManager.getInstance().getMobMoneyReward(this); }
}
