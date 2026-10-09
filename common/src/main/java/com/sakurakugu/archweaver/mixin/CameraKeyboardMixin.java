package com.sakurakugu.archweaver.mixin;

import com.sakurakugu.archweaver.client.camera.CameraSelectorScreen;
import com.sakurakugu.archweaver.client.camera.ClientCamera;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 提前消费视角组合键，防止原版 F5 和调试界面一起响应。 */
@Mixin(KeyboardHandler.class)
public abstract class CameraKeyboardMixin {
    @Shadow private boolean usedDebugKeyAsModifier;

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void archweaver$selector(long handle, int action, KeyEvent event, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (handle != mc.getWindow().handle() || mc.player == null || mc.screen != null
            || !mc.options.keyTogglePerspective.matches(event)) return;
        if (mc.options.keyDebugModifier.isDown()) {
            if (action == 1) {
                usedDebugKeyAsModifier = true;
                mc.setScreen(new CameraSelectorScreen());
            }
            ci.cancel();
        } else if (ClientCamera.active()) {
            if (action == 1) ClientCamera.cycleMode();
            ci.cancel();
        }
    }
}
