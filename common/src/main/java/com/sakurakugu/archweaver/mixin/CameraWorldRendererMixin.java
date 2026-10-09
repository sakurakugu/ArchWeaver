package com.sakurakugu.archweaver.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sakurakugu.archweaver.client.camera.ClientCamera;
import net.minecraft.client.CameraType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 世界渲染声明正确的投影类型，观察相机不继承身体的摇晃。 */
@Mixin(GameRenderer.class)
public abstract class CameraWorldRendererMixin {
    /** 只覆写提取出的渲染选项；预览期间不修改玩家实际的原版视角。 */
    @ModifyExpressionValue(method = "extractOptions", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;getCameraType()Lnet/minecraft/client/CameraType;"))
    private CameraType archweaver$previewCameraType(CameraType original) {
        return ClientCamera.renderActive() ? CameraType.THIRD_PERSON_BACK : original;
    }

    @ModifyArg(method = "renderLevel", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setProjectionMatrix(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lcom/mojang/blaze3d/ProjectionType;)V", ordinal = 0), index = 1)
    private ProjectionType archweaver$projectionType(ProjectionType original) {
        return ClientCamera.renderOrthographic() ? ProjectionType.ORTHOGRAPHIC : original;
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void archweaver$hurt(CameraRenderState state, PoseStack pose, CallbackInfo ci) {
        if (ClientCamera.renderActive()) ci.cancel();
    }
    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void archweaver$walking(CameraRenderState state, PoseStack pose, CallbackInfo ci) {
        if (ClientCamera.renderActive()) ci.cancel();
    }
}
