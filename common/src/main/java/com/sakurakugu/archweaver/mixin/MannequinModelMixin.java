package com.sakurakugu.archweaver.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.Rotations;
import com.sakurakugu.archweaver.client.MannequinRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 将玩偶的自定义角度覆盖到玩家模型四肢。 */
@Mixin(HumanoidModel.class)
public abstract class MannequinModelMixin {
    @Shadow public ModelPart rightArm;
    @Shadow public ModelPart leftArm;
    @Shadow public ModelPart rightLeg;
    @Shadow public ModelPart leftLeg;

    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void archweaver$apply(HumanoidRenderState state, CallbackInfo callback) {
        if (!(state instanceof MannequinRenderState angles)) return;
        apply(rightArm, angles.archweaver$rightArmAngles());
        apply(leftArm, angles.archweaver$leftArmAngles());
        apply(rightLeg, angles.archweaver$rightLegAngles());
        apply(leftLeg, angles.archweaver$leftLegAngles());
    }

    private static void apply(ModelPart part, Rotations rotations) {
        if (rotations == null) return;
        part.xRot += rotations.x() * (float)(Math.PI / 180.0);
        part.yRot += rotations.y() * (float)(Math.PI / 180.0);
        part.zRot += rotations.z() * (float)(Math.PI / 180.0);
    }
}
