package com.towerdefense.game;

import com.towerdefense.tower.TowerRecipe;
import com.towerdefense.tower.TowerRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;

public class PlayerKit {

    private TowerRegistry towerRegistry;

    public void setTowerRegistry(TowerRegistry registry) {
        this.towerRegistry = registry;
    }

    public void giveStartingKit(ServerPlayer player) {
        player.getInventory().clearContent();
        player.getInventory().add(new ItemStack(Items.DIAMOND_PICKAXE));
    }

    public void setupPlayer(ServerPlayer player) {
        player.setGameMode(GameType.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(20.0f);
        player.setInvulnerable(true);
    }

    public void resetPlayer(ServerPlayer player) {
        player.setInvulnerable(false);
    }

    public void respawnPlayer(ServerPlayer player) {
        var center = GameConfig.getArenaCenter();
        player.teleportTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
    }

    public static ItemStack createTowerBlock(TowerRecipe recipe) {
        Block block = recipe.block();
        ItemStack stack = new ItemStack(block.asItem(), 1);

        ChatFormatting color = switch (recipe.type()) {
            case BASIC -> ChatFormatting.WHITE;
            case ARCHER -> ChatFormatting.GREEN;
            case CANNON -> ChatFormatting.RED;
            case LASER -> ChatFormatting.AQUA;
            case FIRE -> ChatFormatting.GOLD;
            case SLOW -> ChatFormatting.BLUE;
            case POISON -> ChatFormatting.DARK_GREEN;
            case SNIPER -> ChatFormatting.GRAY;
            case CHAIN_LIGHTNING -> ChatFormatting.YELLOW;
            case AOE -> ChatFormatting.DARK_RED;
        };

        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal(recipe.name() + " ($" + recipe.price() + ")").withStyle(color, ChatFormatting.BOLD));
        stack.set(DataComponents.MAX_STACK_SIZE, 1);

        return stack;
    }
}
