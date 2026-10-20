package com.sakurakugu.archweaver.config;

import com.sakurakugu.archweaver.platform.PlatformConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** 服务端设置按存档隔离，与 NeoForge 的 SERVER 配置作用域一致。 */
public final class FabricServerConfig extends FabricConfigFile implements PlatformConfig {
    public static final FabricServerConfig INSTANCE = new FabricServerConfig();
    private FabricServerConfig() { }
    public void load(MinecraftServer server) {
        load(server.getWorldPath(LevelResource.ROOT).resolve("serverconfig/archweaver.properties"));
        permissionLevel();
        allowOfflineProfiles();
        profileStrategy();
        restoreFakePlayers();
        maxChunkLoadingRadius();
        maxForcedChunks();
        maxTickingChunks();
        maxPlayerLoadingChunks();
        containerTransferButtons();
        fakePlayerAliasFirst();
        fakePlayerEmptyAliasMarker();
        save();
    }
    public int permissionLevel() { return integer("commands.permissionLevel", 2, 0, 4); }
    public boolean allowOfflineProfiles() { return bool("profiles.allowOfflineProfiles", true); }
    public ArchWeaverConfig.ProfileStrategy profileStrategy() { return choice("profiles.strategy", ArchWeaverConfig.ProfileStrategy.ONLINE_PREFERRED); }
    public boolean restoreFakePlayers() { return bool("persistence.restoreFakePlayers", true); }
    public int maxChunkLoadingRadius() { return integer("chunkloading.maxRadius", 8, 0, 32); }
    public int maxForcedChunks() { return integer("chunkloading.maxForcedChunks", 2048, 1, 65536); }
    public int maxTickingChunks() { return integer("chunkloading.maxTickingChunks", 512, 1, 16384); }
    public int maxPlayerLoadingChunks() { return integer("chunkloading.maxPlayerLoadingChunks", 65536, -1, 65536); }
    public boolean containerTransferButtons() { return bool("ui.enableContainerTransferButtons", false); }
    public boolean fakePlayerAliasFirst() { return bool("ui.fakePlayerAliasFirst", false); }
    public boolean fakePlayerEmptyAliasMarker() { return bool("ui.fakePlayerEmptyAliasMarker", true); }
    public void setRestoreFakePlayers(boolean value) { put("persistence.restoreFakePlayers", value); }
    public void setContainerTransferButtons(boolean value) { put("ui.enableContainerTransferButtons", value); }
    public void setFakePlayerAliasFirst(boolean value) { put("ui.fakePlayerAliasFirst", value); }
    public void setFakePlayerEmptyAliasMarker(boolean value) { put("ui.fakePlayerEmptyAliasMarker", value); }
}
