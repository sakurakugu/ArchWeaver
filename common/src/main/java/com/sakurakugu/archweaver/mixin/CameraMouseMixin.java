package com.sakurakugu.archweaver.mixin;

import com.sakurakugu.archweaver.client.camera.ClientCamera;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 复用原版灵敏度及鼠标反转设置，只替换最终转向目标。 */
@Mixin(MouseHandler.class)
public abstract class CameraMouseMixin {
    @WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void archweaver$turn(LocalPlayer player, double x, double y, Operation<Void> original) {
        if (ClientCamera.cameraMouse()) ClientCamera.turn(x, y);
        else original.call(player, x, y);
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void archweaver$scroll(long handle, double x, double y, CallbackInfo ci) {
        if (handle == Minecraft.getInstance().getWindow().handle() && ClientCamera.scroll(y)) ci.cancel();
    }
}
