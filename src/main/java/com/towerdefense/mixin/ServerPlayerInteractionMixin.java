package com.towerdefense.mixin;

import com.towerdefense.event.BlockPlaceCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPlayerInteractionMixin {

    @Inject(method = "handleUseItemOn", at = @At("TAIL"))
    private void towerdefense$afterBlockPlace(ServerboundUseItemOnPacket packet, CallbackInfo ci) {
        ServerGamePacketListenerImpl handler = (ServerGamePacketListenerImpl) (Object) this;
        ServerPlayer player = handler.player;
        if (player == null) return;

        Level world = player.level();
        BlockHitResult hitResult = packet.getHitResult();
        BlockPos placePos = hitResult.getBlockPos().relative(hitResult.getDirection());

        BlockState state = world.getBlockState(placePos);
        if (state.isAir()) return;

        BlockPlaceCallback.EVENT.invoker().onBlockPlaced(player, world, placePos, state);
    }
}
