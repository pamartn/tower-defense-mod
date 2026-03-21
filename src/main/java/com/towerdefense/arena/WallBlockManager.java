package com.towerdefense.arena;

import com.towerdefense.game.GameConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks player-placed wall blocks for fireball damage. Each block has HP based on tier:
 * wool=1, oak=2, cobblestone=3. Fireball reduces HP by 1 per hit.
 */
public class WallBlockManager {

    private static final int WALL_TOWER_HEIGHT = 2;

    private final Map<BlockPos, WallBlockInfo> blocks = new HashMap<>();

    public void registerBlock(BlockPos pos, int teamId, int tier) {
        blocks.put(pos.immutable(), new WallBlockInfo(teamId, tier));
    }

    public void registerTower(ServerLevel world, BlockPos base, int teamId, int tier) {
        for (int dy = 0; dy < WALL_TOWER_HEIGHT; dy++) {
            BlockPos p = base.above(dy).immutable();
            if (GameConfig.isInsideArena(p)) {
                registerBlock(p, teamId, tier);
            }
        }
    }

    public void onFireballImpact(ServerLevel world, BlockPos impactPos, int casterTeamId, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radius * radius) continue;
                    BlockPos check = impactPos.offset(dx, dy, dz).immutable();
                    WallBlockInfo info = blocks.get(check);
                    if (info == null || info.teamId == casterTeamId) continue;

                    info.hp--;
                    if (info.hp <= 0) {
                        blocks.remove(check);
                        world.setBlock(check, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
        }
    }

    public void removeBlock(BlockPos pos) {
        blocks.remove(pos.immutable());
    }

    public void clearAll() {
        blocks.clear();
    }

    public java.util.List<int[]> getWallXZByTeam(int teamId) {
        java.util.Set<String> seen = new java.util.HashSet<>();
        java.util.List<int[]> result = new java.util.ArrayList<>();
        for (var entry : blocks.entrySet()) {
            if (entry.getValue().teamId == teamId) {
                BlockPos p = entry.getKey();
                String key = p.getX() + "," + p.getZ();
                if (seen.add(key)) {
                    result.add(new int[]{p.getX(), p.getZ()});
                }
            }
        }
        return result;
    }

    public static int getTowerHeight() {
        return WALL_TOWER_HEIGHT;
    }

    private static class WallBlockInfo {
        final int teamId;
        int hp;

        WallBlockInfo(int teamId, int tier) {
            this.teamId = teamId;
            this.hp = tier;
        }
    }
}
