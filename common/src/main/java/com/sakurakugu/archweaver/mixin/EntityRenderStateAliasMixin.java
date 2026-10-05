package com.sakurakugu.archweaver.mixin;

import com.sakurakugu.archweaver.client.FakePlayerNameTagState;
import com.sakurakugu.archweaver.network.FakePlayerAliasPayload;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** 渲染状态单独保存别名，不覆盖原版名称和计分板行。 */
@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateAliasMixin implements FakePlayerNameTagState {
    @Unique
    private FakePlayerAliasPayload archweaver$aliasNameTag;

    @Override
    public FakePlayerAliasPayload archweaver$aliasNameTag() {
        return archweaver$aliasNameTag;
    }

    @Override
    public void archweaver$setAliasNameTag(FakePlayerAliasPayload payload) {
        archweaver$aliasNameTag = payload;
    }
}
