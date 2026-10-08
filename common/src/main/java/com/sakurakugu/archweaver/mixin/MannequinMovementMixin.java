package com.sakurakugu.archweaver.mixin;

import com.sakurakugu.archweaver.entity.MannequinManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 同时阻止活塞等绕过生物 travel 的被动位移，命令传送仍可直接修改位置。 */
@Mixin(Entity.class)
public abstract class MannequinMovementMixin {
    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void archweaver$keepPosition(MoverType type, Vec3 movement, CallbackInfo callback) {
        if ((Object) this instanceof Mannequin mannequin
            && mannequin.entityTags().contains(MannequinManager.MANAGED_TAG)
            && ((MannequinInvoker) mannequin).archweaver$getImmovable()) {
            mannequin.setDeltaMovement(Vec3.ZERO);
            callback.cancel();
        }
    }
}
