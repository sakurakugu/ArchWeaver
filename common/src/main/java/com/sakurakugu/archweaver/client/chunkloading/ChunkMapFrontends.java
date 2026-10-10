package com.sakurakugu.archweaver.client.chunkloading;

import com.sakurakugu.archweaver.ArchWeaverMod;
import com.sakurakugu.archweaver.network.ChunkMapSnapshotPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 共享层只认识前端契约，第三方类仅在平台插件中加载。 */
public final class ChunkMapFrontends {
    public interface JourneyMapProvider {
        boolean available();
        void open(ChunkMapSnapshotPayload snapshot, ClientChunkLoadingState.MapReturnTarget returnTarget) throws Exception;
        void tick();
        void clear();
    }

    private static JourneyMapProvider journeyMap;
    private static boolean journeyMapInstalled;
    private static ChunkLoadMapFrontend active;
    private static int refreshTicks;
    private static boolean bypass;
    private static boolean denied;

    private ChunkMapFrontends() { }
    public static void install(JourneyMapProvider provider) { journeyMap = provider; }
    /** 安装状态由平台提供，不受兼容插件运行故障影响。 */
    public static void setJourneyMapInstalled(boolean installed) { journeyMapInstalled = installed; }
    public static boolean journeyMapInstalled() { return journeyMapInstalled; }
    public static boolean journeyMapAvailable() { return journeyMap != null && journeyMap.available(); }
    public static ChunkLoadMapFrontend active() { return active; }
    public static boolean isMap(Screen screen) { return screen != null && active != null && active.screen() == screen; }
    public static void activate(ChunkLoadMapFrontend frontend) {
        if (active == frontend) return;
        if (active != null) active.dispose();
        active = frontend;
        denied = false;
        refreshTicks = 0;
    }

    public static void open(ChunkMapSnapshotPayload snapshot, ClientChunkLoadingState.MapReturnTarget returnTarget) {
        if (ChunkMapClientConfig.journeyMapPreferred() && journeyMapAvailable()) {
            try {
                journeyMap.open(snapshot, returnTarget);
                return;
            } catch (Exception | LinkageError exception) {
                ArchWeaverMod.LOGGER.warn("无法打开 JourneyMap，回退到内置地图", exception);
                message(Component.translatable("gui.archweaver.chunkloader.journey_failed"));
            }
        }
        Minecraft.getInstance().setScreen(new ChunkMapScreen(snapshot, returnTarget));
    }

    public static String dimension() {
        var minecraft = Minecraft.getInstance();
        return active != null ? active.controller().snapshot().dimension()
            : minecraft.level == null ? "" : minecraft.level.dimension().identifier().toString();
    }

    public static void accept(ChunkMapSnapshotPayload snapshot) {
        if (active != null && active.controller().snapshot().dimension().equals(snapshot.dimension())) {
            active.acceptSnapshot(snapshot);
        }
    }

    public static void deny(String reason) {
        denied = true;
        if (active != null) active.accessDenied();
        message(Component.literal(reason));
    }

    public static void tick() {
        if (journeyMap != null) journeyMap.tick();
        if (active == null || Minecraft.getInstance().player == null) return;
        active.controller().tick();
        var screen = Minecraft.getInstance().screen;
        if (!denied && (isMap(screen) || screen instanceof ChunkMapManagementScreen
            || screen instanceof ChunkMapSettingsScreen || screen instanceof UnsavedChunkMapScreen) && refreshTicks-- <= 0) {
            ClientChunkLoadingState.refresh(dimension());
            refreshTicks = 10;
        }
    }

    /** 离开地图前统一确认未保存草稿。 */
    public static void leave(Runnable continuation) {
        if (active != null && active.controller().dirty()) {
            Minecraft.getInstance().setScreen(new UnsavedChunkMapScreen(
                Minecraft.getInstance().screen, active.controller(), () -> runWithoutGuard(continuation)));
        } else {
            runWithoutGuard(continuation);
        }
    }

    /** 返回替换屏幕给平台事件，避免在 ScreenOpening 回调内递归 setScreen。 */
    public static Screen guard(Screen current, Screen next) {
        if (bypass || active == null || current instanceof UnsavedChunkMapScreen) return next;
        if (isMap(current) && active.journeyMap() && next == null
            && active.returnTarget() == ClientChunkLoadingState.MapReturnTarget.MAIN) {
            var parent = com.sakurakugu.archweaver.client.ClientScreenNavigation.parentOf(current);
            var snapshot = active.controller().snapshot();
            next = parent == null ? new com.sakurakugu.archweaver.client.MainPageScreen(snapshot,
                com.sakurakugu.archweaver.client.MainPageScreen.View.MAP) : parent;
        }
        if (next == active.screen() || next instanceof ChunkMapManagementScreen
            || next instanceof ChunkMapSettingsScreen || next instanceof UnsavedChunkMapScreen) return next;
        if (!isMap(current) && !(current instanceof ChunkMapSettingsScreen)
            && !(current instanceof ChunkMapManagementScreen)) return next;
        if (Minecraft.getInstance().player != null && active.controller().dirty()) {
            Screen destination = next;
            return new UnsavedChunkMapScreen(current, active.controller(), () ->
                runWithoutGuard(() -> {
                    deactivate();
                    Minecraft.getInstance().setScreen(destination);
                }));
        }
        deactivate();
        return next;
    }

    public static void runWithoutGuard(Runnable action) {
        boolean previous = bypass;
        bypass = true;
        try { action.run(); } finally { bypass = previous; }
    }

    public static void deactivate() {
        if (active != null) active.dispose();
        active = null;
    }

    public static void clear() {
        deactivate();
        if (journeyMap != null) journeyMap.clear();
        denied = false;
        refreshTicks = 0;
    }

    public static void message(Component message) {
        var player = Minecraft.getInstance().player;
        if (player != null) Minecraft.getInstance().getChatListener().handleSystemMessage(message, false);
    }
}
