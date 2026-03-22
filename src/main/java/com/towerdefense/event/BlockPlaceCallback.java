package com.towerdefense.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Fired server-side BEFORE a player's block-placement packet is processed by vanilla.
 * Handlers return true to take full ownership of the interaction and cancel vanilla placement.
 */
public interface BlockPlaceCallback {

    Event<BlockPlaceCallback> EVENT = EventFactory.createArrayBacked(
            BlockPlaceCallback.class,
            listeners -> (player, world, pos, heldItem) -> {
                for (BlockPlaceCallback listener : listeners) {
                    if (listener.onBlockPlacing(player, world, pos, heldItem)) return true;
                }
                return false;
            }
    );

    /**
     * Called before vanilla processes the block placement.
     *
     * @return true to cancel vanilla placement and take full ownership of the interaction.
     */
    boolean onBlockPlacing(ServerPlayer player, Level world, BlockPos pos, ItemStack heldItem);
}
