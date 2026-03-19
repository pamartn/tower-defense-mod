package com.towerdefense.shop;

import com.towerdefense.config.ConfigManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import com.towerdefense.shop.Purchasable;
import java.util.List;

public record WeaponShopItem(String name, Item item, int defaultPrice, String description, boolean enchanted) implements Purchasable {

    private static final List<WeaponShopItem> ITEMS = List.of(
            new WeaponShopItem("Wood Sword", Items.WOODEN_SWORD, 15, "Basic melee", false),
            new WeaponShopItem("Iron Sword", Items.IRON_SWORD, 40, "Decent damage", false),
            new WeaponShopItem("Diamond Sword", Items.DIAMOND_SWORD, 80, "Strong melee", false),
            new WeaponShopItem("Netherite Sword", Items.NETHERITE_SWORD, 150, "Very strong", false),
            new WeaponShopItem("Ench. Netherite", Items.NETHERITE_SWORD, 300, "Sharpness V", true)
    );

    public int price() {
        return ConfigManager.getInstance().getWeaponPrice(name);
    }

    @Override
    public int getPrice() { return price(); }

    public int getDefaultTier() {
        return switch (name) {
            case "Wood Sword" -> 1;
            case "Iron Sword", "Diamond Sword" -> 2;
            case "Netherite Sword", "Ench. Netherite" -> 3;
            default -> 1;
        };
    }

    public int getTier() {
        return ConfigManager.getInstance().getWeaponTier(this);
    }

    public static List<WeaponShopItem> getAll() {
        return ITEMS;
    }

    public static List<WeaponShopItem> getAllSortedByPrice() {
        return ITEMS.stream().sorted((a, b) -> Integer.compare(a.price(), b.price())).toList();
    }
}
