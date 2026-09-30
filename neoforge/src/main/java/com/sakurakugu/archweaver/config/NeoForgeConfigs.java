package com.sakurakugu.archweaver.config;

import com.sakurakugu.archweaver.platform.PlatformClientConfig;
import com.sakurakugu.archweaver.platform.PlatformConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** NeoForge 配置文件后端。 */
public final class NeoForgeConfigs {
    public static final Backend SERVER = new Backend(false);
    public static final ClientBackend CLIENT = new ClientBackend();

    private NeoForgeConfigs() {
    }

    public static final class Backend implements PlatformConfig {
        private final ModConfigSpec spec;
        private final ModConfigSpec.IntValue permissionLevel;
        private final ModConfigSpec.BooleanValue allowOfflineProfiles;
        private final ModConfigSpec.EnumValue<ArchWeaverConfig.ProfileStrategy> profileStrategy;
        private final ModConfigSpec.BooleanValue restoreFakePlayers;
        private final ModConfigSpec.IntValue maxChunkLoadingRadius;
        private final ModConfigSpec.IntValue maxForcedChunks;
        private final ModConfigSpec.IntValue maxTickingChunks;
        private final ModConfigSpec.IntValue maxPlayerLoadingChunks;
        private final ModConfigSpec.BooleanValue containerTransferButtons;

        private Backend(boolean ignored) {
            ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
            builder.push("commands");
            permissionLevel = builder.comment("使用命令的最低原版权限等级，范围 0-4。").defineInRange("permissionLevel", 2, 0, 4);
            builder.pop();
            builder.push("profiles");
            allowOfflineProfiles = builder.comment("缓存和在线档案均不可用时，是否允许使用离线 UUID。").define("allowOfflineProfiles", true);
            profileStrategy = builder.comment("ONLINE_PREFERRED、CACHE_ONLY 或 OFFLINE_ONLY。").defineEnum("strategy", ArchWeaverConfig.ProfileStrategy.ONLINE_PREFERRED);
            builder.pop();
            builder.push("persistence");
            restoreFakePlayers = builder.comment("服务器启动后是否恢复上次仍在线的假玩家。").define("restoreFakePlayers", true);
            builder.pop();
            builder.push("chunkloading");
            maxChunkLoadingRadius = builder.comment("单个区块加载点允许的最大半径。").defineInRange("maxRadius", 8, 0, 32);
            maxForcedChunks = builder.comment("强加载区块总预算。").defineInRange("maxForcedChunks", 2048, 1, 65536);
            maxTickingChunks = builder.comment("完整模拟区块总预算。").defineInRange("maxTickingChunks", 512, 1, 16384);
            maxPlayerLoadingChunks = builder.comment("假人模拟区块预算；-1 表示不限。").defineInRange("maxPlayerLoadingChunks", 65536, -1, 65536);
            builder.pop();
            builder.push("ui");
            containerTransferButtons = builder.comment("普通容器是否显示物品转移按钮。").define("enableContainerTransferButtons", true);
            builder.pop();
            spec = builder.build();
        }

        public ModConfigSpec spec() { return spec; }
        public int permissionLevel() { return permissionLevel.get(); }
        public boolean allowOfflineProfiles() { return allowOfflineProfiles.get(); }
        public ArchWeaverConfig.ProfileStrategy profileStrategy() { return profileStrategy.get(); }
        public boolean restoreFakePlayers() { return restoreFakePlayers.get(); }
        public int maxChunkLoadingRadius() { return maxChunkLoadingRadius.get(); }
        public int maxForcedChunks() { return maxForcedChunks.get(); }
        public int maxTickingChunks() { return maxTickingChunks.get(); }
        public int maxPlayerLoadingChunks() { return maxPlayerLoadingChunks.get(); }
        public boolean containerTransferButtons() { return containerTransferButtons.get(); }
        public void setRestoreFakePlayers(boolean value) { restoreFakePlayers.set(value); }
        public void setContainerTransferButtons(boolean value) { containerTransferButtons.set(value); }
        public void save() { spec.save(); }
    }

    public static final class ClientBackend implements PlatformClientConfig {
        private final ModConfigSpec spec;
        private final ModConfigSpec.DoubleValue markerNameScale;
        private final ModConfigSpec.BooleanValue weakLoadingVisible;

        private ClientBackend() {
            ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
            markerNameScale = builder.comment("地图上玩家名称的缩放比例。").defineInRange("chunkMap.markerNameScale", 1.0D, 0.5D, 2.0D);
            weakLoadingVisible = builder.comment("是否画出强加载区块外围的弱加载范围。").define("chunkMap.showWeakLoading", true);
            spec = builder.build();
        }

        public ModConfigSpec spec() { return spec; }
        public double markerNameScale() { return markerNameScale.get(); }
        public void setMarkerNameScale(double value) { markerNameScale.set(value); }
        public boolean weakLoadingVisible() { return weakLoadingVisible.get(); }
        public void setWeakLoadingVisible(boolean value) { weakLoadingVisible.set(value); }
        public void save() { spec.save(); }
    }
}
