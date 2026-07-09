package org.carpet.sgu.mixin;

import carpet.logging.Logger;
import carpet.logging.LoggerRegistry;
import carpet.utils.Messenger;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.carpet.sgu.logger.ProjectileTraker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public class ServerWorldMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void projectileTrakerLog(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        Logger logger = LoggerRegistry.getLogger(ProjectileTraker.NAME);
        if (logger instanceof ProjectileTraker projectileTraker) {
            logger.log((option, player) -> {
                ServerPlayer serverPlayer = (ServerPlayer) player;
                Entity entity = projectileTraker.getEntity(serverPlayer);
                if (entity == null) {
                    return null;
                }

                serverPlayer.sendSystemMessage(
                    Messenger.c(
                        "w [" + entity.getName().getString() + "] ",
                        "w Pos: ",
                        "t " + format(entity.getX(), entity.getY(), entity.getZ()),
                        "w  Vec: ",
                        "t " + format(entity.getDeltaMovement().x, entity.getDeltaMovement().y, entity.getDeltaMovement().z)
                    ),
                    true
                );

                if (entity.isRemoved()) {
                    projectileTraker.entityTrackerMap.remove(serverPlayer.getUUID());
                }
                return null;
            });
        }
    }

    private static String format(double x, double y, double z) {
        return String.format(Locale.ROOT, "%.2f, %.2f, %.2f", x, y, z);
    }
}
