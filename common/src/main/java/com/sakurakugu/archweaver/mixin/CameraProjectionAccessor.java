package com.sakurakugu.archweaver.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.Projection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 只识别世界相机的投影，避免改变 GUI 与手部的投影。 */
@Mixin(Camera.class)
public interface CameraProjectionAccessor {
    @Accessor("projection") Projection archweaver$projection();
}
