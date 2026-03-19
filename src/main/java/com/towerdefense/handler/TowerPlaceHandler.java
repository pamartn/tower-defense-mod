package com.towerdefense.handler;

import com.towerdefense.TowerDefenseMod;
import com.towerdefense.arena.WallBlockManager;
import com.towerdefense.event.BlockPlaceCallback;
import com.towerdefense.game.*;
import com.towerdefense.shop.WallShopItem;
import com.towerdefense.tower.TowerManager;
import com.towerdefense.tower.TowerRecipe;
import com.towerdefense.tower.TowerRegistry;
import com.towerdefense.wave.SpawnerManager;
import com.towerdefense.wave.SpawnerType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

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
        BlockPlaceCallback.EVENT.register(this::onBlockPlaced);
    }

    private void onBlockPlaced(ServerPlayer player, Level world, BlockPos pos, BlockState state) {
        if (world.isClientSide()) return;
        if (!(world instanceof ServerLevel serverLevel)) return;
        if (!gameManager.isActive()) return;

        PlayerState ps = gameManager.getPlayerState(player);

        if (!GameConfig.isInsideArena(pos)) {
            world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            player.sendSystemMessage(Component.literal("You can only place blocks inside the arena!")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        if (ps != null && !ps.isInsideHalf(pos)) {
            world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            player.sendSystemMessage(Component.literal("You can only build on your half!")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        Block placedBlock = state.getBlock();

        SpawnerType spawnerType = SpawnerType.findByBlock(placedBlock);
        if (spawnerType != null && ps != null) {
            world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            gameManager.getSpawnerManager().placeSpawner(serverLevel, pos, spawnerType, ps.getSide(), ps.getEnemyNexusCenter());

            player.sendSystemMessage(Component.literal("\u2726 " + spawnerType.getName() + " placed!")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            return;
        }

        IncomeGeneratorType genType = IncomeGeneratorType.findByBlock(placedBlock);
        if (genType != null && ps != null) {
            world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            if (world.getBlockState(pos.below()).getBlock() != Blocks.GLOWSTONE) {
                player.sendSystemMessage(Component.literal("\u26A0 Generators can only be placed on glowstone pads!")
                        .withStyle(ChatFormatting.RED));
                player.getInventory().add(new ItemStack(placedBlock.asItem()));
                return;
            }
            gameManager.getIncomeGeneratorManager().placeGenerator(serverLevel, pos, genType, ps.getSide());

            player.sendSystemMessage(Component.literal("\u2726 " + genType.getName() + " placed!")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }

        Optional<TowerRecipe> match = registry.findByBlock(placedBlock);
        if (match.isPresent()) {
            TowerRecipe recipe = match.get();
            world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

            int teamId = ps != null ? ps.getSide() : 1;
            manager.spawnTower(serverLevel, pos, recipe, teamId);

            if (ps != null) {
                BlockPos midEntry = GameConfig.arenaOrigin.offset(GameConfig.ARENA_SIZE() / 2, 1, GameConfig.getMidlineZ());
                if (gameManager.checkPathBlocked(ps)) {
                    player.sendSystemMessage(Component.literal("\u26A0 PATH BLOCKED! Mobs can no longer reach your Nexus!")
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                }
            }

            HudManager.sendActionBar(player,
                    Component.literal("\u2714 " + recipe.name() + " placed!")
                            .withStyle(ChatFormatting.GREEN));

            String typeLabel = switch (recipe.type()) {
                case BASIC -> "Arrow";
                case ARCHER -> "Double Arrow";
                case CANNON -> "Explosive Cannonball";
                case LASER -> "Laser Beam";
                case FIRE -> "Fire";
                case SLOW -> "Slowness";
                case POISON -> "Poison";
                case SNIPER -> "Sniper Shot";
                case CHAIN_LIGHTNING -> "Chain Lightning";
                case AOE -> "Area Explosion";
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
            return;
        }

        // Wall block (wool, oak, cobblestone) - place 4-block tower and register for fireball damage
        WallShopItem wallItem = WallShopItem.findByBlock(placedBlock);
        if (wallItem != null && ps != null) {
            BlockPos base = pos;
            for (int dy = 1; dy < WallBlockManager.getTowerHeight(); dy++) {
                BlockPos above = base.above(dy);
                if (GameConfig.isInsideArena(above) && world.getBlockState(above).isAir()) {
                    world.setBlock(above, state, Block.UPDATE_ALL);
                }
            }
            if (gameManager.checkPathBlocked(ps)) {
                for (int dy = 0; dy < WallBlockManager.getTowerHeight(); dy++) {
                    BlockPos p = base.above(dy);
                    world.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    gameManager.getWallBlockManager().removeBlock(p);
                }
                player.sendSystemMessage(Component.literal("This wall would block the mob path!")
                        .withStyle(ChatFormatting.RED));
                return;
            }
            gameManager.getWallBlockManager().registerTower(serverLevel, base, ps.getSide(), wallItem.getTier());
            return;
        }

        // Plain block - check path
        if (ps != null) {
            if (gameManager.checkPathBlocked(ps)) {
                world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                player.sendSystemMessage(Component.literal("This block would block the mob path!")
                        .withStyle(ChatFormatting.RED));
            }
        }
    }
}
