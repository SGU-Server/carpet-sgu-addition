//? if <=1.21.6 {
package org.carpet.sgu.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerEntity.class)
public interface PlayerProfileAccessor {
    @Mutable
    @Accessor("gameProfile")
    void carpet_sgu_addition$setGameProfile(GameProfile profile);
}
//?}
