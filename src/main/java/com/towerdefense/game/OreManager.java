package com.towerdefense.game;

import com.towerdefense.config.ConfigManager;
import com.towerdefense.tower.TowerManager;
import com.towerdefense.wave.SpawnerManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.*;

public class OreManager {

    private static final Block ORE_BLOCK = Blocks.GOLD_ORE;

    private final GameManager gameManager;
    private final Map<BlockPos, Integer> ores = new HashMap<>();
    private int spawnCooldown;

    public OreManager(GameManager gameManager) {
        this.gameManager = gameManager;
        this.spawnCooldown = ConfigManager.getInstance().getOreSpawnIntervalTicks();
    }

    public void tick(ServerLevel world, int playingPhaseTicks) {
        spawnCooldown--;
        if (spawnCooldown <= 0) {
            spawnCooldown = ConfigManager.getInstance().getOreSpawnIntervalTicks();
            trySpawnOres(world, playingPhaseTicks);
        }
    }

    private void trySpawnOres(ServerLevel world, int playingPhaseTicks) {
        List<BlockPos> freeTeam1 = new ArrayList<>();
        List<BlockPos> freeTeam2 = new ArrayList<>();
        getFreeFloorPositions(world, freeTeam1, freeTeam2);

        int income = computeIncomeForPhase(playingPhaseTicks);
        Random r = new Random();

        if (!freeTeam1.isEmpty()) {
            BlockPos pos = freeTeam1.get(r.nextInt(freeTeam1.size()));
            placeOre(world, pos, income);
        }
        if (!freeTeam2.isEmpty()) {
            BlockPos pos = freeTeam2.get(r.nextInt(freeTeam2.size()));
            placeOre(world, pos, income);
        }
    }

    private void getFreeFloorPositions(ServerLevel world, List<BlockPos> outTeam1, List<BlockPos> outTeam2) {
        BlockPos origin = GameConfig.arenaOrigin;
        int size = GameConfig.ARENA_SIZE();
        int midline = GameConfig.getMidlineZ();

        Set<BlockPos> occupied = new HashSet<>();
        TowerManager tm = gameManager.getTowerManager();
        if (tm != null) {
            for (var tower : tm.getTowers()) {
                occupied.addAll(tower.structureBlocks());
            }
        }
        for (var spawner : gameManager.getSpawnerManager().getSpawners()) {
            occupied.addAll(spawner.structureBlocks());
        }
        for (var gen : gameManager.getIncomeGeneratorManager().getGenerators()) {
            occupied.addAll(gen.structureBlocks());
        }

        var team1 = gameManager.getTeam1();
        var team2 = gameManager.getTeam2();

        for (int relX = 0; relX < size; relX++) {
            for (int relZ = 0; relZ < size; relZ++) {
                BlockPos abovePos = origin.offset(relX, 1, relZ);
                if (occupied.contains(abovePos)) continue;
                if (!world.getBlockState(abovePos).isAir()) continue;
                if (ores.containsKey(abovePos)) continue;
                if (team1 != null && team1.getNexusManager().isNexusBlock(abovePos)) continue;
                if (team2 != null && team2.getNexusManager().isNexusBlock(abovePos)) continue;

                if (relZ < midline) {
                    outTeam1.add(abovePos);
                } else {
                    outTeam2.add(abovePos);
                }
            }
        }
    }

    private int computeIncomeForPhase(int playingPhaseTicks) {
        int base;
        if (playingPhaseTicks < 1200) {
            base = 7;
        } else if (playingPhaseTicks < 3600) {
            base = 20;
        } else {
            base = 37;
        }
        int bonus = new Random().nextInt(6);
        return Math.min(50, Math.max(5, base + bonus));
    }

    private void placeOre(ServerLevel world, BlockPos pos, int income) {
        world.setBlock(pos, ORE_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        ores.put(pos.immutable(), income);
        world.playSound(null, pos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 0.5f, 1.0f);
    }

    public Integer findOreAt(ServerLevel world, BlockPos pos) {
        Integer income = ores.get(pos);
        if (income == null) return null;
        if (!world.getBlockState(pos).is(ORE_BLOCK)) {
            ores.remove(pos);
            return null;
        }
        return income;
    }

    public void removeOre(ServerLevel world, BlockPos pos) {
        ores.remove(pos);
        world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    public void removeAll(ServerLevel world) {
        for (BlockPos pos : new ArrayList<>(ores.keySet())) {
            removeOre(world, pos);
        }
    }
}
