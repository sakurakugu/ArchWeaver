package com.sakurakugu.archweaver.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.sakurakugu.archweaver.invisibility.InvisibilityLevels;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 水花只屏蔽粒子生成，保留声音、游戏事件和水中运动。 */
@Mixin(Entity.class)
public abstract class InvisibilityEntityParticlesMixin {
    @Inject(method = "spawnSprintParticle", at = @At("HEAD"), cancellable = true)
    private void archweaver$hideSprintDust(CallbackInfo callback) {
        if (InvisibilityLevels.hidesParticles((Entity) (Object) this)) callback.cancel();
    }

    @WrapOperation(method = "doWaterSplashEffect", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"), require = 2)
    private void archweaver$hideSplash(Level level, ParticleOptions particle, double x, double y, double z,
                                      double dx, double dy, double dz, Operation<Void> original) {
        if (!InvisibilityLevels.hidesParticles((Entity) (Object) this)) {
            original.call(level, particle, x, y, z, dx, dy, dz);
        }
    }
}
