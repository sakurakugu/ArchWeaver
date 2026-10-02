package com.sakurakugu.archweaver.client.chunkloading;

import com.sakurakugu.archweaver.client.ClientGlobalSettings;
import com.sakurakugu.archweaver.client.ClientScreenNavigation;
import com.sakurakugu.archweaver.client.MainPageScreen;
import com.sakurakugu.archweaver.config.ArchWeaverConfig;
import com.sakurakugu.archweaver.network.ChunkMapSnapshotPayload;
import com.sakurakugu.archweaver.network.RequestChunkMapPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/** 保存服务端最近一次同步的加载点快照。 */
public final class ClientChunkLoadingState {
    private static ChunkMapSnapshotPayload snapshot;
    private static boolean mainScreenPending;
    private static MapReturnTarget mapReturnTarget = MapReturnTarget.CLOSE;
    private static ClientLevel terrainLevel;
    private static ChunkTerrainAtlas terrainAtlas;

    private ClientChunkLoadingState() {
    }

    public static void accept(ChunkMapSnapshotPayload value) {
        ChunkMapSnapshotPayload previous = snapshot;
        // 服务端说区域没变时列表是空的，得把上一份拼回去
        boolean mergeable = value.regionsUnchanged() && previous != null
            && value.dimension().equals(previous.dimension());
        ChunkMapSnapshotPayload effective = mergeable ? value.withPreviousRegions(previous) : value;
        // 拼不出来（刚连接或刚换维度）就忘掉已知 revision，下一次请求要全量
        boolean lostRegions = value.regionsUnchanged() && !mergeable;
        snapshot = lostRegions ? null : effective;

        int transferSetting = ArchWeaverConfig.GlobalSetting.CONTAINER_TRANSFER_BUTTONS.ordinal();
        ClientGlobalSettings.setContainerTransferButtons(
            (value.globalSettingsMask() & (1 << transferSetting)) != 0);
        if (value.openScreen()) {
            mainScreenPending = false;
            MapReturnTarget returnTarget = mapReturnTarget;
            mapReturnTarget = MapReturnTarget.CLOSE;
            if (value.openManagement() || value.openSettings()) {
                ChunkMapScreen.openPanel(effective, value.openManagement(), value.openSettings());
            } else {
                Minecraft.getInstance().setScreen(new ChunkMapScreen(effective, returnTarget));
            }
        } else if (mainScreenPending) {
            mainScreenPending = false;
            Minecraft.getInstance().setScreen(new MainPageScreen(effective));
        } else if (Minecraft.getInstance().screen instanceof ChunkMapScreen screen) {
            screen.update(effective);
        } else if (Minecraft.getInstance().screen instanceof MainPageScreen screen) {
            screen.update(effective);
        } else {
            ClientScreenNavigation.updateBackground(effective);
        }
    }

    /** 按已知快照构造请求，让服务端能跳过区块列表。 */
    public static RequestChunkMapPayload request(boolean openScreen, boolean openManagement,
                                                 boolean openSettings) {
        ChunkMapSnapshotPayload known = snapshot;
        return known == null
            ? new RequestChunkMapPayload(openScreen, openManagement, openSettings)
            : new RequestChunkMapPayload(openScreen, openManagement, openSettings,
                known.revision(), known.dimension());
    }

    /** 从指定页面打开地图，返回键和 Esc 会回到该页面。 */
    public static void openMap(MapReturnTarget returnTarget, boolean management, boolean settings) {
        mapReturnTarget = returnTarget;
        com.sakurakugu.archweaver.platform.PlatformNetworking.sendToServer(
            request(true, management, settings));
    }

    /** 从地图页面返回进入地图前的页面。关闭按钮不调用此方法。 */
    public static void returnFromMap(MapReturnTarget returnTarget) {
        switch (returnTarget) {
            case MAIN -> {
                net.minecraft.client.gui.screens.Screen current = Minecraft.getInstance().screen;
                if (current != null) ClientScreenNavigation.back(current);
                else openMainScreen();
            }
            case CLOSE -> Minecraft.getInstance().setScreen(null);
        }
    }

    public static ChunkMapSnapshotPayload snapshot() {
        return snapshot;
    }

    /** 打开总览页；没有服务端快照时先请求一次，再由快照回调完成打开。 */
    public static void openMainScreen() {
        if (snapshot != null) {
            mainScreenPending = false;
            Minecraft.getInstance().setScreen(new MainPageScreen(snapshot));
            return;
        }
        mainScreenPending = true;
        com.sakurakugu.archweaver.platform.PlatformNetworking.sendToServer(request(false, false, false));
    }

    /** 图集跟随连接存在：换维度时释放重建，同一个世界内数据一直有效。 */
    static ChunkTerrainAtlas terrainAtlas() {
        Minecraft minecraft = Minecraft.getInstance();
        if (terrainAtlas == null || terrainLevel != minecraft.level) {
            closeTerrainAtlas();
            terrainLevel = minecraft.level;
            terrainAtlas = new ChunkTerrainAtlas(minecraft);
        }
        return terrainAtlas;
    }

    public static void clear() {
        snapshot = null;
        mainScreenPending = false;
        mapReturnTarget = MapReturnTarget.CLOSE;
        ClientScreenNavigation.clear();
        ClientGlobalSettings.clear();
        closeTerrainAtlas();
    }

    private static void closeTerrainAtlas() {
        if (terrainAtlas != null) terrainAtlas.close();
        terrainAtlas = null;
        terrainLevel = null;
    }

    public enum MapReturnTarget {
        CLOSE,
        MAIN
    }
}
