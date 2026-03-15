package com.towerdefense.mixin;

import com.towerdefense.TowerDefenseMod;
import com.towerdefense.game.GameManager;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class MobSunBurnMixin {

    @Inject(method = "isSunBurnTick", at = @At("HEAD"), cancellable = true)
    private void towerdefense$preventSunBurn(CallbackInfoReturnable<Boolean> cir) {
        TowerDefenseMod mod = TowerDefenseMod.getInstance();
        if (mod == null) return;

        GameManager gm = mod.getGameManager();
        if (gm.isActive()) {
            cir.setReturnValue(false);
        }
    }
}
