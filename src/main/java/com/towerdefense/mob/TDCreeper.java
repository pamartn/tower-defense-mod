package com.towerdefense.mob;

import com.towerdefense.game.GameConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * TD behavior for a creeper mob: randomly explodes, destroying nearby enemy buildings only.
 * The creeper dies on explosion. Average time before explosion: ~20 seconds (1/400 per tick).
 */
public class TDCreeper extends TDMob {

    /** 1-in-N chance to explode each tick. */
    private static final int EXPLODE_CHANCE = 400;
    /** Block radius to destroy on explosion. */
    private static final int EXPLODE_RADIUS = 3;

    public TDCreeper(Mob entity, int teamId) {
        super(entity, teamId);
    }

    @Override
    protected void tick(ServerLevel level) {
        if (!entity.isAlive()) return;

        if (level.random.nextInt(EXPLODE_CHANCE) == 0) {
            explode(level);
        }
    }

    private void explode(ServerLevel level) {
        BlockPos center = entity.blockPosition();
        int enemySide = 3 - teamId; // teams are 1 and 2

        int destroyed = 0;
        for (int dx = -EXPLODE_RADIUS; dx <= EXPLODE_RADIUS; dx++) {
            for (int dy = -1; dy <= EXPLODE_RADIUS; dy++) {
                for (int dz = -EXPLODE_RADIUS; dz <= EXPLODE_RADIUS; dz++) {
                    if (dx * dx + dy * dy + dz * dz > EXPLODE_RADIUS * EXPLODE_RADIUS) continue;
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (!GameConfig.isInsidePlayerHalf(pos, enemySide)) continue;
                    if (canDestroy(level.getBlockState(pos), pos)) {
                        level.destroyBlock(pos, false);
                        destroyed++;
                    }
                }
            }
        }

        // Sound + particles
        level.playSound(null, center, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0f, 1.0f);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                entity.getX(), entity.getY() + 1, entity.getZ(),
                1, 0, 0, 0, 0);

        entity.discard();
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
