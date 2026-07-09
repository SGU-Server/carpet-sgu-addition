package org.carpet.sgu.mixin;

import carpet.logging.Logger;
import carpet.logging.LoggerRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import org.carpet.sgu.logger.ProjectileTraker;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Projectile.class)
public abstract class ProjectileEntityMixin {
    @Shadow
    @Nullable
    public abstract Entity getOwner();

    @Unique
    private boolean rof$logged = false;

    @Inject(method = "tick", at = @At("TAIL"))
    private void tick(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (!(entity.level() instanceof ServerLevel)) {
            return;
        }
        if (rof$logged) {
            return;
        }

        Logger logger = LoggerRegistry.getLogger(ProjectileTraker.NAME);
        if (logger instanceof ProjectileTraker projectileTraker) {
            logger.log((option, player) -> {
                if (player == getOwner()) {
                    if (option.equals("all")) {
                        rof$logged = true;
                        projectileTraker.addNormalEntity((ServerPlayer) player, entity);
                        return null;
                    }
                    if (option.equals("pearlCannon") && (Object) this instanceof ThrownEnderpearl pearl) {
                        if (pearl.getDeltaMovement().multiply(1, 0, 1).length() >= 10) {
                            rof$logged = true;
                            projectileTraker.addNormalEntity((ServerPlayer) player, entity);
                            return null;
                        }
                    }
                }
                return null;
            });
        }
    }
}
