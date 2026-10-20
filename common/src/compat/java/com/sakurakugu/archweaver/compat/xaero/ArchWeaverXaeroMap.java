package com.sakurakugu.archweaver.compat.xaero;

import com.sakurakugu.archweaver.ArchWeaverMod;
import com.sakurakugu.archweaver.chunkloading.ChunkKey;
import com.sakurakugu.archweaver.chunkloading.FakePlayerSimulationMode;
import com.sakurakugu.archweaver.client.ClientScreenNavigation;
import com.sakurakugu.archweaver.client.chunkloading.*;
import com.sakurakugu.archweaver.client.ui.PixelGlyph;
import com.sakurakugu.archweaver.client.ui.SolidButton;
import com.sakurakugu.archweaver.compat.xaero.mixin.XaeroScreenBaseAccessor;
import com.sakurakugu.archweaver.network.ChunkMapOpenTarget;
import com.sakurakugu.archweaver.network.ChunkMapSnapshotPayload;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.map.WorldMapSession;
import xaero.map.graphics.MapRenderHelper;
import xaero.map.gui.GuiMap;

/** 可选客户端兼容层；复用共享控制器，不写入 Xaero 的地图缓存或标记数据。 */
public final class ArchWeaverXaeroMap implements ChunkMapFrontends.MapProvider {
    // 与原生地图按钮同一套配色：无底板，可用时近白，悬停压暗并上移 1px，关闭或不可用时暗灰。
    private static final int ICON_IDLE_COLOR = 0xFFFCFCFC;
    private static final int ICON_HOVER_COLOR = 0xFFE6E6E6;
    private static final int ICON_OFF_COLOR = 0xFF404040;
    private static final int ICON_SIZE = 20; // 原生左侧图标列的按钮边长，对齐同一列。
    // 原生地图的坐标与生物群系显示占住顶部 2..20 行，ArchWeaver 的提示接在它们下面。
    private static final int STATUS_TOP = 22;
    private static final int STATUS_LINE_HEIGHT = 10; // 原生提示文字的行高，底板恰好写满一行。
    private static ArchWeaverXaeroMap instance;
    private Session session;
    private boolean failed;
    private Throwable pendingFailure;

    public static void install() {
        try {
            instance = new ArchWeaverXaeroMap();
            ChunkMapFrontends.install(MapBackend.XAERO, instance);
            if (!instance.available()) throw new IllegalStateException("Xaero 地图钩子未安装");
            ArchWeaverMod.LOGGER.info("Xaero 世界地图兼容已启用（1.47.0）");
        } catch (RuntimeException | LinkageError exception) {
            if (instance != null) instance.failed = true;
            ArchWeaverMod.LOGGER.warn("无法初始化 Xaero 世界地图兼容层", exception);
        }
    }

    @Override public boolean available() {
        return !failed && pendingFailure == null && XaeroMapAccess.class.isAssignableFrom(GuiMap.class)
            && XaeroScreenBaseAccessor.class.isAssignableFrom(xaero.lib.client.gui.ScreenBase.class);
    }

    @Override public void open(ChunkMapSnapshotPayload snapshot, ClientChunkLoadingState.MapReturnTarget target) {
        var minecraft = Minecraft.getInstance();
        var world = WorldMapSession.getCurrentSession();
        if (world == null || !world.isUsable() || minecraft.player == null)
            throw new IllegalStateException("Xaero 地图会话尚未就绪");
        var map = new GuiMap(null, null, world.getMapProcessor(), minecraft.getCameraEntity());
        session = new Session(map, snapshot, target, true);
        ChunkMapFrontends.activate(session);
        minecraft.setScreen(map);
        ClientScreenNavigation.registerMap(map);
        session.requestFocus(snapshot.dimension(), snapshot.playerChunkX() * 16.0 + 8,
            snapshot.playerChunkZ() * 16.0 + 8, 0, 0);
        if (pendingFailure != null) throw new IllegalStateException("Xaero 地图初始化失败", pendingFailure);
    }

    private boolean active(GuiMap map) {
        return session != null && session.map == map && ChunkMapFrontends.active() == session;
    }

    private void attach(GuiMap map) {
        if (active(map)) return;
        String dimension = ClientChunkLoadingState.playerDimension();
        var snapshot = ClientChunkLoadingState.snapshot(dimension);
        if (snapshot == null) snapshot = new ChunkMapSnapshotPayload(ChunkMapOpenTarget.NONE, 0, 32,
            Long.MIN_VALUE, false, dimension, 0, 0, List.of(), List.of(), List.of());
        session = new Session(map, snapshot, ClientChunkLoadingState.MapReturnTarget.CLOSE, false);
        ChunkMapFrontends.activate(session);
        ClientScreenNavigation.registerMap(map);
        session.minimumRequest = ClientChunkLoadingState.refresh(dimension);
    }

    @Override public void tick() {
        if (pendingFailure != null) {
            fail();
            return;
        }
        if (failed) return;
        try {
            var minecraft = Minecraft.getInstance();
            if (minecraft.player == null) return;
            if (minecraft.screen instanceof GuiMap map) {
                if (!available()) { markFailure(new IllegalStateException("Xaero 地图钩子未安装")); return; }
                if (!active(map)) { attach(map); session.addButtons(); }
            }
            if (session == null || ChunkMapFrontends.active() != session) return;
            if (!session.editableDimension()) session.lastPaint = null;
            session.updateButtons();
        } catch (RuntimeException | LinkageError exception) { markFailure(exception); }
    }

    private void markFailure(Throwable exception) {
        if (pendingFailure == null && !failed) pendingFailure = exception;
    }

    /** 渲染和输入回调中只记录故障，在下一 tick 安全替换屏幕，避免破坏矩阵栈。 */
    private void fail() {
        failed = true;
        ArchWeaverMod.LOGGER.warn("Xaero 世界地图兼容发生错误，回退到内置地图", pendingFailure);
        pendingFailure = null;
        ChunkMapFrontends.message(Component.translatable("gui.archweaver.chunkloader.backend_failed",
            Component.translatable(MapBackend.XAERO.labelKey())));
        if (session != null && ChunkMapFrontends.active() == session) {
            var previous = session;
            ClientScreenNavigation.prepareMapReplacement(previous.map);
            ChunkMapFrontends.runWithoutGuard(() -> Minecraft.getInstance().setScreen(
                new ChunkMapScreen(previous.controller, previous.target)));
        }
    }

    @Override public void clear() {
        if (session != null) session.dispose();
        session = null;
    }

    public static void init(GuiMap map) {
        var provider = instance;
        if (provider == null || provider.failed || provider.pendingFailure != null || Minecraft.getInstance().player == null) return;
        try {
            provider.attach(map);
            provider.session.addButtons();
        } catch (RuntimeException | LinkageError exception) { provider.markFailure(exception); }
    }

    public static void renderMap(GuiMap map, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        var provider = instance;
        if (provider == null || provider.failed || provider.pendingFailure != null || !provider.active(map)) return;
        try { provider.session.renderMap(graphics, mouseX, mouseY); }
        catch (RuntimeException | LinkageError exception) { provider.markFailure(exception); }
    }

    public static void renderStatus(GuiMap map, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        var provider = instance;
        if (provider == null || provider.failed || provider.pendingFailure != null || !provider.active(map)) return;
        try { provider.session.renderStatus(graphics, mouseX, mouseY); }
        catch (RuntimeException | LinkageError exception) { provider.markFailure(exception); }
    }

    public static boolean click(GuiMap map, MouseButtonEvent event) {
        var provider = instance;
        if (provider == null || provider.failed || provider.pendingFailure != null || !provider.active(map)) return false;
        try {
            var session = provider.session;
            if ((event.button() != 0 && event.button() != 1) || session.controller.mode() != ChunkMapEditMode.EDIT
                || !session.editableDimension() || !session.mapArea(event.x(), event.y())) return false;
            session.ownedButtons.add(event.button());
            session.lastPaint = null;
            session.paint(event);
            return true;
        } catch (RuntimeException | LinkageError exception) { provider.markFailure(exception); return true; }
    }

    public static boolean drag(GuiMap map, MouseButtonEvent event) {
        var provider = instance;
        if (provider == null || !provider.active(map) || !provider.session.ownedButtons.contains(event.button())) return false;
        try {
            if (!provider.failed && provider.pendingFailure == null) provider.session.paint(event);
        } catch (RuntimeException | LinkageError exception) { provider.markFailure(exception); }
        return true;
    }

    public static boolean release(GuiMap map, int button) {
        var provider = instance;
        if (provider == null || !provider.active(map)) return false;
        boolean owned = provider.session.ownedButtons.remove(button);
        if (owned) provider.session.lastPaint = null;
        return owned;
    }

    private final class Session implements ChunkLoadMapFrontend {
        private final GuiMap map;
        private final XaeroMapAccess access;
        private final ChunkLoadMapController controller;
        private final ClientChunkLoadingState.MapReturnTarget target;
        private boolean ready;
        private long minimumRequest;
        private final Set<Integer> ownedButtons = new HashSet<>();
        private Long lastPaint;
        private int lastButton;
        private String highlightName, highlightDimension;
        private Focus pendingFocus;
        private Map<Long, ChunkMapLoadLevel> lastLevels;
        private Set<Long> lastHighlight;
        private List<ChunkMapRectangles.Rectangle> rectangles = List.of(), highlightedRectangles = List.of();
        private final List<SolidButton> buttons = new ArrayList<>();
        private SolidButton editButton, weakButton, undoButton, saveButton, managementButton;

        private Session(GuiMap map, ChunkMapSnapshotPayload snapshot, ClientChunkLoadingState.MapReturnTarget target, boolean ready) {
            this.map = map;
            this.access = (XaeroMapAccess) map;
            this.controller = new ChunkLoadMapController(snapshot);
            controller.setShowWeakLoading(ChunkMapClientConfig.weakLoadingVisible());
            this.target = target;
            this.ready = ready;
        }

        @Override public ChunkLoadMapController controller() { return controller; }
        @Override public Screen screen() { return map; }
        @Override public boolean externalMap() { return true; }
        @Override public ClientChunkLoadingState.MapReturnTarget returnTarget() { return target; }
        @Override public void accessDenied() { ready = false; setEditMode(ChunkMapEditMode.BROWSE); }
        @Override public void setEditMode(ChunkMapEditMode mode) {
            if (mode == ChunkMapEditMode.BROWSE || (ready && editableDimension())) controller.setMode(mode);
            lastPaint = null;
        }
        @Override public void close() { ClientChunkLoadingState.returnFromMap(target); }
        @Override public void acceptSnapshot(ChunkMapSnapshotPayload snapshot) {
            if (!ready && snapshot.requestId() < minimumRequest) return;
            controller.accept(snapshot);
            ready = true;
        }
        private String viewedDimension() {
            var world = map.getMapProcessor().getMapWorld();
            var dimension = world == null ? null : world.getCurrentDimensionId();
            return dimension == null ? "" : dimension.identifier().toString();
        }
        private boolean editableDimension() {
            return controller.snapshot().dimension().equals(ClientChunkLoadingState.playerDimension())
                && controller.snapshot().dimension().equals(viewedDimension()) && pendingFocus == null;
        }

        private void addButtons() {
            buttons.clear();
            // 并入原生左侧的图标列：贴左边缘 20x20，20px 一档从下往上排，
            // 起始位置比原生那一列（最高一枚在 height-120）再抬高一个按钮的高度。
            int x = 0;
            int bottom = map.height - 160;
            editButton = button(x, bottom - 80, PixelGlyph.ADD, "map_edit_mode", () -> {
                if (ready && editableDimension() && !controller.awaitingApply())
                    setEditMode(controller.mode() == ChunkMapEditMode.EDIT ? ChunkMapEditMode.BROWSE : ChunkMapEditMode.EDIT);
            });
            weakButton = button(x, bottom - 60, PixelGlyph.RESET, "map_weak_range", () -> {
                boolean visible = !ChunkMapClientConfig.weakLoadingVisible();
                ChunkMapClientConfig.setWeakLoadingVisible(visible);
                ChunkMapClientConfig.save();
                controller.setShowWeakLoading(visible);
            });
            undoButton = button(x, bottom - 40, PixelGlyph.UNDO, "map_undo", controller::undo);
            saveButton = button(x, bottom - 20, PixelGlyph.SAVE, "map_save", () -> { if (editableDimension()) controller.apply(); });
            managementButton = button(x, bottom, PixelGlyph.CUBE, "bottom_management", () -> {
                if (ready) ChunkMapScreen.openPanel(controller.snapshot(), ChunkMapOpenTarget.MANAGEMENT);
            });
            updateButtons();
        }
        private SolidButton button(int x, int y, PixelGlyph glyph, String key, Runnable action) {
            var button = new SolidButton(x, y, ICON_SIZE, ICON_SIZE, glyph,
                Component.translatable("gui.archweaver.chunkloader." + key), ignored -> {
                    try { action.run(); updateButtons(); }
                    catch (RuntimeException | LinkageError exception) { markFailure(exception); }
                }).flatIcon(ICON_IDLE_COLOR, ICON_HOVER_COLOR, ICON_OFF_COLOR);
            buttons.add(button);
            return map.addRenderableWidget(button);
        }
        private void updateButtons() {
            if (editButton == null) return;
            boolean editable = ready && editableDimension();
            editButton.active = editable && !controller.awaitingApply();
            editButton.setIconToggled(controller.mode() == ChunkMapEditMode.EDIT);
            weakButton.setIconToggled(ChunkMapClientConfig.weakLoadingVisible());
            undoButton.active = editable && controller.canUndo();
            saveButton.active = editable && controller.dirty() && !controller.awaitingApply();
            managementButton.active = ready;
            buttons.forEach(button -> button.visible = !GuiMap.hiddenUI);
        }

        private boolean mapArea(double x, double y) {
            if (GuiMap.hiddenUI || x < 0 || y < 0 || x >= map.width || y >= map.height || access.archweaver$inputBlocked()) return false;
            // 置灰控件仍占据界面；不能穿透它们修改地图。
            for (var child : map.children()) if (child instanceof AbstractWidget widget
                && widget.visible && widget.isMouseOver(x, y)) return false;
            return true;
        }
        private void paint(MouseButtonEvent event) {
            if (!ready || !editableDimension() || controller.awaitingApply() || !mapArea(event.x(), event.y())) {
                lastPaint = null;
                return;
            }
            var viewport = access.archweaver$viewport();
            int x = viewport.chunkX(event.x()), z = viewport.chunkZ(event.y());
            if (lastPaint != null && lastButton == event.button()) {
                int oldX = ChunkKey.x(lastPaint), oldZ = ChunkKey.z(lastPaint);
                int steps = Math.max(Math.abs(x - oldX), Math.abs(z - oldZ));
                if (steps <= 1024) for (int i = 1; i < steps; i++)
                    controller.edit((int) Math.round(oldX + (x - oldX) * (double) i / steps),
                        (int) Math.round(oldZ + (z - oldZ) * (double) i / steps), event.button() == 1);
            }
            controller.edit(x, z, event.button() == 1);
            lastPaint = ChunkKey.pack(x, z);
            lastButton = event.button();
        }

        private void requestFocus(String dimension, double x, double z, double spanX, double spanZ) {
            var processor = map.getMapProcessor();
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimension));
            synchronized (processor.renderThreadPauseSync) {
                processor.getMapWorld().setCustomDimensionId(key);
                processor.checkForWorldUpdate();
            }
            pendingFocus = new Focus(dimension, x, z, spanX, spanZ);
        }
        @Override public void focus(String dimension, double x, double z) {
            if (!supportsDimension(dimension)) return;
            ChunkMapFrontends.leave(() -> {
                requestFocus(dimension, x, z, 0, 0);
                Minecraft.getInstance().setScreen(map);
            });
        }
        @Override public boolean highlightRegion(String name, String dimension) {
            if (!supportsDimension(dimension)) { clearHighlightedRegion(); return false; }
            highlightName = name; highlightDimension = dimension;
            return true;
        }
        @Override public void clearHighlightedRegion() { highlightName = highlightDimension = null; }
        private ChunkMapSnapshotPayload.RegionView highlighted() {
            return controller.snapshot().regions().stream().filter(region -> region.name().equals(highlightName)
                && region.dimension().equals(highlightDimension)).findFirst().orElse(null);
        }
        @Override public void focusHighlightedRegion() {
            var region = highlighted();
            if (region == null || region.chunks().isEmpty()) return;
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
            for (long chunk : region.chunks()) {
                minX = Math.min(minX, ChunkKey.x(chunk)); maxX = Math.max(maxX, ChunkKey.x(chunk));
                minZ = Math.min(minZ, ChunkKey.z(chunk)); maxZ = Math.max(maxZ, ChunkKey.z(chunk));
            }
            requestFocus(region.dimension(), ((double) minX + maxX + 1) * 8, ((double) minZ + maxZ + 1) * 8,
                ((double) maxX - minX + 3) * 16, ((double) maxZ - minZ + 3) * 16);
            if (Minecraft.getInstance().screen instanceof ChunkMapManagementScreen) Minecraft.getInstance().setScreen(map);
        }

        private void renderMap(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            // 原生渲染已处理维度切换、摄像机重置后再定位，避免下一帧被 Xaero 自动覆盖。
            if (pendingFocus != null && pendingFocus.dimension.equals(viewedDimension())) {
                var focus = pendingFocus;
                access.archweaver$center(focus.x, focus.z, focus.spanX, focus.spanZ);
                pendingFocus = null;
                // 地图纹理使用的是调整前的摄像机，这一帧不画覆盖物，下一帧一起对齐。
                return;
            }
            if (!ready || !controller.snapshot().dimension().equals(viewedDimension())) return;
            var viewport = access.archweaver$viewport();
            var levels = controller.levels();
            if (lastLevels != levels) { rectangles = ChunkMapRectangles.merge(levels); lastLevels = levels; }
            var region = highlighted();
            Set<Long> highlight = region == null ? Set.of() : region.chunks();
            if (!highlight.equals(lastHighlight)) {
                Map<Long, ChunkMapLoadLevel> cells = new HashMap<>();
                highlight.forEach(chunk -> cells.put(chunk, ChunkMapLoadLevel.STRONG));
                highlightedRectangles = ChunkMapRectangles.merge(cells);
                lastHighlight = highlight;
            }
            graphics.enableScissor(0, 0, map.width, map.height);
            try {
                for (var rectangle : rectangles) rectangle(graphics, viewport, rectangle,
                    rectangle.level() == ChunkMapLoadLevel.STRONG ? 0x66D18B35 : 0x66C7A13A, 0);
                for (var fake : controller.snapshot().fakePlayers()) {
                    String dimension;
                    int x, z, radius;
                    if (fake.mode() == FakePlayerSimulationMode.CUSTOM && fake.loadingActive()) {
                        dimension = fake.loadingDimension(); x = fake.loadingChunkX(); z = fake.loadingChunkZ(); radius = fake.loadingDistance();
                    } else if (fake.mode() == FakePlayerSimulationMode.FOLLOW_SERVER && fake.online()) {
                        dimension = fake.dimension(); x = fake.x() >> 4; z = fake.z() >> 4; radius = fake.simulationDistance();
                    } else continue;
                    if (dimension.equals(viewedDimension())) rectangle(graphics, viewport,
                        new ChunkMapRectangles.Rectangle(x - radius, z - radius, x + radius, z + radius, ChunkMapLoadLevel.STRONG), 0, 0xFF4EC9E8);
                }
                for (var rectangle : highlightedRectangles) rectangle(graphics, viewport, rectangle, 0x6651C8B4, 0xFFF6DC70);
                if (mapArea(mouseX, mouseY)) {
                    int x = viewport.chunkX(mouseX), z = viewport.chunkZ(mouseY);
                    rectangle(graphics, viewport, new ChunkMapRectangles.Rectangle(x, z, x, z, ChunkMapLoadLevel.STRONG), 0, 0xFFFFFFFF);
                }
            } finally { graphics.disableScissor(); }
        }
        private void rectangle(GuiGraphicsExtractor graphics, ChunkMapViewport viewport,
                               ChunkMapRectangles.Rectangle rectangle, int fill, int border) {
            double x0 = viewport.screenX(rectangle.minX() * 16.0), z0 = viewport.screenY(rectangle.minZ() * 16.0);
            double x1 = viewport.screenX(((double) rectangle.maxX() + 1) * 16), z1 = viewport.screenY(((double) rectangle.maxZ() + 1) * 16);
            if (x1 <= 0 || z1 <= 0 || x0 >= map.width || z0 >= map.height) return;
            double left = Math.max(-1, x0), top = Math.max(-1, z0);
            double right = Math.min(map.width + 1, x1), bottom = Math.min(map.height + 1, z1);
            if (fill != 0) fill(graphics, left, top, right, bottom, fill);
            // 裁剪只截断原边界，不能把屏幕边缘画成区域边界。
            if (border != 0) {
                if (x0 >= 0) fill(graphics, left, top, Math.min(left + 1, right), bottom, border);
                if (x1 <= map.width) fill(graphics, Math.max(left, right - 1), top, right, bottom, border);
                if (z0 >= 0) fill(graphics, left, top, right, Math.min(top + 1, bottom), border);
                if (z1 <= map.height) fill(graphics, left, Math.max(top, bottom - 1), right, bottom, border);
            }
        }

        /** 用矩阵保留亚像素位置，避免平移时边线以整个 GUI 像素跳动。 */
        private void fill(GuiGraphicsExtractor graphics, double left, double top, double right, double bottom, int color) {
            var pose = graphics.pose();
            pose.pushMatrix();
            try {
                pose.translate((float) left, (float) top);
                pose.scale((float) (right - left), (float) (bottom - top));
                graphics.fill(0, 0, 1, 1, color);
            } finally { pose.popMatrix(); }
        }

        private void renderStatus(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            if (GuiMap.hiddenUI) return;
            updateButtons();
            var font = Minecraft.getInstance().font;
            // 提示文字占上一行、悬停信息占下一行；提示为空时信息行顶上去，中间不留空行。
            int y = STATUS_TOP;
            Component status;
            if (!ready) status = Component.translatable("gui.archweaver.chunkloader.map_waiting");
            else if (!editableDimension()) status = Component.translatable("gui.archweaver.chunkloader.map_dimension_paused");
            else if (!controller.failure().isEmpty()) status = Component.literal(controller.failure());
            else status = null;
            if (status != null) {
                statusText(graphics, font, status, y);
                y += STATUS_LINE_HEIGHT;
            }
            if (!ready || !controller.snapshot().dimension().equals(viewedDimension()) || !mapArea(mouseX, mouseY)) return;
            var viewport = access.archweaver$viewport();
            int x = viewport.chunkX(mouseX), z = viewport.chunkZ(mouseY);
            String names = controller.snapshot().regions().stream()
                .filter(region -> region.dimension().equals(viewedDimension()) && region.contains(x, z))
                .map(ChunkMapSnapshotPayload.RegionView::name).reduce((a, b) -> a + ", " + b).orElse("");
            if (controller.snapshot().fakePlayers().stream().anyMatch(fake -> fake.loadsChunk(viewedDimension(), x, z)))
                names += " " + Component.translatable("archweaver.chunkloader.fake_label").getString();
            var level = controller.levels().get(ChunkKey.pack(x, z));
            if (level != null) names += " " + Component.translatable(level == ChunkMapLoadLevel.STRONG
                ? "gui.archweaver.chunkloader.level_strong" : "gui.archweaver.chunkloader.level_weak").getString();
            Component loading = names.isBlank() ? Component.translatable("gui.archweaver.chunkloader.map_hover.none") : Component.literal(names.strip());
            Component info = Component.translatable("gui.archweaver.chunkloader.map_hover.chunk", x, z).append(" | ")
                .append(Component.translatable("gui.archweaver.chunkloader.map_hover.regions", loading));
            statusText(graphics, font, info, y);
        }

        /** 复用原生地图提示文字的样式：白字配 40% 黑底，与坐标、生物群系显示排在同一行距上。 */
        private void statusText(GuiGraphicsExtractor graphics, Font font, Component text, int y) {
            MapRenderHelper.drawCenteredStringWithBackground(graphics, font, text, map.width / 2, y,
                0xFFFFFFFF, 0, 0, 0, 0.4F);
        }
        @Override public void dispose() {
            ownedButtons.clear(); lastPaint = null;
            buttons.forEach(button -> button.active = false);
        }
    }

    private record Focus(String dimension, double x, double z, double spanX, double spanZ) { }
}
