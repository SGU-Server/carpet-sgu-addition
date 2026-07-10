package org.carpet.sgu.mixin;

import carpet.logging.Logger;
import carpet.logging.LoggerRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import org.carpet.sgu.logger.ProjectileTraker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Projectile.class)
public abstract class ProjectileEntityMixin {
    @Unique
    private boolean sgu$logged = false;

    @Inject(method = "tick", at = @At("TAIL"))
    private void sgu$trackProjectile(CallbackInfo ci) {
        if (sgu$logged) {
            return;
        }

        Entity entity = (Entity) (Object) this;
        if (!(entity.level() instanceof ServerLevel)) {
            return;
        }

        Projectile projectile = (Projectile) (Object) this;
        if (!(projectile.getOwner() instanceof ServerPlayer owner)) {
            return;
        }

        Logger logger = LoggerRegistry.getLogger(ProjectileTraker.NAME);
        if (!(logger instanceof ProjectileTraker projectileTraker)) {
            return;
        }

        logger.log((option, player) -> {
            if (player != owner) {
                return null;
            }

            if ("all".equals(option)) {
                sgu$logged = true;
                projectileTraker.addNormalEntity(owner, entity);
                return null;
            }

            if ("pearlCannon".equals(option) && projectile instanceof ThrownEnderpearl) {
                if (entity.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D).length() >= 10.0D) {
                    sgu$logged = true;
                    projectileTraker.addNormalEntity(owner, entity);
                }
            }
            return null;
        });
    }
}
