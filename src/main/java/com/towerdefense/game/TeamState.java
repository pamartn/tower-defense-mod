package com.towerdefense.game;

import com.towerdefense.arena.NexusManager;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a team (1 or 2) with shared resources: money, nexus, zone.
 * Multiple players can be in the same team.
 */
public class TeamState {

    private final int side;
    private final List<UUID> members = new ArrayList<>();
    private final NexusManager nexusManager = new NexusManager();
    private final MoneyManager moneyManager = new MoneyManager();
    private final BlockPos nexusCenter;
    private final BlockPos enemyNexusCenter;
    private final int minZ;
    private final int maxZ;

    public TeamState(int side, BlockPos nexusCenter, BlockPos enemyNexusCenter) {
        this.side = side;
        this.nexusCenter = nexusCenter;
        this.enemyNexusCenter = enemyNexusCenter;

        if (side == 1) {
            this.minZ = 0;
            this.maxZ = GameConfig.ARENA_SIZE() / 2 - 1;
        } else {
            this.minZ = GameConfig.ARENA_SIZE() / 2;
            this.maxZ = GameConfig.ARENA_SIZE() - 1;
        }
    }

    public int getSide() { return side; }
    public List<UUID> getMembers() { return new ArrayList<>(members); }
    public boolean contains(UUID uuid) { return members.contains(uuid); }
    public int getMemberCount() { return members.size(); }

    public void addMember(UUID uuid) {
        if (!members.contains(uuid)) members.add(uuid);
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
    }

    public NexusManager getNexusManager() { return nexusManager; }
    public MoneyManager getMoneyManager() { return moneyManager; }
    public BlockPos getNexusCenter() { return nexusCenter; }
    public BlockPos getEnemyNexusCenter() { return enemyNexusCenter; }
    public int getMinZ() { return minZ; }
    public int getMaxZ() { return maxZ; }

    public boolean isInsideHalf(BlockPos pos) {
        int relZ = pos.getZ() - GameConfig.arenaOrigin.getZ();
        return relZ >= minZ && relZ <= maxZ;
    }
}
