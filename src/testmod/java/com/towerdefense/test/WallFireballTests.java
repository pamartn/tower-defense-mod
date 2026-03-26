package com.towerdefense.test;

import com.towerdefense.arena.WallBlockManager;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Tests for wall fireball-resistance mechanics via {@link WallBlockManager}.
 *
 * <p>Each tier adds one fireball hit of durability:
 * <ul>
 *   <li>Tier 1 (wool, 1 HP) — destroyed after 1 hit
 *   <li>Tier 2 (oak, 2 HP)  — survives 1 hit, destroyed after 2
 *   <li>Tier 3 (stone, 3 HP) — survives 2 hits, destroyed after 3
 * </ul>
 */
public class WallFireballTests {

    private static final int OWNER = 1;   // team that owns the walls
    private static final int ENEMY = 2;   // team casting fireballs

    /**
     * Verifies that walls of increasing tier require proportionally more fireball
     * hits to destroy, but every tier is eventually destroyable.
     *
     * <p>Uses radius=0 so each {@code onFireballImpact} call targets exactly one block.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void testWallResistanceIncreasesByTier(GameTestHelper ctx) {
        ServerLevel level = ctx.getLevel();
        WallBlockManager wbm = new WallBlockManager();

        BlockPos tier1 = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos tier2 = ctx.absolutePos(new BlockPos(4, 2, 2));
        BlockPos tier3 = ctx.absolutePos(new BlockPos(6, 2, 2));

        // Place real blocks so world.setBlock(pos, AIR) has something to remove
        level.setBlock(tier1, Blocks.WHITE_WOOL.defaultBlockState(),    Block.UPDATE_NONE);
        level.setBlock(tier2, Blocks.OAK_PLANKS.defaultBlockState(),    Block.UPDATE_NONE);
        level.setBlock(tier3, Blocks.COBBLESTONE.defaultBlockState(),   Block.UPDATE_NONE);

        wbm.registerBlock(tier1, OWNER, 1);
        wbm.registerBlock(tier2, OWNER, 2);
        wbm.registerBlock(tier3, OWNER, 3);

        // ── Tier 1: exactly 1 hit to destroy ──────────────────────────────────
        wbm.onFireballImpact(level, tier1, ENEMY, 0);
        ctx.assertTrue(level.getBlockState(tier1).isAir(),
                "Tier 1 wall must be destroyed after 1 fireball hit");

        // ── Tier 2: survives hit 1, destroyed on hit 2 ────────────────────────
        wbm.onFireballImpact(level, tier2, ENEMY, 0);
        ctx.assertFalse(level.getBlockState(tier2).isAir(),
                "Tier 2 wall must survive the 1st fireball hit");

        wbm.onFireballImpact(level, tier2, ENEMY, 0);
        ctx.assertTrue(level.getBlockState(tier2).isAir(),
                "Tier 2 wall must be destroyed after 2 fireball hits");

        // ── Tier 3: survives hits 1-2, destroyed on hit 3 ─────────────────────
        wbm.onFireballImpact(level, tier3, ENEMY, 0);
        ctx.assertFalse(level.getBlockState(tier3).isAir(),
                "Tier 3 wall must survive the 1st fireball hit");

        wbm.onFireballImpact(level, tier3, ENEMY, 0);
        ctx.assertFalse(level.getBlockState(tier3).isAir(),
                "Tier 3 wall must survive the 2nd fireball hit");

        wbm.onFireballImpact(level, tier3, ENEMY, 0);
        ctx.assertTrue(level.getBlockState(tier3).isAir(),
                "Tier 3 wall must be destroyed after 3 fireball hits");

        ctx.succeed();
    }

    /**
     * Verifies that a fireball from the wall's own team deals no damage
     * (friendly fire is disabled).
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void testFriendlyFireballDoesNotDamageWall(GameTestHelper ctx) {
        ServerLevel level = ctx.getLevel();
        WallBlockManager wbm = new WallBlockManager();

        BlockPos pos = ctx.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(pos, Blocks.WHITE_WOOL.defaultBlockState(), Block.UPDATE_NONE);
        wbm.registerBlock(pos, OWNER, 1);

        // Fireball from same team — must not remove the block
        wbm.onFireballImpact(level, pos, OWNER, 0);
        ctx.assertFalse(level.getBlockState(pos).isAir(),
                "Friendly fireball must not damage own walls");

        ctx.succeed();
    }
}
