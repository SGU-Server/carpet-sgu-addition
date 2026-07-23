package org.carpet.sgu.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.carpet.sgu.SguSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void sgu$preventItemFrameCombatAttack(Entity target, CallbackInfo ci) {
        if (target instanceof ItemFrameEntity
                && SguSettings.itemFrameCombatDamageProtection
                && (Object) this instanceof PlayerEntity player
                && (sgu$isBlockedItem(player.getMainHandStack()) || sgu$isBlockedItem(player.getOffHandStack()))) {
            ci.cancel();
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
        if (sgu$isBlockedCombatItemPath(path)) {
            return true;
        }

        String stackText = stack.toString();
        return sgu$isBlockedCombatItemPath(stackText);
    }

    private static boolean sgu$isBlockedCombatItemPath(String path) {
        return path.contains("spear")
                || path.contains("mace")
                || path.contains("hammer")
                || path.contains("storm")
                || path.contains("smash");
    }
}
