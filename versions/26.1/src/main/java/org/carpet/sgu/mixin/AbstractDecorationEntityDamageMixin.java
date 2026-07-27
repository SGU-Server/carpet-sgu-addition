package org.carpet.sgu.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.AbstractWindCharge;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;
import org.carpet.sgu.SguSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockAttachedEntity.class)
public abstract class AbstractDecorationEntityDamageMixin {
    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void sgu$protectItemFrameBeforeKill(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ItemFrame
                && SguSettings.itemFrameCombatDamageProtection
                && sgu$isBlockedCombatDamage(source)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "ignoreExplosion", at = @At("HEAD"), cancellable = true)
    private void sgu$protectItemFrameFromCombatExplosion(Explosion explosion, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ItemFrame) || !SguSettings.itemFrameCombatDamageProtection) {
            return;
        }

        Entity directEntity = explosion.getDirectSourceEntity();
        Entity sourceEntity = explosion.getIndirectSourceEntity();
        if (directEntity instanceof AbstractWindCharge
                || directEntity instanceof ThrownTrident
                || sourceEntity instanceof LivingEntity) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void sgu$protectItemFrameFromCombatMovement(MoverType type, Vec3 movement, CallbackInfo ci) {
        if ((Object) this instanceof ItemFrame
                && SguSettings.itemFrameCombatDamageProtection
                && movement.lengthSqr() > 0.0) {
            ci.cancel();
        }
    }

    @Inject(method = "push(DDD)V", at = @At("HEAD"), cancellable = true)
    private void sgu$protectItemFrameFromCombatPush(double x, double y, double z, CallbackInfo ci) {
        if ((Object) this instanceof ItemFrame
                && SguSettings.itemFrameCombatDamageProtection
                && x * x + y * y + z * z > 0.0) {
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

        return sgu$isBlockedItem(source.getWeaponItem());
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
