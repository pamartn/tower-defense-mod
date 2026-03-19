package com.towerdefense.game;

import com.towerdefense.config.ConfigManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import com.towerdefense.shop.Purchasable;
import java.util.List;

public enum IncomeGeneratorType implements Purchasable {

    BASIC("Basic Generator", 25, 2, 10 * 20, Blocks.GOLD_BLOCK),
    ADVANCED("Advanced Generator", 60, 5, 10 * 20, Blocks.EMERALD_BLOCK),
    ELITE("Elite Generator", 150, 15, 10 * 20, Blocks.NETHERITE_BLOCK);

    private final String name;
    private final int defaultPrice;
    private final int defaultIncomeAmount;
    private final int defaultIncomeIntervalTicks;
    private final Block triggerBlock;

    IncomeGeneratorType(String name, int price, int incomeAmount, int incomeIntervalTicks, Block triggerBlock) {
        this.name = name;
        this.defaultPrice = price;
        this.defaultIncomeAmount = incomeAmount;
        this.defaultIncomeIntervalTicks = incomeIntervalTicks;
        this.triggerBlock = triggerBlock;
    }

    public String getName() { return name; }
    public int getDefaultPrice() { return defaultPrice; }
    public int getDefaultIncomeAmount() { return defaultIncomeAmount; }
    public int getDefaultIncomeIntervalTicks() { return defaultIncomeIntervalTicks; }
    public int getPrice() { return ConfigManager.getInstance().getGeneratorPrice(this); }
    public int getIncomeAmount() { return ConfigManager.getInstance().getGeneratorIncomeAmount(this); }
    public int getIncomeIntervalTicks() { return ConfigManager.getInstance().getGeneratorIncomeInterval(this); }
    public Block getTriggerBlock() { return triggerBlock; }

    public static List<IncomeGeneratorType> getAll() {
        return List.of(values());
    }

    public static List<IncomeGeneratorType> getAllSortedByPrice() {
        return java.util.Arrays.stream(values()).sorted((a, b) -> Integer.compare(a.getPrice(), b.getPrice())).toList();
    }

    public int getDefaultTier() {
        return switch (this) {
            case BASIC -> 1;
            case ADVANCED -> 2;
            case ELITE -> 3;
        };
    }

    public int getTier() {
        return ConfigManager.getInstance().getGeneratorTier(this);
    }

    public static IncomeGeneratorType findByBlock(Block block) {
        for (IncomeGeneratorType type : values()) {
            if (type.triggerBlock == block) return type;
        }
        return null;
    }
}
