package com.sakurakugu.archweaver.mixin;

import com.sakurakugu.archweaver.entity.AvatarModelParts;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** 复用原版同步字段，不向原版实体添加额外的同步字段。 */
@Mixin(Avatar.class)
public abstract class AvatarModelPartsMixin implements AvatarModelParts {
    @Shadow protected static EntityDataAccessor<Byte> DATA_PLAYER_MODE_CUSTOMISATION;

    @Override public int archweaver$modelParts() {
        return ((Avatar) (Object) this).getEntityData().get(DATA_PLAYER_MODE_CUSTOMISATION) & 0xFF;
    }

    @Override public void archweaver$setModelParts(int mask) {
        ((Avatar) (Object) this).getEntityData().set(DATA_PLAYER_MODE_CUSTOMISATION, (byte) (mask & 127));
    }
}
