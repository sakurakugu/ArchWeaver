package com.sakurakugu.archweaver.mixin;

import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.Rotations;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.Avatar;
import com.sakurakugu.archweaver.client.MannequinRenderState;
import com.sakurakugu.archweaver.client.ClientMannequinAngles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 从客户端 mannequin 实体提取四肢角度到渲染状态。 */
@Mixin(AvatarRenderer.class)
public abstract class MannequinAvatarRendererMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void archweaver$extract(Avatar entity, AvatarRenderState state, float partialTick, CallbackInfo callback) {
        if (entity instanceof Mannequin) {
            ((MannequinRenderState) state).archweaver$setAngles(toRotations(ClientMannequinAngles.get(entity.getUUID(), 0)),
                toRotations(ClientMannequinAngles.get(entity.getUUID(), 1)),
                toRotations(ClientMannequinAngles.get(entity.getUUID(), 2)),
                toRotations(ClientMannequinAngles.get(entity.getUUID(), 3)));
        } else {
            ((MannequinRenderState) state).archweaver$setAngles(null, null, null, null);
        }
    }

    private static Rotations toRotations(com.sakurakugu.archweaver.persistence.MannequinSavedData.Angles angles) {
        return new Rotations(angles.x(), angles.y(), angles.z());
    }
}
