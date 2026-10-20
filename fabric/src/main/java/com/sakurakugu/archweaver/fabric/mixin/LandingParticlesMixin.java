package com.sakurakugu.archweaver.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.sakurakugu.archweaver.invisibility.InvisibilityLevels;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Fabric 原版落地粒子路径，落地伤害仍正常结算。 */
@Mixin(LivingEntity.class)
public abstract class LandingParticlesMixin {
    @WrapOperation(method = "checkFallDamage", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/server/level/ServerLevel;sendParticles(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I"))
    private int archweaver$hideLandingDust(ServerLevel level, ParticleOptions particle,
        double x, double y, double z, int count, double dx, double dy, double dz, double speed, Operation<Integer> original) {
        return InvisibilityLevels.hidesParticles((LivingEntity) (Object) this) ? 0
            : original.call(level, particle, x, y, z, count, dx, dy, dz, speed);
    }
}
