package com.towerdefense.pathfinding;

import com.towerdefense.game.GameConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

public class PathValidator {

    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    public boolean hasPath(ServerLevel world, BlockPos from, BlockPos to) {
        BlockPos origin = GameConfig.arenaOrigin;
        int size = GameConfig.ARENA_SIZE();

        int sx = from.getX() - origin.getX();
        int sz = from.getZ() - origin.getZ();
        int tx = to.getX() - origin.getX();
        int tz = to.getZ() - origin.getZ();

        boolean[][] blocked = buildBlockedGrid(world, origin, size);

        clearNexusArea(blocked, GameConfig.getPlayer1NexusCenter(), origin, size);
        clearNexusArea(blocked, GameConfig.getPlayer2NexusCenter(), origin, size);

        return bfs(blocked, size, sx, sz, tx, tz);
    }

    public boolean hasPath(ServerLevel world) {
        return hasPath(world, GameConfig.getSpawnCorner(), GameConfig.getNexusCenter());
    }

    private boolean[][] buildBlockedGrid(ServerLevel world, BlockPos origin, int size) {
        boolean[][] blocked = new boolean[size][size];
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                BlockPos check = origin.offset(x, 1, z);
                BlockState state = world.getBlockState(check);
                blocked[x][z] = !state.isAir() && state.getCollisionShape(world, check) != net.minecraft.world.phys.shapes.Shapes.empty();

                if (!blocked[x][z]) {
                    BlockPos check2 = origin.offset(x, 2, z);
                    BlockState state2 = world.getBlockState(check2);
                    blocked[x][z] = !state2.isAir() && state2.getCollisionShape(world, check2) != net.minecraft.world.phys.shapes.Shapes.empty();
                }
            }
        }
        return blocked;
    }

    private void clearNexusArea(boolean[][] blocked, BlockPos nexusCenter, BlockPos origin, int size) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int cx = nexusCenter.getX() - origin.getX() + dx;
                int cz = nexusCenter.getZ() - origin.getZ() + dz;
                if (cx >= 0 && cx < size && cz >= 0 && cz < size) {
                    blocked[cx][cz] = false;
                }
            }
        }
    }

    private boolean bfs(boolean[][] blocked, int size, int sx, int sz, int tx, int tz) {
        if (sx < 0 || sx >= size || sz < 0 || sz >= size) return false;
        if (tx < 0 || tx >= size || tz < 0 || tz >= size) return false;
        if (blocked[sx][sz] || blocked[tx][tz]) return false;

        Set<Long> visited = new HashSet<>();
        Queue<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{sx, sz});
        visited.add(key(sx, sz));

        while (!queue.isEmpty()) {
            int[] pos = queue.poll();
            if (pos[0] == tx && pos[1] == tz) return true;

            for (int[] dir : DIRS) {
                int nx = pos[0] + dir[0];
                int nz = pos[1] + dir[1];
                if (nx < 0 || nx >= size || nz < 0 || nz >= size) continue;
                if (blocked[nx][nz]) continue;
                long k = key(nx, nz);
                if (visited.contains(k)) continue;
                visited.add(k);
                queue.add(new int[]{nx, nz});
            }
        }

        return false;
    }

    private long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
