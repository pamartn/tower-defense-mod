package com.towerdefense.game;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

/**
 * Manages two boss bars shown at the top of the screen for each player:
 * one for Team 1's nexus and one for Team 2's nexus.
 */
public class NexusBossBarManager {

    private final ServerBossEvent bar1 = new ServerBossEvent(
            Component.literal("❤ Nexus 1"),
            BossEvent.BossBarColor.GREEN,
            BossEvent.BossBarOverlay.NOTCHED_10
    );
    private final ServerBossEvent bar2 = new ServerBossEvent(
            Component.literal("❤ Nexus 2"),
            BossEvent.BossBarColor.PINK,
            BossEvent.BossBarOverlay.NOTCHED_10
    );

    public void addPlayer(ServerPlayer player) {
        bar1.addPlayer(player);
        bar2.addPlayer(player);
    }

    public void removePlayer(ServerPlayer player) {
        bar1.removePlayer(player);
        bar2.removePlayer(player);
    }

    public void removeAllPlayers() {
        bar1.removeAllPlayers();
        bar2.removeAllPlayers();
    }

    public void update(int hp1, int maxHp1, int hp2, int maxHp2) {
        float p1 = maxHp1 > 0 ? Math.max(0, Math.min(1, (float) hp1 / maxHp1)) : 0;
        float p2 = maxHp2 > 0 ? Math.max(0, Math.min(1, (float) hp2 / maxHp2)) : 0;

        bar1.setProgress(p1);
        bar2.setProgress(p2);
        bar1.setColor(hpColor(p1));
        bar2.setColor(hpColor(p2));
        bar1.setName(Component.literal("❤ Nexus 1: " + hp1 + "/" + maxHp1));
        bar2.setName(Component.literal("❤ Nexus 2: " + hp2 + "/" + maxHp2));
    }

    private BossEvent.BossBarColor hpColor(float ratio) {
        if (ratio > 0.6f) return BossEvent.BossBarColor.GREEN;
        if (ratio > 0.3f) return BossEvent.BossBarColor.YELLOW;
        return BossEvent.BossBarColor.RED;
    }
}
