package com.towerdefense.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fired server-side after a player successfully places a block.
 * Fabric does not provide this event natively, so we fire it via Mixin.
 */
public interface BlockPlaceCallback {

    Event<BlockPlaceCallback> EVENT = EventFactory.createArrayBacked(
            BlockPlaceCallback.class,
            listeners -> (player, world, pos, state) -> {
                for (BlockPlaceCallback listener : listeners) {
                    listener.onBlockPlaced(player, world, pos, state);
                }
            }
    );

    void onBlockPlaced(ServerPlayer player, Level world, BlockPos pos, BlockState state);
}
