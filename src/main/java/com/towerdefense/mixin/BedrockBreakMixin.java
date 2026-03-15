package com.towerdefense.mixin;

import com.towerdefense.TowerDefenseMod;
import com.towerdefense.game.GameConfig;
import com.towerdefense.game.GameManager;
import com.towerdefense.tower.TowerManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BedrockBreakMixin {

    @Inject(method = "getDestroyProgress", at = @At("HEAD"), cancellable = true)
    private void towerdefense$makeBedrockBreakable(Player player, BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        BlockState self = (BlockState) (Object) this;
        if (!self.is(Blocks.BEDROCK)) return;

        TowerDefenseMod mod = TowerDefenseMod.getInstance();
        if (mod == null) return;

        GameManager gm = mod.getGameManager();
        if (!gm.isActive()) return;

        if (!GameConfig.isInsideArena(pos)) return;

        TowerManager tm = mod.getTowerManager();
        if (tm.findTowerByBlock(pos) != null) {
            cir.setReturnValue(0.05f);
        }
    }
}
