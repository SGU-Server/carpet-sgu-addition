package org.carpet.sgu.mixin;

import net.minecraft.block.DecoratedPotBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.carpet.sgu.SguSettings;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerInteractionManager.class)
public abstract class ServerPlayerGameModeMixin {
    @Shadow @Final protected ServerPlayerEntity player;
    @Shadow protected ServerWorld world;

    @Inject(method = "tryBreakBlock", at = @At("HEAD"), cancellable = true)
    private void sgu$protectDecoratedPotFromCombatItems(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (SguSettings.itemFrameCombatDamageProtection
                && this.world.getBlockState(pos).getBlock() instanceof DecoratedPotBlock
                && sgu$isBlockedItem(this.player.getMainHandStack())) {
            cir.setReturnValue(false);
        }
    }

    private static boolean sgu$isBlockedItem(ItemStack stack) {
        if (stack == null) {
            return false;
        }

        if (stack.isOf(Items.MACE)
                || stack.isOf(Items.TRIDENT)
                || stack.isOf(Items.WIND_CHARGE)) {
            return true;
        }

        Identifier itemId = Registries.ITEM.getId(stack.getItem());
        String path = itemId.getPath();
        return path.contains("spear")
                || path.contains("mace")
                || path.contains("hammer")
                || path.contains("storm")
                || path.contains("smash");
    }
}
