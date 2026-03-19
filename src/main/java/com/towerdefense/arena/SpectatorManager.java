package com.towerdefense.arena;

import com.towerdefense.game.GameConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SpectatorManager {

    private static final EntityType<?>[] SPECTATOR_TYPES = {
            EntityType.ZOMBIE, EntityType.VILLAGER, EntityType.CHICKEN,
            EntityType.SKELETON, EntityType.PIG, EntityType.COW,
            EntityType.SHEEP, EntityType.IRON_GOLEM, EntityType.WOLF,
            EntityType.CAT, EntityType.PARROT, EntityType.ALLAY
    };

    private final List<Mob> spectators = new ArrayList<>();
    private final Random random = new Random();
    private int tickCounter = 0;

    public void spawnSpectators(ServerLevel world, BlockPos origin) {
        removeAll();

        int size = GameConfig.ARENA_SIZE();
        int rows = 10;
        int innerWallH = 5;

        for (int row = 0; row < rows; row++) {
            int seatY = innerWallH + 1 + row;
            int outward = 2 + row;

            for (int side = 0; side < 4; side++) {
                int len = size + 2 * outward;
                for (int t = 1; t < len - 1; t += 1 + random.nextInt(2)) {
                    int wx, wz;
                    switch (side) {
                        case 0 -> { wx = t - outward; wz = -1 - outward; }
                        case 1 -> { wx = t - outward; wz = size + outward; }
                        case 2 -> { wx = -1 - outward; wz = t - outward; }
                        default -> { wx = size + outward; wz = t - outward; }
                    }

                    BlockPos seatPos = origin.offset(wx, seatY + 1, wz);
                    spawnOneSpectator(world, seatPos, side);
                }
            }
        }
    }

    private void spawnOneSpectator(ServerLevel world, BlockPos pos, int side) {
        EntityType<?> type = SPECTATOR_TYPES[random.nextInt(SPECTATOR_TYPES.length)];
        Entity entity = type.create(world, EntitySpawnReason.MOB_SUMMONED);
        if (!(entity instanceof Mob mob)) {
            if (entity != null) entity.discard();
            return;
        }

        int size = GameConfig.ARENA_SIZE();
        double centerX = GameConfig.arenaOrigin.getX() + size / 2.0;
        double centerZ = GameConfig.arenaOrigin.getZ() + size / 2.0;
        double dx = centerX - (pos.getX() + 0.5);
        double dz = centerZ - (pos.getZ() + 0.5);
        float yaw = (float) (Math.atan2(-dx, dz) * 180.0 / Math.PI);

        mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0);
        mob.yHeadRot = yaw;
        mob.yBodyRot = yaw;
        mob.setNoAi(true);
        mob.setInvulnerable(true);
        mob.setSilent(true);
        mob.setPersistenceRequired();
        mob.addTag("td_spectator");

        var knockback = mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (knockback != null) knockback.setBaseValue(1.0);

        world.addFreshEntity(mob);
        spectators.add(mob);
    }

    public void tick() {
        tickCounter++;
        if (tickCounter % 20 != 0) return;

        for (Mob mob : spectators) {
            if (!mob.isAlive()) continue;
            if (random.nextInt(4) == 0) {
                mob.setDeltaMovement(mob.getDeltaMovement().add(0, 0.3 + random.nextDouble() * 0.15, 0));
                mob.hasImpulse = true;
            }
        }
    }

    public void removeAll() {
        for (Mob mob : spectators) {
            if (mob.isAlive()) mob.discard();
        }
        spectators.clear();
        tickCounter = 0;
    }
}
