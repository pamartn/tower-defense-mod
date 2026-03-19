package com.towerdefense.arena;

import com.towerdefense.TowerDefenseMod;
import com.towerdefense.game.GameConfig;
import com.towerdefense.wave.MobTags;
import com.towerdefense.wave.MobType;
import com.towerdefense.wave.MobUpgradeManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

public class NexusManager {

    private int hp;
    private int maxHp;
    private ArmorStand hpDisplay;
    private BlockPos center;
    private final List<BlockPos> structureBlocks = new ArrayList<>();

    public void build(ServerLevel world, BlockPos nexusCenter) {
        this.maxHp = GameConfig.NEXUS_MAX_HP();
        this.hp = maxHp;
        this.center = nexusCenter;

        structureBlocks.clear();

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos p = center.offset(x, 0, z);
                world.setBlock(p, Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
                structureBlocks.add(p);
            }
        }

        world.setBlock(center.above(1), Blocks.BEACON.defaultBlockState(), Block.UPDATE_ALL);
        structureBlocks.add(center.above(1));

        world.setBlock(center.offset(0, 2, 0), Blocks.RED_STAINED_GLASS.defaultBlockState(), Block.UPDATE_ALL);
        structureBlocks.add(center.offset(0, 2, 0));

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue;
                if (Math.abs(x) + Math.abs(z) == 1) {
                    BlockPos p = center.offset(x, 1, z);
                    world.setBlock(p, Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
                    structureBlocks.add(p);
                }
            }
        }

        spawnHpDisplay(world);
    }

    @Deprecated
    public void build(ServerLevel world) {
        build(world, GameConfig.getNexusCenter());
    }

    private void spawnHpDisplay(ServerLevel world) {
        if (hpDisplay != null && hpDisplay.isAlive()) {
            hpDisplay.discard();
        }

        hpDisplay = new ArmorStand(EntityType.ARMOR_STAND, world);
        hpDisplay.setPos(center.getX() + 0.5, center.getY() + 3.0, center.getZ() + 0.5);
        hpDisplay.setCustomName(buildHpText());
        hpDisplay.setCustomNameVisible(true);
        hpDisplay.setNoGravity(true);
        hpDisplay.setInvulnerable(true);
        hpDisplay.setInvisible(true);
        hpDisplay.setSilent(true);
        world.addFreshEntity(hpDisplay);
    }

    private Component buildHpText() {
        float ratio = (float) hp / maxHp;
        ChatFormatting color;
        if (ratio > 0.6f) color = ChatFormatting.GREEN;
        else if (ratio > 0.3f) color = ChatFormatting.YELLOW;
        else color = ChatFormatting.RED;

        int bars = 20;
        int filled = (int) (ratio * bars);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bars; i++) {
            sb.append(i < filled ? "\u2588" : "\u2591");
        }

        return Component.literal("\u2764 Nexus ")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                .append(Component.literal(sb.toString()).withStyle(color))
                .append(Component.literal(" " + hp + "/" + maxHp).withStyle(ChatFormatting.WHITE));
    }

    private int shieldTicks = 0;

    public void damage(ServerLevel world, int amount) {
        if (shieldTicks > 0) return;
        hp = Math.max(0, hp - amount);

        if (hpDisplay != null && hpDisplay.isAlive()) {
            hpDisplay.setCustomName(buildHpText());
        }

        world.sendParticles(ParticleTypes.EXPLOSION,
                center.getX() + 0.5, center.getY() + 1.5, center.getZ() + 0.5,
                5, 0.5, 0.5, 0.5, 0.01);
        world.playSound(null, center, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.6f, 1.2f);
    }

    public void heal(int amount) {
        hp = Math.min(maxHp, hp + amount);
        if (hpDisplay != null && hpDisplay.isAlive()) {
            hpDisplay.setCustomName(buildHpText());
        }
    }

    /** Adds bonus HP to max and current (e.g. on tier unlock). */
    public void addMaxHpBonus(int amount) {
        maxHp += amount;
        if (hpDisplay != null && hpDisplay.isAlive()) {
            hpDisplay.setCustomName(buildHpText());
        }
    }

    public void activateShield(int ticks) {
        shieldTicks = ticks;
    }

    public void tickShield() {
        if (shieldTicks > 0) shieldTicks--;
    }

    public boolean isShielded() {
        return shieldTicks > 0;
    }

    /**
     * Checks if the given mob is within nexus explosion radius. If so, applies mob damage
     * to the nexus, spawns particles, and discards the mob.
     *
     * @return true if the mob impacted (and was discarded), false if it was out of range.
     */
    public boolean checkAndApplyMobImpact(ServerLevel world, Mob mob, MobUpgradeManager mobUpgradeManager) {
        if (!mob.isAlive() || center == null) return false;
        double dist = mob.distanceToSqr(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
        double radius = GameConfig.NEXUS_EXPLOSION_RADIUS();
        if (dist >= radius * radius) return false;

        String typeName = MobTags.getType(mob);
        if (typeName == null) typeName = "ZOMBIE";

        int damage = 5;
        try {
            MobType mt = MobType.valueOf(typeName);
            int mobTeam = MobTags.getTeamId(mob);
            damage = mt.getNexusDamage() + mobUpgradeManager.getDamageBonus(mobTeam, mt);
        } catch (IllegalArgumentException ignored) {}

        damage(world, damage);
        world.sendParticles(ParticleTypes.EXPLOSION,
                mob.getX(), mob.getY() + 0.5, mob.getZ(), 3, 0.3, 0.3, 0.3, 0.01);
        mob.discard();
        return true;
    }

    public boolean isNexusBlock(BlockPos pos) {
        if (center == null) return false;
        int dx = Math.abs(pos.getX() - center.getX());
        int dz = Math.abs(pos.getZ() - center.getZ());
        int dy = pos.getY() - center.getY();
        return dx <= 1 && dz <= 1 && dy >= 0 && dy <= 2;
    }

    public boolean isDead() { return hp <= 0; }
    public int getHp() { return hp; }
    public int getMaxHp() { return maxHp; }
    public BlockPos getCenter() { return center; }

    public void destroy(ServerLevel world) {
        if (center == null) return;
        world.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                center.getX() + 0.5, center.getY() + 1, center.getZ() + 0.5,
                3, 1, 1, 1, 0.1);
        world.playSound(null, center, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 2.0f, 0.5f);

        for (BlockPos pos : structureBlocks) {
            world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        structureBlocks.clear();

        if (hpDisplay != null && hpDisplay.isAlive()) {
            hpDisplay.discard();
            hpDisplay = null;
        }
    }

    public void cleanup() {
        if (hpDisplay != null && hpDisplay.isAlive()) {
            hpDisplay.discard();
            hpDisplay = null;
        }
        structureBlocks.clear();
    }
}
