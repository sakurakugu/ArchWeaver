package com.sakurakugu.archweaver.mixin;

import com.sakurakugu.archweaver.entity.MannequinLook;
import net.minecraft.world.entity.decoration.Mannequin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** 给玩偶挂上头身联动开关；其余视角数据都写在原版旋转字段里。 */
@Mixin(Mannequin.class)
public abstract class MannequinLookMixin implements MannequinLook {
    @Unique private boolean archweaver$bodyFollowsHeadFlag;

    @Override public boolean archweaver$bodyFollowsHead() { return archweaver$bodyFollowsHeadFlag; }

    @Override public void archweaver$setBodyFollowsHead(boolean follows) { archweaver$bodyFollowsHeadFlag = follows; }
}
