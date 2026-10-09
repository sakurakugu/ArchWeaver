package com.sakurakugu.archweaver.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.sakurakugu.archweaver.client.camera.CameraMath;
import com.sakurakugu.archweaver.client.camera.CameraPreferences;
import com.sakurakugu.archweaver.client.camera.ClientCamera;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 在构建视锥之前调整相机，并让绘制和裁剪使用相同的正交参数。 */
@Mixin(Camera.class)
public abstract class CameraViewMixin {
    @Shadow private boolean detached;
    @Shadow private float depthFar;
    @Shadow protected abstract void setPosition(Vec3 position);
    @Shadow protected abstract void setRotation(float yaw, float pitch, float roll);
    @Shadow private void setupOrtho(float near, float far, float width, float height, boolean invertY) { }

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void archweaver$transform(float partial, CallbackInfo ci) {
        ClientCamera.Transform transform = ClientCamera.transform(partial);
        if (transform == null) return;
        detached = true;
        setRotation(transform.yaw(), transform.pitch(), 0);
        setPosition(transform.position());
    }

    @Inject(method = "createProjectionMatrixForCulling", at = @At("HEAD"), cancellable = true)
    private void archweaver$culling(CallbackInfoReturnable<Matrix4f> cir) {
        if (ClientCamera.renderOrthographic()) {
            var window = Minecraft.getInstance().getWindow();
            cir.setReturnValue(CameraMath.orthographic(CameraPreferences.get(CameraPreferences.NumberSetting.SCALE),
                (double) window.getWidth() / Math.max(1, window.getHeight()), depthFar, RenderSystem.getDevice().isZZeroToOne()));
        }
    }

    @Inject(method = "setupPerspective", at = @At("HEAD"), cancellable = true)
    private void archweaver$projection(float near, float far, float fov, float width, float height, CallbackInfo ci) {
        if (ClientCamera.renderOrthographic()) {
            float span = (float) CameraPreferences.get(CameraPreferences.NumberSetting.SCALE);
            setupOrtho(near, far, span * width / Math.max(1, height), span, false);
            ci.cancel();
        }
    }
}
