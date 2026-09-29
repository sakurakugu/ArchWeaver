package com.sakurakugu.fakeplayer.chunkloading;

import java.util.UUID;

/** 假玩家加载配置；位置和维度始终从在线实体读取。 */
public record FakePlayerLoadPolicy(UUID fakePlayerId, FakePlayerLoadMode mode, int simulationDistance) {
    public FakePlayerLoadPolicy {
        if (mode == null) throw new IllegalArgumentException("假玩家加载模式不能为空");
    }

    public boolean usesCustomSimulation() {
        return mode == FakePlayerLoadMode.DOLL;
    }
}
