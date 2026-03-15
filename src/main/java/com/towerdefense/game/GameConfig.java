package com.towerdefense.game;

import com.towerdefense.config.ConfigManager;
import net.minecraft.core.BlockPos;

public final class GameConfig {

    private GameConfig() {}

    public static int ARENA_SIZE() { return ConfigManager.getInstance().getArenaSize(); }
    public static int ARENA_WALL_HEIGHT() { return ConfigManager.getInstance().getArenaWallHeight(); }
    public static int ARENA_Y() { return ConfigManager.getInstance().getArenaY(); }
    public static int STAND_DEPTH() { return ConfigManager.getInstance().getStandDepth(); }
    public static int STAND_HEIGHT() { return ConfigManager.getInstance().getStandHeight(); }
    public static int NEXUS_MAX_HP() { return ConfigManager.getInstance().getNexusMaxHp(); }
    public static double NEXUS_EXPLOSION_RADIUS() { return ConfigManager.getInstance().getNexusExplosionRadius(); }
    public static int PREP_PHASE_TICKS() { return ConfigManager.getInstance().getPrepPhaseTicks(); }
    public static int STARTING_MONEY() { return ConfigManager.getInstance().getStartingMoney(); }
    public static int BASE_PASSIVE_INCOME() { return ConfigManager.getInstance().getBasePassiveIncome(); }
    public static int BASE_PASSIVE_INTERVAL() { return ConfigManager.getInstance().getBasePassiveInterval(); }
    @Deprecated public static final int WAVE_BREAK_TICKS = 15 * 20;
    @Deprecated public static final int SPAWN_INTERVAL_TICKS = 15;
    @Deprecated public static final int BASE_MOB_COUNT = 5;
    @Deprecated public static final double MOB_COUNT_SCALING = 1.4;
    @Deprecated public static final double MOB_HP_SCALING = 0.12;
    public static int DEFEAT_DELAY_TICKS() { return ConfigManager.getInstance().getDefeatDelayTicks(); }
    public static int CHAIN_EXPLOSION_DELAY() { return ConfigManager.getInstance().getChainExplosionDelay(); }
    public static final int PATH_CHECK_MARGIN = 1;
    public static final int CONFINEMENT_CHECK_INTERVAL = 10;

    public static BlockPos arenaOrigin = BlockPos.ZERO;

    public static BlockPos getPlayer1NexusCenter() {
        return arenaOrigin.offset(ARENA_SIZE() / 2, 1, 2);
    }

    public static BlockPos getPlayer2NexusCenter() {
        return arenaOrigin.offset(ARENA_SIZE() / 2, 1, ARENA_SIZE() - 3);
    }

    public static int getMidlineZ() {
        return ARENA_SIZE() / 2;
    }

    public static BlockPos getArenaCenter() {
        return arenaOrigin.offset(ARENA_SIZE() / 2, 1, ARENA_SIZE() / 2);
    }

    public static BlockPos getPlayer1SpawnPoint() {
        return arenaOrigin.offset(ARENA_SIZE() / 2, 1, ARENA_SIZE() / 4);
    }

    public static BlockPos getPlayer2SpawnPoint() {
        return arenaOrigin.offset(ARENA_SIZE() / 2, 1, ARENA_SIZE() * 3 / 4);
    }

    public static boolean isInsideArena(BlockPos pos) {
        int relX = pos.getX() - arenaOrigin.getX();
        int relZ = pos.getZ() - arenaOrigin.getZ();
        return relX >= 0 && relX < ARENA_SIZE() && relZ >= 0 && relZ < ARENA_SIZE()
                && pos.getY() >= ARENA_Y() && pos.getY() <= ARENA_Y() + 10;
    }

    public static boolean isInsidePlayerHalf(BlockPos pos, int side) {
        int relZ = pos.getZ() - arenaOrigin.getZ();
        if (side == 1) {
            return relZ >= 0 && relZ < getMidlineZ();
        } else {
            return relZ >= getMidlineZ() && relZ < ARENA_SIZE();
        }
    }

    @Deprecated
    public static BlockPos getSpawnCorner() {
        return arenaOrigin.offset(2, 1, 2);
    }

    @Deprecated
    public static BlockPos getNexusCenter() {
        return getPlayer1NexusCenter();
    }
}
