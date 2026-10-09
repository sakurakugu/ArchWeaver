package com.sakurakugu.archweaver.mixin;

import com.sakurakugu.archweaver.client.camera.ClientCamera;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 相机读取真实按键，只有玩家输入被清空，避免相机移动传到服务端。 */
@Mixin(KeyboardInput.class)
public abstract class CameraInputMixin extends ClientInput {
    @Inject(method = "tick", at = @At("TAIL"))
    private void archweaver$bodyInput(CallbackInfo ci) {
        if (ClientCamera.blockMovement()) { keyPresses = Input.EMPTY; moveVector = Vec2.ZERO; }
    }
}
