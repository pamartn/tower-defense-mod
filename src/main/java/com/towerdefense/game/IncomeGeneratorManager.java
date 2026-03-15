package com.towerdefense.game;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.*;

public class IncomeGeneratorManager {

    public record ActiveGenerator(
            ArmorStand marker,
            IncomeGeneratorType type,
            int teamId,
            BlockPos basePos,
            List<BlockPos> structureBlocks,
            int[] cooldown
    ) {}

    private final List<ActiveGenerator> generators = new ArrayList<>();
    private java.util.function.Function<Integer, MoneyManager> moneyManagerLookup;
    private java.util.function.DoubleSupplier incomeMultiplierForAITeam = () -> 1.0;

    public void setMoneyManagerLookup(java.util.function.Function<Integer, MoneyManager> lookup) {
        this.moneyManagerLookup = lookup;
    }

    public void setIncomeMultiplierForAITeam(java.util.function.DoubleSupplier multiplier) {
        this.incomeMultiplierForAITeam = multiplier != null ? multiplier : () -> 1.0;
    }

    public void placeGenerator(ServerLevel world, BlockPos basePos, IncomeGeneratorType type, int teamId) {
        List<BlockPos> structure = buildStructure(world, basePos, type);

        ArmorStand marker = new ArmorStand(EntityType.ARMOR_STAND, world);
        marker.setPos(basePos.getX() + 0.5, basePos.getY() + 2.5, basePos.getZ() + 0.5);
        marker.setCustomName(Component.literal("\u00a7e" + type.getName()));
        marker.setCustomNameVisible(true);
        marker.setNoGravity(true);
        marker.setInvulnerable(true);
        marker.setInvisible(true);
        marker.setSilent(true);
        world.addFreshEntity(marker);

        generators.add(new ActiveGenerator(marker, type, teamId, basePos, structure, new int[]{type.getIncomeIntervalTicks()}));

        world.playSound(null, basePos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1.0f, 1.2f);
    }

    private List<BlockPos> buildStructure(ServerLevel world, BlockPos base, IncomeGeneratorType type) {
        List<BlockPos> positions = new ArrayList<>();
        world.setBlock(base, type.getTriggerBlock().defaultBlockState(), Block.UPDATE_ALL);
        positions.add(base);
        world.setBlock(base.above(1), Blocks.HOPPER.defaultBlockState(), Block.UPDATE_ALL);
        positions.add(base.above(1));
        world.setBlock(base.above(2), Blocks.GLOWSTONE.defaultBlockState(), Block.UPDATE_ALL);
        positions.add(base.above(2));
        return positions;
    }

    public void tick() {
        Iterator<ActiveGenerator> it = generators.iterator();
        while (it.hasNext()) {
            ActiveGenerator gen = it.next();
            if (!gen.marker().isAlive()) {
                it.remove();
                continue;
            }

            gen.cooldown()[0]--;
            if (gen.cooldown()[0] <= 0) {
                gen.cooldown()[0] = gen.type().getIncomeIntervalTicks();
                if (moneyManagerLookup != null) {
                    MoneyManager mm = moneyManagerLookup.apply(gen.teamId());
                    if (mm != null) {
                        int amount = gen.type().getIncomeAmount();
                        if (gen.teamId() == GameManager.getAITeamId()) {
                            amount = (int) (amount * incomeMultiplierForAITeam.getAsDouble());
                        }
                        mm.addMoney(amount);
                    }
                }
            }
        }
    }

    public ActiveGenerator findByBlock(BlockPos pos) {
        for (ActiveGenerator g : generators) {
            for (BlockPos bp : g.structureBlocks()) {
                if (bp.equals(pos)) return g;
            }
        }
        return null;
    }

    public boolean removeGenerator(ServerLevel world, BlockPos blockPos) {
        ActiveGenerator gen = findByBlock(blockPos);
        if (gen == null) return false;
        clearStructure(world, gen);
        if (gen.marker().isAlive()) gen.marker().discard();
        generators.remove(gen);
        return true;
    }

    private void clearStructure(ServerLevel world, ActiveGenerator gen) {
        for (BlockPos pos : gen.structureBlocks()) {
            world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    public void removeAll() {
        for (ActiveGenerator g : generators) {
            if (g.marker() != null && g.marker().isAlive()) g.marker().discard();
        }
        generators.clear();
    }

    public int getGeneratorCount() {
        return generators.size();
    }

    public int getGeneratorCountForOwner(UUID owner) {
        return getGeneratorCount();
    }

    public int getGeneratorCountForTeam(int teamId) {
        return (int) generators.stream().filter(g -> g.teamId() == teamId).count();
    }

    public int getIncomeRateForOwner(UUID owner) {
        return getGeneratorCount() > 0 ? generators.stream().mapToInt(g -> g.type().getIncomeAmount()).sum() : 0;
    }

    public int getIncomeRateForTeam(int teamId) {
        return generators.stream()
                .filter(g -> g.teamId() == teamId)
                .mapToInt(g -> g.type().getIncomeAmount())
                .sum();
    }

    public List<ActiveGenerator> getGenerators() {
        return generators;
    }
}
