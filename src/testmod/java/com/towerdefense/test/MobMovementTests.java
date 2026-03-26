package com.towerdefense.test;

import com.towerdefense.mob.MoveToNexusGoal;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Integration tests for mob movement toward the nexus.
 */
public class MobMovementTests {

    /**
     * Verifies that a mob with {@link MoveToNexusGoal} pathfinds toward the nexus.
     *
     * <p>Setup:
     * <ul>
     *   <li>Nexus at relative (1, 1, 1)
     *   <li>Zombie spawned at relative (11, 1, 1) — 10 blocks away
     *   <li>Clear stone-floored corridor between them
     * </ul>
     *
     * <p>Pass condition: mob has moved at least 4 blocks closer after 80 ticks.
     * At speed 1.0 the mob should reach the 2-block stop radius well within that window.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 120)
    public void testMobMovesTowardNexus(GameTestHelper ctx) {
        ServerLevel level = ctx.getLevel();

        BlockPos nexusPos = ctx.absolutePos(new BlockPos(1, 1, 1));
        BlockPos spawnPos = ctx.absolutePos(new BlockPos(11, 1, 1));

        // Clear a 15×5×3 corridor (stone floor at Y=0, air above).
        // Necessary because tests run underground where world gen fills space with stone.
        for (int x = -1; x <= 14; x++) {
            for (int y = 0; y <= 4; y++) {
                for (int z = 0; z <= 2; z++) {
                    level.setBlock(ctx.absolutePos(new BlockPos(x, y, z)),
                            y == 0 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(),
                            Block.UPDATE_NONE);
                }
            }
        }

        // Spawn zombie with only the nexus goal — clear defaults so vanilla AI
        // (random walk, player targeting, etc.) can't interfere.
        Zombie zombie = new Zombie(EntityType.ZOMBIE, level);
        zombie.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
        zombie.goalSelector.removeAllGoals(g -> true);
        zombie.targetSelector.removeAllGoals(g -> true);
        zombie.goalSelector.addGoal(0, new MoveToNexusGoal(zombie, nexusPos, 1.0));
        level.addFreshEntity(zombie);

        double startDistSq = zombie.distanceToSqr(
                nexusPos.getX() + 0.5, nexusPos.getY(), nexusPos.getZ() + 0.5);

        // Entity AI doesn't auto-tick in the no-player GameTest server — drive it manually.
        // Same pattern as ArcherTowerTests.
        AtomicBoolean active = new AtomicBoolean(true);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (active.get() && zombie.isAlive()) {
                zombie.getNavigation().moveTo(
                        nexusPos.getX() + 0.5, nexusPos.getY(), nexusPos.getZ() + 0.5, 1.0);
                zombie.getNavigation().tick();
                zombie.getMoveControl().tick();
            }
        });

        ctx.runAfterDelay(80, () -> {
            active.set(false);
            double endDistSq = zombie.distanceToSqr(
                    nexusPos.getX() + 0.5, nexusPos.getY(), nexusPos.getZ() + 0.5);

            ctx.assertTrue(endDistSq < startDistSq - 16.0,
                    "Mob should have moved at least 4 blocks closer to nexus. "
                    + "startDist=" + String.format("%.1f", Math.sqrt(startDistSq))
                    + " endDist="  + String.format("%.1f", Math.sqrt(endDistSq)));

            zombie.discard();
            ctx.succeed();
        });
    }
}
