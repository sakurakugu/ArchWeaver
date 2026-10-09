package com.sakurakugu.archweaver.mixin;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sakurakugu.archweaver.client.camera.ClientCamera;
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
    @ModifyArg(method = "renderLevel", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setProjectionMatrix(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lcom/mojang/blaze3d/ProjectionType;)V", ordinal = 0), index = 1)
    private ProjectionType archweaver$projectionType(ProjectionType original) {
        return ClientCamera.orthographic() ? ProjectionType.ORTHOGRAPHIC : original;
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void archweaver$hurt(CameraRenderState state, PoseStack pose, CallbackInfo ci) {
        if (ClientCamera.active()) ci.cancel();
    }
    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void archweaver$walking(CameraRenderState state, PoseStack pose, CallbackInfo ci) {
        if (ClientCamera.active()) ci.cancel();
    }
}
