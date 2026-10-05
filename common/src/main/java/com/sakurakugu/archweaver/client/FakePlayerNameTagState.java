package com.sakurakugu.archweaver.client;

import com.sakurakugu.archweaver.network.FakePlayerAliasPayload;

/** 将提取阶段的别名快照传递给延迟执行的名牌渲染阶段。 */
public interface FakePlayerNameTagState {
    FakePlayerAliasPayload archweaver$aliasNameTag();
    void archweaver$setAliasNameTag(FakePlayerAliasPayload payload);
}
