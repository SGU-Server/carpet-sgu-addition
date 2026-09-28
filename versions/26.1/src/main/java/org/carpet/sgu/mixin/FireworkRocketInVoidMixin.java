package org.carpet.sgu.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import org.carpet.sgu.SguSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class FireworkRocketInVoidMixin {
    @Inject(method = "onBelowWorld", at = @At("HEAD"), cancellable = true)
    private void sgu$keepFireworkRocket(CallbackInfo ci) {
        if (SguSettings.fireworkRocketInVoid && (Object) this instanceof FireworkRocketEntity) {
            ci.cancel();
        }
    }
}
