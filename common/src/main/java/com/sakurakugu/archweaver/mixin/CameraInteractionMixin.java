package com.sakurakugu.archweaver.mixin;

import com.sakurakugu.archweaver.client.camera.ClientCamera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 身体交互关闭时禁用攻击和使用；打开时原版射线和距离校验完整保留。 */
@Mixin(Minecraft.class)
public abstract class CameraInteractionMixin {
    @Inject(method = "pick", at = @At("HEAD"))
    private void archweaver$shoulderAim(float partial, CallbackInfo ci) {
        ClientCamera.updateShoulderAim(partial);
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void archweaver$attack(CallbackInfoReturnable<Boolean> cir) {
        if (ClientCamera.blockInteraction()) cir.setReturnValue(false);
    }
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void archweaver$use(CallbackInfo ci) {
        if (ClientCamera.blockInteraction()) ci.cancel();
    }
    @Inject(method = "pickBlockOrEntity", at = @At("HEAD"), cancellable = true)
    private void archweaver$pick(CallbackInfo ci) {
        if (ClientCamera.blockInteraction()) ci.cancel();
    }
    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void archweaver$breaking(boolean down, CallbackInfo ci) {
        if (ClientCamera.blockInteraction()) {
            var mc = Minecraft.getInstance();
            if (mc.gameMode != null) mc.gameMode.stopDestroyBlock();
            ci.cancel();
        }
    }
}
