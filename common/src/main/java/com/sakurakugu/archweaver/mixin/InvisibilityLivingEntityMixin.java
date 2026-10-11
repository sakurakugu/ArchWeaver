package com.sakurakugu.archweaver.mixin;

import com.sakurakugu.archweaver.invisibility.InvisibilityLevels;
import com.sakurakugu.archweaver.invisibility.InvisibilityState;
import com.sakurakugu.archweaver.invisibility.InvisibilitySync;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 等级仅存在于运行时，保存和效果叠加继续交给原版。 */
@Mixin(LivingEntity.class)
public abstract class InvisibilityLivingEntityMixin implements InvisibilityState {
    @Unique private int archweaver$clientInvisibilityLevel = -1;
    @Unique private int archweaver$lastInvisibilityLevel;

    @Override
    public int archweaver$invisibilityLevel() {
        return archweaver$clientInvisibilityLevel;
    }

    @Override
    public void archweaver$setInvisibilityLevel(int level) {
        archweaver$clientInvisibilityLevel = level;
    }

    @Inject(method = {"tickEffects", "updateInvisibilityStatus"}, at = @At("TAIL"))
    private void archweaver$syncLevel(CallbackInfo callback) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (entity.level().isClientSide()) return;
        int level = InvisibilityLevels.effectLevel(entity);
        if (level != archweaver$lastInvisibilityLevel) {
            archweaver$lastInvisibilityLevel = level;
            InvisibilitySync.broadcast(entity);
        }
    }
}
