package com.towerdefense.shop;

import com.towerdefense.config.ConfigManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import com.towerdefense.shop.Purchasable;
import java.util.List;

public record WallShopItem(String name, Block block, int defaultPrice) implements Purchasable {

    private static final List<WallShopItem> ITEMS = List.of(
            new WallShopItem("Wool Wall", Blocks.WHITE_WOOL, 4),
            new WallShopItem("Oak Planks Wall", Blocks.OAK_PLANKS, 6),
            new WallShopItem("Cobblestone Wall", Blocks.COBBLESTONE, 10)
    );

    public int price() {
        return ConfigManager.getInstance().getWallPrice(name);
    }

    @Override
    public int getPrice() { return price(); }

    public int getDefaultTier() {
        return switch (name) {
            case "Wool Wall" -> 1;
            case "Oak Planks Wall" -> 2;
            case "Cobblestone Wall" -> 3;
            default -> 1;
        };
    }

    public int getTier() {
        return ConfigManager.getInstance().getWallTier(this);
    }

    public static WallShopItem findByBlock(net.minecraft.world.level.block.Block block) {
        for (WallShopItem w : ITEMS) {
            if (w.block() == block) return w;
        }
        return null;
    }

    public static WallShopItem findByName(String name) {
        for (WallShopItem w : ITEMS) {
            if (w.name().equals(name)) return w;
        }
        return null;
    }

    public static List<WallShopItem> getAll() {
        return ITEMS;
    }

    public static List<WallShopItem> getAllSortedByPrice() {
        return ITEMS.stream().sorted((a, b) -> Integer.compare(a.price(), b.price())).toList();
    }
}
