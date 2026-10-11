package com.sakurakugu.archweaver.api.invisibility;

import com.sakurakugu.archweaver.invisibility.InvisibilityLevels;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/** 内容模组通过原版效果实例使用核心的四档隐身能力。 */
public final class InvisibilityApi {
    public static final int VANILLA = 1;
    public static final int HIDE_EQUIPMENT = 2;
    public static final int HIDE_HELD_ITEMS = 3;
    public static final int HIDE_PARTICLES = 4;

    private InvisibilityApi() {
    }

    /** 等级从 I 开始，持续时间以游戏刻计。 */
    public static MobEffectInstance createEffect(int level, int durationTicks) {
        if (level < VANILLA || level > HIDE_PARTICLES) throw new IllegalArgumentException("隐身等级必须在 I 到 IV 之间");
        if (durationTicks <= 0) throw new IllegalArgumentException("隐身持续时间必须大于零");
        return new MobEffectInstance(MobEffects.INVISIBILITY, durationTicks, level - 1);
    }

    /** 服务端读取原版效果，客户端读取已同步的有效等级。 */
    public static int level(LivingEntity entity) {
        return InvisibilityLevels.level(entity);
    }
}
