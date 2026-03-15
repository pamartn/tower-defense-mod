package com.towerdefense.spell;

import com.towerdefense.TowerDefenseMod;
import com.towerdefense.config.ConfigManager;
import com.towerdefense.game.GameManager;
import com.towerdefense.game.IncomeGeneratorManager;
import com.towerdefense.game.PlayerState;
import com.towerdefense.tower.TowerManager;
import com.towerdefense.wave.SpawnerManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class SpellManager {

    private static class TrackedFireball {
        final int casterTeamId;
        final LargeFireball entity;
        Vec3 lastPos;
        int lifetime;

        TrackedFireball(LargeFireball entity, int casterTeamId) {
            this.entity = entity;
            this.casterTeamId = casterTeamId;
            this.lastPos = entity.position();
            this.lifetime = ConfigManager.getInstance().getFireballLifetime();
        }
    }

    private final List<TrackedFireball> fireballs = new ArrayList<>();

    public boolean tryUseSpell(ServerPlayer player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty()) return false;

        Component name = stack.get(DataComponents.CUSTOM_NAME);
        if (name == null) return false;
        String spellName = name.getString();

        GameManager gm = TowerDefenseMod.getInstance().getGameManager();
        if (!gm.isActive()) return false;
        PlayerState ps = gm.getPlayerState(player);
        if (ps == null) return false;

        ServerLevel world = player.serverLevel();

        if (spellName.contains("Fireball") && stack.is(Items.FIRE_CHARGE)) {
            stack.shrink(1);
            return castFireball(player, ps, world);
        }
        if (spellName.contains("Freeze") && stack.is(Items.SNOWBALL)) {
            stack.shrink(1);
            return castFreeze(player, ps, world);
        }
        if (spellName.contains("Heal Nexus") && stack.is(Items.GOLDEN_APPLE)) {
            stack.shrink(1);
            return castHealNexus(player, ps, world);
        }
        if (spellName.contains("Lightning") && stack.is(Items.TRIDENT)) {
            stack.shrink(1);
            return castLightning(player, ps, world);
        }
        if (spellName.contains("Shield") && stack.is(Items.SHIELD)) {
            stack.shrink(1);
            return castShield(player, ps, world);
        }

        return false;
    }

    private boolean castFireball(ServerPlayer player, PlayerState ps, ServerLevel world) {
        Vec3 look = player.getLookAngle();
        Vec3 pos = player.getEyePosition().add(look.scale(2.0));

        LargeFireball fireball = new LargeFireball(world, player, look, 1);
        fireball.setPos(pos.x, pos.y, pos.z);
        fireball.setDeltaMovement(look.scale(1.2));
        world.addFreshEntity(fireball);

        fireballs.add(new TrackedFireball(fireball, ps.getSide()));
        world.playSound(null, player.blockPosition(), SoundEvents.GHAST_SHOOT, SoundSource.PLAYERS, 1.5f, 1.0f);
        player.sendSystemMessage(Component.literal("\u2604 Fireball!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        return true;
    }

    private boolean castFreeze(ServerPlayer player, PlayerState ps, ServerLevel world) {
        GameManager gm = TowerDefenseMod.getInstance().getGameManager();
        int enemyTeamId = getEnemyTeamId(ps, gm);

        String enemyOwnerTag = "td_owner_" + enemyTeamId;
        for (Mob mob : gm.getSpawnerManager().getAliveMobs()) {
            if (!mob.isAlive()) continue;
            if (!mob.getTags().contains(enemyOwnerTag)) continue;
            mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ConfigManager.getInstance().getFreezeDurationTicks(), 127));
        }

        world.playSound(null, player.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.5f, 0.5f);
        player.sendSystemMessage(Component.literal("\u2744 Freeze Bomb!").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        return true;
    }

    private boolean castHealNexus(ServerPlayer player, PlayerState ps, ServerLevel world) {
        ps.getNexusManager().heal(ConfigManager.getInstance().getHealNexusAmount());
        world.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.0f);
        player.sendSystemMessage(Component.literal("\u2764 Nexus healed +15 HP!").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        return true;
    }

    private boolean castLightning(ServerPlayer player, PlayerState ps, ServerLevel world) {
        Vec3 look = player.getLookAngle();
        Vec3 target = player.getEyePosition().add(look.scale(20));

        LightningBolt bolt = (LightningBolt) EntityType.LIGHTNING_BOLT.create(world, EntitySpawnReason.COMMAND);
        if (bolt != null) {
            bolt.moveTo(target.x, target.y, target.z);
            bolt.setVisualOnly(true);
            world.addFreshEntity(bolt);
        }

        BlockPos center = BlockPos.containing(target);
        int enemyTeamId = getEnemyTeamId(ps, TowerDefenseMod.getInstance().getGameManager());
        String enemyTag = "td_owner_" + enemyTeamId;
        int boxSize = ConfigManager.getInstance().getLightningBoxSize();
        AABB killBox = AABB.ofSize(target, boxSize, boxSize, boxSize);

        for (Entity e : world.getEntities((Entity) null, killBox, ent -> ent instanceof LivingEntity && ent.isAlive() && ent.getTags().contains("td_mob"))) {
            if (!e.getTags().contains(enemyTag)) continue;
            ((LivingEntity) e).hurt(world.damageSources().magic(), ConfigManager.getInstance().getLightningDamage());
        }

        world.playSound(null, center, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 2.0f, 1.0f);
        player.sendSystemMessage(Component.literal("\u26A1 Lightning Strike!").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        return true;
    }

    private boolean castShield(ServerPlayer player, PlayerState ps, ServerLevel world) {
        ps.getNexusManager().activateShield(ConfigManager.getInstance().getShieldDurationTicks());
        world.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.5f, 1.0f);
        player.sendSystemMessage(Component.literal("\u2728 Nexus Shield active for 10s!").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        return true;
    }

    private int getEnemyTeamId(PlayerState ps, GameManager gm) {
        int mySide = ps.getSide();
        return mySide == 1 ? 2 : 1;
    }

    public boolean castSpellForTeam(ServerLevel world, int teamId, SpellType type) {
        GameManager gm = TowerDefenseMod.getInstance().getGameManager();
        if (gm == null || !gm.isSoloMode()) return false;

        var team = teamId == 1 ? gm.getTeam1() : gm.getTeam2();
        if (team == null) return false;

        int price = type.getPrice();
        var mm = team.getMoneyManager();
        if (!mm.canAfford(price)) return false;

        mm.spend(price);
        int enemyTeamId = teamId == 1 ? 2 : 1;

        switch (type) {
            case FIREBALL -> {
                BlockPos from = team.getNexusCenter();
                BlockPos to = team.getEnemyNexusCenter();
                Vec3 dir = Vec3.atCenterOf(to).subtract(Vec3.atCenterOf(from)).normalize();
                Vec3 spawnPos = Vec3.atCenterOf(from).add(dir.scale(3));
                LargeFireball fb = new LargeFireball(world, null, dir, 1);
                fb.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
                fb.setDeltaMovement(dir.scale(1.2));
                world.addFreshEntity(fb);
                fireballs.add(new TrackedFireball(fb, teamId));
                world.playSound(null, BlockPos.containing(spawnPos), SoundEvents.GHAST_SHOOT, SoundSource.HOSTILE, 1.5f, 1.0f);
            }
            case FREEZE_BOMB -> {
                String enemyTag = "td_owner_" + enemyTeamId;
                for (Mob mob : gm.getSpawnerManager().getAliveMobs()) {
                    if (!mob.isAlive()) continue;
                    if (!mob.getTags().contains(enemyTag)) continue;
                    mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ConfigManager.getInstance().getFreezeDurationTicks(), 127));
                }
                world.playSound(null, team.getNexusCenter(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.5f, 0.5f);
            }
            case HEAL_NEXUS -> {
                team.getNexusManager().heal(ConfigManager.getInstance().getHealNexusAmount());
                world.playSound(null, team.getNexusCenter(), SoundEvents.PLAYER_LEVELUP, SoundSource.HOSTILE, 1.0f, 1.0f);
            }
            case LIGHTNING -> {
                BlockPos enemyNexus = team.getEnemyNexusCenter();
                Vec3 target = Vec3.atCenterOf(enemyNexus);
                LightningBolt bolt = (LightningBolt) EntityType.LIGHTNING_BOLT.create(world, EntitySpawnReason.COMMAND);
                if (bolt != null) {
                    bolt.moveTo(target.x, target.y, target.z);
                    bolt.setVisualOnly(true);
                    world.addFreshEntity(bolt);
                }
                String enemyTag = "td_owner_" + enemyTeamId;
                int boxSize = ConfigManager.getInstance().getLightningBoxSize();
                AABB killBox = AABB.ofSize(target, boxSize, boxSize, boxSize);
                for (Entity e : world.getEntities((Entity) null, killBox, ent -> ent instanceof LivingEntity && ent.isAlive() && ent.getTags().contains("td_mob"))) {
                    if (!e.getTags().contains(enemyTag)) continue;
                    ((LivingEntity) e).hurt(world.damageSources().magic(), ConfigManager.getInstance().getLightningDamage());
                }
                world.playSound(null, enemyNexus, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 2.0f, 1.0f);
            }
            case SHIELD -> {
                team.getNexusManager().activateShield(ConfigManager.getInstance().getShieldDurationTicks());
                world.playSound(null, team.getNexusCenter(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.HOSTILE, 1.5f, 1.0f);
            }
            default -> {
                mm.addMoney(price);
                return false;
            }
        }
        return true;
    }

    public void tick(ServerLevel world) {
        Iterator<TrackedFireball> it = fireballs.iterator();
        while (it.hasNext()) {
            TrackedFireball fb = it.next();
            fb.lifetime--;

            if (!fb.entity.isAlive()) {
                onFireballImpact(world, BlockPos.containing(fb.lastPos), fb.casterTeamId);
                it.remove();
                continue;
            }
            if (fb.lifetime <= 0) {
                onFireballImpact(world, BlockPos.containing(fb.entity.position()), fb.casterTeamId);
                fb.entity.discard();
                it.remove();
                continue;
            }
            fb.lastPos = fb.entity.position();
        }
    }

    private void onFireballImpact(ServerLevel world, BlockPos impactPos, int casterTeamId) {
        world.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                impactPos.getX() + 0.5, impactPos.getY() + 0.5, impactPos.getZ() + 0.5,
                5, 2, 2, 2, 0.1);
        world.playSound(null, impactPos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 2.0f, 0.8f);

        GameManager gm = TowerDefenseMod.getInstance().getGameManager();
        TowerManager tm = TowerDefenseMod.getInstance().getTowerManager();
        SpawnerManager sm = gm.getSpawnerManager();
        IncomeGeneratorManager igm = gm.getIncomeGeneratorManager();

        int radius = (int) ConfigManager.getInstance().getFireballExplosionRadius();
        Set<Object> destroyed = new HashSet<>();

        gm.getWallBlockManager().onFireballImpact(world, impactPos, casterTeamId, radius);

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radius * radius) continue;
                    BlockPos check = impactPos.offset(dx, dy, dz);

                    var tower = tm.findTowerByBlock(check);
                    if (tower != null && tower.teamId() != casterTeamId && destroyed.add(tower)) {
                        tm.clearAndRemoveTower(tower);
                    }
                    var spawner = sm.findByBlock(check);
                    if (spawner != null && spawner.teamId() != casterTeamId && destroyed.add(spawner)) {
                        sm.removeSpawner(world, check);
                    }
                    var gen = igm.findByBlock(check);
                    if (gen != null && gen.teamId() != casterTeamId && destroyed.add(gen)) {
                        igm.removeGenerator(world, check);
                    }
                }
            }
        }
    }

    public void removeAll() {
        for (TrackedFireball fb : fireballs) {
            if (fb.entity.isAlive()) fb.entity.discard();
        }
        fireballs.clear();
    }
}
