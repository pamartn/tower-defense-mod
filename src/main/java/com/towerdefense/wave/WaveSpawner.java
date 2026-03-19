package com.towerdefense.wave;

import com.towerdefense.TowerDefenseMod;
import com.towerdefense.game.GameConfig;
import com.towerdefense.mob.MoveToNexusGoal;
import com.towerdefense.mob.TDMob;
import com.towerdefense.mob.TDRavager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class WaveSpawner {

    private WaveDefinition currentWave;
    private final Queue<MobType> spawnQueue = new LinkedList<>();
    private final List<Mob> aliveMobs = new ArrayList<>();
    private int spawnCooldown;
    private boolean waveActive;

    public void startWave(int waveNumber) {
        currentWave = WaveDefinition.forWave(waveNumber);
        spawnQueue.clear();

        for (WaveDefinition.MobEntry entry : currentWave.mobs()) {
            for (int i = 0; i < entry.count(); i++) {
                spawnQueue.add(entry.type());
            }
        }

        waveActive = true;
        spawnCooldown = 0;

        TowerDefenseMod.LOGGER.info("Wave {} started: {} mobs, HP multiplier x{}",
                waveNumber, currentWave.totalMobCount(), String.format("%.2f", currentWave.hpMultiplier()));
    }

    public void tick(ServerLevel world) {
        if (!waveActive) return;

        aliveMobs.removeIf(mob -> !mob.isAlive());

        if (!spawnQueue.isEmpty()) {
            spawnCooldown--;
            if (spawnCooldown <= 0) {
                spawnCooldown = GameConfig.SPAWN_INTERVAL_TICKS;
                spawnMob(world, spawnQueue.poll());
            }
        }
    }

    private void spawnMob(ServerLevel world, MobType type) {
        BlockPos spawnPos = GameConfig.getSpawnCorner();
        double spread = 3.0;
        double x = spawnPos.getX() + 0.5 + (world.random.nextDouble() - 0.5) * spread;
        double z = spawnPos.getZ() + 0.5 + (world.random.nextDouble() - 0.5) * spread;

        Entity entity = type.getEntityType().create(world, EntitySpawnReason.MOB_SUMMONED);
        if (!(entity instanceof PathfinderMob mob)) {
            if (entity != null) entity.discard();
            return;
        }

        boolean isFlying = entity instanceof net.minecraft.world.entity.FlyingMob;
        double spawnY = isFlying ? spawnPos.getY() + 6.0 : spawnPos.getY();
        mob.moveTo(x, spawnY, z, 0, 0);
        mob.setNoAi(false);
        if (!isFlying) {
            mob.setOnGround(true);
        }

        double scaledHp = type.getBaseHp() * currentWave.hpMultiplier();
        var maxHpAttr = mob.getAttribute(Attributes.MAX_HEALTH);
        if (maxHpAttr != null) {
            maxHpAttr.setBaseValue(scaledHp);
        }
        mob.setHealth((float) scaledHp);

        var speedAttr = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.setBaseValue(type.getSpeed());
        }

        var followRange = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange != null) {
            followRange.setBaseValue(128.0);
        }

        mob.setPersistenceRequired();

        mob.goalSelector.removeAllGoals(g -> true);
        mob.targetSelector.removeAllGoals(g -> true);

        mob.goalSelector.addGoal(0, new MoveToNexusGoal(mob, GameConfig.getNexusCenter(), type.getSpeed()));

        mob.addTag("td_mob");
        mob.addTag("td_type_" + type.name());

        world.addFreshEntity(mob);
        aliveMobs.add(mob);

        if (type == MobType.RAVAGER) {
            new TDRavager(mob, 0); // teamId 0 — WaveSpawner mobs are team-neutral
            TowerDefenseMod.LOGGER.info("[RAVAGER] Registered breach behavior for mob {}", mob.getId());
        }

        TowerDefenseMod.LOGGER.info("[DEBUG] Spawned {} at ({}, {}, {}) -> target nexus ({}, {}, {})",
            type.name(), mob.getX(), mob.getY(), mob.getZ(),
            GameConfig.getNexusCenter().getX(), GameConfig.getNexusCenter().getY(), GameConfig.getNexusCenter().getZ());
        TowerDefenseMod.LOGGER.info("[DEBUG] Mob AI: noAi={}, onGround={}, followRange={}",
            mob.isNoAi(), mob.onGround(), followRange != null ? followRange.getValue() : "null");
    }

    public boolean isWaveComplete() {
        return waveActive && spawnQueue.isEmpty() && aliveMobs.stream().noneMatch(Entity::isAlive);
    }

    public boolean isWaveActive() {
        return waveActive;
    }

    public void stopWave() {
        waveActive = false;
        spawnQueue.clear();
    }

    public int getMobsRemaining() {
        int inQueue = spawnQueue.size();
        long alive = aliveMobs.stream().filter(Entity::isAlive).count();
        return inQueue + (int) alive;
    }

    public List<Mob> getAliveMobs() {
        aliveMobs.removeIf(mob -> !mob.isAlive());
        return aliveMobs;
    }

    public void killAllMobs() {
        for (Mob mob : aliveMobs) {
            if (mob.isAlive()) {
                mob.discard();
            }
        }
        aliveMobs.clear();
        TDMob.clearAll();
        spawnQueue.clear();
        waveActive = false;
    }

    public int getMoneyRewardForMob(Mob mob) {
        String typeTag = mob.getTags().stream()
                .filter(t -> t.startsWith("td_type_"))
                .findFirst()
                .orElse(null);
        if (typeTag == null) return 0;

        String typeName = typeTag.substring("td_type_".length());
        try {
            MobType type = MobType.valueOf(typeName);
            return type.getMoneyReward();
        } catch (IllegalArgumentException ignored) {
            return 0;
        }
    }
}
