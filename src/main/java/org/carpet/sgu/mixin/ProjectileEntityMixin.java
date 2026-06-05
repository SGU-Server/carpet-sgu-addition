//? if <=1.21.6 {
package org.carpet.sgu.mixin;


import carpet.logging.Logger;
import carpet.logging.LoggerRegistry;

import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.carpet.sgu.logger.ProjectileTraker;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(ProjectileEntity.class)
public abstract class ProjectileEntityMixin
{

    @Shadow @Nullable public abstract Entity getOwner();

    @Unique
    private boolean rof$logged = false;

    @Inject(method = "tick()V",at = @At(value = "TAIL"))
    void tick(CallbackInfo ci){
        if(! (((Entity)(Object)this).getWorld() instanceof  ServerWorld))
            return;
        if(rof$logged) return;

        Logger logger = LoggerRegistry.getLogger(ProjectileTraker.NAME);
        if(logger instanceof ProjectileTraker projectileTraker) {
            logger.log((str, player) ->
            {
                if ( player == getOwner()) {
                    if(str.equals("all")){
                        rof$logged = true;
                        projectileTraker.addNormalEntity((ServerPlayerEntity) player, ((Entity) (Object) this));
                        return null;
                    }
                    if(str.equals("pearlCannon") && (Object)this instanceof EnderPearlEntity enderPearl) {
                        if (enderPearl.getVelocity().multiply(1, 0, 1).length() >= 10) {
                            rof$logged = true;
                            projectileTraker.addNormalEntity((ServerPlayerEntity) player, ((Entity) (Object) this));
                            return null;
                        }
                    }
                }
                return null;
            });
        }
    }
}

//?}



