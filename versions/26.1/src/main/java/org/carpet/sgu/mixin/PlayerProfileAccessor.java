package org.carpet.sgu.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Player.class)
public interface PlayerProfileAccessor {
    @Mutable
    @Accessor("gameProfile")
    void carpet_sgu_addition$setGameProfile(GameProfile profile);
}
