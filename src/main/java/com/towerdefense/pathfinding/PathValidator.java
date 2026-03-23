package com.towerdefense.pathfinding;

import com.towerdefense.game.GameConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

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

    /**
     * Computes the full BFS path from {@code from} to {@code to} through the arena grid,
     * respecting all solid blocks (towers, walls, and any other placed blocks).
     *
     * @return ordered list of world BlockPos waypoints (Y = arena floor + 1), or null if no path exists
     */
    public List<BlockPos> computePath(ServerLevel world, BlockPos from, BlockPos to) {
        BlockPos origin = GameConfig.arenaOrigin;
        int size = GameConfig.ARENA_SIZE();

        // Clamp to grid bounds — mobs may start just outside the arena
        int sx = Math.max(0, Math.min(size - 1, from.getX() - origin.getX()));
        int sz = Math.max(0, Math.min(size - 1, from.getZ() - origin.getZ()));
        int tx = Math.max(0, Math.min(size - 1, to.getX() - origin.getX()));
        int tz = Math.max(0, Math.min(size - 1, to.getZ() - origin.getZ()));

        boolean[][] blocked = buildBlockedGrid(world, origin, size);
        clearNexusArea(blocked, GameConfig.getPlayer1NexusCenter(), origin, size);
        clearNexusArea(blocked, GameConfig.getPlayer2NexusCenter(), origin, size);

        if (blocked[tx][tz]) return null;

        // BFS with parent tracking (index = x*size + z)
        int[] parent = new int[size * size];
        Arrays.fill(parent, -1);
        int startKey = sx * size + sz;
        int endKey   = tx * size + tz;
        parent[startKey] = startKey; // self-reference marks start as visited

        if (startKey == endKey) {
            List<BlockPos> single = new ArrayList<>();
            single.add(origin.offset(sx, 1, sz));
            return single;
        }

        Queue<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{sx, sz});
        boolean found = false;

        outer:
        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            for (int[] dir : DIRS) {
                int nx = cur[0] + dir[0];
                int nz = cur[1] + dir[1];
                if (nx < 0 || nx >= size || nz < 0 || nz >= size) continue;
                if (blocked[nx][nz]) continue;
                int nk = nx * size + nz;
                if (parent[nk] != -1) continue;
                parent[nk] = cur[0] * size + cur[1];
                if (nk == endKey) { found = true; break outer; }
                queue.add(new int[]{nx, nz});
            }
        }

        if (!found) return null;

        // Reconstruct path from end back to start, then reverse
        List<BlockPos> path = new ArrayList<>();
        int cx = tx, cz = tz;
        while (cx != sx || cz != sz) {
            path.add(origin.offset(cx, 1, cz));
            int pk = parent[cx * size + cz];
            cx = pk / size;
            cz = pk % size;
        }
        path.add(origin.offset(sx, 1, sz));
        Collections.reverse(path);
        return path;
    }

    private boolean[][] buildBlockedGrid(ServerLevel world, BlockPos origin, int size) {
        boolean[][] blocked = new boolean[size][size];
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                BlockPos check = origin.offset(x, 1, z);
                BlockState state = world.getBlockState(check);
                blocked[x][z] = !state.isAir() &&
                    state.getCollisionShape(world, check) != net.minecraft.world.phys.shapes.Shapes.empty();

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
