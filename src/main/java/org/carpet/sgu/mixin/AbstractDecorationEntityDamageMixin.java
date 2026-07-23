package org.carpet.sgu.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.decoration.AbstractDecorationEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.projectile.AbstractWindChargeEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.explosion.Explosion;
import org.carpet.sgu.SguSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractDecorationEntity.class)
public abstract class AbstractDecorationEntityDamageMixin {
    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void sgu$protectItemFrameBeforeKill(ServerWorld world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ItemFrameEntity
                && SguSettings.itemFrameCombatDamageProtection
                && sgu$isBlockedCombatDamage(source)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "ignoreExplosion", at = @At("HEAD"), cancellable = true)
    private void sgu$protectItemFrameFromCombatExplosion(Explosion explosion, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ItemFrameEntity) || !SguSettings.itemFrameCombatDamageProtection) {
            return;
        }

        Entity directEntity = explosion.getEntity();
        Entity sourceEntity = explosion.getCausingEntity();
        if (directEntity instanceof AbstractWindChargeEntity
                || directEntity instanceof TridentEntity
                || sourceEntity instanceof LivingEntity) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void sgu$protectItemFrameFromCombatMovement(MovementType type, Vec3d movement, CallbackInfo ci) {
        if ((Object) this instanceof ItemFrameEntity
                && SguSettings.itemFrameCombatDamageProtection
                && movement.lengthSquared() > 0.0) {
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

        return sgu$isBlockedItem(source.getWeaponStack());
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
