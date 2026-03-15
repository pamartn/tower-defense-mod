package com.towerdefense.mob;

import com.towerdefense.TowerDefenseMod;
import com.towerdefense.game.GameConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class RavagerBreachBehavior {

    private double lastY;
    private int cooldown = 0;
    private boolean initialized = false;

    public void tick(Mob mob, ServerLevel world) {
        if (!initialized) {
            lastY = mob.getY();
            initialized = true;
            return;
        }

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        double currentY = mob.getY();
        boolean jumped = currentY - lastY > 0.4;
        lastY = currentY;

        if (jumped) {
            TowerDefenseMod.LOGGER.info("[RAVAGER] Mob {} jumped! Breaching blocks...", mob.getId());
            breachBlocks(mob, world);
            cooldown = 20;
        }
    }

    private void breachBlocks(Mob mob, ServerLevel world) {
        Direction facing = Direction.fromYRot(mob.getYRot());
        BlockPos mobPos = mob.blockPosition();
        
        int blocksDestroyed = 0;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                for (int dz = 1; dz <= 2; dz++) {
                    BlockPos targetPos = getOffsetPos(mobPos, facing, dx, dy, dz);
                    BlockState state = world.getBlockState(targetPos);

                    if (canDestroy(state, targetPos)) {
                        world.destroyBlock(targetPos, false);
                        blocksDestroyed++;

                        world.sendParticles(
                            ParticleTypes.EXPLOSION,
                            targetPos.getX() + 0.5,
                            targetPos.getY() + 0.5,
                            targetPos.getZ() + 0.5,
                            1, 0, 0, 0, 0
                        );
                    }
                }
            }
        }

        if (blocksDestroyed > 0) {
            world.playSound(
                null,
                mob.getX(), mob.getY(), mob.getZ(),
                SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.HOSTILE,
                1.0f, 1.0f
            );

            TowerDefenseMod.LOGGER.info("[RAVAGER] Destroyed {} blocks", blocksDestroyed);
        }
    }

    private BlockPos getOffsetPos(BlockPos origin, Direction facing, int sideways, int up, int forward) {
        Direction left = facing.getCounterClockWise();
        
        int x = origin.getX() + facing.getStepX() * forward + left.getStepX() * sideways;
        int y = origin.getY() + up;
        int z = origin.getZ() + facing.getStepZ() * forward + left.getStepZ() * sideways;
        
        return new BlockPos(x, y, z);
    }

    private boolean canDestroy(BlockState state, BlockPos pos) {
        if (state.isAir()) return false;
        if (state.is(Blocks.BEDROCK)) return false;
        if (pos.getY() <= GameConfig.ARENA_Y()) return false;
        if (!GameConfig.isInsideArena(pos)) return false;
        if (state.is(Blocks.SMOOTH_STONE)) return false;
        if (state.is(Blocks.OBSIDIAN)) return false;
        if (state.is(Blocks.BEACON)) return false;
        if (state.is(Blocks.RED_STAINED_GLASS)) return false;
        if (state.is(Blocks.GOLD_BLOCK)) return false;
        if (state.is(Blocks.EMERALD_BLOCK)) return false;
        if (state.is(Blocks.NETHERITE_BLOCK)) return false;
        if (state.is(Blocks.HOPPER)) return false;
        if (state.is(Blocks.GLOWSTONE)) return false;
        if (state.is(Blocks.END_PORTAL_FRAME)) return false;
        if (state.is(Blocks.END_PORTAL)) return false;
        if (state.is(Blocks.BARRIER)) return false;
        
        float hardness = state.getDestroySpeed(null, BlockPos.ZERO);
        return hardness >= 0 && hardness < 50;
    }
}
