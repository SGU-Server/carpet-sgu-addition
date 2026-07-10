package org.carpet.sgu.mixin;

import carpet.logging.Logger;
import carpet.logging.LoggerRegistry;
import carpet.utils.Messenger;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.carpet.sgu.logger.ProjectileTraker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public class ServerWorldMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void sgu$projectileTrakerLog(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        Logger logger = LoggerRegistry.getLogger(ProjectileTraker.NAME);
        if (!(logger instanceof ProjectileTraker projectileTraker)) {
            return;
        }

        ServerLevel level = (ServerLevel) (Object) this;
        logger.log((option, player) -> {
            ServerPlayer serverPlayer = (ServerPlayer) player;
            Entity entity = projectileTraker.getEntity(level.getServer(), serverPlayer);
            if (entity == null) {
                projectileTraker.entityTrackerMap.remove(serverPlayer.getUUID());
                return null;
            }

            Vec3 pos = entity.position();
            Vec3 velocity = entity.getDeltaMovement();
            serverPlayer.sendSystemMessage(
                Messenger.c(
                    "w [" + entity.getName().getString() + "] ",
                    "w Pos: ",
                    "t " + toStringShort(pos),
                    "w  Vec: ",
                    "t " + toStringShort(velocity)
                ),
                true
            );

            if (entity.isRemoved()) {
                projectileTraker.entityTrackerMap.remove(serverPlayer.getUUID());
            }
            return null;
        });
    }

    private static String toStringShort(Vec3 vec) {
        if (vec == null) {
            return "(0.000, 0.000, 0.000)";
        }
        return "(%.3f, %.3f, %.3f)".formatted(vec.x, vec.y, vec.z);
    }
}
