package com.sakurakugu.archweaver.api.fakeplayer;

import java.util.UUID;

/** 已登记假人的持久化清单项；未加载时没有实体位置。 */
public record RegisteredFakePlayerInfo(UUID id, String name, boolean loaded, boolean restoreOnRestart) {
}
