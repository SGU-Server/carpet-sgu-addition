package org.carpet.sgu.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.carpet.sgu.SguSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerEntityMixin {
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void sgu$preventItemFrameCombatAttack(Entity target, CallbackInfo ci) {
        if (target instanceof ItemFrame
                && SguSettings.itemFrameCombatDamageProtection
                && (Object) this instanceof Player player
                && (sgu$isBlockedItem(player.getMainHandItem()) || sgu$isBlockedItem(player.getOffhandItem()))) {
            ci.cancel();
        }
    }

    @Inject(method = "stabAttack", at = @At("HEAD"), cancellable = true)
    private void sgu$preventItemFrameCombatStab(EquipmentSlot slot, Entity target, float amount, boolean bl, boolean bl2, boolean bl3, CallbackInfoReturnable<Boolean> cir) {
        if (target instanceof ItemFrame
                && SguSettings.itemFrameCombatDamageProtection
                && (Object) this instanceof Player player
                && sgu$isBlockedItem(player.getItemBySlot(slot))) {
            cir.setReturnValue(false);
        }
    }

    private static boolean sgu$isBlockedItem(ItemStack stack) {
        if (stack == null) {
            return false;
        }

        if (stack.getItem() == Items.MACE
                || stack.getItem() == Items.TRIDENT
                || stack.getItem() == Items.WIND_CHARGE) {
            return true;
        }

        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
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
