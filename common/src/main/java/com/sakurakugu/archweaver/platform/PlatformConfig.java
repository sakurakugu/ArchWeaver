package com.sakurakugu.archweaver.platform;

import com.sakurakugu.archweaver.config.ArchWeaverConfig.ProfileStrategy;

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
    boolean fakePlayerAliasFirst();
    void setRestoreFakePlayers(boolean value);
    void setContainerTransferButtons(boolean value);
    void setFakePlayerAliasFirst(boolean value);
    void save();
}
