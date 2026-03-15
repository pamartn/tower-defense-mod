package com.towerdefense.tower;

import com.towerdefense.config.ConfigManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.*;

public class TowerRegistry {

    private final Map<Block, TowerRecipe> blockToTower = new HashMap<>();
    private final List<TowerRecipe> recipes = new ArrayList<>();

    public void registerDefaults() {
        var cfg = ConfigManager.getInstance();
        blockToTower.clear();
        recipes.clear();
        addRecipe(new TowerRecipe("Basic Tower", TowerType.BASIC, Blocks.DIRT,
                cfg.getTowerPower(TowerType.BASIC), cfg.getTowerRange(TowerType.BASIC),
                cfg.getTowerFireRate(TowerType.BASIC), cfg.getTowerPrice(TowerType.BASIC)));
        addRecipe(new TowerRecipe("Archer Tower", TowerType.ARCHER, Blocks.OAK_LOG,
                cfg.getTowerPower(TowerType.ARCHER), cfg.getTowerRange(TowerType.ARCHER),
                cfg.getTowerFireRate(TowerType.ARCHER), cfg.getTowerPrice(TowerType.ARCHER)));
        addRecipe(new TowerRecipe("Cannon Tower", TowerType.CANNON, Blocks.STONE,
                cfg.getTowerPower(TowerType.CANNON), cfg.getTowerRange(TowerType.CANNON),
                cfg.getTowerFireRate(TowerType.CANNON), cfg.getTowerPrice(TowerType.CANNON)));
        addRecipe(new TowerRecipe("Laser Tower", TowerType.LASER, Blocks.DIAMOND_BLOCK,
                cfg.getTowerPower(TowerType.LASER), cfg.getTowerRange(TowerType.LASER),
                cfg.getTowerFireRate(TowerType.LASER), cfg.getTowerPrice(TowerType.LASER)));
        addRecipe(new TowerRecipe("Fire Tower", TowerType.FIRE, Blocks.NETHERRACK,
                cfg.getTowerPower(TowerType.FIRE), cfg.getTowerRange(TowerType.FIRE),
                cfg.getTowerFireRate(TowerType.FIRE), cfg.getTowerPrice(TowerType.FIRE)));
        addRecipe(new TowerRecipe("Slow Tower", TowerType.SLOW, Blocks.BLUE_ICE,
                cfg.getTowerPower(TowerType.SLOW), cfg.getTowerRange(TowerType.SLOW),
                cfg.getTowerFireRate(TowerType.SLOW), cfg.getTowerPrice(TowerType.SLOW)));
        addRecipe(new TowerRecipe("Poison Tower", TowerType.POISON, Blocks.SLIME_BLOCK,
                cfg.getTowerPower(TowerType.POISON), cfg.getTowerRange(TowerType.POISON),
                cfg.getTowerFireRate(TowerType.POISON), cfg.getTowerPrice(TowerType.POISON)));
        addRecipe(new TowerRecipe("Sniper Tower", TowerType.SNIPER, Blocks.COPPER_BLOCK,
                cfg.getTowerPower(TowerType.SNIPER), cfg.getTowerRange(TowerType.SNIPER),
                cfg.getTowerFireRate(TowerType.SNIPER), cfg.getTowerPrice(TowerType.SNIPER)));
        addRecipe(new TowerRecipe("Lightning Tower", TowerType.CHAIN_LIGHTNING, Blocks.LIGHTNING_ROD,
                cfg.getTowerPower(TowerType.CHAIN_LIGHTNING), cfg.getTowerRange(TowerType.CHAIN_LIGHTNING),
                cfg.getTowerFireRate(TowerType.CHAIN_LIGHTNING), cfg.getTowerPrice(TowerType.CHAIN_LIGHTNING)));
        addRecipe(new TowerRecipe("AOE Tower", TowerType.AOE, Blocks.TNT,
                cfg.getTowerPower(TowerType.AOE), cfg.getTowerRange(TowerType.AOE),
                cfg.getTowerFireRate(TowerType.AOE), cfg.getTowerPrice(TowerType.AOE)));
    }

    public void reloadFromConfig() {
        registerDefaults();
    }

    private void addRecipe(TowerRecipe recipe) {
        recipes.add(recipe);
        blockToTower.put(recipe.block(), recipe);
    }

    public Optional<TowerRecipe> findByBlock(Block block) {
        return Optional.ofNullable(blockToTower.get(block));
    }

    public List<TowerRecipe> getRecipes() {
        return Collections.unmodifiableList(recipes);
    }

    public List<TowerRecipe> getRecipesSortedByPrice() {
        return recipes.stream().sorted((a, b) -> Integer.compare(a.price(), b.price())).toList();
    }
}
