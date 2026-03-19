package com.towerdefense.test;

import com.towerdefense.arena.NexusManager;
import com.towerdefense.game.GameConfig;
import com.towerdefense.wave.MobTags;
import com.towerdefense.wave.MobUpgradeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * Tests for {@link NexusManager#checkAndApplyMobImpact}.
 */
public class NexusDamageTests {

    /** Helper: build a nexus near the test structure's origin. */
    private NexusManager buildNexus(GameTestHelper ctx, BlockPos localCenter) {
        ServerLevel level = ctx.getLevel();
        BlockPos worldCenter = ctx.absolutePos(localCenter);
        // Use a static arenaOrigin offset so GameConfig helpers resolve correctly
        GameConfig.arenaOrigin = worldCenter.offset(-GameConfig.ARENA_SIZE() / 2, -1, -2);
        NexusManager nexus = new NexusManager();
        nexus.build(level, worldCenter);
        return nexus;
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void testMobInRangeDamagesNexus(GameTestHelper ctx) {
        NexusManager nexus = buildNexus(ctx, new BlockPos(1, 2, 1));
        int initialHp = nexus.getHp();
        BlockPos center = nexus.getCenter();

        ServerLevel level = ctx.getLevel();
        Zombie zombie = new Zombie(EntityType.ZOMBIE, level);
        zombie.setPos(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
        zombie.addTag(MobTags.MOB);
        zombie.addTag(MobTags.typeTag("ZOMBIE"));
        zombie.addTag(MobTags.ownerTag(2)); // attacker team = 2
        level.addFreshEntity(zombie);

        MobUpgradeManager mobUpgrades = new MobUpgradeManager();
        boolean impacted = nexus.checkAndApplyMobImpact(level, zombie, mobUpgrades);

        ctx.assertTrue(impacted, "Mob in range should trigger impact");
        ctx.assertTrue(nexus.getHp() < initialHp, "Nexus HP should decrease after mob impact");
        ctx.assertFalse(zombie.isAlive(), "Mob should be discarded after impact");

        nexus.cleanup();
        ctx.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void testMobOutOfRangeDoesNotDamage(GameTestHelper ctx) {
        NexusManager nexus = buildNexus(ctx, new BlockPos(1, 2, 1));
        int initialHp = nexus.getHp();
        BlockPos center = nexus.getCenter();

        ServerLevel level = ctx.getLevel();
        Zombie zombie = new Zombie(EntityType.ZOMBIE, level);
        // Place far away (100 blocks out)
        zombie.setPos(center.getX() + 100, center.getY(), center.getZ() + 100);
        zombie.addTag(MobTags.MOB);
        zombie.addTag(MobTags.typeTag("ZOMBIE"));
        zombie.addTag(MobTags.ownerTag(2));
        level.addFreshEntity(zombie);

        MobUpgradeManager mobUpgrades = new MobUpgradeManager();
        boolean impacted = nexus.checkAndApplyMobImpact(level, zombie, mobUpgrades);

        ctx.assertFalse(impacted, "Mob out of range should not trigger impact");
        ctx.assertTrue(nexus.getHp() == initialHp, "Nexus HP should be unchanged");
        ctx.assertTrue(zombie.isAlive(), "Mob should still be alive");

        zombie.discard();
        nexus.cleanup();
        ctx.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void testShieldedNexusTakesNoDamage(GameTestHelper ctx) {
        NexusManager nexus = buildNexus(ctx, new BlockPos(1, 2, 1));
        nexus.activateShield(200);
        int initialHp = nexus.getHp();
        BlockPos center = nexus.getCenter();

        ServerLevel level = ctx.getLevel();
        Zombie zombie = new Zombie(EntityType.ZOMBIE, level);
        zombie.setPos(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
        zombie.addTag(MobTags.MOB);
        zombie.addTag(MobTags.typeTag("ZOMBIE"));
        zombie.addTag(MobTags.ownerTag(2));
        level.addFreshEntity(zombie);

        MobUpgradeManager mobUpgrades = new MobUpgradeManager();
        boolean impacted = nexus.checkAndApplyMobImpact(level, zombie, mobUpgrades);

        // The mob is in range so it IS discarded, but shield blocks HP loss
        ctx.assertTrue(impacted, "Impact should be triggered (mob in range)");
        ctx.assertTrue(nexus.getHp() == initialHp, "Shielded nexus should take no HP damage");
        ctx.assertFalse(zombie.isAlive(), "Mob is still discarded regardless of shield");

        nexus.cleanup();
        ctx.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void testNexusDeadAfterEnoughDamage(GameTestHelper ctx) {
        NexusManager nexus = buildNexus(ctx, new BlockPos(1, 2, 1));
        ServerLevel level = ctx.getLevel();

        nexus.damage(level, nexus.getMaxHp());

        ctx.assertTrue(nexus.isDead(), "Nexus should be dead after max-HP damage");

        nexus.cleanup();
        ctx.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void testHealRestoresHp(GameTestHelper ctx) {
        NexusManager nexus = buildNexus(ctx, new BlockPos(1, 2, 1));
        ServerLevel level = ctx.getLevel();

        nexus.damage(level, 20);
        int hpAfterDamage = nexus.getHp();
        nexus.heal(10);

        ctx.assertTrue(nexus.getHp() == hpAfterDamage + 10, "Heal should restore HP");

        nexus.cleanup();
        ctx.succeed();
    }
}
