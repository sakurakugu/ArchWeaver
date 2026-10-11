package com.sakurakugu.archweaver.client.chunkloading;

import com.sakurakugu.archweaver.ArchWeaverMod;
import com.sakurakugu.archweaver.network.ChunkMapSnapshotPayload;
import net.minecraft.client.Minecraft;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 共享层只认识前端契约，第三方类仅在平台插件中加载。 */
public final class ChunkMapFrontends {
    public interface MapProvider {
        boolean available();
        void open(ChunkMapSnapshotPayload snapshot, ClientChunkLoadingState.MapReturnTarget returnTarget) throws Exception;
        void tick();
        void clear();
    }

    private static final EnumMap<MapBackend, MapProvider> PROVIDERS = new EnumMap<>(MapBackend.class);
    private static final EnumSet<MapBackend> INSTALLED = EnumSet.of(MapBackend.BUILTIN);
    private static ChunkLoadMapFrontend active;
    private static int refreshTicks;
    private static boolean bypass;
    private static boolean denied;

    private ChunkMapFrontends() { }
    public static void install(MapBackend backend, MapProvider provider) { PROVIDERS.put(backend, provider); }
    /** 安装状态由平台提供，不受兼容插件运行故障影响。 */
    public static void setInstalled(MapBackend backend, boolean installed) {
        if (backend == MapBackend.BUILTIN) return;
        if (installed) INSTALLED.add(backend); else INSTALLED.remove(backend);
    }
    public static List<MapBackend> installedBackends() { return List.copyOf(INSTALLED); }
    public static MapBackend selectedInstalledBackend() {
        return MapBackend.resolve(ChunkMapClientConfig.mapBackend(), INSTALLED);
    }
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
        MapBackend backend = selectedInstalledBackend();
        var provider = PROVIDERS.get(backend);
        var previous = active;
        try {
            if (provider != null && provider.available()) {
                provider.open(snapshot, returnTarget);
                return;
            } else if (backend != MapBackend.BUILTIN) {
                message(Component.translatable("gui.archweaver.chunkloader.backend_failed", Component.translatable(backend.labelKey())));
            }
        } catch (Exception | LinkageError exception) {
            ArchWeaverMod.LOGGER.warn("无法打开 {}，回退到内置地图", backend, exception);
            message(Component.translatable("gui.archweaver.chunkloader.backend_failed", Component.translatable(backend.labelKey())));
        }
        // 第三方屏幕初始化到一半才报错时，保留已经建立的控制器与草稿。
        if (active != null && active != previous && active.externalMap()) {
            var opened = active;
            com.sakurakugu.archweaver.client.ClientScreenNavigation.prepareMapReplacement(opened.screen());
            runWithoutGuard(() -> Minecraft.getInstance().setScreen(new ChunkMapScreen(opened.controller(), returnTarget)));
            return;
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
        for (var provider : PROVIDERS.values()) provider.tick();
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
        if (isMap(current) && active.externalMap() && next == null
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
        for (var provider : PROVIDERS.values()) provider.clear();
        denied = false;
        refreshTicks = 0;
    }

    public static void message(Component message) {
        var player = Minecraft.getInstance().player;
        if (player != null) Minecraft.getInstance().getChatListener().handleSystemMessage(message, false);
    }
}
