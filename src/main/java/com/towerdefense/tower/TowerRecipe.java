package com.towerdefense.tower;

import net.minecraft.world.level.block.Block;

public record TowerRecipe(
        String name,
        TowerType type,
        Block block,
        int power,
        double range,
        int fireRateInTicks,
        int price
) {}
