package com.towerdefense.mixin;

import com.towerdefense.event.BlockPlaceCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPlayerInteractionMixin {

    @Inject(method = "handleUseItemOn", at = @At("HEAD"), cancellable = true)
    private void towerdefense$beforeBlockPlace(ServerboundUseItemOnPacket packet, CallbackInfo ci) {
        // Minecraft sends handleUseItemOn for both MAIN_HAND and OFF_HAND.
        // Only process the main-hand packet to avoid double-charging.
        if (packet.getHand() != InteractionHand.MAIN_HAND) return;

        ServerGamePacketListenerImpl handler = (ServerGamePacketListenerImpl) (Object) this;
        ServerPlayer player = handler.player;
        if (player == null) return;

        // handleUseItemOn is first called from the Netty thread; Minecraft's
        // ensureRunningOnSameThread then re-queues it on the server thread.
        // We must only act on the server-thread invocation.
        if (!player.getServer().isSameThread()) return;

        Level world = player.level();
        BlockHitResult hitResult = packet.getHitResult();
        BlockPos placePos = hitResult.getBlockPos().relative(hitResult.getDirection());

        ItemStack heldItem = player.getMainHandItem();
        CustomData cd = heldItem.get(DataComponents.CUSTOM_DATA);
        if (cd == null || !cd.copyTag().contains("td_category")) return;

        boolean handled = BlockPlaceCallback.EVENT.invoker().onBlockPlacing(player, world, placePos, heldItem);
        if (handled) ci.cancel();
    }
}
