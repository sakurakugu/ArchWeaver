package com.sakurakugu.archweaver.client.chunkloading;

import com.mojang.authlib.GameProfile;
import com.sakurakugu.archweaver.chunkloading.ChunkKey;
import com.sakurakugu.archweaver.chunkloading.FakePlayerSimulationMode;
import com.sakurakugu.archweaver.client.ClientScreenNavigation;
import com.sakurakugu.archweaver.client.ui.SolidButton;
import com.sakurakugu.archweaver.client.ui.PixelGlyph;
import com.sakurakugu.archweaver.client.ui.SegmentedSwitchButton;
import com.sakurakugu.archweaver.client.ui.ToggleSwitchButton;
import com.sakurakugu.archweaver.network.ChunkMapSnapshotPayload;
import com.sakurakugu.archweaver.network.ChunkMapOpenTarget;
import com.sakurakugu.archweaver.platform.PlatformNetworking;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerSkin;

/** 以客户端已加载地形为背景的区块加载编辑地图。 */
public final class ChunkMapScreen extends Screen implements ChunkLoadMapFrontend {
    private static final int BOTTOM_BUTTON_WIDTH = 92; // 底部工具条上按钮的宽度。
    private static final int BOTTOM_BUTTON_GAP = 6; // 底部工具条上相邻按钮之间的间距。
    private static final double MIN_SCALE = 0.35D; // 缩放下限，即每个方块最少占多少屏幕像素。
    private static final double MAX_SCALE = 2.5D; // 缩放上限，即每个方块最多占多少屏幕像素。
    private static final int BACKGROUND_COLOR = 0xFF22282C; // 整屏底色，未加载区域与地形透明处都露出它。
    private static final int GRID_COLOR = 0x283A4449; // 普通区块网格用约 16% 不透明度的蓝灰色。
    private static final int HIGHLIGHT_FILL = 0x6651C8B4; // 选中区域用 40% 不透明度的青绿色填充。
    private static final int HIGHLIGHT_BORDER = 0xFFF6DC70; // 选中区域外轮廓用不透明亮黄色。
    private static final int HOVER_BORDER = 0xFFFFFFFF; // 鼠标悬停的单个区块用白色 1px 描边。
    private static final double MIN_GRID_PIXELS = 8.0D; // 绘制区块网格线所需的最小像素间距。
    private static final double MIN_THICK_HIGHLIGHT_PIXELS = 16.0D; // 区块边长至少 16px 时选中外框才用 2px，否则用 1px。
    // 玩家标记用的原版地图装饰图标纹理。
    private static final Identifier PLAYER_MARKER = Identifier.withDefaultNamespace(
        "textures/map/decorations/player.png"
    );
    // 玩家标记按 UUID 哈希取色的调色板。
    private static final int[] PLAYER_MARKER_COLORS = {
        0xFF4FC3F7, 0xFFFF6B6B, 0xFF66D17A, 0xFFFFC857,
        0xFFC77DFF, 0xFF36C9B4, 0xFFFF8A4C, 0xFFF06292
    };

    private final ChunkLoadMapController controller; // 地图快照与编辑草稿的状态控制器。
    private final MapQuadBatch backgroundBatch = new MapQuadBatch(); // 背景底色的绘制批次，三类绘制各自合并成一个元素，见 MapQuadBatch。
    private final MapQuadBatch terrainBatch = new MapQuadBatch(); // 地形贴图的绘制批次，三类绘制各自合并成一个元素，见 MapQuadBatch。
    private final MapQuadBatch overlayBatch = new MapQuadBatch(); // 网格线与色块的绘制批次，三类绘制各自合并成一个元素，见 MapQuadBatch。
    private int[] overlayChunkX = new int[256]; // 加载区域色块的区块 X 缓存（世界坐标，与视图无关）。
    private int[] overlayChunkZ = new int[256]; // 加载区域色块的区块 Z 缓存，与 X 数组按下标一一对应。
    private int[] overlayColor = new int[256]; // 加载区域色块的颜色缓存，与坐标数组按下标一一对应。
    private int overlayCount; // 色块缓存中当前有效条目的数量。
    private Map<Long, ChunkMapLoadLevel> overlayLevelCache = Map.of(); // 上次生成色块列表时用的等级表，靠对象身份判断"等级是否变过"。
    private double centerBlockX; // 视图中心对应的世界 X 方块坐标。
    private double centerBlockZ; // 视图中心对应的世界 Z 方块坐标。
    private double pixelsPerBlock = 0.75D; // 当前缩放，每个方块占多少像素，取值范围见 MIN_SCALE 与 MAX_SCALE。
    private boolean dragging; // 是否正在拖动地图。
    private int draggingButton = -1; // 按下时用的是哪个键，拖动过程中按这个键决定是涂、擦还是平移。
    private final ClientChunkLoadingState.MapReturnTarget returnTarget; // 关闭地图后要返回的上级页面目标。
    private int snapshotRefreshTicks; // 距离下次向服务端请求状态刷新的剩余 tick 数。
    private UUID highlightedRegionId;
    private ChunkMapSnapshotPayload.RegionView highlightedRegion;
    private Button saveButton; // 顶部工具条上的保存按钮。
    private Button undoButton; // 顶部工具条上的撤回按钮。

    public ChunkMapScreen(ChunkMapSnapshotPayload snapshot, ClientChunkLoadingState.MapReturnTarget returnTarget) {
        super(Component.translatable("gui.archweaver.chunkloader.map_title"));
        controller = new ChunkLoadMapController(snapshot);
        controller.setShowWeakLoading(ChunkMapClientConfig.weakLoadingVisible());
        this.returnTarget = returnTarget;
        centerBlockX = snapshot.playerChunkX() * 16.0D + 8.0D;
        centerBlockZ = snapshot.playerChunkZ() * 16.0D + 8.0D;
    }

    /** 在当前地图页面上打开独立的设置或管理页面。 */
    public static void openPanel(ChunkMapSnapshotPayload snapshot, ChunkMapOpenTarget target) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen parent = minecraft.screen;
        if (parent != null) parent.clearFocus();
        if (parent instanceof ChunkMapScreen map) {
            map.dragging = false;
            map.draggingButton = -1;
        }
        Screen panel = target == ChunkMapOpenTarget.SETTINGS
            ? new ChunkMapSettingsScreen(snapshot)
            : new ChunkMapManagementScreen(snapshot, parent instanceof ChunkMapScreen map ? map : null);
        ClientScreenNavigation.registerLayer(parent, panel);
        // 子页由导航器统一绘制父页面背景，不能同时保留 GUI layer 的底层自动绘制。
        minecraft.setScreen(panel);
    }

    public void update(ChunkMapSnapshotPayload value) { acceptSnapshot(value); }

    @Override
    public void tick() {
        super.tick();
        if (saveButton != null) saveButton.active = controller.dirty();
        if (undoButton != null) undoButton.active = controller.mode() == ChunkMapEditMode.EDIT && controller.canUndo();
        if (minecraft.player != null && minecraft.getConnection() != null && snapshotRefreshTicks-- <= 0) {
            PlatformNetworking.sendToServer(ClientChunkLoadingState.request(ChunkMapOpenTarget.NONE));
            snapshotRefreshTicks = 10;
        }
    }

    @Override
    protected void init() {
        saveButton = null;
        undoButton = null;
        int modeSwitchWidth = Mth.clamp(width - 138, 64, 80);
        int x = 6;
        SegmentedSwitchButton modeSwitch = addRenderableWidget(new SegmentedSwitchButton(
            x, 6, modeSwitchWidth, 20,
            Component.translatable("gui.archweaver.chunkloader.map_browse_mode"),
            Component.translatable("gui.archweaver.chunkloader.map_edit_mode"),
            () -> controller.mode() == ChunkMapEditMode.EDIT,
            selectedRight -> setEditMode(selectedRight ? ChunkMapEditMode.EDIT : ChunkMapEditMode.BROWSE)));
        modeSwitch.setTooltip(Tooltip.create(
            Component.translatable("gui.archweaver.chunkloader.map_edit_mode_tooltip")));
        x += modeSwitchWidth;
        addWeakLoadingSwitch(x + 6);

        undoButton = addRenderableWidget(new SolidButton(width - 82, 7, 18, 18, PixelGlyph.UNDO,
            Component.translatable("gui.archweaver.chunkloader.map_undo"), button -> controller.undo()));
        undoButton.active = controller.mode() == ChunkMapEditMode.EDIT && controller.canUndo();
        saveButton = addRenderableWidget(new SolidButton(width - 62, 7, 18, 18, PixelGlyph.SAVE,
            Component.translatable("gui.archweaver.chunkloader.map_save"), button -> controller.apply()));
        saveButton.active = controller.dirty();
        addRenderableWidget(new SolidButton(width - 42, 7, 18, 18, PixelGlyph.SETTING,
            Component.translatable("gui.archweaver.chunkloader.map_settings"),
            button -> openPanel(controller.snapshot(), ChunkMapOpenTarget.SETTINGS)));
        addRenderableWidget(new SolidButton(width - 22, 7, 18, 18, PixelGlyph.CLOSE,
            Component.translatable("gui.close"), button -> minecraft.setScreen(null)));

        addBottomBar();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        drawMapContents(graphics);
        PlayerMarker hoveredPlayer = playerMarkerAt(mouseX, mouseY);
        drawHoveredChunk(graphics, mouseX, mouseY, hoveredPlayer);

        MutableComponent heading = title.copy().append("  [").append(label(controller.mode()));
        if (controller.mode() == ChunkMapEditMode.EDIT) {
            heading.append(" ").append(Component.translatable("gui.archweaver.chunkloader.map_edit_hint"));
        }
        heading.append("]");
        drawFloatingText(graphics, heading, width / 2, 32, 0xFFFFFFFF);
        int centerChunkX = Mth.floor(centerBlockX) >> 4;
        int centerChunkZ = Mth.floor(centerBlockZ) >> 4;
        Component status = Component.translatable("gui.archweaver.chunkloader.map_position",
            centerChunkX, centerChunkZ, controller.snapshot().dimension()).copy()
            .append("  ").append(Math.round(pixelsPerBlock * 100.0D) + "%");
        drawFloatingText(graphics, status, width / 2, height - 38, 0xFFC8D6CF);
    }

    private void drawMapContents(GuiGraphicsExtractor graphics) {
        graphics.enableScissor(0, 0, width, height);
        backgroundBatch.begin(false, RenderPipelines.GUI, TextureSetup.noTexture(), graphics, width, height);
        backgroundBatch.addRect(0, 0, width, height, BACKGROUND_COLOR);
        graphics.submitGuiElementRenderState(backgroundBatch);

        int sampleY = minecraft.player == null ? 64 : minecraft.player.getBlockY();
        drawTerrain(graphics, sampleY);

        overlayBatch.begin(false, RenderPipelines.GUI, TextureSetup.noTexture(), graphics, width, height);
        drawChunkGrid();
        drawChunkOverlays();
        drawHighlightedRegion();
        if (!overlayBatch.isEmpty()) graphics.submitGuiElementRenderState(overlayBatch);

        // 假人标记数量有限，直接走原版元素即可
        drawFakePlayers(graphics);
        drawPlayer(graphics);
        graphics.disableScissor();
    }

    private void drawFloatingText(GuiGraphicsExtractor graphics, Component text, int centerX, int y, int color) {
        int textWidth = font.width(text);
        graphics.fill(centerX - textWidth / 2 - 3, y - 2,
            centerX + (textWidth + 1) / 2 + 3, y + 11, 0xB8111518);
        graphics.centeredText(font, text, centerX, y, color);
    }

    /**
     * 绘制地形底图：只遍历客户端已加载的那一片区块，缩小时一个格覆盖多个区块。
     * 每格对应图集里的一个 slot，屏幕上的位置由格坐标换算而来。
     */
    private void drawTerrain(GuiGraphicsExtractor graphics, int sampleY) {
        // 每帧现取：切换维度时状态里会重建图集，缓存字段会留下已关闭的那个
        ChunkTerrainAtlas terrainAtlas = ClientChunkLoadingState.terrainAtlas();
        if (terrainAtlas.isClosed()) return;
        terrainAtlas.beginFrame(pixelsPerBlock, sampleY);
        terrainBatch.begin(true, RenderPipelines.GUI_TEXTURED, terrainAtlas.textureSetup(), graphics,
            width, height);
        int span = terrainAtlas.cellSpanBlocks();
        float atlasWidth = ChunkTerrainAtlas.atlasWidth();
        float atlasHeight = ChunkTerrainAtlas.atlasHeight();
        for (int cellZ = terrainAtlas.minCellZ(); cellZ <= terrainAtlas.maxCellZ(); cellZ++) {
            int top = worldToScreenZ((double) cellZ * span);
            int bottom = worldToScreenZ((double) (cellZ + 1) * span);
            if (bottom <= 0 || top >= height) continue;
            float v0 = (float) ChunkTerrainAtlas.slotV(cellZ) / atlasHeight;
            float v1 = v0 + (float) ChunkTerrainAtlas.SLOT_SIZE / atlasHeight;
            for (int cellX = terrainAtlas.minCellX(); cellX <= terrainAtlas.maxCellX(); cellX++) {
                int left = worldToScreenX((double) cellX * span);
                int right = worldToScreenX((double) (cellX + 1) * span);
                if (right <= 0 || left >= width) continue;
                // 没命中已加载区块的格不画，露出底色
                if (!terrainAtlas.prepare(cellX, cellZ)) continue;
                float u0 = (float) ChunkTerrainAtlas.slotU(cellX) / atlasWidth;
                terrainBatch.addTexturedRect(left, top, Math.max(left + 1, right),
                    Math.max(top + 1, bottom), u0, v0,
                    u0 + (float) ChunkTerrainAtlas.SLOT_SIZE / atlasWidth, v1, 0xFFFFFFFF);
            }
        }
        terrainAtlas.endFrame();
        if (!terrainBatch.isEmpty()) graphics.submitGuiElementRenderState(terrainBatch);
    }

    /** 区块网格线：间距够大时才画，跨越整个视口，条数与屏幕尺寸而非区块数成正比。 */
    private void drawChunkGrid() {
        if (16.0D * pixelsPerBlock < MIN_GRID_PIXELS) return;
        int minChunkX = Mth.floor(screenToWorldX(0)) >> 4;
        int maxChunkX = Mth.floor(screenToWorldX(width - 1)) >> 4;
        int minChunkZ = Mth.floor(screenToWorldZ(0)) >> 4;
        int maxChunkZ = Mth.floor(screenToWorldZ(height - 1)) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX + 1; chunkX++) {
            int x = worldToScreenX(chunkX * 16.0D);
            if (x >= 0 && x < width) overlayBatch.addRect(x, 0, x + 1, height, GRID_COLOR);
        }
        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ + 1; chunkZ++) {
            int y = worldToScreenZ(chunkZ * 16.0D);
            if (y >= 0 && y < height) overlayBatch.addRect(0, y, width, y + 1, GRID_COLOR);
        }
    }

    /**
     * 绘制加载等级色块：遍历的是数据（草稿 + 权威等级），不是屏幕上的区块，
     * 因此缩得很小时也不会因为可见区块变多而变慢。
     */
    private void drawChunkOverlays() {
        refreshOverlayCells();
        for (int index = 0; index < overlayCount; index++) {
            int chunkX = overlayChunkX[index];
            int chunkZ = overlayChunkZ[index];
            int left = worldToScreenX(chunkX * 16.0D);
            int top = worldToScreenZ(chunkZ * 16.0D);
            int right = worldToScreenX((chunkX + 1) * 16.0D);
            int bottom = worldToScreenZ((chunkZ + 1) * 16.0D);
            if (right <= 0 || left >= width || bottom <= 0 || top >= height) continue;
            overlayBatch.addRect(left, top, Math.max(left + 1, right), Math.max(top + 1, bottom),
                overlayColor[index]);
        }
    }

    private void drawHighlightedRegion() {
        if (highlightedRegion == null) return;
        Set<Long> chunks = highlightedRegion.chunks();
        int borderWidth = 16.0D * pixelsPerBlock >= MIN_THICK_HIGHLIGHT_PIXELS ? 2 : 1;
        for (long chunk : chunks) {
            int chunkX = ChunkKey.x(chunk);
            int chunkZ = ChunkKey.z(chunk);
            int left = worldToScreenX(chunkX * 16.0D);
            int top = worldToScreenZ(chunkZ * 16.0D);
            int right = Math.max(left + 1, worldToScreenX((chunkX + 1.0D) * 16.0D));
            int bottom = Math.max(top + 1, worldToScreenZ((chunkZ + 1.0D) * 16.0D));
            if (right <= 0 || left >= width || bottom <= 0 || top >= height) continue;
            overlayBatch.addRect(left, top, right, bottom, HIGHLIGHT_FILL);
            int border = Math.min(borderWidth, Math.min(right - left, bottom - top));
            if (!chunks.contains(ChunkKey.pack(chunkX - 1, chunkZ)))
                overlayBatch.addRect(left, top, left + border, bottom, HIGHLIGHT_BORDER);
            if (!chunks.contains(ChunkKey.pack(chunkX + 1, chunkZ)))
                overlayBatch.addRect(right - border, top, right, bottom, HIGHLIGHT_BORDER);
            if (!chunks.contains(ChunkKey.pack(chunkX, chunkZ - 1)))
                overlayBatch.addRect(left, top, right, top + border, HIGHLIGHT_BORDER);
            if (!chunks.contains(ChunkKey.pack(chunkX, chunkZ + 1)))
                overlayBatch.addRect(left, bottom - border, right, bottom, HIGHLIGHT_BORDER);
        }
    }

    public boolean highlightRegion(String name, String dimension) {
        highlightedRegion = controller.snapshot().regions().stream()
            .filter(region -> region.dimension().equals(dimension) && region.name().equals(name))
            .findFirst().orElse(null);
        highlightedRegionId = highlightedRegion == null ? null : highlightedRegion.id();
        return highlightedRegion != null;
    }

    public void clearHighlightedRegion() {
        highlightedRegionId = null;
        highlightedRegion = null;
    }

    public void focusHighlightedRegion() {
        if (highlightedRegion == null) return;
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (long chunk : highlightedRegion.chunks()) {
            int x = ChunkKey.x(chunk), z = ChunkKey.z(chunk);
            minX = Math.min(minX, x);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxZ = Math.max(maxZ, z);
        }
        if (minX > maxX) return;
        double spanX = ((double) maxX - minX + 1.0D) * 16.0D;
        double spanZ = ((double) maxZ - minZ + 1.0D) * 16.0D;
        double fitScale = Math.min(Math.max(1, width - 32) / spanX,
            Math.max(1, height - 96) / spanZ);
        pixelsPerBlock = Math.min(pixelsPerBlock, Mth.clamp(fitScale, MIN_SCALE, MAX_SCALE));
        if (fitScale >= MIN_SCALE) {
            centerBlockX = (minX / 2.0D + maxX / 2.0D + 0.5D) * 16.0D;
            centerBlockZ = (minZ / 2.0D + maxZ / 2.0D + 0.5D) * 16.0D;
        } else {
            // 区域跨度超出最小缩放可见范围时，至少定位到一个真实区块。
            long nearest = highlightedRegion.chunks().iterator().next();
            double distance = Double.POSITIVE_INFINITY;
            double middleX = minX / 2.0D + maxX / 2.0D;
            double middleZ = minZ / 2.0D + maxZ / 2.0D;
            for (long chunk : highlightedRegion.chunks()) {
                double dx = ChunkKey.x(chunk) - middleX;
                double dz = ChunkKey.z(chunk) - middleZ;
                double squared = dx * dx + dz * dz;
                if (squared < distance) {
                    distance = squared;
                    nearest = chunk;
                }
            }
            centerBlockX = ChunkKey.x(nearest) * 16.0D + 8.0D;
            centerBlockZ = ChunkKey.z(nearest) * 16.0D + 8.0D;
        }
    }

    /** 快照或草稿变化时重建色块列表；视图平移缩放不触发重建。 */
    private void refreshOverlayCells() {
        // 等级表由控制器按（强加载集合，草稿版本）缓存，这里比对对象身份即可
        Map<Long, ChunkMapLoadLevel> levels = controller.levels();
        if (levels == overlayLevelCache) return;
        overlayLevelCache = levels;
        int count = 0;
        for (var entry : levels.entrySet()) {
            count = appendOverlayCell(count, entry.getKey(), entry.getValue());
        }
        overlayCount = count;
    }

    private int appendOverlayCell(int count, long chunk, ChunkMapLoadLevel level) {
        if (count == overlayChunkX.length) {
            int size = count * 2;
            overlayChunkX = java.util.Arrays.copyOf(overlayChunkX, size);
            overlayChunkZ = java.util.Arrays.copyOf(overlayChunkZ, size);
            overlayColor = java.util.Arrays.copyOf(overlayColor, size);
        }
        overlayChunkX[count] = ChunkKey.x(chunk);
        overlayChunkZ[count] = ChunkKey.z(chunk);
        overlayColor[count] = loadLevelColor(level);
        return count + 1;
    }

    private void drawFakePlayers(GuiGraphicsExtractor graphics) {
        for (var fake : controller.snapshot().fakePlayers()) {
            if (fake.mode() == FakePlayerSimulationMode.CUSTOM && fake.loadingActive()
                && fake.loadingDimension().equals(controller.snapshot().dimension())) {
                outlineRange(graphics, fake.loadingChunkX(), fake.loadingChunkZ(),
                    fake.loadingDistance(), 0xFF4EC9E8);
            } else if (fake.mode() == FakePlayerSimulationMode.FOLLOW_SERVER
                && fake.online() && fake.dimension().equals(controller.snapshot().dimension())) {
                // 自动模式使用原版玩家模拟距离，不会出现在 activeRange 里。
                outlineRange(graphics, fake.x() >> 4, fake.z() >> 4,
                    fake.simulationDistance(), 0xFF4EC9E8);
            }
        }
        for (var fake : controller.snapshot().fakePlayers()) {
            if (!fake.dimension().equals(controller.snapshot().dimension())) continue;
            drawPlayerMarker(graphics, fake.id(), fake.x() + 0.5D, fake.z() + 0.5D, fake.yaw(), fake.name());
        }
    }

    private void outlineRange(GuiGraphicsExtractor graphics, int centerChunkX, int centerChunkZ,
                              int radius, int color) {
        int left = worldToScreenX((centerChunkX - radius) * 16.0D);
        int top = worldToScreenZ((centerChunkZ - radius) * 16.0D);
        int right = worldToScreenX((centerChunkX + radius + 1) * 16.0D);
        int bottom = worldToScreenZ((centerChunkZ + radius + 1) * 16.0D);
        graphics.outline(left, top, Math.max(1, right - left), Math.max(1, bottom - top), color);
    }

    private void drawPlayer(GuiGraphicsExtractor graphics) {
        if (minecraft.player == null) return;
        drawPlayerMarker(graphics, minecraft.player.getUUID(), minecraft.player.getX(), minecraft.player.getZ(),
            minecraft.player.getYRot(), minecraft.player.getGameProfile().name());
    }

    private void drawPlayerMarker(GuiGraphicsExtractor graphics, java.util.UUID id, double blockX, double blockZ,
                                  float yaw, String name) {
        int screenX = worldToScreenX(blockX);
        int screenY = worldToScreenZ(blockZ);
        int nameHeight = Mth.ceil(9.0D * ChunkMapClientConfig.markerNameScale());
        if (screenX < 4 || screenX >= width - 4 || screenY < 4 || screenY >= height - nameHeight - 6) return;
        graphics.pose().pushMatrix();
        graphics.pose().translate(screenX, screenY);
        // 原版地图玩家图标默认朝北，而实体 yaw 为 0 时朝南。
        graphics.pose().rotate((float) Math.toRadians(yaw + 180.0F));
        graphics.blit(RenderPipelines.GUI_TEXTURED, PLAYER_MARKER, -4, -4,
            0.0F, 0.0F, 8, 8, 8, 8, 8, 8, markerColor(id));
        graphics.pose().popMatrix();
        float scale = (float) ChunkMapClientConfig.markerNameScale();
        int nameWidth = Mth.ceil(font.width(name) * scale);
        int nameX = Mth.clamp(screenX - nameWidth / 2, 2, Math.max(2, width - nameWidth - 2));
        int nameY = screenY + 5;
        graphics.fill(nameX - 2, nameY - 1, nameX + nameWidth + 2, nameY + nameHeight, 0x99000000);
        graphics.pose().pushMatrix();
        graphics.pose().translate(nameX, nameY);
        graphics.pose().scale(scale, scale);
        graphics.text(font, Component.literal(name), 0, 0, 0xFFFFFFFF, false);
        graphics.pose().popMatrix();
    }

    private static int markerColor(java.util.UUID id) {
        int hash = Long.hashCode(id.getMostSignificantBits()) ^ Long.hashCode(id.getLeastSignificantBits());
        return PLAYER_MARKER_COLORS[Math.floorMod(hash, PLAYER_MARKER_COLORS.length)];
    }

    private PlayerMarker playerMarkerAt(int mouseX, int mouseY) {
        if (minecraft.player != null) {
            PlayerMarker player = new PlayerMarker(minecraft.player.getUUID(),
                minecraft.player.getGameProfile().name(), minecraft.player.getX(), minecraft.player.getZ(), false);
            if (containsMarker(player, mouseX, mouseY)) return player;
        }
        for (var fake : controller.snapshot().fakePlayers()) {
            if (!fake.dimension().equals(controller.snapshot().dimension())) continue;
            PlayerMarker marker = new PlayerMarker(fake.id(), fake.name(), fake.x() + 0.5D, fake.z() + 0.5D, true);
            if (containsMarker(marker, mouseX, mouseY)) return marker;
        }
        return null;
    }

    private boolean containsMarker(PlayerMarker player, int mouseX, int mouseY) {
        int markerX = worldToScreenX(player.blockX());
        int markerY = worldToScreenZ(player.blockZ());
        float scale = (float) ChunkMapClientConfig.markerNameScale();
        int nameWidth = Mth.ceil(font.width(player.name()) * scale);
        int nameHeight = Mth.ceil(9.0F * scale);
        int nameX = Mth.clamp(markerX - nameWidth / 2, 2, Math.max(2, width - nameWidth - 2));
        boolean overIcon = mouseX >= markerX - 5 && mouseX <= markerX + 5
            && mouseY >= markerY - 5 && mouseY <= markerY + 5;
        boolean overName = mouseX >= nameX - 2 && mouseX <= nameX + nameWidth + 2
            && mouseY >= markerY + 4 && mouseY <= markerY + 5 + nameHeight;
        return overIcon || overName;
    }

    private PlayerSkin skin(PlayerMarker player) {
        if (minecraft.getConnection() != null) {
            var info = minecraft.getConnection().getPlayerInfo(player.id());
            if (info != null) return info.getSkin();
        }
        return DefaultPlayerSkin.get(new GameProfile(player.id(), player.name()));
    }

    private void addBottomBar() {
        int count = 1;
        int totalWidth = count * BOTTOM_BUTTON_WIDTH + (count - 1) * BOTTOM_BUTTON_GAP;
        int x = (width - totalWidth) / 2;
        int y = height - 26;
        addRenderableWidget(new SolidButton(x, y, BOTTOM_BUTTON_WIDTH, 20,
            Component.translatable("gui.archweaver.chunkloader.bottom_management"),
            button -> openPanel(controller.snapshot(), ChunkMapOpenTarget.MANAGEMENT)));
    }

    /** 顶部开关：是否显示弱加载区块。窗口很窄时只留开关本身，标签自己滚动。 */
    private void addWeakLoadingSwitch(int x) {
        Component label = Component.translatable("gui.archweaver.chunkloader.map_weak_range");
        int preferredWidth = ToggleSwitchButton.preferredBoxedWidth(font, label);
        // 右边留出撤回、保存、设置、关闭四个按钮的位置。
        int switchWidth = Math.max(31, Math.min(preferredWidth, Math.min(96, width - 88 - x)));
        addRenderableWidget(new ToggleSwitchButton(x, 6, switchWidth, 20,
            label, 0xFFFFFFFF,
            ChunkMapClientConfig::weakLoadingVisible, button -> toggleWeakLoading(), true));
    }

    private void toggleWeakLoading() {
        boolean visible = !ChunkMapClientConfig.weakLoadingVisible();
        ChunkMapClientConfig.setWeakLoadingVisible(visible);
        ChunkMapClientConfig.save();
        controller.setShowWeakLoading(visible);
    }

    @Override
    public void onClose() {
        ClientChunkLoadingState.returnFromMap(returnTarget);
    }

    private void drawHoveredChunk(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                  PlayerMarker hoveredPlayer) {
        int[] chunk = chunkAt(mouseX, mouseY);
        if (chunk == null) return;
        int blockX = Mth.floor(screenToWorldX(mouseX));
        int blockZ = Mth.floor(screenToWorldZ(mouseY));
        int left = worldToScreenX(chunk[0] * 16.0D);
        int top = worldToScreenZ(chunk[1] * 16.0D);
        int right = worldToScreenX((chunk[0] + 1) * 16.0D);
        int tileBottom = worldToScreenZ((chunk[1] + 1) * 16.0D);
        graphics.outline(left, top, Math.max(1, right - left), Math.max(1, tileBottom - top), HOVER_BORDER);
        var regionNames = controller.snapshot().regions().stream()
            .filter(region -> region.dimension().equals(controller.snapshot().dimension()))
            .filter(region -> region.contains(chunk[0], chunk[1]))
            .map(region -> region.name() + ":" + loadLevelLabel(ChunkMapLoadLevel.STRONG).getString())
            .reduce((a, b) -> a + ", " + b);
        Component chunkLine = Component.translatable("gui.archweaver.chunkloader.map_hover.chunk", chunk[0], chunk[1]);
        Component blockLine = Component.translatable("gui.archweaver.chunkloader.map_hover.block", blockX, blockZ);
        boolean loadedByFakePlayer = controller.snapshot().fakePlayers().stream()
            .anyMatch(fake -> fake.loadsChunk(controller.snapshot().dimension(), chunk[0], chunk[1]));
        ChunkMapLoadLevel loadLevel = controller.levels().get(ChunkKey.pack(chunk[0], chunk[1]));
        Component names = regionNames.<Component>map(Component::literal).orElseGet(() -> loadLevel == null
            ? Component.translatable(loadedByFakePlayer
                ? "archweaver.chunkloader.fake_label"
                : "gui.archweaver.chunkloader.map_hover.none")
            : loadLevelLabel(loadLevel));
        if (loadedByFakePlayer && regionNames.isPresent()) {
            names = names.copy().append(" | ").append(Component.translatable("archweaver.chunkloader.fake_label"));
        }
        Component regionsLine = Component.translatable("gui.archweaver.chunkloader.map_hover.regions", names);
        Component playerLine = hoveredPlayer == null ? Component.empty() : Component.literal(hoveredPlayer.name());
        if (hoveredPlayer != null && hoveredPlayer.fake()) {
            playerLine = playerLine.copy().append(Component.translatable("gui.archweaver.fakeplayer.tab_marker")
                .withStyle(ChatFormatting.DARK_GRAY));
        }
        int textWidth = Math.max(font.width(chunkLine), Math.max(font.width(blockLine), font.width(regionsLine)));
        int faceSize = font.lineHeight;
        int playerWidth = hoveredPlayer == null ? 0 : faceSize + 2 + font.width(playerLine);
        int contentWidth = Math.max(textWidth, playerWidth);
        int contentHeight = hoveredPlayer == null ? 29 : 29 + faceSize + 2;
        int textX = Math.max(4, Math.min(width - contentWidth - 4, mouseX + 10));
        int textY = Math.max(4, Math.min(height - contentHeight - 4, mouseY + 10));
        graphics.fill(textX - 2, textY - 2, textX + contentWidth + 2,
            textY + contentHeight + 2, 0xD9111518);
        graphics.text(font, chunkLine, textX, textY, 0xFFFFFFFF);
        graphics.text(font, blockLine, textX, textY + 10, 0xFFFFFFFF);
        graphics.text(font, regionsLine, textX, textY + 20, 0xFFFFFFFF);
        if (hoveredPlayer != null) {
            int playerY = textY + 31;
            PlayerFaceExtractor.extractRenderState(graphics, skin(hoveredPlayer), textX, playerY, faceSize);
            graphics.text(font, playerLine, textX + faceSize + 2, playerY, 0xFFFFFFFF, false);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        // 置灰的按钮不是点击目标，别让这一下透到地图上涂掉按钮底下的区块
        if (isOverWidget(event.x(), event.y())) return false;
        int[] chunk = chunkAt(event.x(), event.y());
        if (chunk == null) return false;
        dragging = true;
        draggingButton = event.button();
        if (controller.mode() != ChunkMapEditMode.BROWSE && isEditButton(event.button())) {
            controller.edit(chunk[0], chunk[1], event.button() == 1);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (!dragging) return super.mouseDragged(event, deltaX, deltaY);
        // 浏览模式还是拖哪都能平移；编辑模式左右键被涂/擦占了，平移留给中键
        if (draggingButton == 2 || controller.mode() == ChunkMapEditMode.BROWSE) {
            centerBlockX -= deltaX / pixelsPerBlock;
            centerBlockZ -= deltaY / pixelsPerBlock;
        } else {
            int[] chunk = chunkAt(event.x(), event.y());
            if (chunk != null && isEditButton(draggingButton) && !isOverWidget(event.x(), event.y())) {
                controller.edit(chunk[0], chunk[1], draggingButton == 1);
            }
        }
        return true;
    }

    /** 编辑模式里认的按键：左键强加载，右键擦除。 */
    private static boolean isEditButton(int button) {
        return button == 0 || button == 1;
    }

    /** 光标是否压在任一可见控件上；置灰控件同样算，用来挡住透传到地图的点击。 */
    private boolean isOverWidget(double x, double y) {
        for (GuiEventListener child : children()) {
            if (child instanceof AbstractWidget widget && widget.visible
                && x >= widget.getX() && x < widget.getX() + widget.getWidth()
                && y >= widget.getY() && y < widget.getY() + widget.getHeight()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean wasDragging = dragging;
        dragging = false;
        draggingButton = -1;
        return wasDragging || super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // 顶部和底部工具条上的滚轮归控件，别让它缩放底下的地图
        if (!insideMap(mouseX, mouseY) || isOverWidget(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        double worldX = screenToWorldX(mouseX);
        double worldZ = screenToWorldZ(mouseY);
        double next = Mth.clamp(pixelsPerBlock * Math.pow(1.2D, verticalAmount), MIN_SCALE, MAX_SCALE);
        if (next != pixelsPerBlock) {
            pixelsPerBlock = next;
            centerBlockX = worldX - (mouseX - width / 2.0D) / pixelsPerBlock;
            centerBlockZ = worldZ - (mouseY - height / 2.0D) / pixelsPerBlock;
        }
        return true;
    }

    private int[] chunkAt(double mouseX, double mouseY) {
        if (!insideMap(mouseX, mouseY)) return null;
        return new int[]{Mth.floor(screenToWorldX(mouseX)) >> 4, Mth.floor(screenToWorldZ(mouseY)) >> 4};
    }

    private boolean insideMap(double x, double y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    private int worldToScreenX(double worldX) {
        return Mth.floor(width / 2.0D + (worldX - centerBlockX) * pixelsPerBlock);
    }

    private int worldToScreenZ(double worldZ) {
        return Mth.floor(height / 2.0D + (worldZ - centerBlockZ) * pixelsPerBlock);
    }

    private double screenToWorldX(double screenX) {
        return centerBlockX + (screenX - width / 2.0D) / pixelsPerBlock;
    }

    private double screenToWorldZ(double screenY) {
        return centerBlockZ + (screenY - height / 2.0D) / pixelsPerBlock;
    }

    private static int loadLevelColor(ChunkMapLoadLevel level) { return switch (level) {
        case WEAK -> 0x66C7A13A;
        case STRONG -> 0x66D18B35;
    }; }

    private static Component loadLevelLabel(ChunkMapLoadLevel level) {
        return Component.translatable(switch (level) {
            case WEAK -> "gui.archweaver.chunkloader.level_weak";
            case STRONG -> "gui.archweaver.chunkloader.level_strong";
        });
    }

    /** 按钮和标题共用短标签，保证标题也能随语言切换。 */
    private static Component label(ChunkMapEditMode mode) { return Component.translatable(switch (mode) {
        case BROWSE -> "gui.archweaver.chunkloader.map_browse_mode";
        case EDIT -> "gui.archweaver.chunkloader.map_edit_mode";
    }); }

    @Override
    public void acceptSnapshot(ChunkMapSnapshotPayload snapshot) {
        controller.accept(snapshot);
        if (highlightedRegionId != null) {
            highlightedRegion = snapshot.regions().stream()
                .filter(region -> region.id().equals(highlightedRegionId))
                .findFirst().orElse(null);
            if (highlightedRegion == null) highlightedRegionId = null;
        }
    }

    @Override
    public void focus(String dimension, double blockX, double blockZ) {
        if (dimension.equals(controller.snapshot().dimension())) {
            centerBlockX = blockX;
            centerBlockZ = blockZ;
        }
    }

    @Override public void setEditMode(ChunkMapEditMode mode) { controller.setMode(mode); }
    @Override public void close() { onClose(); }

    private record PlayerMarker(java.util.UUID id, String name, double blockX, double blockZ, boolean fake) {
    }

}
