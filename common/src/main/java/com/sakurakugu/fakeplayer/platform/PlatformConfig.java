package com.sakurakugu.fakeplayer.platform;

import com.sakurakugu.fakeplayer.config.FakePlayerConfig.ProfileStrategy;

/** 服务端配置的类型化平台后端。 */
public interface PlatformConfig {
    int permissionLevel();
    boolean allowOfflineProfiles();
    ProfileStrategy profileStrategy();
    boolean restoreFakePlayers();
    int maxChunkLoadingRadius();
    int maxForcedChunks();
    int maxTickingChunks();
    int maxPlayerLoadingChunks();
    boolean containerTransferButtons();
    void setRestoreFakePlayers(boolean value);
    void setContainerTransferButtons(boolean value);
    void save();
}
