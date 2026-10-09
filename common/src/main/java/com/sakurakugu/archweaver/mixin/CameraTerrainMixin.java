package com.sakurakugu.archweaver.mixin;

import com.sakurakugu.archweaver.client.camera.ClientCamera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 正交视锥不能通过后退来扩大，跳过原版仅适用于透视投影的无限循环。 */
@Mixin(LevelRenderer.class)
public abstract class CameraTerrainMixin {
    @Inject(method = "offsetFrustum", at = @At("HEAD"), cancellable = true)
    private static void archweaver$orthographicFrustum(Frustum frustum, CallbackInfoReturnable<Frustum> cir) {
        if (ClientCamera.orthographic()) cir.setReturnValue(new Frustum(frustum));
    }

    @Redirect(method = "cullTerrain", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;smartCull:Z"))
    private boolean archweaver$cameraOcclusion(net.minecraft.client.Minecraft minecraft) {
        return minecraft.smartCull && !ClientCamera.active();
    }
}
