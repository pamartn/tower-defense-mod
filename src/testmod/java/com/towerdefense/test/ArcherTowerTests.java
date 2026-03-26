package com.towerdefense.test;

import com.towerdefense.tower.*;
import com.towerdefense.wave.MobTags;
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
 * Integration tests for tower attack behaviour.
 * Each test creates a standalone TowerManager (no game session) and drives it
 * via a per-test ServerTickEvents listener flagged with an AtomicBoolean so it
 * becomes a no-op once the test concludes.
 *
 * NOTE: ServerTickEvents listeners cannot be unregistered in Fabric — the
 * AtomicBoolean pattern is the standard workaround for test isolation.
 */
public class ArcherTowerTests {

    /**
     * Verifies that an Archer Tower detects a mob in range and fires arrows that
     * deal damage to it.
     *
     * Setup:
     *  - Archer tower placed at relative (2, 1, 1)  [4-block structure + armor stand]
     *  - Zombie placed at relative (2, 1, 8)         [7 blocks away, well within range=12]
     *
     * Timeline:
     *  - Archer fire rate = 35 ticks → first volley at tick 35
     *  - Arrow speed = 2.5 blocks/tick, distance ≈ 7 blocks → impact at tick ~38
     *  - Assertion fires at tick 60 with 40-tick safety margin before timeout
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void testArcherTowerDamagesMob(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();

        // --- Infrastructure ---
        TowerManager towerManager = new TowerManager();
        TowerRegistry registry = new TowerRegistry();
        registry.registerDefaults();
        TowerRecipe recipe = registry.findByType(TowerType.ARCHER)
                .orElseThrow(() -> new AssertionError("ARCHER recipe not registered"));

        // --- Clear the test volume ---
        // Tests run underground (Y≈-59) where world generation fills space with stone.
        // Clear a 5×10×12 corridor so terrain doesn't block the tower's line-of-sight.
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 10; y++) {
                for (int z = 0; z < 12; z++) {
                    world.setBlock(ctx.absolutePos(new BlockPos(x, y, z)),
                            Blocks.AIR.defaultBlockState(), Block.UPDATE_NONE);
                }
            }
        }
        // Solid floor
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 12; z++) {
                world.setBlock(ctx.absolutePos(new BlockPos(x, 0, z)),
                        Blocks.STONE.defaultBlockState(), Block.UPDATE_NONE);
            }
        }

        // --- Tower ---
        BlockPos towerBase = ctx.absolutePos(new BlockPos(2, 1, 1));
        towerManager.spawnTower(world, towerBase, recipe, 0);

        // --- Mob ---
        BlockPos mobBase = ctx.absolutePos(new BlockPos(2, 1, 8));
        // Ground already set in the clear pass above

        Zombie zombie = new Zombie(EntityType.ZOMBIE, world);
        zombie.setPos(mobBase.getX() + 0.5, mobBase.getY(), mobBase.getZ() + 0.5);
        zombie.addTag(MobTags.MOB);
        zombie.setNoAi(true);
        world.addFreshEntity(zombie);
        float initialHp = zombie.getMaxHealth();

        // --- Drive tower ticks from the server tick loop ---
        AtomicBoolean active = new AtomicBoolean(true);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (active.get()) towerManager.tick();
        });

        // --- Assert after fire-rate + arrow travel time ---
        ctx.runAfterDelay(60, () -> {
            active.set(false);
            ctx.assertTrue(zombie.isAlive(),
                    "Zombie should still be alive (not killed outright by a single volley)");
            ctx.assertTrue(zombie.getHealth() < initialHp,
                    "Archer tower should have reduced zombie HP (expected < " + initialHp
                    + ", got " + zombie.getHealth() + ")");
            ctx.succeed();
        });
    }
}
