package com.sakurakugu.archweaver.config;

import com.sakurakugu.archweaver.platform.PlatformClientConfig;
import com.sakurakugu.archweaver.platform.PlatformConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import com.sakurakugu.archweaver.client.camera.CameraPreferences;
import java.util.EnumMap;

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
        private final ModConfigSpec.BooleanValue fakePlayerAliasFirst;
        private final ModConfigSpec.BooleanValue fakePlayerEmptyAliasMarker;

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
            restoreFakePlayers = builder.comment("游戏重启后是否默认恢复新生成的假玩家；可对每个假人单独设置。").define("restoreFakePlayers", true);
            builder.pop();
            builder.push("chunkloading");
            maxChunkLoadingRadius = builder.comment("单个区块加载点允许的最大半径。").defineInRange("maxRadius", 8, 0, 32);
            maxForcedChunks = builder.comment("强加载区块总预算。").defineInRange("maxForcedChunks", 2048, 1, 65536);
            maxTickingChunks = builder.comment("完整模拟区块总预算。").defineInRange("maxTickingChunks", 512, 1, 16384);
            maxPlayerLoadingChunks = builder.comment("假人模拟区块预算；-1 表示不限。").defineInRange("maxPlayerLoadingChunks", 65536, -1, 65536);
            builder.pop();
            builder.push("ui");
            containerTransferButtons = builder.comment("普通容器是否显示物品转移按钮。").define("enableContainerTransferButtons", false);
            fakePlayerAliasFirst = builder.comment("假人别名是否在 Tab 中优先显示，并在头顶与假人列表中显示于真实名称上方。").define("fakePlayerAliasFirst", false);
            fakePlayerEmptyAliasMarker = builder.comment("别名为空的假人是否用「假人」占位，作用于头顶名牌与假人列表。").define("fakePlayerEmptyAliasMarker", true);
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
        public boolean fakePlayerAliasFirst() { return fakePlayerAliasFirst.get(); }
        public boolean fakePlayerEmptyAliasMarker() { return fakePlayerEmptyAliasMarker.get(); }
        public void setRestoreFakePlayers(boolean value) { restoreFakePlayers.set(value); }
        public void setContainerTransferButtons(boolean value) { containerTransferButtons.set(value); }
        public void setFakePlayerAliasFirst(boolean value) { fakePlayerAliasFirst.set(value); }
        public void setFakePlayerEmptyAliasMarker(boolean value) { fakePlayerEmptyAliasMarker.set(value); }
        public void save() { spec.save(); }
    }

    public static final class ClientBackend implements PlatformClientConfig, CameraPreferences.Backend {
        private final ModConfigSpec spec;
        private final ModConfigSpec.DoubleValue markerNameScale;
        private final ModConfigSpec.BooleanValue weakLoadingVisible;
        private final ModConfigSpec.IntValue mainPageView;
        private final EnumMap<CameraPreferences.NumberSetting, ModConfigSpec.DoubleValue> cameraNumbers = new EnumMap<>(CameraPreferences.NumberSetting.class);
        private final EnumMap<CameraPreferences.Toggle, ModConfigSpec.BooleanValue> cameraToggles = new EnumMap<>(CameraPreferences.Toggle.class);

        private ClientBackend() {
            ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
            markerNameScale = builder.comment("地图上玩家名称的缩放比例。").defineInRange("chunkMap.markerNameScale", 1.0D, 0.5D, 2.0D);
            weakLoadingVisible = builder.comment("是否画出强加载区块外围的弱加载范围。").define("chunkMap.showWeakLoading", true);
            mainPageView = builder.comment("控制中心上次停留的页面：0 假人列表，1 区块地图，2 全局设置。")
                .defineInRange("mainPage.lastView", 0, 0, 2);
            builder.push("camera");
            for (var setting : CameraPreferences.NumberSetting.values()) {
                cameraNumbers.put(setting, builder.comment("相机参数：" + setting.key())
                    .defineInRange(setting.key(), setting.initial, setting.min, setting.max));
            }
            for (var setting : CameraPreferences.Toggle.values()) {
                cameraToggles.put(setting, builder.comment("相机开关：" + setting.key()).define(setting.key(), false));
            }
            builder.pop();
            spec = builder.build();
        }

        public ModConfigSpec spec() { return spec; }
        public double markerNameScale() { return markerNameScale.get(); }
        public void setMarkerNameScale(double value) { markerNameScale.set(value); }
        public boolean weakLoadingVisible() { return weakLoadingVisible.get(); }
        public void setWeakLoadingVisible(boolean value) { weakLoadingVisible.set(value); }
        public int mainPageView() { return mainPageView.get(); }
        public void setMainPageView(int value) { mainPageView.set(value); }
        public double number(CameraPreferences.NumberSetting setting) { return cameraNumbers.get(setting).get(); }
        public void number(CameraPreferences.NumberSetting setting, double value) { cameraNumbers.get(setting).set(setting.clamp(value)); }
        public boolean toggle(CameraPreferences.Toggle setting) { return cameraToggles.get(setting).get(); }
        public void toggle(CameraPreferences.Toggle setting, boolean value) { cameraToggles.get(setting).set(value); }
        public void save() { spec.save(); }
    }
}
