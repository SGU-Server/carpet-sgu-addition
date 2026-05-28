//? if <=1.21.6 {
package org.carpet.sgu.carpetSguAddition.mixin;

import carpet.logging.Logger;
import carpet.logging.LoggerRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.carpet.sgu.logger.ProjectileTraker;
import org.carpet.sgu.util.TextTool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(ServerWorld.class)
public class ServerWorldMixin
{
    @Inject(method = "tick", at = @At(value = "INVOKE",
                                      target = "Lnet/minecraft/world/EntityList;forEach(Ljava/util/function/Consumer;)V",
                                      shift = At.Shift.AFTER))
    void projectileTrakerLog(BooleanSupplier shouldKeepTicking, CallbackInfo ci){
        Logger logger = LoggerRegistry.getLogger(ProjectileTraker.NAME);
        if(logger instanceof ProjectileTraker projectileTraker){

            logger.log((str,player)->{
                Entity entity =    projectileTraker.getEntity((ServerPlayerEntity) player);
                if(entity == null) return null;
                var text = TextTool.text("["+entity.getName().getString()+ "] " +"Pos: &9" + TextTool.toString_short(entity.getPos())
                        + " &rVec: &9" + TextTool.toString_short(entity.getVelocity()));
                player.sendMessage(text, true);

                if(entity.isRemoved()){
                    projectileTraker.entityTrackerMap.remove(player.getUuid());
                }
                return null;
            });
        }
    }
}

//?}



