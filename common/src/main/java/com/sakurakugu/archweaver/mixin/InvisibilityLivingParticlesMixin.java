package com.sakurakugu.archweaver.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.sakurakugu.archweaver.invisibility.InvisibilityLevels;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 屏蔽实体自身的粒子，不跳过药水计时、落地伤害或物品使用逻辑。 */
@Mixin(LivingEntity.class)
public abstract class InvisibilityLivingParticlesMixin {
    @WrapOperation(method = {"tickEffects", "handleEntityEvent", "makePoofParticles", "makeDrownParticles", "spawnItemParticles"},
        at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
    private void archweaver$hideEntityParticles(Level level, ParticleOptions particle, double x, double y, double z,
                                               double dx, double dy, double dz, Operation<Void> original) {
        if (!InvisibilityLevels.hidesParticles((LivingEntity) (Object) this)) {
            original.call(level, particle, x, y, z, dx, dy, dz);
        }
    }

    @WrapOperation(method = "checkFallDamage", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/world/level/block/state/BlockState;addLandingEffects(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/LivingEntity;I)Z"))
    private boolean archweaver$hideLandingDust(BlockState block, ServerLevel level, BlockPos pos,
                                              BlockState state, LivingEntity entity, int count,
                                              Operation<Boolean> original) {
        return InvisibilityLevels.hidesParticles(entity) || original.call(block, level, pos, state, entity, count);
    }
}
