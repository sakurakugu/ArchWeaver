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

/** NeoForge 落地粒子扩展。 */
@Mixin(LivingEntity.class)
public abstract class InvisibilityLandingMixin {
    @WrapOperation(method = "checkFallDamage", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/world/level/block/state/BlockState;addLandingEffects(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/LivingEntity;I)Z"))
    private boolean archweaver$hideLandingDust(BlockState block, ServerLevel level, BlockPos pos,
                                              BlockState state, LivingEntity entity, int count,
                                              Operation<Boolean> original) {
        return InvisibilityLevels.hidesParticles(entity) || original.call(block, level, pos, state, entity, count);
    }
}
