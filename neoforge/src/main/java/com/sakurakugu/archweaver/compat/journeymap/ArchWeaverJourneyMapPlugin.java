package com.sakurakugu.archweaver.compat.journeymap;

import com.sakurakugu.archweaver.ArchWeaverMod;
import com.sakurakugu.archweaver.chunkloading.ChunkKey;
import com.sakurakugu.archweaver.chunkloading.FakePlayerSimulationMode;
import com.sakurakugu.archweaver.client.chunkloading.*;
import com.sakurakugu.archweaver.network.ChunkMapOpenTarget;
import com.sakurakugu.archweaver.network.ChunkMapSnapshotPayload;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.display.*;
import journeymap.api.v2.client.event.*;
import journeymap.api.v2.client.fullscreen.*;
import journeymap.api.v2.client.model.*;
import journeymap.api.v2.client.util.UIState;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.event.FullscreenEventRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.*;

/** 仅由 JourneyMap 的客户端插件发现器加载，未安装 JourneyMap 时不加载任何 API 类。 */
@JourneyMapPlugin(apiVersion = "2.0.0")
public final class ArchWeaverJourneyMapPlugin implements IClientPlugin, ChunkMapFrontends.JourneyMapProvider {
    private static final String MOD = ArchWeaverMod.MOD_ID;
    private static ArchWeaverJourneyMapPlugin instance;
    private IClientAPI api;
    private Session session;
    private boolean failed;
    private IFullscreen buttonsFor;
    private IThemeButton editButton, weakButton, saveButton, undoButton;
    private final List<IThemeButton> buttons = new ArrayList<>();

    @Override public String getModId() { return MOD; }

    @Override public void initialize(IClientAPI api) {
        this.api = api;
        instance = this;
        ChunkMapFrontends.install(this);
        FullscreenEventRegistry.ADDON_BUTTON_DISPLAY_EVENT.subscribe(MOD, this::addButtons);
        FullscreenEventRegistry.FULLSCREEN_MAP_CLICK_EVENT.subscribe(MOD, event -> {
            if (event.getStage() == FullscreenMapEvent.Stage.PRE && !event.isCancelled()
                && paint(event.getLocation(), event.dimension, event.getButton(), event.getMouseX(), event.getMouseY(), false)) event.cancel();
        });
        FullscreenEventRegistry.FULLSCREEN_MAP_DRAG_EVENT.subscribe(MOD, event -> {
            if (event.getStage() == FullscreenMapEvent.Stage.PRE && !event.isCancelled()
                && paint(event.getLocation(), event.dimension, event.getButton(), event.getMouseX(), event.getMouseY(), true)) event.cancel();
        });
        FullscreenEventRegistry.FULLSCREEN_MAP_MOVE_EVENT.subscribe(MOD, event -> {
            if (session != null) session.hover = event.getLocation();
        });
        FullscreenEventRegistry.FULLSCREEN_RENDER_EVENT.subscribe(MOD, this::render);
        ArchWeaverMod.LOGGER.info("JourneyMap 全屏地图兼容已启用（API 2.0.0）");
    }

    @Override public boolean available() { return api != null && !failed; }

    @Override public void open(ChunkMapSnapshotPayload snapshot, ClientChunkLoadingState.MapReturnTarget target) throws Exception {
        // API 没有打开全屏地图的方法，唯一的非 API 调用集中在这里；失败由共享层回退。
        Class.forName("journeymap.client.ui.dialog.FullscreenActions").getMethod("open").invoke(null);
        if (!(Minecraft.getInstance().screen instanceof IFullscreen fullscreen)) {
            throw new IllegalStateException("JourneyMap 未打开全屏地图");
        }
        session = new Session(fullscreen, snapshot, target, true);
        ChunkMapFrontends.activate(session);
        com.sakurakugu.archweaver.client.ClientScreenNavigation.registerMap(session.screen());
        session.selectMapDimension(snapshot.dimension());
        fullscreen.centerOn(snapshot.playerChunkX() * 16.0 + 8, snapshot.playerChunkZ() * 16.0 + 8);
        updateButtons();
    }

    @Override public void tick() {
        if (!available()) return;
        try {
            var minecraft = Minecraft.getInstance();
            if (minecraft.player == null) return;
            if (minecraft.screen instanceof IFullscreen fullscreen
                && (session == null || ChunkMapFrontends.active() != session || session.screen() != minecraft.screen)) {
                String dimension = ClientChunkLoadingState.playerDimension();
                var known = ClientChunkLoadingState.snapshot(dimension);
                session = new Session(fullscreen, known == null ? empty(dimension) : known,
                    ClientChunkLoadingState.MapReturnTarget.CLOSE, false);
                ChunkMapFrontends.activate(session);
                com.sakurakugu.archweaver.client.ClientScreenNavigation.registerMap(session.screen());
                session.minimumRequest = ClientChunkLoadingState.refresh(dimension);
            }
            if (session == null || ChunkMapFrontends.active() != session) return;
            // 查看其他维度不切换编辑会话，返回当前维度后继续原来的草稿。
            if (!session.editableDimension()) session.hover = session.lastPaint = null;
            session.updateOverlays();
            updateButtons();
        } catch (Exception | LinkageError exception) {
            fail(exception);
        }
    }

    private void fail(Throwable exception) {
        failed = true;
        ArchWeaverMod.LOGGER.warn("JourneyMap 兼容发生错误，停用覆盖物", exception);
        if (session != null) session.dispose();
        ChunkMapFrontends.message(Component.translatable("gui.archweaver.chunkloader.journey_failed"));
        // 保存同一个控制器和草稿，出错后仍可保存或放弃编辑。
        if (session != null && ChunkMapFrontends.active() == session) {
            Session previous = session;
            com.sakurakugu.archweaver.client.ClientScreenNavigation.prepareMapReplacement(previous.screen());
            ChunkMapFrontends.runWithoutGuard(() -> Minecraft.getInstance().setScreen(
                new ChunkMapScreen(previous.controller, previous.target)));
        }
    }

    @Override public void clear() {
        if (session != null) session.dispose();
        session = null;
        buttonsFor = null;
        buttons.clear();
    }

    private static ChunkMapSnapshotPayload empty(String dimension) {
        return new ChunkMapSnapshotPayload(ChunkMapOpenTarget.NONE, 0, 32, Long.MIN_VALUE,
            false, dimension, 0, 0, List.of(), List.of(), List.of());
    }

    private static ResourceKey<Level> dimensionKey(String dimension) {
        return ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimension));
    }

    /** 工具栏使用与内置地图相同的像素字形纹理。 */
    private static Identifier buttonIcon(String name) {
        return Identifier.fromNamespaceAndPath(MOD, "textures/gui/journeymap/" + name + ".png");
    }

    private void addButtons(FullscreenDisplayEvent.AddonButtonDisplayEvent event) {
        buttonsFor = event.getFullscreen();
        buttons.clear();
        var display = event.getThemeButtonDisplay();
        editButton = display.addThemeToggleButton("gui.archweaver.chunkloader.map_edit_mode", buttonIcon("add"), false, button -> {
            // API 的点击回调不会自动切换按钮，依据控制器状态切换后再同步显示。
            if (activeSession() && session.ready && session.editableDimension() && !session.controller.awaitingApply())
                session.setEditMode(session.controller.mode() == ChunkMapEditMode.EDIT ? ChunkMapEditMode.BROWSE : ChunkMapEditMode.EDIT);
            updateButtons();
        });
        weakButton = display.addThemeToggleButton("gui.archweaver.chunkloader.map_weak_range", buttonIcon("reset"),
            ChunkMapClientConfig.weakLoadingVisible(), button -> {
                boolean visible = !ChunkMapClientConfig.weakLoadingVisible();
                ChunkMapClientConfig.setWeakLoadingVisible(visible);
                ChunkMapClientConfig.save();
                if (activeSession()) session.controller.setShowWeakLoading(visible);
                updateButtons();
            });
        undoButton = display.addThemeButton("gui.archweaver.chunkloader.map_undo", buttonIcon("undo"), button -> {
            if (activeSession()) session.controller.undo();
        });
        saveButton = display.addThemeButton("gui.archweaver.chunkloader.map_save", buttonIcon("save"), button -> {
            if (activeSession() && session.editableDimension()) session.controller.apply();
        });
        buttons.addAll(List.of(editButton, weakButton, undoButton, saveButton));
        buttons.add(display.addThemeButton("gui.archweaver.chunkloader.bottom_management", buttonIcon("cube"), button -> {
            if (activeSession() && session.ready) ChunkMapScreen.openPanel(session.controller.snapshot(), ChunkMapOpenTarget.MANAGEMENT);
        }));
        updateButtons();
    }

    private boolean activeSession() { return session != null && ChunkMapFrontends.active() == session; }

    private void updateButtons() {
        if (editButton == null) return;
        boolean active = activeSession() && session.fullscreen == buttonsFor;
        boolean editable = active && session.editableDimension();
        editButton.setEnabled(editable && session.ready && !session.controller.awaitingApply());
        editButton.setToggled(editable && session.controller.mode() == ChunkMapEditMode.EDIT);
        weakButton.setToggled(ChunkMapClientConfig.weakLoadingVisible());
        saveButton.setEnabled(editable && session.ready && session.controller.dirty() && !session.controller.awaitingApply());
        undoButton.setEnabled(editable && session.controller.canUndo());
    }

    private boolean paint(BlockPos pos, ResourceKey<Level> dimension, int button, double mouseX, double mouseY, boolean drag) {
        if (!activeSession() || Minecraft.getInstance().screen != session.screen()
            || session.controller.mode() != ChunkMapEditMode.EDIT || (button != 0 && button != 1)) return false;
        if (!session.editableDimension()
            || !dimension.identifier().toString().equals(session.controller.snapshot().dimension())) return false;
        var state = session.fullscreen.getUiState();
        if (state.displayBounds == null || !state.displayBounds.contains(mouseX, mouseY)) return false;
        // 地图事件可能覆盖侧边工具区；任何按钮上都不能绘制草稿。
        for (var child : session.screen().children()) {
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget
                && widget.visible && widget.isMouseOver(mouseX, mouseY)) return false;
        }
        if (!session.ready || session.controller.awaitingApply()) return true;
        int x = pos.getX() >> 4, z = pos.getZ() >> 4;
        if (drag && session.lastPaint != null && session.lastButton == button) {
            // 连续拖动采用整数直线插值，快速移动鼠标也不会漏格。
            int oldX = session.lastPaint.getX() >> 4, oldZ = session.lastPaint.getZ() >> 4;
            int steps = Math.max(Math.abs(x - oldX), Math.abs(z - oldZ));
            if (steps <= 1024) for (int i = 1; i < steps; i++)
                session.controller.edit((int) Math.round(oldX + (x - oldX) * (double) i / steps),
                    (int) Math.round(oldZ + (z - oldZ) * (double) i / steps), button == 1);
        }
        session.controller.edit(x, z, button == 1);
        session.lastPaint = pos;
        session.lastButton = button;
        return true;
    }

    private void render(FullscreenRenderEvent event) {
        if (!activeSession() || !session.editableDimension() || session.fullscreen != event.getFullscreen()
            || Minecraft.getInstance().screen != session.screen()) return;
        var graphics = event.getGraphics();
        var font = Minecraft.getInstance().font;
        String failure = session.controller.failure();
        if (!failure.isEmpty()) graphics.centeredText(font, Component.literal(failure), session.screen().width / 2, 32, 0xFFFF8888);
        if (session.controller.mode() == ChunkMapEditMode.EDIT) graphics.centeredText(font,
            Component.translatable("gui.archweaver.chunkloader.map_edit_hint"), session.screen().width / 2, 18, 0xFFFFFFFF);
        UIState state = event.getFullscreen().getUiState();
        if (state.displayBounds == null || state.blockBounds == null
            || !state.displayBounds.contains(event.getMouseX(), event.getMouseY()) || session.hover == null) return;
        int x = session.hover.getX() >> 4, z = session.hover.getZ() >> 4;
        double pixels = state.blockSize;
        int left = (int) Math.round(state.displayBounds.x + (x * 16.0 - state.blockBounds.minX) * pixels);
        int top = (int) Math.round(state.displayBounds.y + (z * 16.0 - state.blockBounds.minZ) * pixels);
        graphics.enableScissor((int) state.displayBounds.x, (int) state.displayBounds.y,
            (int) state.displayBounds.getMaxX(), (int) state.displayBounds.getMaxY());
        graphics.outline(left, top, Math.max(1, (int) Math.ceil(16 * pixels)), Math.max(1, (int) Math.ceil(16 * pixels)), 0xFFFFFFFF);
        graphics.disableScissor();

    }

    /** 底部信息栏每帧读取当前草稿，鼠标静止时保存、撤销和快照刷新也能立即更新。 */
    public static Component appendStatusInfo(Component original) {
        var plugin = instance;
        if (plugin == null || !plugin.available() || !plugin.activeSession()) return original;
        var session = plugin.session;
        if (!session.ready || Minecraft.getInstance().screen != session.screen() || session.hover == null
            || !session.fullscreen.getUiState().dimension.identifier().toString().equals(session.controller.snapshot().dimension()))
            return original;
        int x = session.hover.getX() >> 4, z = session.hover.getZ() >> 4;
        String names = session.controller.snapshot().regions().stream()
            .filter(region -> region.dimension().equals(session.controller.snapshot().dimension()) && region.contains(x, z))
            .map(ChunkMapSnapshotPayload.RegionView::name).reduce((a, b) -> a + ", " + b).orElse("");
        if (session.controller.snapshot().fakePlayers().stream().anyMatch(fake -> fake.loadsChunk(session.controller.snapshot().dimension(), x, z)))
            names += " " + Component.translatable("archweaver.chunkloader.fake_label").getString();
        var level = session.controller.levels().get(ChunkKey.pack(x, z));
        if (level != null) names += " " + Component.translatable(level == ChunkMapLoadLevel.STRONG
            ? "gui.archweaver.chunkloader.level_strong" : "gui.archweaver.chunkloader.level_weak").getString();
        Component loading = names.isBlank() ? Component.translatable("gui.archweaver.chunkloader.map_hover.none")
            : Component.literal(names.strip());
        // 坐标、方块和生物群系继续使用 JourneyMap 原有的文本与显示设置。
        return original.copy().append(" | ")
            .append(Component.translatable("gui.archweaver.chunkloader.map_hover.chunk", x, z))
            .append(" | ").append(Component.translatable("gui.archweaver.chunkloader.map_hover.regions", loading));
    }

    private final class Session implements ChunkLoadMapFrontend {
        private final IFullscreen fullscreen;
        private final ChunkLoadMapController controller;
        private final ClientChunkLoadingState.MapReturnTarget target;
        private boolean ready;
        private long minimumRequest;
        private BlockPos hover, lastPaint;
        private int lastButton;
        private String highlightName, highlightDimension;
        private final Map<Object, Overlay> overlays = new LinkedHashMap<>();
        private Map<Long, ChunkMapLoadLevel> lastLevels;
        private List<ChunkMapSnapshotPayload.FakePlayerView> lastFakes;
        private Set<Long> lastHighlight;
        private boolean lastRangeUsesGridTexture;

        private Session(IFullscreen fullscreen, ChunkMapSnapshotPayload snapshot,
                        ClientChunkLoadingState.MapReturnTarget target, boolean ready) {
            this.fullscreen = fullscreen;
            this.controller = new ChunkLoadMapController(snapshot);
            controller.setShowWeakLoading(ChunkMapClientConfig.weakLoadingVisible());
            this.target = target;
            this.ready = ready;
        }

        @Override public ChunkLoadMapController controller() { return controller; }
        @Override public Screen screen() { return fullscreen.getScreen(); }
        @Override public boolean journeyMap() { return true; }
        @Override public ClientChunkLoadingState.MapReturnTarget returnTarget() { return target; }
        @Override public void accessDenied() { ready = false; controller.setMode(ChunkMapEditMode.BROWSE); }
        @Override public void setEditMode(ChunkMapEditMode mode) {
            if (mode == ChunkMapEditMode.BROWSE || (ready && editableDimension())) controller.setMode(mode);
        }
        @Override public void close() { ClientChunkLoadingState.returnFromMap(target); }

        @Override public void acceptSnapshot(ChunkMapSnapshotPayload snapshot) {
            if (!ready && snapshot.requestId() < minimumRequest) return;
            controller.accept(snapshot);
            ready = true;
        }

        private void selectMapDimension(String dimension) {
            var state = fullscreen.getUiState();
            fullscreen.updateMapType(state.mapType, state.chunkY, dimensionKey(dimension));
        }

        private boolean editableDimension() {
            String dimension = controller.snapshot().dimension();
            return dimension.equals(ClientChunkLoadingState.playerDimension())
                && dimension.equals(fullscreen.getUiState().dimension.identifier().toString());
        }

        @Override public void focus(String dimension, double x, double z) {
            if (!supportsDimension(dimension)) return;
            ChunkMapFrontends.leave(() -> {
                selectMapDimension(dimension);
                fullscreen.centerOn(x, z);
                Minecraft.getInstance().setScreen(screen());
            });
        }

        @Override public boolean highlightRegion(String name, String dimension) {
            if (!supportsDimension(dimension)) {
                clearHighlightedRegion();
                return false;
            }
            highlightName = name;
            highlightDimension = dimension;
            return true;
        }

        @Override public void clearHighlightedRegion() { highlightName = highlightDimension = null; }

        @Override public void focusHighlightedRegion() {
            if (highlightName == null) return;
            if (!supportsDimension(highlightDimension)) return;
            var region = highlighted();
            if (region == null || region.chunks().isEmpty()) return;
            selectMapDimension(highlightDimension);
            if (Minecraft.getInstance().screen instanceof ChunkMapManagementScreen) Minecraft.getInstance().setScreen(screen());
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
            for (long chunk : region.chunks()) {
                minX = Math.min(minX, ChunkKey.x(chunk)); maxX = Math.max(maxX, ChunkKey.x(chunk));
                minZ = Math.min(minZ, ChunkKey.z(chunk)); maxZ = Math.max(maxZ, ChunkKey.z(chunk));
            }
            fullscreen.centerOn(((double) minX + maxX + 1) * 8, ((double) minZ + maxZ + 1) * 8);
            // 放大区域超出视口时逐级缩小，次数上限来自 JourneyMap 的缩放范围。
            for (int i = 0; i < 14; i++) {
                var bounds = fullscreen.getUiState().blockBounds;
                if (bounds == null || (bounds.getXsize() >= ((double) maxX - minX + 3) * 16
                    && bounds.getZsize() >= ((double) maxZ - minZ + 3) * 16)) break;
                fullscreen.zoomOut();
            }
        }

        private ChunkMapSnapshotPayload.RegionView highlighted() {
            return controller.snapshot().regions().stream().filter(region -> region.name().equals(highlightName)
                && region.dimension().equals(highlightDimension)).findFirst().orElse(null);
        }

        private void updateOverlays() throws Exception {
            var levels = controller.levels();
            var fakes = controller.snapshot().fakePlayers();
            var region = highlighted();
            Set<Long> highlight = region == null ? Set.of() : region.chunks();
            boolean rangeUsesGridTexture = fullscreen.getUiState().blockSize >= 1;
            if (levels == lastLevels && fakes.equals(lastFakes) && highlight.equals(lastHighlight)
                && rangeUsesGridTexture == lastRangeUsesGridTexture) return;
            Map<Object, java.util.function.Supplier<Overlay>> wanted = new LinkedHashMap<>();
            for (var rectangle : ChunkMapRectangles.merge(levels)) {
                wanted.put(rectangle, () -> polygon(rectangle.minX(), rectangle.minZ(), rectangle.maxX(), rectangle.maxZ(),
                    rectangle.level() == ChunkMapLoadLevel.STRONG ? 0xD18B35 : 0xC7A13A, 0.4F, 0, 0));
            }
            for (var fake : fakes) {
                String rangeDimension;
                int x, z, radius;
                if (fake.mode() == FakePlayerSimulationMode.CUSTOM && fake.loadingActive()) {
                    rangeDimension = fake.loadingDimension(); x = fake.loadingChunkX(); z = fake.loadingChunkZ(); radius = fake.loadingDistance();
                } else if (fake.mode() == FakePlayerSimulationMode.FOLLOW_SERVER && fake.online()) {
                    rangeDimension = fake.dimension(); x = fake.x() >> 4; z = fake.z() >> 4; radius = fake.simulationDistance();
                } else continue;
                if (rangeDimension.equals(controller.snapshot().dimension())) {
                    wanted.put(List.of("range", fake.id(), x, z, radius, rangeUsesGridTexture),
                        () -> rangeBorder(x - radius, z - radius, x + radius, z + radius, rangeUsesGridTexture));
                }
            }
            Map<Long, ChunkMapLoadLevel> highlightedCells = new HashMap<>();
            highlight.forEach(chunk -> highlightedCells.put(chunk, ChunkMapLoadLevel.STRONG));
            for (var rectangle : ChunkMapRectangles.merge(highlightedCells)) wanted.put(List.of("highlight", rectangle),
                () -> polygon(rectangle.minX(), rectangle.minZ(), rectangle.maxX(), rectangle.maxZ(), 0x51C8B4, 0.4F, 0xF6DC70, 2));
            var iterator = overlays.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                if (!wanted.containsKey(entry.getKey())) { api.remove(entry.getValue()); iterator.remove(); }
            }
            for (var entry : wanted.entrySet()) if (!overlays.containsKey(entry.getKey())) {
                var overlay = entry.getValue().get();
                overlay.setActiveUIs(Context.UI.Fullscreen).setOverlayGroupName("ArchWeaver");
                api.show(overlay);
                overlays.put(entry.getKey(), overlay);
            }
            lastLevels = levels; lastFakes = fakes; lastHighlight = highlight;
            lastRangeUsesGridTexture = rangeUsesGridTexture;
        }

        private PolygonOverlay rangeBorder(int minX, int minZ, int maxX, int maxZ, boolean usesGridTexture) {
            // JourneyMap 在一方块至少一像素时使用一方块宽的网格纹理，缩小时改用细线。
            if (!usesGridTexture) return polygon(minX, minZ, maxX, maxZ, 0, 0, 0x4EC9E8, 1);
            int x0 = minX * 16, z0 = minZ * 16, x1 = (maxX + 1) * 16, z1 = (maxZ + 1) * 16;
            // 挖空矩形内部，四条边都与网格纹理从区块边界开始的一方块宽度对齐。
            var border = new MapPolygonWithHoles(blockRectangle(x0, z0, x1 + 1, z1 + 1),
                List.of(blockRectangle(x0 + 1, z0 + 1, x1, z1)));
            var shape = new ShapeProperties().setFillColor(0x4EC9E8).setFillOpacity(1)
                .setStrokeOpacity(0).setStrokeWidth(0);
            return new PolygonOverlay(MOD, dimensionKey(controller.snapshot().dimension()), shape, border);
        }

        private MapPolygon blockRectangle(int x0, int z0, int x1, int z1) {
            return new MapPolygon(new BlockPos(x0, 0, z0), new BlockPos(x1, 0, z0),
                new BlockPos(x1, 0, z1), new BlockPos(x0, 0, z1));
        }

        private PolygonOverlay polygon(int minX, int minZ, int maxX, int maxZ, int fill, float opacity, int stroke, float width) {
            int x0 = minX * 16, z0 = minZ * 16, x1 = (maxX + 1) * 16, z1 = (maxZ + 1) * 16;
            var shape = new ShapeProperties().setFillColor(fill).setFillOpacity(opacity)
                .setStrokeColor(stroke).setStrokeOpacity(width == 0 ? 0 : 1).setStrokeWidth(width);
            return new PolygonOverlay(MOD, dimensionKey(controller.snapshot().dimension()), shape,
                blockRectangle(x0, z0, x1, z1));
        }

        @Override public void dispose() {
            for (var overlay : overlays.values()) {
                try { api.remove(overlay); }
                catch (RuntimeException | LinkageError exception) {
                    ArchWeaverMod.LOGGER.warn("无法移除 JourneyMap 覆盖物", exception);
                }
            }
            overlays.clear();
            lastLevels = null; lastFakes = null; lastHighlight = null;
        }
    }
}
