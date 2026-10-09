package com.sakurakugu.archweaver.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.sakurakugu.archweaver.client.camera.CameraMath;
import com.sakurakugu.archweaver.client.camera.ClientCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Projection;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 原版正交矩阵以左下角为原点，世界相机改用居中矩阵，保留原版版本号管理。 */
@Mixin(Projection.class)
public abstract class CameraProjectionMixin {
    @Shadow public abstract float zFar();
    @Shadow public abstract float width();
    @Shadow public abstract float height();

    @Inject(method = "getMatrix", at = @At("RETURN"))
    private void archweaver$center(Matrix4f dest, CallbackInfoReturnable<Matrix4f> cir) {
        if (ClientCamera.renderOrthographic()
            && ((CameraProjectionAccessor) Minecraft.getInstance().gameRenderer.getMainCamera()).archweaver$projection() == (Object) this) {
            dest.set(CameraMath.orthographic(height(), width() / height(), zFar(), RenderSystem.getDevice().isZZeroToOne()));
        }
    }
}
