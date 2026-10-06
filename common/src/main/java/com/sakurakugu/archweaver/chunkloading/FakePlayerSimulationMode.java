package com.sakurakugu.archweaver.chunkloading;

/** 假人的模拟加载策略，与玩家/玩偶行为模式独立。 */
public enum FakePlayerSimulationMode {
    FOLLOW_SERVER, // 自动：跟随服务器模拟距离。
    DISABLED,      // 关闭：不主动维持区块加载。
    CUSTOM         // 手动：使用自定义模拟距离。
}
