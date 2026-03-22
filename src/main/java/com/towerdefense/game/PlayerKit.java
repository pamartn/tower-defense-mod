package com.towerdefense.game;

import com.towerdefense.tower.TowerRecipe;
import com.towerdefense.tower.TowerRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;

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

    public static ItemStack createTowerItem(TowerRecipe recipe) {
        net.minecraft.world.item.Item baseItem = switch (recipe.type()) {
            case BASIC          -> Items.STICK;
            case ARCHER         -> Items.ARROW;
            case CANNON         -> Items.GUNPOWDER;
            case LASER          -> Items.NETHER_STAR;
            case FIRE           -> Items.BLAZE_POWDER;
            case SLOW           -> Items.BLUE_DYE;
            case POISON         -> Items.SPIDER_EYE;
            case SNIPER         -> Items.FEATHER;
            case CHAIN_LIGHTNING -> Items.COPPER_INGOT;
            case AOE            -> Items.FIREWORK_STAR;
        };
        ItemStack stack = new ItemStack(baseItem, 1);

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

        CompoundTag tag = new CompoundTag();
        tag.putString("td_category", "tower");
        tag.putString("td_id", recipe.type().name());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        return stack;
    }
}
