package org.carpet.sgu.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.projectile.AbstractWindChargeEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.carpet.sgu.SguSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemFrameEntity.class)
public abstract class ItemFrameDamageMixin {
    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void sgu$protectItemFramesFromCombatDamage(ServerWorld world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (SguSettings.itemFrameCombatDamageProtection && sgu$isBlockedCombatDamage(source)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "shouldDropHeldStackWhenDamaged", at = @At("HEAD"), cancellable = true)
    private void sgu$preventCombatDamageItemDrop(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (SguSettings.itemFrameCombatDamageProtection && sgu$isBlockedCombatDamage(source)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "onBreak", at = @At("HEAD"), cancellable = true)
    private void sgu$preventCombatOnBreak(ServerWorld world, Entity entity, CallbackInfo ci) {
        if (SguSettings.itemFrameCombatDamageProtection && sgu$isBlockedDropEntity(entity)) {
            ci.cancel();
        }
    }

    @Inject(method = "dropHeldStack", at = @At("HEAD"), cancellable = true)
    private void sgu$preventCombatDropHeldStack(ServerWorld world, Entity entity, boolean dropSelf, CallbackInfo ci) {
        if (SguSettings.itemFrameCombatDamageProtection && sgu$isBlockedDropEntity(entity)) {
            ci.cancel();
        }
    }

    private static boolean sgu$isBlockedCombatDamage(DamageSource source) {
        if (source.isOf(DamageTypes.TRIDENT)
                || source.isOf(DamageTypes.WIND_CHARGE)
                || source.isOf(DamageTypes.MACE_SMASH)) {
            return true;
        }

        Entity directEntity = source.getSource();
        if (directEntity instanceof AbstractWindChargeEntity || directEntity instanceof TridentEntity) {
            return true;
        }

        Entity attacker = source.getAttacker();
        if (attacker instanceof AbstractWindChargeEntity || attacker instanceof TridentEntity) {
            return true;
        }
        if (attacker instanceof LivingEntity livingEntity
                && (sgu$isBlockedItem(livingEntity.getMainHandStack())
                || sgu$isBlockedItem(livingEntity.getOffHandStack()))) {
            return true;
        }

        ItemStack weaponStack = source.getWeaponStack();
        if (sgu$isBlockedItem(weaponStack)) {
            return true;
        }

        String damageId = source.getName();
        return damageId != null
                && (damageId.contains("mace")
                || damageId.contains("trident")
                || damageId.contains("wind"));
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

    private static boolean sgu$isBlockedDropEntity(Entity entity) {
        if (entity instanceof AbstractWindChargeEntity || entity instanceof TridentEntity) {
            return true;
        }

        return entity instanceof LivingEntity livingEntity
                && (sgu$isBlockedItem(livingEntity.getMainHandStack())
                || sgu$isBlockedItem(livingEntity.getOffHandStack()));
    }
}
