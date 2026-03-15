package com.towerdefense.ai;

import com.towerdefense.TowerDefenseMod;
import com.towerdefense.game.*;
import com.towerdefense.spell.SpellManager;
import com.towerdefense.spell.SpellType;
import com.towerdefense.shop.WallShopItem;
import com.towerdefense.tower.TowerManager;
import com.towerdefense.tower.TowerRecipe;
import com.towerdefense.tower.TowerRegistry;
import com.towerdefense.wave.SpawnerManager;
import com.towerdefense.wave.SpawnerType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;

import java.util.*;

public class SoloAI {

    private static final int TICK_INTERVAL = 100;
    private static final int SPELL_COOLDOWN_TICKS = 200;

    private final GameManager gameManager;
    private final int teamId = GameManager.getAITeamId();
    private int tickAccumulator;
    private final Map<SpellType, Integer> spellCooldowns = new HashMap<>();
    private final Random random = new Random();

    public SoloAI(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    public void tick(ServerLevel world) {
        if (world == null || !gameManager.isSoloMode()) return;
        if (gameManager.getState() != GameManager.GameState.PREP_PHASE
                && gameManager.getState() != GameManager.GameState.PLAYING) return;

        tickAccumulator++;
        if (tickAccumulator < TICK_INTERVAL) return;
        tickAccumulator = 0;

        for (SpellType st : SpellType.values()) {
            int cd = spellCooldowns.getOrDefault(st, 0);
            if (cd > 0) spellCooldowns.put(st, cd - TICK_INTERVAL);
        }

        var team2 = gameManager.getTeam2();
        if (team2 == null) return;

        var moneyManager = team2.getMoneyManager();
        int money = moneyManager.getMoney();
        if (money < 10) return;

        int incomeBudget = (int) (money * 0.15);
        int defenseBudget = (int) (money * 0.35);
        int attackBudget = (int) (money * 0.50);

        tryBuyIncome(world, moneyManager, incomeBudget);
        tryBuyDefense(world, moneyManager, defenseBudget);
        tryBuyAttack(world, moneyManager, attackBudget);
    }

    private void tryBuyIncome(ServerLevel world, MoneyManager mm, int budget) {
        if (budget < 25) return;
        var gens = IncomeGeneratorType.getAllSortedByPrice();
        int tier = gameManager.getTierManager().getCurrentTier(teamId);

        for (IncomeGeneratorType gen : gens) {
            if (gen.getTier() > tier) continue;
            int cost = gen.getPrice();
            if (cost > budget || !mm.canAfford(cost)) continue;

            BlockPos pos = findValidPlacementPosition(world, 4);
            if (pos == null) continue;

            if (!mm.spend(cost)) continue;
            gameManager.getIncomeGeneratorManager().placeGenerator(world, pos, gen, teamId);
            if (gameManager.checkPathBlockedForTeam(teamId)) {
                gameManager.getIncomeGeneratorManager().removeGenerator(world, pos);
                mm.addMoney(cost);
            }
            return;
        }
    }

    private void tryBuyDefense(ServerLevel world, MoneyManager mm, int budget) {
        if (budget < 10) return;
        int tier = gameManager.getTierManager().getCurrentTier(teamId);

        if (random.nextBoolean() && budget >= 25) {
            var recipes = TowerDefenseMod.getInstance().getTowerRegistry().getRecipesSortedByPrice();
            for (TowerRecipe recipe : recipes) {
                if (recipe.type().getTier() > tier) continue;
                if (recipe.price() > budget || !mm.canAfford(recipe.price())) continue;

                BlockPos pos = findValidPlacementPosition(world, 4);
                if (pos == null) continue;

                if (!mm.spend(recipe.price())) continue;
                TowerManager tm = gameManager.getTowerManager();
                if (tm != null) {
                    tm.spawnTower(world, pos, recipe, teamId);
                    if (gameManager.checkPathBlockedForTeam(teamId)) {
                        var tower = tm.findTowerByBlock(pos);
                        if (tower != null) tm.clearAndRemoveTower(tower);
                        mm.addMoney(recipe.price());
                    }
                }
                return;
            }
        }

        var walls = WallShopItem.getAllSortedByPrice();
        for (WallShopItem wall : walls) {
            if (wall.getTier() > tier) continue;
            if (wall.price() > budget || !mm.canAfford(wall.price())) continue;

            BlockPos pos = findValidPlacementPosition(world, 4);
            if (pos == null) continue;

            if (!mm.spend(wall.price())) continue;
            for (int dy = 0; dy < 4; dy++) {
                BlockPos p = pos.above(dy);
                if (GameConfig.isInsideArena(p)) {
                    world.setBlock(p, wall.block().defaultBlockState(), Block.UPDATE_ALL);
                }
            }
            gameManager.getWallBlockManager().registerTower(world, pos, teamId, wall.getTier());
            if (gameManager.checkPathBlockedForTeam(teamId)) {
                for (int dy = 0; dy < 4; dy++) {
                    BlockPos p = pos.above(dy);
                    world.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    gameManager.getWallBlockManager().removeBlock(p.immutable());
                }
                mm.addMoney(wall.price());
            }
            return;
        }
    }

    private void tryBuyAttack(ServerLevel world, MoneyManager mm, int budget) {
        if (budget < 10) return;
        int tier = gameManager.getTierManager().getCurrentTier(teamId);

        if (random.nextBoolean()) {
            var spawners = SpawnerType.getAllSortedByPrice();
            for (SpawnerType spawner : spawners) {
                if (spawner.getTier() > tier) continue;
                if (spawner.getPrice() > budget || !mm.canAfford(spawner.getPrice())) continue;

                BlockPos pos = findValidPlacementPosition(world, 4);
                if (pos == null) continue;

                if (!mm.spend(spawner.getPrice())) continue;
                var team2 = gameManager.getTeam2();
                gameManager.getSpawnerManager().placeSpawner(world, pos, spawner, teamId, team2.getEnemyNexusCenter());
                if (gameManager.checkPathBlockedForTeam(teamId)) {
                    gameManager.getSpawnerManager().removeSpawner(world, pos);
                    mm.addMoney(spawner.getPrice());
                }
                return;
            }
        }

        var spells = SpellType.getAllSortedByPrice();
        for (SpellType spell : spells) {
            if (spell.getTier() > tier) continue;
            if (spell.getPrice() > budget || !mm.canAfford(spell.getPrice())) continue;
            if (spellCooldowns.getOrDefault(spell, 0) > 0) continue;

            SpellManager sm = gameManager.getSpellManager();
            if (sm.castSpellForTeam(world, teamId, spell)) {
                spellCooldowns.put(spell, SPELL_COOLDOWN_TICKS);
                return;
            }
        }

        if (budget >= 200 && tier < 3) {
            int targetTier = tier + 1;
            int cost = gameManager.getTierManager().getTierCost(targetTier);
            if (mm.canAfford(cost) && gameManager.getTierManager().buyTierUpgradeForTeam(mm, teamId, targetTier)) {
                return;
            }
        }
    }

    private BlockPos findValidPlacementPosition(ServerLevel world, int structureHeight) {
        BlockPos origin = GameConfig.arenaOrigin;
        int size = GameConfig.ARENA_SIZE();
        int midline = GameConfig.getMidlineZ();
        int arenaY = GameConfig.ARENA_Y();

        List<BlockPos> candidates = new ArrayList<>();
        for (int x = 2; x < size - 2; x++) {
            for (int z = midline + 2; z < size - 5; z++) {
                if (Math.abs(x - size / 2) < 3) continue;
                BlockPos base = origin.offset(x, 1, z);
                boolean allAir = true;
                for (int dy = 0; dy < structureHeight && allAir; dy++) {
                    if (!world.getBlockState(base.above(dy)).isAir()) allAir = false;
                }
                if (allAir && GameConfig.isInsidePlayerHalf(base, teamId)) {
                    candidates.add(base);
                }
            }
        }
        if (candidates.isEmpty()) return null;
        Collections.shuffle(candidates, random);
        return candidates.get(0);
    }
}
