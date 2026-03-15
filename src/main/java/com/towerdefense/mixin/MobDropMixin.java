package com.towerdefense.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class MobDropMixin {

    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"), cancellable = true)
    private void towerdefense$preventMobDrops(ServerLevel level, DamageSource source, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self.getTags().contains("td_mob")) {
            ci.cancel();
        }
    }
}
