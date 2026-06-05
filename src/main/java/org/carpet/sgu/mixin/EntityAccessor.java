//? if <=1.21.6 {
package org.carpet.sgu.mixin;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Entity.class)
public interface EntityAccessor {
    @Invoker("unsetRemoved")
    void invokeUnsetRemoved();
}
//?}



