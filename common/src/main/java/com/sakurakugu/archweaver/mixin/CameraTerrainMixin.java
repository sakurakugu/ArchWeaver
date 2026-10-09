package com.sakurakugu.archweaver.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.sakurakugu.archweaver.client.camera.ClientCamera;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 正交视锥不能通过后退来扩大，跳过原版仅适用于透视投影的无限循环。 */
@Mixin(LevelRenderer.class)
public abstract class CameraTerrainMixin {
    @Inject(method = "offsetFrustum", at = @At("HEAD"), cancellable = true)
    private static void archweaver$orthographicFrustum(Frustum frustum, CallbackInfoReturnable<Frustum> cir) {
        if (ClientCamera.orthographic()) cir.setReturnValue(new Frustum(frustum));
    }

    // Sodium 会覆盖 cullTerrain，并在自己的区块管理器中读取 smartCull。
    // 包装调用而非修改方法内部，同时兼容原版和 Sodium，并在异常时恢复调试选项。
    @WrapOperation(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;cullTerrain(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;Z)V"))
    private void archweaver$cameraOcclusion(LevelRenderer renderer, Camera camera, Frustum frustum,
                                           boolean spectator, Operation<Void> original) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean smartCull = minecraft.smartCull;
        if (ClientCamera.active()) minecraft.smartCull = false;
        try {
            original.call(renderer, camera, frustum, spectator);
        } finally {
            minecraft.smartCull = smartCull;
        }
    }
}
