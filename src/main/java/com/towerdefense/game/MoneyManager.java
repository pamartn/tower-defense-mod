package com.towerdefense.game;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.function.Consumer;

public class MoneyManager {

    private int money = 0;
    private ServerPlayer player;
    private Consumer<Integer> onMoneyAdded;

    public void setPlayer(ServerPlayer player) {
        this.player = player;
    }

    /** When set, called instead of single-player notification. Use for team-wide notifications. */
    public void setOnMoneyAdded(Consumer<Integer> callback) {
        this.onMoneyAdded = callback;
    }

    public int getMoney() {
        return money;
    }

    public void addMoney(int amount) {
        money += amount;
        if (onMoneyAdded != null) {
            onMoneyAdded.accept(amount);
        } else if (player != null) {
            HudManager.sendActionBar(player,
                    Component.literal("+$" + amount).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
            player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 1.2f);
        }
    }

    public boolean canAfford(int amount) {
        return money >= amount;
    }

    public boolean spend(int amount) {
        if (money >= amount) {
            money -= amount;
            return true;
        }
        return false;
    }

    public void reset() {
        money = GameConfig.STARTING_MONEY();
    }

    public void resetWithAmount(int amount) {
        money = amount;
    }
}
