package com.towerdefense.wave;

import com.towerdefense.TowerDefenseMod;
import com.towerdefense.config.ConfigManager;
import com.towerdefense.game.GameConfig;
import net.minecraft.ChatFormatting;
import com.towerdefense.mob.MoveToNexusGoal;
import com.towerdefense.mob.RavagerBreachBehavior;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.*;

public class SpawnerManager {

    public record ActiveSpawner(
            ArmorStand marker,
            SpawnerType type,
            int teamId,
            BlockPos basePos,
            BlockPos targetNexus,
            List<BlockPos> structureBlocks,
            int[] cooldown
    ) {}

    private final List<ActiveSpawner> spawners = new ArrayList<>();
    private final List<Mob> aliveMobs = new ArrayList<>();
    private final Map<Mob, RavagerBreachBehavior> ravagerBehaviors = new HashMap<>();
    private MobUpgradeManager upgradeManager;

    public void setUpgradeManager(MobUpgradeManager manager) {
        this.upgradeManager = manager;
    }

    public void placeSpawner(ServerLevel world, BlockPos basePos, SpawnerType type, int teamId, BlockPos targetNexus) {
        List<BlockPos> structure = buildStructure(world, basePos, type);

        ArmorStand marker = new ArmorStand(EntityType.ARMOR_STAND, world);
        marker.setPos(basePos.getX() + 0.5, basePos.getY() + 2.5, basePos.getZ() + 0.5);
        marker.setCustomName(Component.literal("\u00a7d" + type.getName()));
        marker.setCustomNameVisible(true);
        marker.setNoGravity(true);
        marker.setInvulnerable(true);
        marker.setInvisible(true);
        marker.setSilent(true);
        world.addFreshEntity(marker);

        spawners.add(new ActiveSpawner(marker, type, teamId, basePos, targetNexus, structure, new int[]{type.getSpawnIntervalTicks()}));

        world.playSound(null, basePos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1.0f, 0.8f);
    }

    private List<BlockPos> buildStructure(ServerLevel world, BlockPos base, SpawnerType type) {
        List<BlockPos> positions = new ArrayList<>();
        world.setBlock(base, type.getTriggerBlock().defaultBlockState(), Block.UPDATE_ALL);
        positions.add(base);
        world.setBlock(base.above(1), Blocks.IRON_BARS.defaultBlockState(), Block.UPDATE_ALL);
        positions.add(base.above(1));
        world.setBlock(base.above(2), Blocks.SOUL_LANTERN.defaultBlockState(), Block.UPDATE_ALL);
        positions.add(base.above(2));
        return positions;
    }

    public void tick(ServerLevel world, boolean prepPhase) {
        aliveMobs.removeIf(mob -> !mob.isAlive());
        ravagerBehaviors.keySet().removeIf(mob -> !mob.isAlive());

        for (var entry : ravagerBehaviors.entrySet()) {
            if (entry.getKey().isAlive()) {
                entry.getValue().tick(entry.getKey(), world);
            }
        }

        tickSpecialMobs(world);
        tickMobHpDisplays(world);

        if (prepPhase) return;

        Iterator<ActiveSpawner> it = spawners.iterator();
        while (it.hasNext()) {
            ActiveSpawner spawner = it.next();
            if (!spawner.marker().isAlive()) {
                clearStructure(world, spawner);
                it.remove();
                continue;
            }

            spawner.cooldown()[0]--;
            if (spawner.cooldown()[0] <= 0) {
                spawner.cooldown()[0] = spawner.type().getSpawnIntervalTicks();
                spawnMob(world, spawner);
            }
        }
    }

    private int specialTickCounter = 0;
    private int hpDisplayTickCounter = 0;

    private void tickSpecialMobs(ServerLevel world) {
        specialTickCounter++;
        int specialInterval = ConfigManager.getInstance().getSpecialMobTickInterval();
        if (specialTickCounter % specialInterval != 0) return;

        int witchHealInterval = ConfigManager.getInstance().getWitchHealIntervalTicks();

        for (Mob mob : aliveMobs) {
            if (!mob.isAlive()) continue;

            String typeTag = mob.getTags().stream().filter(t -> t.startsWith("td_type_")).findFirst().orElse("");
            String ownerTag = mob.getTags().stream().filter(t -> t.startsWith("td_owner_")).findFirst().orElse("");

            if (typeTag.equals("td_type_ENDERMAN")) {
                double rx = mob.getX() + (world.random.nextDouble() - 0.5) * 8;
                double rz = mob.getZ() + (world.random.nextDouble() - 0.5) * 8;
                mob.teleportTo(rx, mob.getY(), rz);
            }

            if (typeTag.equals("td_type_WITCH")) {
                if (specialTickCounter % witchHealInterval != 0) continue;
                int boxSize = ConfigManager.getInstance().getWitchHealBoxSize();
                double healAmount = ConfigManager.getInstance().getWitchHealAmount();
                if (healAmount <= 0) continue;
                AABB healBox = AABB.ofSize(mob.position(), boxSize, 4, boxSize);
                String allyTag = ownerTag.isEmpty() ? "none" : ownerTag;
                for (Entity e : world.getEntities((Entity) null, healBox, ent -> ent instanceof LivingEntity && ent.isAlive() && ent.getTags().contains("td_mob"))) {
                    if (!e.getTags().contains(allyTag)) continue;
                    LivingEntity le = (LivingEntity) e;
                    le.heal((float) healAmount);
                }
            }
        }
    }

    private void tickMobHpDisplays(ServerLevel world) {
        hpDisplayTickCounter++;
        if (hpDisplayTickCounter % 10 != 0) return;

        for (Mob mob : aliveMobs) {
            if (mob.isAlive() && mob.getTags().contains("td_mob")) {
                updateMobHpDisplay(mob);
            }
        }
    }

    private void updateMobHpDisplay(Mob mob) {
        float hp = mob.getHealth();
        float maxHp = mob.getMaxHealth();
        float ratio = maxHp > 0 ? hp / maxHp : 0;

        ChatFormatting color;
        if (ratio > 0.6f) color = ChatFormatting.GREEN;
        else if (ratio > 0.3f) color = ChatFormatting.YELLOW;
        else color = ChatFormatting.RED;

        int bars = 10;
        int filled = (int) (ratio * bars);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bars; i++) {
            sb.append(i < filled ? "\u2588" : "\u2591");
        }

        mob.setCustomName(
                Component.literal(sb.toString()).withStyle(color)
                        .append(Component.literal(" " + (int) hp + "/" + (int) maxHp).withStyle(ChatFormatting.WHITE))
        );
    }

    private void spawnMob(ServerLevel world, ActiveSpawner spawner) {
        MobType type = spawner.type().getMobType();
        BlockPos spawnPos = spawner.basePos();
        double spread = 2.0;
        double x = spawnPos.getX() + 0.5 + (world.random.nextDouble() - 0.5) * spread;
        double z = spawnPos.getZ() + 0.5 + (world.random.nextDouble() - 0.5) * spread;

        Entity entity = type.getEntityType().create(world, EntitySpawnReason.MOB_SUMMONED);
        if (!(entity instanceof Mob mob)) {
            if (entity != null) entity.discard();
            return;
        }

        double spawnY = (mob instanceof net.minecraft.world.entity.FlyingMob)
                ? spawnPos.getY() + 6.0  // Flying mobs spawn in the air
                : spawnPos.getY() + 1;
        mob.moveTo(x, spawnY, z, 0, 0);
        mob.setNoAi(false);
        if (!(mob instanceof net.minecraft.world.entity.FlyingMob)) {
            mob.setOnGround(true);
        }

        if (type == MobType.BABY_ZOMBIE && mob instanceof net.minecraft.world.entity.monster.Zombie zombie) {
            zombie.setBaby(true);
        }

        double hp = type.getBaseHp();
        double speed = type.getSpeed();
        if (upgradeManager != null) {
            hp *= upgradeManager.getHpMultiplier(spawner.teamId(), type);
            speed *= upgradeManager.getSpeedMultiplier(spawner.teamId(), type);
        }

        var maxHpAttr = mob.getAttribute(Attributes.MAX_HEALTH);
        if (maxHpAttr != null) maxHpAttr.setBaseValue(hp);
        mob.setHealth((float) hp);

        var speedAttr = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) speedAttr.setBaseValue(speed);

        var followRange = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange != null) followRange.setBaseValue(128.0);

        mob.setPersistenceRequired();
        mob.goalSelector.removeAllGoals(g -> true);
        mob.targetSelector.removeAllGoals(g -> true);
        mob.goalSelector.addGoal(0, new MoveToNexusGoal(mob, spawner.targetNexus(), speed));

        mob.addTag("td_mob");
        mob.addTag("td_type_" + type.name());
        mob.addTag("td_owner_" + spawner.teamId());

        mob.setCustomNameVisible(true);
        updateMobHpDisplay(mob);

        world.addFreshEntity(mob);
        aliveMobs.add(mob);

        if (type == MobType.RAVAGER) {
            ravagerBehaviors.put(mob, new RavagerBreachBehavior());
        }
    }

    public ActiveSpawner findByBlock(BlockPos pos) {
        for (ActiveSpawner s : spawners) {
            for (BlockPos bp : s.structureBlocks()) {
                if (bp.equals(pos)) return s;
            }
        }
        return null;
    }

    public boolean removeSpawner(ServerLevel world, BlockPos blockPos) {
        ActiveSpawner spawner = findByBlock(blockPos);
        if (spawner == null) return false;
        clearStructure(world, spawner);
        if (spawner.marker().isAlive()) spawner.marker().discard();
        spawners.remove(spawner);
        return true;
    }

    private void clearStructure(ServerLevel world, ActiveSpawner spawner) {
        for (BlockPos pos : spawner.structureBlocks()) {
            world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    public void removeAll() {
        for (ActiveSpawner s : spawners) {
            if (s.marker() != null && s.marker().isAlive()) s.marker().discard();
        }
        spawners.clear();
    }

    public void killAllMobs() {
        for (Mob mob : aliveMobs) {
            if (mob.isAlive()) mob.discard();
        }
        aliveMobs.clear();
        ravagerBehaviors.clear();
    }

    public List<Mob> getAliveMobs() {
        aliveMobs.removeIf(mob -> !mob.isAlive());
        return aliveMobs;
    }

    public int getSpawnerCount() {
        return spawners.size();
    }

    public int getSpawnerCountForOwner(UUID owner) {
        return getSpawnerCount();
    }

    public int getSpawnerCountForTeam(int teamId) {
        return (int) spawners.stream().filter(s -> s.teamId() == teamId).count();
    }

    public List<ActiveSpawner> getSpawners() {
        return spawners;
    }
}
