package com.sakurakugu.archweaver.mixin;

import com.sakurakugu.archweaver.client.InvisibilityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** 不在渲染提交阶段查询实体，避免复用渲染状态时串用等级。 */
@Mixin(LivingEntityRenderState.class)
public abstract class InvisibilityRenderStateMixin implements InvisibilityRenderState {
    @Unique private int archweaver$invisibilityLevel;

    @Override
    public int archweaver$invisibilityLevel() {
        return archweaver$invisibilityLevel;
    }

    @Override
    public void archweaver$setInvisibilityLevel(int level) {
        archweaver$invisibilityLevel = level;
    }
}
