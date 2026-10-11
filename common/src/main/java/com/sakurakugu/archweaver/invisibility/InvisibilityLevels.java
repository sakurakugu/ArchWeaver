package com.sakurakugu.archweaver.invisibility;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** 复用原版隐身效果，等级 I 保留原版行为，更高等级逐步隐藏装备与粒子。 */
public final class InvisibilityLevels {
    private InvisibilityLevels() {
    }

    public static int effectLevel(LivingEntity entity) {
        MobEffectInstance effect = entity.getEffect(MobEffects.INVISIBILITY);
        return effect == null ? 0 : Math.min(4, effect.getAmplifier() + 1);
    }

    public static int level(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return 0;
        if (entity.level().isClientSide() && living instanceof InvisibilityState state
            && state.archweaver$invisibilityLevel() >= 0) {
            return state.archweaver$invisibilityLevel();
        }
        return effectLevel(living);
    }

    public static boolean hidesParticles(Entity entity) {
        return level(entity) >= 4;
    }
}
