package com.towerdefense.test;

import com.towerdefense.config.ConfigManager;
import com.towerdefense.mob.TDWitch;
import com.towerdefense.wave.MobTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.Zombie;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * Tests for witch aura heal behavior via {@link TDWitch#performHealPass}.
 *
 * Each test calls {@link TDWitch#resetHealState()} first to ensure the shared
 * cross-witch dedup and consecutive-count maps are clean.
 */
public class WitchHealTests {

    // ─── helpers ───────────────────────────────────────────────────────────────

    private static Witch spawnWitch(ServerLevel level, BlockPos pos, int teamId) {
        Witch witch = new Witch(EntityType.WITCH, level);
        witch.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        witch.setNoAi(true);  // Prevent vanilla witch from self-healing via potions
        witch.addTag(MobTags.MOB);
        witch.addTag(MobTags.typeTag("WITCH"));
        witch.addTag(MobTags.ownerTag(teamId));
        level.addFreshEntity(witch);
        return witch;
    }

    private static Zombie spawnZombie(ServerLevel level, BlockPos pos, int teamId) {
        Zombie zombie = new Zombie(EntityType.ZOMBIE, level);
        zombie.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        zombie.setNoAi(true);
        zombie.addTag(MobTags.MOB);
        zombie.addTag(MobTags.typeTag("ZOMBIE"));
        zombie.addTag(MobTags.ownerTag(teamId));
        level.addFreshEntity(zombie);
        return zombie;
    }

    private static void damageToHp(LivingEntity entity, float remainingHp) {
        // Use setHealth() directly — witch.hurt() triggers vanilla potion self-heal,
        // corrupting the HP baseline we need for measuring heal deltas.
        entity.setHealth(Math.max(0.5f, remainingHp));
    }

    // ─── tests ─────────────────────────────────────────────────────────────────

    /** Ally zombie in range should gain at least witchHealPercent% of max HP. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void testWitchHealsNearbyAlly(GameTestHelper ctx) {
        TDWitch.resetHealState();
        ServerLevel level = ctx.getLevel();
        BlockPos origin = ctx.absolutePos(new BlockPos(2, 2, 2));

        Witch witch = spawnWitch(level, origin, 1);
        Zombie zombie = spawnZombie(level, origin.east(2), 1);
        TDWitch tdWitch = new TDWitch(witch, 1);

        damageToHp(zombie, 5f);
        float hpBefore = zombie.getHealth();

        tdWitch.performHealPass(level);

        float healed = zombie.getHealth() - hpBefore;
        float expectedMin = (float) (zombie.getMaxHealth()
                * ConfigManager.getInstance().getWitchHealPercent()) * 0.9f;
        ctx.assertTrue(healed >= expectedMin,
                "Zombie should be healed by ≥" + expectedMin + " HP, got: " + healed);

        witch.discard();
        zombie.discard();
        ctx.succeed();
    }

    /** Enemy mob in range must NOT be healed. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void testWitchDoesNotHealEnemy(GameTestHelper ctx) {
        TDWitch.resetHealState();
        ServerLevel level = ctx.getLevel();
        BlockPos origin = ctx.absolutePos(new BlockPos(2, 2, 2));

        Witch witch = spawnWitch(level, origin, 1);
        Zombie enemy = spawnZombie(level, origin.east(2), 2); // different team
        TDWitch tdWitch = new TDWitch(witch, 1);

        damageToHp(enemy, 5f);
        float hpBefore = enemy.getHealth();

        tdWitch.performHealPass(level);

        ctx.assertTrue(enemy.getHealth() == hpBefore, "Enemy mob should NOT be healed");

        witch.discard();
        enemy.discard();
        ctx.succeed();
    }

    /** Mob far outside the heal box should receive no HP. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void testWitchDoesNotHealOutOfRange(GameTestHelper ctx) {
        TDWitch.resetHealState();
        ServerLevel level = ctx.getLevel();
        BlockPos origin = ctx.absolutePos(new BlockPos(2, 2, 2));

        Witch witch = spawnWitch(level, origin, 1);
        Zombie farAlly = spawnZombie(level, origin.offset(200, 0, 200), 1);
        TDWitch tdWitch = new TDWitch(witch, 1);

        damageToHp(farAlly, 5f);
        float hpBefore = farAlly.getHealth();

        tdWitch.performHealPass(level);

        ctx.assertTrue(farAlly.getHealth() == hpBefore, "Out-of-range ally should NOT be healed");

        witch.discard();
        farAlly.discard();
        ctx.succeed();
    }

    /**
     * Two witches covering the same target → target healed exactly once per cycle.
     * Second witch's pass is a no-op for already-healed targets (shared dedup map).
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void testMultipleWitchesHealTargetOnce(GameTestHelper ctx) {
        TDWitch.resetHealState();
        ServerLevel level = ctx.getLevel();
        BlockPos origin = ctx.absolutePos(new BlockPos(2, 2, 2));

        Witch witch1 = spawnWitch(level, origin, 1);
        Witch witch2 = spawnWitch(level, origin.north(1), 1);
        Zombie zombie = spawnZombie(level, origin.east(2), 1);

        TDWitch tdWitch1 = new TDWitch(witch1, 1);
        TDWitch tdWitch2 = new TDWitch(witch2, 1);

        damageToHp(zombie, 5f);
        float hpBefore = zombie.getHealth();

        long t = level.getGameTime();
        tdWitch1.performHealPass(level, t);
        tdWitch2.performHealPass(level, t); // same game time → dedup skips

        float healedByTwo = zombie.getHealth() - hpBefore;

        // Measure single-witch heal for reference
        TDWitch.resetHealState();
        damageToHp(zombie, 5f);
        float hpBefore2 = zombie.getHealth();
        tdWitch1.performHealPass(level, t + 1000);
        float healedByOne = zombie.getHealth() - hpBefore2;

        ctx.assertTrue(Math.abs(healedByTwo - healedByOne) < 0.01f,
                "Two witches should heal the same as one per cycle. Two=" + healedByTwo
                        + " One=" + healedByOne);

        witch1.discard();
        witch2.discard();
        zombie.discard();
        ctx.succeed();
    }

    /**
     * Consecutive-cycle decay: each cycle heals less than the previous,
     * converging to the minimum percent floor. Always at least 1 HP.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void testConsecutiveHealDecays(GameTestHelper ctx) {
        TDWitch.resetHealState();
        ServerLevel level = ctx.getLevel();
        BlockPos origin = ctx.absolutePos(new BlockPos(2, 2, 2));

        Witch witch = spawnWitch(level, origin, 1);
        Zombie zombie = spawnZombie(level, origin.east(2), 1);
        TDWitch tdWitch = new TDWitch(witch, 1);

        float maxHp = zombie.getMaxHealth();
        long interval = ConfigManager.getInstance().getWitchHealIntervalTicks();
        float prevHeal = Float.MAX_VALUE;

        for (int cycle = 0; cycle < 6; cycle++) {
            zombie.setHealth(1f);  // Ensure room to heal; full-HP mobs absorb 0 from heal()
            float hpBefore = zombie.getHealth();

            // Simulate distinct cycle timestamps so dedup doesn't block
            tdWitch.performHealPass(level, cycle * interval);

            float healed = zombie.getHealth() - hpBefore;

            ctx.assertTrue(healed <= prevHeal + 0.01f,
                    "Cycle " + cycle + ": heal should not increase. prev=" + prevHeal + " curr=" + healed);
            ctx.assertTrue(healed >= 1f,
                    "Cycle " + cycle + ": heal must be ≥ 1 HP, got " + healed);

            prevHeal = healed;
        }

        double minPercent = ConfigManager.getInstance().getWitchHealMinPercent();
        float expectedFloor = Math.max(1f, (float) (maxHp * minPercent));
        ctx.assertTrue(prevHeal <= expectedFloor * 1.1f,
                "After 6 cycles heal should be near the floor (" + expectedFloor + "), got " + prevHeal);

        witch.discard();
        zombie.discard();
        ctx.succeed();
    }

    /** Witch-to-witch heals are debuffed to ~25% compared to zombie heals. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void testWitchToWitchHealIsDebuffed(GameTestHelper ctx) {
        TDWitch.resetHealState();
        ServerLevel level = ctx.getLevel();
        BlockPos origin = ctx.absolutePos(new BlockPos(2, 2, 2));

        Witch healer = spawnWitch(level, origin, 1);
        Witch targetWitch = spawnWitch(level, origin.east(2), 1);
        Zombie zombie = spawnZombie(level, origin.east(2), 1);
        TDWitch tdHealer = new TDWitch(healer, 1);

        float damage = 10f;
        damageToHp(targetWitch, targetWitch.getMaxHealth() - damage);
        damageToHp(zombie, zombie.getMaxHealth() - damage);

        tdHealer.performHealPass(level);

        float witchHealed  = targetWitch.getHealth() - (targetWitch.getMaxHealth() - damage);
        float zombieHealed = zombie.getHealth()      - (zombie.getMaxHealth()      - damage);

        // When both heals hit the 1-HP floor the debuff is still present but masked;
        // only enforce strict inequality and ratio when the zombie heal is above the floor.
        ctx.assertTrue(witchHealed <= zombieHealed,
                "Witch should not receive more healing than a zombie");
        if (zombieHealed > 1f) {
            ctx.assertTrue(witchHealed < zombieHealed,
                    "Witch should receive less healing than a zombie (debuff active above 1-HP floor)");
            float ratio = witchHealed / zombieHealed;
            ctx.assertTrue(ratio > 0.2f && ratio < 0.35f,
                    "Witch heal ratio should be ~0.25, got " + ratio);
        }

        healer.discard();
        targetWitch.discard();
        zombie.discard();
        ctx.succeed();
    }
}
