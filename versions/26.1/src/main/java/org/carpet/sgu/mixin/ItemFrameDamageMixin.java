package org.carpet.sgu.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.AbstractWindCharge;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.carpet.sgu.SguSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemFrame.class)
public abstract class ItemFrameDamageMixin {
    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void sgu$protectItemFramesFromCombatDamage(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (SguSettings.itemFrameCombatDamageProtection && sgu$isBlockedCombatDamage(source)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "shouldDamageDropItem", at = @At("HEAD"), cancellable = true)
    private void sgu$preventCombatDamageItemDrop(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (SguSettings.itemFrameCombatDamageProtection && sgu$isBlockedCombatDamage(source)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "dropItem(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void sgu$preventCombatDropItem(ServerLevel level, Entity entity, CallbackInfo ci) {
        if (SguSettings.itemFrameCombatDamageProtection && sgu$isBlockedDropEntity(entity)) {
            ci.cancel();
        }
    }

    @Inject(method = "dropItem(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;Z)V", at = @At("HEAD"), cancellable = true)
    private void sgu$preventCombatDropItem(ServerLevel level, Entity entity, boolean dropSelf, CallbackInfo ci) {
        if (SguSettings.itemFrameCombatDamageProtection && sgu$isBlockedDropEntity(entity)) {
            ci.cancel();
        }
    }

    private static boolean sgu$isBlockedCombatDamage(DamageSource source) {
        if (source.is(DamageTypes.TRIDENT)
                || source.is(DamageTypes.WIND_CHARGE)
                || source.is(DamageTypes.MACE_SMASH)) {
            return true;
        }

        Entity directEntity = source.getDirectEntity();
        if (directEntity instanceof AbstractWindCharge || directEntity instanceof ThrownTrident) {
            return true;
        }

        Entity attacker = source.getEntity();
        if (attacker instanceof AbstractWindCharge || attacker instanceof ThrownTrident) {
            return true;
        }
        if (attacker instanceof LivingEntity livingEntity
                && (sgu$isBlockedItem(livingEntity.getMainHandItem())
                || sgu$isBlockedItem(livingEntity.getOffhandItem()))) {
            return true;
        }

        ItemStack weaponStack = source.getWeaponItem();
        if (sgu$isBlockedItem(weaponStack)) {
            return true;
        }

        String damageId = source.getMsgId();
        return damageId != null
                && (damageId.contains("mace")
                || damageId.contains("trident")
                || damageId.contains("wind"));
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

    private static boolean sgu$isBlockedDropEntity(Entity entity) {
        if (entity instanceof AbstractWindCharge || entity instanceof ThrownTrident) {
            return true;
        }

        return entity instanceof LivingEntity livingEntity
                && (sgu$isBlockedItem(livingEntity.getMainHandItem())
                || sgu$isBlockedItem(livingEntity.getOffhandItem()));
    }
}
