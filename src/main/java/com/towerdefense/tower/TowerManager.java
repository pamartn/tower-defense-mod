package com.towerdefense.tower;

import com.towerdefense.config.ConfigManager;
import com.towerdefense.game.GameManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class TowerManager {

    public record ActiveTower(
            ArmorStand marker,
            TowerRecipe recipe,
            int teamId,
            BlockPos basePos,
            List<BlockPos> structureBlocks,
            int[] cooldown
    ) {}

    private final List<ActiveTower> towers = new ArrayList<>();
    private final List<PendingCannonImpact> pendingCannonImpacts = new ArrayList<>();
    private GameManager gameManager;

    private record PendingCannonImpact(ServerLevel world, Vec3 pos, int power, int fireTicks, int ticksLeft, int teamId) {}
    private com.towerdefense.tower.TowerUpgradeManager towerUpgradeManager;

    public void setGameManager(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    public void setTowerUpgradeManager(com.towerdefense.tower.TowerUpgradeManager towerUpgradeManager) {
        this.towerUpgradeManager = towerUpgradeManager;
    }

    public void spawnTower(ServerLevel world, BlockPos basePos, TowerRecipe recipe, int teamId) {
        List<BlockPos> structureBlocks = buildStructure(world, basePos, recipe.type());

        int structureHeight = getStructureHeight(recipe.type());
        double cx = basePos.getX() + 0.5;
        double cy = basePos.getY() + structureHeight;
        double cz = basePos.getZ() + 0.5;

        ArmorStand marker = new ArmorStand(EntityType.ARMOR_STAND, world);
        marker.setPos(cx, cy, cz);
        marker.setCustomName(Component.literal(getColorCode(recipe.type()) + recipe.name()));
        marker.setCustomNameVisible(true);
        marker.setNoGravity(true);
        marker.setInvulnerable(true);
        marker.setInvisible(true);
        marker.setSilent(true);
        world.addFreshEntity(marker);

        towers.add(new ActiveTower(marker, recipe, teamId, basePos, structureBlocks, new int[]{recipe.fireRateInTicks()}));
    }

    private String getColorCode(TowerType type) {
        return switch (type) {
            case BASIC -> "\u00a77";
            case ARCHER -> "\u00a7a";
            case CANNON -> "\u00a7c";
            case LASER -> "\u00a7b";
            case FIRE -> "\u00a76";
            case SLOW -> "\u00a79";
            case POISON -> "\u00a72";
            case SNIPER -> "\u00a78";
            case CHAIN_LIGHTNING -> "\u00a7e";
            case AOE -> "\u00a74";
        };
    }

    private int getStructureHeight(TowerType type) {
        return 4;
    }

    // ─── Structure Builders ───

    private List<BlockPos> buildStructure(ServerLevel world, BlockPos base, TowerType type) {
        return switch (type) {
            case BASIC -> buildBasicStructure(world, base);
            case ARCHER -> buildArcherStructure(world, base);
            case CANNON -> buildCannonStructure(world, base);
            case LASER -> buildLaserStructure(world, base);
            case FIRE -> buildFireStructure(world, base);
            case SLOW -> buildSlowStructure(world, base);
            case POISON -> buildSimpleStructure(world, base, Blocks.SLIME_BLOCK, Blocks.SLIME_BLOCK, Blocks.GREEN_STAINED_GLASS);
            case SNIPER -> buildSimpleStructure(world, base, Blocks.COPPER_BLOCK, Blocks.CUT_COPPER, Blocks.REDSTONE_TORCH);
            case CHAIN_LIGHTNING -> buildSimpleStructure(world, base, Blocks.GOLD_BLOCK, Blocks.LIGHTNING_ROD, Blocks.REDSTONE_LAMP);
            case AOE -> buildSimpleStructure(world, base, Blocks.TNT, Blocks.OBSERVER, Blocks.REDSTONE_BLOCK);
        };
    }

    private List<BlockPos> buildBasicStructure(ServerLevel world, BlockPos base) {
        List<BlockPos> positions = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos p = base.offset(x, 0, z);
                world.setBlock(p, Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
                positions.add(p);
            }
        }
        placeAndTrack(world, base.above(1), Blocks.COBBLESTONE, positions);
        placeAndTrack(world, base.above(2), Blocks.COBBLESTONE, positions);
        placeAndTrack(world, base.above(3), Blocks.TORCH, positions);
        return positions;
    }

    private List<BlockPos> buildArcherStructure(ServerLevel world, BlockPos base) {
        List<BlockPos> positions = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos p = base.offset(x, 0, z);
                world.setBlock(p, Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
                positions.add(p);
            }
        }
        placeAndTrack(world, base.above(1), Blocks.OAK_PLANKS, positions);
        placeAndTrack(world, base.above(2), Blocks.OAK_FENCE, positions);
        placeAndTrack(world, base.above(3), Blocks.CARVED_PUMPKIN, positions);
        placeAndTrack(world, base.offset(-1, 1, -1), Blocks.OAK_FENCE, positions);
        placeAndTrack(world, base.offset(1, 1, -1), Blocks.OAK_FENCE, positions);
        placeAndTrack(world, base.offset(-1, 1, 1), Blocks.OAK_FENCE, positions);
        placeAndTrack(world, base.offset(1, 1, 1), Blocks.OAK_FENCE, positions);
        return positions;
    }

    private List<BlockPos> buildCannonStructure(ServerLevel world, BlockPos base) {
        List<BlockPos> positions = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos p = base.offset(x, 0, z);
                world.setBlock(p, Blocks.STONE_BRICKS.defaultBlockState(), Block.UPDATE_ALL);
                positions.add(p);
            }
        }
        placeAndTrack(world, base.above(1), Blocks.STONE_BRICKS, positions);
        placeAndTrack(world, base.above(2), Blocks.IRON_BLOCK, positions);
        placeAndTrack(world, base.above(3), Blocks.DISPENSER, positions);
        placeAndTrack(world, base.offset(-1, 1, 0), Blocks.STONE_BRICK_WALL, positions);
        placeAndTrack(world, base.offset(1, 1, 0), Blocks.STONE_BRICK_WALL, positions);
        placeAndTrack(world, base.offset(0, 1, -1), Blocks.STONE_BRICK_WALL, positions);
        placeAndTrack(world, base.offset(0, 1, 1), Blocks.STONE_BRICK_WALL, positions);
        return positions;
    }

    private List<BlockPos> buildLaserStructure(ServerLevel world, BlockPos base) {
        List<BlockPos> positions = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos p = base.offset(x, 0, z);
                boolean corner = (x != 0 && z != 0);
                Block block = corner ? Blocks.OBSIDIAN : Blocks.DIAMOND_BLOCK;
                world.setBlock(p, block.defaultBlockState(), Block.UPDATE_ALL);
                positions.add(p);
            }
        }
        placeAndTrack(world, base.above(1), Blocks.DIAMOND_BLOCK, positions);
        placeAndTrack(world, base.above(2), Blocks.END_ROD, positions);
        placeAndTrack(world, base.above(3), Blocks.BEACON, positions);
        placeAndTrack(world, base.offset(-1, 2, 0), Blocks.END_ROD, positions);
        placeAndTrack(world, base.offset(1, 2, 0), Blocks.END_ROD, positions);
        placeAndTrack(world, base.offset(0, 2, -1), Blocks.END_ROD, positions);
        placeAndTrack(world, base.offset(0, 2, 1), Blocks.END_ROD, positions);
        return positions;
    }

    private List<BlockPos> buildFireStructure(ServerLevel world, BlockPos base) {
        List<BlockPos> positions = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos p = base.offset(x, 0, z);
                world.setBlock(p, Blocks.NETHERRACK.defaultBlockState(), Block.UPDATE_ALL);
                positions.add(p);
            }
        }
        placeAndTrack(world, base.above(1), Blocks.NETHER_BRICKS, positions);
        placeAndTrack(world, base.above(2), Blocks.MAGMA_BLOCK, positions);
        placeAndTrack(world, base.above(3), Blocks.SOUL_LANTERN, positions);
        return positions;
    }

    private List<BlockPos> buildSimpleStructure(ServerLevel world, BlockPos base, Block baseBlock, Block mid, Block top) {
        List<BlockPos> positions = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos p = base.offset(x, 0, z);
                world.setBlock(p, baseBlock.defaultBlockState(), Block.UPDATE_ALL);
                positions.add(p);
            }
        }
        placeAndTrack(world, base.above(1), baseBlock, positions);
        placeAndTrack(world, base.above(2), mid, positions);
        placeAndTrack(world, base.above(3), top, positions);
        return positions;
    }

    private List<BlockPos> buildSlowStructure(ServerLevel world, BlockPos base) {
        List<BlockPos> positions = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos p = base.offset(x, 0, z);
                world.setBlock(p, Blocks.BLUE_ICE.defaultBlockState(), Block.UPDATE_ALL);
                positions.add(p);
            }
        }
        placeAndTrack(world, base.above(1), Blocks.PACKED_ICE, positions);
        placeAndTrack(world, base.above(2), Blocks.SNOW_BLOCK, positions);
        placeAndTrack(world, base.above(3), Blocks.BLUE_STAINED_GLASS, positions);
        return positions;
    }

    private void placeAndTrack(ServerLevel world, BlockPos pos, Block block, List<BlockPos> positions) {
        world.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
        positions.add(pos);
    }

    // ─── Tick Loop ───

    public void tick() {
        processPendingCannonImpacts();

        Iterator<ActiveTower> it = towers.iterator();
        while (it.hasNext()) {
            ActiveTower tower = it.next();
            ArmorStand marker = tower.marker();

            if (!marker.isAlive()) {
                clearStructure(tower);
                it.remove();
                continue;
            }

            if (!(marker.level() instanceof ServerLevel serverLevel)) continue;

            if (hasStructureDamage(serverLevel, tower)) {
                if (gameManager != null) gameManager.onStructureDestroyed(tower.teamId());
                clearStructure(tower);
                marker.discard();
                it.remove();
                serverLevel.playSound(null, tower.basePos(), SoundEvents.ANVIL_BREAK, SoundSource.BLOCKS, 0.8f, 1.0f);
                continue;
            }

            tower.cooldown()[0]--;
            if (tower.cooldown()[0] > 0) continue;
            int fireRate = getEffectiveFireRate(tower);
            tower.cooldown()[0] = fireRate;

            boolean tdMode = gameManager != null && gameManager.isActive();
            LivingEntity target = findTarget(serverLevel, marker.position(), tower.teamId(), tower.recipe().range(), tdMode);
            if (target == null) continue;

            dispatchAttack(serverLevel, marker, target, tower);
        }
    }

    private int getEffectivePower(ActiveTower tower) {
        if (towerUpgradeManager == null) return tower.recipe().power();
        return towerUpgradeManager.getEffectivePower(tower.recipe().power(), tower.teamId(), tower.recipe().type());
    }

    private int getEffectiveFireRate(ActiveTower tower) {
        if (towerUpgradeManager == null) return tower.recipe().fireRateInTicks();
        return towerUpgradeManager.getEffectiveFireRate(tower.recipe().fireRateInTicks(), tower.teamId(), tower.recipe().type());
    }

    private int getEffectiveEffectDuration(ActiveTower tower, int baseDuration) {
        if (towerUpgradeManager == null) return baseDuration;
        return towerUpgradeManager.getEffectiveEffectDuration(baseDuration, tower.teamId(), tower.recipe().type());
    }

    private void dispatchAttack(ServerLevel world, ArmorStand marker, LivingEntity target, ActiveTower tower) {
        switch (tower.recipe().type()) {
            case BASIC -> shootArrow(world, marker, target, tower);
            case ARCHER -> shootDoubleArrow(world, marker, target, tower);
            case CANNON -> shootCannonball(world, marker, target, tower);
            case LASER -> shootLaser(world, marker, target, tower);
            case FIRE -> shootFire(world, marker, target, tower);
            case SLOW -> shootSlow(world, marker, target, tower);
            case POISON -> shootPoison(world, marker, target, tower);
            case SNIPER -> shootSniper(world, marker, target, tower);
            case CHAIN_LIGHTNING -> shootChainLightning(world, marker, target, tower);
            case AOE -> shootAOE(world, marker, target, tower);
        }
    }

    // ─── Target Finding ───

    private LivingEntity findTarget(ServerLevel world, Vec3 center, int teamId, double range, boolean tdMode) {
        LivingEntity closest = null;
        double closestDist = range;

        AABB scanBox = AABB.ofSize(center, range * 2, range * 2, range * 2);
        String ownerTag = "td_owner_" + teamId;

        for (Entity entity : world.getEntities((Entity) null, scanBox, e -> e instanceof LivingEntity le && le.isAlive() && le.getTags().contains("td_mob") && (!tdMode || !le.getTags().contains(ownerTag)))) {

            double dist = entity.position().distanceTo(center);
            if (dist < closestDist) {
                closestDist = dist;
                closest = (LivingEntity) entity;
            }
        }

        return closest;
    }

    // ─── Attack: Basic (1 arrow) ───

    private void shootArrow(ServerLevel world, ArmorStand marker, LivingEntity target, ActiveTower tower) {
        int power = getEffectivePower(tower);
        Vec3 origin = marker.position().add(0, 0.5, 0);
        Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Vec3 direction = targetPos.subtract(origin).normalize().scale(2.0);

        Arrow arrow = new Arrow(EntityType.ARROW, world);
        arrow.setPos(origin.x, origin.y, origin.z);
        arrow.setDeltaMovement(direction);
        arrow.setBaseDamage(power);
        arrow.setOwner(marker);
        world.addFreshEntity(arrow);

        world.sendParticles(ParticleTypes.FLAME, origin.x, origin.y, origin.z, 3, 0.1, 0.1, 0.1, 0.01);
        world.playSound(null, marker.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.BLOCKS, 1.0f, 1.2f);
    }

    // ─── Attack: Archer (2 arrows with spread) ───

    private void shootDoubleArrow(ServerLevel world, ArmorStand marker, LivingEntity target, ActiveTower tower) {
        int power = getEffectivePower(tower);
        Vec3 origin = marker.position().add(0, 0.5, 0);
        Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Vec3 direction = targetPos.subtract(origin).normalize();

        Vec3 perpendicular = new Vec3(-direction.z, 0, direction.x).normalize();
        double spread = 0.3;

        for (int i = -1; i <= 1; i += 2) {
            Vec3 offset = perpendicular.scale(spread * i);
            Vec3 arrowDir = direction.add(offset.scale(0.15)).normalize().scale(2.5);

            Arrow arrow = new Arrow(EntityType.ARROW, world);
            arrow.setPos(origin.x + offset.x, origin.y, origin.z + offset.z);
            arrow.setDeltaMovement(arrowDir);
            arrow.setBaseDamage(power);
            arrow.setOwner(marker);
            world.addFreshEntity(arrow);
        }

        world.sendParticles(ParticleTypes.CRIT, origin.x, origin.y, origin.z, 8, 0.3, 0.2, 0.3, 0.05);
        world.playSound(null, marker.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.BLOCKS, 1.0f, 1.5f);
    }

    // ─── Attack: Cannon (delayed impact, no block destruction) ───

    private static final int CANNON_IMPACT_DELAY_TICKS = 15;
    private static final double CANNON_AOE_RADIUS = 4.0;

    private void processPendingCannonImpacts() {
        for (int i = pendingCannonImpacts.size() - 1; i >= 0; i--) {
            PendingCannonImpact impact = pendingCannonImpacts.get(i);
            int next = impact.ticksLeft() - 1;
            if (next <= 0) {
                executeCannonImpact(impact);
                pendingCannonImpacts.remove(i);
            } else {
                pendingCannonImpacts.set(i, new PendingCannonImpact(impact.world(), impact.pos(), impact.power(), impact.fireTicks(), next, impact.teamId()));
            }
        }
    }

    private void executeCannonImpact(PendingCannonImpact impact) {
        ServerLevel world = impact.world();
        Vec3 center = impact.pos();
        String ownerTag = "td_owner_" + impact.teamId();
        AABB aoeBox = AABB.ofSize(center, 8, 8, 8);

        for (Entity e : world.getEntities((Entity) null, aoeBox, ent -> ent instanceof LivingEntity le && le.isAlive() && le.getTags().contains("td_mob") && !le.getTags().contains(ownerTag))) {
            if (e.position().distanceTo(center) <= CANNON_AOE_RADIUS) {
                LivingEntity le = (LivingEntity) e;
                le.hurt(world.damageSources().explosion(null, null), impact.power());
                le.setRemainingFireTicks(impact.fireTicks());
            }
        }

        world.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.5, center.z, 5, 1.5, 0.5, 1.5, 0.01);
        world.playSound(null, BlockPos.containing(center), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.8f, 0.9f);
    }

    private void shootCannonball(ServerLevel world, ArmorStand marker, LivingEntity target, ActiveTower tower) {
        int power = getEffectivePower(tower);
        int fireTicks = getEffectiveEffectDuration(tower, ConfigManager.getInstance().getFireTicks());
        Vec3 origin = marker.position().add(0, 0.5, 0);
        Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.5, 0);

        pendingCannonImpacts.add(new PendingCannonImpact(world, targetPos, power, fireTicks, CANNON_IMPACT_DELAY_TICKS, tower.teamId()));

        world.sendParticles(ParticleTypes.LARGE_SMOKE, origin.x, origin.y, origin.z, 10, 0.3, 0.3, 0.3, 0.02);
        world.playSound(null, marker.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.8f, 0.9f);
    }

    // ─── Attack: Laser (particle beam + direct damage) ───

    private void shootLaser(ServerLevel world, ArmorStand marker, LivingEntity target, ActiveTower tower) {
        int power = getEffectivePower(tower);
        Vec3 origin = marker.position().add(0, 0.5, 0);
        Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.5, 0);

        int particles = 30;
        for (int i = 0; i <= particles; i++) {
            double t = (double) i / particles;
            double px = origin.x + (targetPos.x - origin.x) * t;
            double py = origin.y + (targetPos.y - origin.y) * t;
            double pz = origin.z + (targetPos.z - origin.z) * t;
            world.sendParticles(ParticleTypes.END_ROD, px, py, pz, 1, 0, 0, 0, 0);
        }
        world.sendParticles(ParticleTypes.ELECTRIC_SPARK, targetPos.x, targetPos.y, targetPos.z, 15, 0.3, 0.3, 0.3, 0.1);

        target.hurt(world.damageSources().magic(), power);

        world.playSound(null, marker.blockPosition(), SoundEvents.GUARDIAN_ATTACK, SoundSource.HOSTILE, 1.5f, 1.8f);
    }

    // ─── Attack: Fire (ignite target) ───

    private void shootFire(ServerLevel world, ArmorStand marker, LivingEntity target, ActiveTower tower) {
        int power = getEffectivePower(tower);
        int fireTicks = getEffectiveEffectDuration(tower, ConfigManager.getInstance().getFireTicks());
        Vec3 origin = marker.position().add(0, 0.5, 0);

        target.setRemainingFireTicks(fireTicks);
        if (power > 0) {
            target.hurt(world.damageSources().onFire(), power);
        }

        world.sendParticles(ParticleTypes.FLAME, origin.x, origin.y, origin.z, 10, 0.3, 0.3, 0.3, 0.05);
        world.sendParticles(ParticleTypes.LAVA, target.getX(), target.getY() + 0.5, target.getZ(), 5, 0.3, 0.3, 0.3, 0.01);
        world.playSound(null, marker.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.0f, 1.0f);
    }

    // ─── Attack: Slow (apply slowness) ───

    private void shootSlow(ServerLevel world, ArmorStand marker, LivingEntity target, ActiveTower tower) {
        int duration = getEffectiveEffectDuration(tower, ConfigManager.getInstance().getSlowDurationTicks());
        int amplifier = ConfigManager.getInstance().getSlowAmplifier();
        Vec3 origin = marker.position().add(0, 0.5, 0);

        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, amplifier));

        world.sendParticles(ParticleTypes.SNOWFLAKE, origin.x, origin.y, origin.z, 8, 0.3, 0.3, 0.3, 0.05);
        world.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY() + 0.5, target.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
        world.playSound(null, marker.blockPosition(), SoundEvents.SNOW_GOLEM_SHOOT, SoundSource.BLOCKS, 1.0f, 1.2f);
    }

    // ─── Attack: Poison ───

    private void shootPoison(ServerLevel world, ArmorStand marker, LivingEntity target, ActiveTower tower) {
        int duration = getEffectiveEffectDuration(tower, ConfigManager.getInstance().getPoisonDurationTicks());
        int amplifier = ConfigManager.getInstance().getPoisonAmplifier();
        target.addEffect(new MobEffectInstance(MobEffects.POISON, duration, amplifier));
        world.sendParticles(ParticleTypes.ITEM_SLIME, target.getX(), target.getY() + 0.5, target.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
        world.playSound(null, marker.blockPosition(), SoundEvents.SLIME_SQUISH, SoundSource.BLOCKS, 1.0f, 1.0f);
    }

    // ─── Attack: Sniper ───

    private void shootSniper(ServerLevel world, ArmorStand marker, LivingEntity target, ActiveTower tower) {
        int power = getEffectivePower(tower);
        Vec3 origin = marker.position().add(0, 0.5, 0);
        Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.5, 0);

        int particles = 40;
        for (int i = 0; i <= particles; i++) {
            double t = (double) i / particles;
            world.sendParticles(ParticleTypes.CRIT,
                    origin.x + (targetPos.x - origin.x) * t,
                    origin.y + (targetPos.y - origin.y) * t,
                    origin.z + (targetPos.z - origin.z) * t, 1, 0, 0, 0, 0);
        }
        target.hurt(world.damageSources().magic(), power);
        world.playSound(null, marker.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.BLOCKS, 1.5f, 0.5f);
    }

    // ─── Attack: Chain Lightning ───

    private void shootChainLightning(ServerLevel world, ArmorStand marker, LivingEntity target, ActiveTower tower) {
        int power = getEffectivePower(tower);
        Vec3 center = target.position();
        spawnLightningOnTarget(world, target);
        target.hurt(world.damageSources().magic(), power);

        String ownerTag = "td_owner_" + findTeamIdForTower(marker);
        AABB chainBox = AABB.ofSize(center, 8, 8, 8);
        int chained = 0;
        for (Entity e : world.getEntities((Entity) null, chainBox, ent -> ent instanceof LivingEntity le && le.isAlive() && ent != target && le.getTags().contains("td_mob") && !le.getTags().contains(ownerTag))) {
            if (chained >= 2) break;
            spawnLightningOnTarget(world, (LivingEntity) e);
            ((LivingEntity) e).hurt(world.damageSources().magic(), power);
            chained++;
        }
        world.playSound(null, marker.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 0.5f, 1.5f);
    }

    private void spawnLightningOnTarget(ServerLevel world, LivingEntity target) {
        LightningBolt bolt = (LightningBolt) EntityType.LIGHTNING_BOLT.create(world, EntitySpawnReason.TRIGGERED);
        if (bolt != null) {
            bolt.moveTo(target.getX(), target.getY(), target.getZ());
            bolt.setVisualOnly(true);
            world.addFreshEntity(bolt);
        }
    }

    private int findTeamIdForTower(ArmorStand marker) {
        for (ActiveTower t : towers) {
            if (t.marker() == marker) return t.teamId();
        }
        return 0;
    }

    // ─── Attack: AOE ───

    private void shootAOE(ServerLevel world, ArmorStand marker, LivingEntity target, ActiveTower tower) {
        int power = getEffectivePower(tower);
        Vec3 center = target.position();
        String ownerTag = "td_owner_" + findTeamIdForTower(marker);
        AABB aoeBox = AABB.ofSize(center, 8, 8, 8);

        for (Entity e : world.getEntities((Entity) null, aoeBox, ent -> ent instanceof LivingEntity le && le.isAlive() && le.getTags().contains("td_mob") && !le.getTags().contains(ownerTag))) {
            if (e.position().distanceTo(center) <= 4.0) {
                ((LivingEntity) e).hurt(world.damageSources().explosion(null, null), power);
            }
        }

        world.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.5, center.z, 5, 1.5, 0.5, 1.5, 0.01);
        world.playSound(null, marker.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.6f, 1.2f);
    }

    private boolean hasStructureDamage(ServerLevel world, ActiveTower tower) {
        for (BlockPos pos : tower.structureBlocks()) {
            if (world.getBlockState(pos).isAir()) {
                return true;
            }
        }
        return false;
    }

    // ─── Cleanup ───

    private void clearStructure(ActiveTower tower) {
        if (!(tower.marker().level() instanceof ServerLevel world)) return;
        for (BlockPos pos : tower.structureBlocks()) {
            world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    public void removeAll() {
        pendingCannonImpacts.clear();
        for (ActiveTower tower : towers) {
            clearStructure(tower);
            if (tower.marker().isAlive()) {
                tower.marker().discard();
            }
        }
        towers.clear();
    }

    /**
     * Explode the tower at the given index with particles/sound.
     * Returns true if a tower was exploded, false if index is out of range.
     */
    public boolean explodeNextTower(ServerLevel world, int index) {
        if (index >= towers.size()) return false;

        ActiveTower tower = towers.get(index);
        BlockPos pos = tower.basePos();

        world.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                pos.getX() + 0.5, pos.getY() + 2, pos.getZ() + 0.5,
                2, 0.5, 0.5, 0.5, 0.1);
        world.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.0f, 0.8f);

        clearStructure(tower);
        if (tower.marker().isAlive()) {
            tower.marker().discard();
        }

        return true;
    }

    public void clearExplodedTowers() {
        towers.removeIf(t -> !t.marker().isAlive());
    }

    public ActiveTower findTowerByBlock(BlockPos pos) {
        for (ActiveTower tower : towers) {
            for (BlockPos bp : tower.structureBlocks()) {
                if (bp.equals(pos)) return tower;
            }
        }
        return null;
    }

    public boolean destroyTowerAndRefund(ServerLevel world, BlockPos brokenPos, ServerPlayer player) {
        ActiveTower tower = findTowerByBlock(brokenPos);
        if (tower == null) return false;

        player.getInventory().add(new ItemStack(tower.recipe().block().asItem()));

        clearStructure(tower);
        if (tower.marker().isAlive()) {
            tower.marker().discard();
        }
        towers.remove(tower);

        world.playSound(null, brokenPos, SoundEvents.ANVIL_BREAK, SoundSource.BLOCKS, 0.8f, 1.2f);

        player.sendSystemMessage(
                Component.literal("\u2716 " + tower.recipe().name() + " detruite !").withStyle(net.minecraft.ChatFormatting.RED)
        );

        return true;
    }

    public void clearAndRemoveTower(ActiveTower tower) {
        clearStructure(tower);
        if (tower.marker().isAlive()) tower.marker().discard();
        towers.remove(tower);
    }

    public int activeTowerCount() {
        return towers.size();
    }

    public List<ActiveTower> getTowers() {
        return towers;
    }
}
