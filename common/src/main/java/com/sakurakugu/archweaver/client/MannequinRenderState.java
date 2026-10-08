package com.sakurakugu.archweaver.client;

import net.minecraft.core.Rotations;

/** 玩偶渲染状态中的四肢角度。 */
public interface MannequinRenderState {
    Rotations archweaver$leftArmAngles();
    Rotations archweaver$rightArmAngles();
    Rotations archweaver$leftLegAngles();
    Rotations archweaver$rightLegAngles();
    void archweaver$setAngles(Rotations leftArm, Rotations rightArm, Rotations leftLeg, Rotations rightLeg);
}
