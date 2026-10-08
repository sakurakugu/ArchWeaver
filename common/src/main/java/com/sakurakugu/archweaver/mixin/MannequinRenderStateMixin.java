package com.sakurakugu.archweaver.mixin;

import net.minecraft.core.Rotations;
import com.sakurakugu.archweaver.client.MannequinRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** 为 AvatarRenderState 保留玩偶的姿势角度。 */
@Mixin(AvatarRenderState.class)
public abstract class MannequinRenderStateMixin implements MannequinRenderState {
    @Unique private Rotations archweaver$leftArm = new Rotations(0, 0, 0);
    @Unique private Rotations archweaver$rightArm = new Rotations(0, 0, 0);
    @Unique private Rotations archweaver$leftLeg = new Rotations(0, 0, 0);
    @Unique private Rotations archweaver$rightLeg = new Rotations(0, 0, 0);
    @Override public Rotations archweaver$leftArmAngles() { return archweaver$leftArm; }
    @Override public Rotations archweaver$rightArmAngles() { return archweaver$rightArm; }
    @Override public Rotations archweaver$leftLegAngles() { return archweaver$leftLeg; }
    @Override public Rotations archweaver$rightLegAngles() { return archweaver$rightLeg; }
    @Override public void archweaver$setAngles(Rotations leftArm, Rotations rightArm, Rotations leftLeg, Rotations rightLeg) {
        archweaver$leftArm = leftArm; archweaver$rightArm = rightArm;
        archweaver$leftLeg = leftLeg; archweaver$rightLeg = rightLeg;
    }
}
