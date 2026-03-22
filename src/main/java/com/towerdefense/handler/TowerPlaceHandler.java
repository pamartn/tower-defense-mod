package com.towerdefense.handler;

import com.towerdefense.arena.WallBlockManager;
import com.towerdefense.event.BlockPlaceCallback;
import com.towerdefense.game.*;
import com.towerdefense.shop.WallShopItem;
import com.towerdefense.tower.TowerManager;
import com.towerdefense.tower.TowerRecipe;
import com.towerdefense.tower.TowerRegistry;
import com.towerdefense.tower.TowerType;
import com.towerdefense.wave.SpawnerType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class TowerPlaceHandler {

    private final TowerRegistry registry;
    private final TowerManager manager;
    private final GameManager gameManager;

    public TowerPlaceHandler(TowerRegistry registry, TowerManager manager, GameManager gameManager) {
        this.registry = registry;
        this.manager = manager;
        this.gameManager = gameManager;
    }

    public void register() {
        BlockPlaceCallback.EVENT.register(this::onBlockPlacing);
    }

    /**
     * Intercepts block placement BEFORE vanilla processes it (server thread only).
     * Returns true to cancel vanilla — block never placed, item never consumed.
     * No client-side prediction to undo: held item is a plain non-BlockItem.
     */
    private boolean onBlockPlacing(ServerPlayer player, Level world, BlockPos pos, ItemStack heldItem) {
        if (world.isClientSide()) return false;
        if (!(world instanceof ServerLevel serverLevel)) return false;
        if (!gameManager.isActive()) return false;

        CustomData cd = heldItem.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return false;
        CompoundTag tag = cd.copyTag();
        String category = tag.getString("td_category");
        String typeId   = tag.getString("td_id");

        SpawnerType spawnerType     = null;
        IncomeGeneratorType genType = null;
        TowerRecipe towerRecipe     = null;
        WallShopItem wallItem       = null;

        switch (category) {
            case "spawner" -> {
                try { spawnerType = SpawnerType.valueOf(typeId); } catch (IllegalArgumentException ignored) {}
            }
            case "generator" -> {
                try { genType = IncomeGeneratorType.valueOf(typeId); } catch (IllegalArgumentException ignored) {}
            }
            case "tower" -> {
                try {
                    TowerType type = TowerType.valueOf(typeId);
                    towerRecipe = registry.findByType(type).orElse(null);
                } catch (IllegalArgumentException ignored) {}
            }
            case "wall" -> wallItem = WallShopItem.findByName(typeId);
        }

        boolean isManaged = spawnerType != null || genType != null || towerRecipe != null || wallItem != null;
        if (!isManaged) return false;

        PlayerState ps = gameManager.getPlayerState(player);

        if (!GameConfig.isInsideArena(pos)) {
            player.sendSystemMessage(Component.literal("You can only place blocks inside the arena!")
                    .withStyle(ChatFormatting.RED));
            return true;
        }
        if (ps != null && !ps.isInsideHalf(pos)) {
            player.sendSystemMessage(Component.literal("You can only build on your half!")
                    .withStyle(ChatFormatting.RED));
            return true;
        }
        if (ps == null) {
            return true;
        }

        // ── Spawner ──────────────────────────────────────────────────────────
        if (spawnerType != null) {
            MoneyManager money = ps.getMoneyManager();
            if (!money.canAfford(spawnerType.getPrice())) {
                player.sendSystemMessage(Component.literal("Not enough money! Need $" + spawnerType.getPrice())
                        .withStyle(ChatFormatting.RED));
                return true;
            }
            money.spend(spawnerType.getPrice());
            gameManager.getSpawnerManager().placeSpawner(serverLevel, pos, spawnerType, ps.getSide(), ps.getEnemyNexusCenter());
            player.sendSystemMessage(Component.literal("\u2726 " + spawnerType.getName() + " placed!")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            return true;
        }

        // ── Income generator ──────────────────────────────────────────────────
        if (genType != null) {
            MoneyManager money = ps.getMoneyManager();
            if (!money.canAfford(genType.getPrice())) {
                player.sendSystemMessage(Component.literal("Not enough money! Need $" + genType.getPrice())
                        .withStyle(ChatFormatting.RED));
                return true;
            }
            if (world.getBlockState(pos.below()).getBlock() != Blocks.GLOWSTONE) {
                player.sendSystemMessage(Component.literal("\u26A0 Generators can only be placed on glowstone pads!")
                        .withStyle(ChatFormatting.RED));
                return true;
            }
            money.spend(genType.getPrice());
            gameManager.getIncomeGeneratorManager().placeGenerator(serverLevel, pos, genType, ps.getSide());
            player.sendSystemMessage(Component.literal("\u2726 " + genType.getName() + " placed!")
                    .withStyle(ChatFormatting.YELLOW));
            return true;
        }

        // ── Tower ─────────────────────────────────────────────────────────────
        if (towerRecipe != null) {
            TowerRecipe recipe = towerRecipe;
            MoneyManager money = ps.getMoneyManager();
            if (!money.canAfford(recipe.price())) {
                player.sendSystemMessage(Component.literal("Not enough money! Need $" + recipe.price())
                        .withStyle(ChatFormatting.RED));
                return true;
            }
            money.spend(recipe.price());
            manager.spawnTower(serverLevel, pos, recipe, ps.getSide());

            if (gameManager.checkPathBlocked(ps)) {
                player.sendSystemMessage(Component.literal("\u26A0 PATH BLOCKED! Mobs can no longer reach your Nexus!")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            }

            HudManager.sendActionBar(player,
                    Component.literal("\u2714 " + recipe.name() + " placed!").withStyle(ChatFormatting.GREEN));

            String typeLabel = switch (recipe.type()) {
                case BASIC          -> "Arrow";
                case ARCHER         -> "Double Arrow";
                case CANNON         -> "Explosive Cannonball";
                case LASER          -> "Laser Beam";
                case FIRE           -> "Fire";
                case SLOW           -> "Slowness";
                case POISON         -> "Poison";
                case SNIPER         -> "Sniper Shot";
                case CHAIN_LIGHTNING -> "Chain Lightning";
                case AOE            -> "Area Explosion";
            };

            player.sendSystemMessage(
                    Component.literal("\u2726 " + recipe.name() + " built!")
                            .withStyle(ChatFormatting.GREEN)
                            .append(Component.literal(" [" + typeLabel + "]").withStyle(ChatFormatting.GOLD))
                            .append(Component.literal(
                                    " (Power: " + recipe.power() +
                                    ", Range: " + (int) recipe.range() +
                                    ", Rate: " + String.format("%.1f", recipe.fireRateInTicks() / 20.0) + "s)"
                            ).withStyle(ChatFormatting.GRAY))
            );

            world.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1.0f, 1.5f);
            return true;
        }

        // ── Wall block ────────────────────────────────────────────────────────
        if (wallItem != null) {
            MoneyManager money = ps.getMoneyManager();
            if (!money.canAfford(wallItem.price())) {
                player.sendSystemMessage(Component.literal("Not enough money! Need $" + wallItem.price())
                        .withStyle(ChatFormatting.RED));
                return true;
            }
            var wallState = wallItem.block().defaultBlockState();
            world.setBlock(pos, wallState, Block.UPDATE_ALL);
            for (int dy = 1; dy < WallBlockManager.getTowerHeight(); dy++) {
                BlockPos above = pos.above(dy);
                if (GameConfig.isInsideArena(above) && world.getBlockState(above).isAir()) {
                    world.setBlock(above, wallState, Block.UPDATE_ALL);
                }
            }
            if (gameManager.checkPathBlocked(ps)) {
                for (int dy = 0; dy < WallBlockManager.getTowerHeight(); dy++) {
                    BlockPos p = pos.above(dy);
                    world.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    gameManager.getWallBlockManager().removeBlock(p);
                }
                player.sendSystemMessage(Component.literal("This wall would block the mob path!")
                        .withStyle(ChatFormatting.RED));
                return true;
            }
            money.spend(wallItem.price());
            gameManager.getWallBlockManager().registerTower(serverLevel, pos, ps.getSide(), wallItem.getTier());
            return true;
        }

        return false;
    }
}
