package com.towerdefense.spell;

import com.towerdefense.config.ConfigManager;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;

import com.towerdefense.shop.Purchasable;
import java.util.List;

public enum SpellType implements Purchasable {

    FIREBALL("Fireball", 200, Items.FIRE_CHARGE, "Destroys enemy buildings"),
    FREEZE_BOMB("Freeze Bomb", 150, Items.SNOWBALL, "Freeze enemy mobs 5s"),
    HEAL_NEXUS("Heal Nexus", 150, Items.GOLDEN_APPLE, "Restore 15 nexus HP"),
    LIGHTNING("Lightning", 100, Items.TRIDENT, "Kill mobs in area"),
    SHIELD("Shield", 300, Items.SHIELD, "Nexus immune 10s");

    private final String name;
    private final int defaultPrice;
    private final Item item;
    private final String description;

    SpellType(String name, int price, Item item, String description) {
        this.name = name;
        this.defaultPrice = price;
        this.item = item;
        this.description = description;
    }

    public String getName() { return name; }
    public int getDefaultPrice() { return defaultPrice; }
    public int getPrice() { return ConfigManager.getInstance().getSpellPrice(this); }
    public Item getItem() { return item; }
    public String getDescription() { return description; }

    public int getDefaultTier() {
        return switch (this) {
            case LIGHTNING -> 1;
            case FREEZE_BOMB, FIREBALL -> 2;
            case HEAL_NEXUS -> 2;
            case SHIELD -> 3;
        };
    }

    public int getTier() {
        return ConfigManager.getInstance().getSpellTier(this);
    }

    public static List<SpellType> getAll() {
        return List.of(values());
    }

    public static List<SpellType> getAllSortedByPrice() {
        return java.util.Arrays.stream(values()).sorted((a, b) -> Integer.compare(a.getPrice(), b.getPrice())).toList();
    }
}
