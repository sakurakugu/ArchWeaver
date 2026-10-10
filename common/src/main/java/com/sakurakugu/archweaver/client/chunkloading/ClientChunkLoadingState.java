package com.sakurakugu.archweaver.client.chunkloading;

import com.sakurakugu.archweaver.client.ClientGlobalSettings;
import com.sakurakugu.archweaver.client.ClientScreenNavigation;
import com.sakurakugu.archweaver.client.MainPageScreen;
import com.sakurakugu.archweaver.config.ArchWeaverConfig;
import com.sakurakugu.archweaver.network.ChunkMapSnapshotPayload;
import com.sakurakugu.archweaver.network.ChunkMapOpenTarget;
import com.sakurakugu.archweaver.network.RequestChunkMapPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/** 保存服务端最近一次同步的加载点快照。 */
public final class ClientChunkLoadingState {
    private static ChunkMapSnapshotPayload snapshot;
    private static final ChunkMapSnapshotCache CACHE = new ChunkMapSnapshotCache();
    private static long nextRequestId;
    private static long pendingOpenId;
    private static final java.util.Map<Long, String> REQUESTS = new java.util.HashMap<>();
    private static boolean mainScreenPending;
    /** 等待快照期间要打开的主页面视图，只在 {@link #mainScreenPending} 为真时有意义。 */
    private static MainPageScreen.View pendingMainView = MainPageScreen.View.FAKE_PLAYERS;
    private static MapReturnTarget mapReturnTarget = MapReturnTarget.CLOSE;
    private static ClientLevel terrainLevel;
    private static ChunkTerrainAtlas terrainAtlas;

    private ClientChunkLoadingState() {
    }

    public static void accept(ChunkMapSnapshotPayload value) {
        REQUESTS.remove(value.requestId());
        ChunkMapSnapshotPayload effective = CACHE.accept(value);
        if (effective == null) return;
        snapshot = effective;
        ChunkMapFrontends.accept(effective);
        boolean openResponse = value.requestId() == pendingOpenId;

        int transferSetting = ArchWeaverConfig.GlobalSetting.CONTAINER_TRANSFER_BUTTONS.ordinal();
        ClientGlobalSettings.setContainerTransferButtons(
            (value.globalSettingsMask() & (1 << transferSetting)) != 0);
        if (value.openTarget() != ChunkMapOpenTarget.NONE && openResponse) {
            mainScreenPending = false;
            MapReturnTarget returnTarget = mapReturnTarget;
            mapReturnTarget = MapReturnTarget.CLOSE;
            if (value.openTarget() != ChunkMapOpenTarget.MAP) {
                ChunkMapScreen.openPanel(effective, value.openTarget());
            } else {
                ChunkMapFrontends.open(effective, returnTarget);
            }
        } else if (mainScreenPending && openResponse) {
            mainScreenPending = false;
            MainPageScreen.View view = pendingMainView;
            pendingMainView = MainPageScreen.View.FAKE_PLAYERS;
            Minecraft.getInstance().setScreen(new MainPageScreen(effective, view));
        } else if (Minecraft.getInstance().screen instanceof ChunkMapScreen) {
            // 活动地图已在上面按维度更新，不接受其他维度的推送。
        } else if (Minecraft.getInstance().screen instanceof MainPageScreen screen) {
            screen.update(effective);
        } else {
            if (ChunkMapFrontends.active() == null || effective.dimension().equals(ChunkMapFrontends.dimension()))
                ClientScreenNavigation.updateBackground(effective);
        }
    }

    /** 按已知快照构造请求，让服务端能跳过区块列表。 */
    public static RequestChunkMapPayload request(ChunkMapOpenTarget openTarget) {
        String dimension = openTarget == ChunkMapOpenTarget.NONE ? ChunkMapFrontends.dimension() : playerDimension();
        return request(openTarget, dimension);
    }

    public static String playerDimension() {
        var level = Minecraft.getInstance().level;
        return level == null ? "" : level.dimension().identifier().toString();
    }

    public static RequestChunkMapPayload request(ChunkMapOpenTarget target, String dimension) {
        var known = CACHE.get(dimension);
        long id = ++nextRequestId;
        REQUESTS.put(id, dimension);
        // 请求历史只用于错误归属，限制长期打开地图时的内存占用。
        if (REQUESTS.size() > 256) REQUESTS.keySet().removeIf(key -> key < id - 128);
        return new RequestChunkMapPayload(target, known == null ? RequestChunkMapPayload.NO_REVISION : known.revision(),
            known == null ? "" : known.dimension(), dimension, id);
    }

    public static ChunkMapSnapshotPayload snapshot(String dimension) { return CACHE.get(dimension); }

    public static long refresh(String dimension) {
        var request = request(ChunkMapOpenTarget.NONE, dimension);
        com.sakurakugu.archweaver.platform.PlatformNetworking.sendToServer(request);
        return request.requestId();
    }

    public static void acceptResult(com.sakurakugu.archweaver.network.ChunkMapApplyResultPayload result) {
        var active = ChunkMapFrontends.active();
        if (active != null && active.controller().acceptResult(result) && !result.successful())
            ChunkMapFrontends.message(net.minecraft.network.chat.Component.literal(result.reason()));
    }

    public static void requestFailed(com.sakurakugu.archweaver.network.ChunkMapRequestFailedPayload failure) {
        String dimension = REQUESTS.remove(failure.requestId());
        if (failure.requestId() == pendingOpenId) mainScreenPending = false;
        if (dimension != null && dimension.equals(ChunkMapFrontends.dimension())) ChunkMapFrontends.deny(failure.reason());
    }

    /** 从指定页面打开地图，返回键和 Esc 会回到该页面。 */
    public static void openMap(MapReturnTarget returnTarget, ChunkMapOpenTarget openTarget) {
        mapReturnTarget = returnTarget;
        var request = request(openTarget);
        pendingOpenId = request.requestId();
        com.sakurakugu.archweaver.platform.PlatformNetworking.sendToServer(request);
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

    /**
     * 打开总览页并回到上次停留的页面；
     * 没有服务端快照时先请求一次，再由快照回调完成打开。
     */
    public static void openMainScreen() {
        openMainScreen(MainPageScreen.fromIndex(ChunkMapClientConfig.mainPageView()));
    }

    /** 打开总览页并指定进入时显示的页面。 */
    public static void openMainScreen(MainPageScreen.View view) {
        if (snapshot != null) {
            mainScreenPending = false;
            Minecraft.getInstance().setScreen(new MainPageScreen(snapshot, view));
            return;
        }
        pendingMainView = view;
        mainScreenPending = true;
        var request = request(ChunkMapOpenTarget.NONE, playerDimension());
        pendingOpenId = request.requestId();
        com.sakurakugu.archweaver.platform.PlatformNetworking.sendToServer(request);
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
        CACHE.clear();
        REQUESTS.clear();
        pendingOpenId = 0;
        ChunkMapFrontends.clear();
        mainScreenPending = false;
        pendingMainView = MainPageScreen.View.FAKE_PLAYERS;
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
