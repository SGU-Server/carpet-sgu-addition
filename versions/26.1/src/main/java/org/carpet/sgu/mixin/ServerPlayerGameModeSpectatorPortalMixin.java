package org.carpet.sgu.mixin;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.BlockHitResult;
import org.carpet.sgu.SguSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public class ServerPlayerGameModeSpectatorPortalMixin {
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void sgu$spectatorPortalTeleport(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand,
                                             BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        if (!SguSettings.spectatorPortalTeleport || !player.isSpectator()) {
            return;
        }

        BlockPos pos = hitResult.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof Portal portal)) {
            return;
        }

        ServerLevel serverLevel = player.level();
        TeleportTransition transition = portal.getPortalDestination(serverLevel, player, pos);
        if (transition != null) {
            player.teleport(transition);
        } else {
            player.sendOverlayMessage(Component.translatable("spectator.cannot_teleport").withStyle(ChatFormatting.RED));
        }

        cir.setReturnValue(InteractionResult.CONSUME);
    }
}
