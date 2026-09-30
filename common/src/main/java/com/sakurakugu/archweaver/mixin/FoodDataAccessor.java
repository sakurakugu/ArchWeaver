package com.sakurakugu.archweaver.mixin;

import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 补足原版饥饿数据缺少的读写接口，以便身体状态完整交换。 */
@Mixin(FoodData.class)
public interface FoodDataAccessor {
    @Accessor("exhaustionLevel")
    float archweaver$getExhaustionLevel();

    @Accessor("exhaustionLevel")
    void archweaver$setExhaustionLevel(float value);

    @Accessor("tickTimer")
    int archweaver$getTickTimer();

    @Accessor("tickTimer")
    void archweaver$setTickTimer(int value);
}
