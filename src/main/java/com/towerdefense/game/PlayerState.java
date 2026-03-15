package com.towerdefense.game;

import com.towerdefense.arena.NexusManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class PlayerState {

    private ServerPlayer player;
    private final TeamState team;
    private final HudManager hudManager = new HudManager();

    public PlayerState(ServerPlayer player, TeamState team) {
        this.player = player;
        this.team = team;
    }

    public int getSide() { return team.getSide(); }
    public ServerPlayer getPlayer() { return player; }
    public void setPlayer(ServerPlayer player) { this.player = player; }
    public UUID getUUID() { return player.getUUID(); }
    public TeamState getTeam() { return team; }
    public NexusManager getNexusManager() { return team.getNexusManager(); }
    public MoneyManager getMoneyManager() { return team.getMoneyManager(); }
    public HudManager getHudManager() { return hudManager; }
    public int getMinZ() { return team.getMinZ(); }
    public int getMaxZ() { return team.getMaxZ(); }
    public BlockPos getNexusCenter() { return team.getNexusCenter(); }
    public BlockPos getEnemyNexusCenter() { return team.getEnemyNexusCenter(); }

    public boolean isInsideHalf(BlockPos pos) {
        return team.isInsideHalf(pos);
    }
}
