package com.towerdefense.game;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

/**
 * Four boss bars: each player sees "Your Nexus" (own) and "Opponent's Nexus" (enemy).
 */
public class NexusBossBarManager {

    // Team 1 players see these two
    private final ServerBossEvent ownTeam1 = bar("❤ Your Nexus", BossEvent.BossBarColor.GREEN);
    private final ServerBossEvent enemyTeam1 = bar("☠ Opponent's Nexus", BossEvent.BossBarColor.RED);

    // Team 2 players see these two
    private final ServerBossEvent ownTeam2 = bar("❤ Your Nexus", BossEvent.BossBarColor.GREEN);
    private final ServerBossEvent enemyTeam2 = bar("☠ Opponent's Nexus", BossEvent.BossBarColor.RED);

    private static ServerBossEvent bar(String name, BossEvent.BossBarColor color) {
        return new ServerBossEvent(Component.literal(name), color, BossEvent.BossBarOverlay.NOTCHED_10);
    }

    public void addPlayer(ServerPlayer player, int side) {
        if (side == 1) {
            ownTeam1.addPlayer(player);
            enemyTeam1.addPlayer(player);
        } else {
            ownTeam2.addPlayer(player);
            enemyTeam2.addPlayer(player);
        }
    }

    public void removePlayer(ServerPlayer player) {
        ownTeam1.removePlayer(player);
        enemyTeam1.removePlayer(player);
        ownTeam2.removePlayer(player);
        enemyTeam2.removePlayer(player);
    }

    public void removeAllPlayers() {
        ownTeam1.removeAllPlayers();
        enemyTeam1.removeAllPlayers();
        ownTeam2.removeAllPlayers();
        enemyTeam2.removeAllPlayers();
    }

    public void update(int hp1, int maxHp1, int hp2, int maxHp2) {
        float p1 = ratio(hp1, maxHp1);
        float p2 = ratio(hp2, maxHp2);

        // Team 1 players: own=team1, enemy=team2
        ownTeam1.setProgress(p1);
        ownTeam1.setColor(hpColor(p1));
        ownTeam1.setName(Component.literal("❤ Your Nexus: " + hp1 + "/" + maxHp1));

        enemyTeam1.setProgress(p2);
        enemyTeam1.setColor(hpColor(p2));
        enemyTeam1.setName(Component.literal("☠ Opponent's Nexus: " + hp2 + "/" + maxHp2));

        // Team 2 players: own=team2, enemy=team1
        ownTeam2.setProgress(p2);
        ownTeam2.setColor(hpColor(p2));
        ownTeam2.setName(Component.literal("❤ Your Nexus: " + hp2 + "/" + maxHp2));

        enemyTeam2.setProgress(p1);
        enemyTeam2.setColor(hpColor(p1));
        enemyTeam2.setName(Component.literal("☠ Opponent's Nexus: " + hp1 + "/" + maxHp1));
    }

    private float ratio(int hp, int max) {
        return max > 0 ? Math.max(0, Math.min(1, (float) hp / max)) : 0;
    }

    private BossEvent.BossBarColor hpColor(float ratio) {
        if (ratio > 0.6f) return BossEvent.BossBarColor.GREEN;
        if (ratio > 0.3f) return BossEvent.BossBarColor.YELLOW;
        return BossEvent.BossBarColor.RED;
    }
}
