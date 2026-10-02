package com.sakurakugu.archweaver.client;

import com.sakurakugu.archweaver.client.chunkloading.ClientChunkLoadingState;
import com.sakurakugu.archweaver.client.ui.PixelGlyph;
import com.sakurakugu.archweaver.client.ui.SolidButton;
import com.sakurakugu.archweaver.network.ChunkMapSnapshotPayload;
import com.sakurakugu.archweaver.network.OpenFakePlayerPagePayload;
import com.sakurakugu.archweaver.network.ToggleGlobalSettingPayload;
import com.sakurakugu.archweaver.platform.PlatformNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** G 键打开的总览页：左侧导航，中间假人列表与详情，右侧地图和全局设置。 */
public final class MainPageScreen extends Screen {
    private static final int SIDE_WIDTH = 190;
    private static final int GAP = 8;
    private static final int PANEL_PADDING = 12;
    private static final int ROW_HEIGHT = 38;

    private ChunkMapSnapshotPayload snapshot;
    private int selectedFake;
    private final Button[] settingButtons = new Button[2];

    public MainPageScreen(ChunkMapSnapshotPayload snapshot) {
        super(Component.translatable("gui.archweaver.main.title"));
        this.snapshot = snapshot;
    }

    public void update(ChunkMapSnapshotPayload value) {
        snapshot = value;
        selectedFake = Math.min(selectedFake, Math.max(0, snapshot.fakePlayers().size() - 1));
        rebuildMainWidgets();
    }

    @Override
    protected void init() {
        rebuildMainWidgets();
    }

    private void rebuildMainWidgets() {
        clearWidgets();
        int left = panelLeft();
        int top = 34;
        int contentWidth = width - 24;
        int sideWidth = Math.min(SIDE_WIDTH, Math.max(148, contentWidth / 5));
        int centerLeft = left + sideWidth + GAP;
        int rightLeft = centerLeft + centerWidth() + GAP;
        int rightWidth = Math.max(150, width - rightLeft - left);

        addRenderableWidget(new SolidButton(left + PANEL_PADDING, top + 8, 18, 18, PixelGlyph.BACK,
            Component.translatable("gui.back"), button -> onClose()));
        addRenderableWidget(new SolidButton(left + PANEL_PADDING, top + 54, sideWidth - PANEL_PADDING * 2, 24,
            Component.translatable("gui.archweaver.main.fake_players"), button -> focusFakePlayers()));
        addRenderableWidget(new SolidButton(left + PANEL_PADDING, top + 84, sideWidth - PANEL_PADDING * 2, 24,
            Component.translatable("gui.archweaver.main.map"), button -> openMap()));
        addRenderableWidget(new SolidButton(left + PANEL_PADDING, top + 114, sideWidth - PANEL_PADDING * 2, 24,
            Component.translatable("gui.archweaver.main.presets"), button -> openPresets()));

        int listTop = top + 38;
        int listWidth = centerWidth() - PANEL_PADDING * 2;
        int count = Math.min(snapshot.fakePlayers().size(), 7);
        for (int index = 0; index < count; index++) {
            int fakeIndex = index;
            SolidButton row = addRenderableWidget(new SolidButton(centerLeft + PANEL_PADDING,
                listTop + index * (ROW_HEIGHT + 4), listWidth, ROW_HEIGHT,
                Component.literal(snapshot.fakePlayers().get(index).name()), button -> selectFake(fakeIndex)));
            row.setTextColor(index == selectedFake ? 0xFFE7C778 : 0xFFFFFFFF);
        }
        addRenderableWidget(new SolidButton(centerLeft + PANEL_PADDING, top + 316, listWidth, 22,
            Component.translatable("gui.archweaver.main.spawn"), button -> openSpawn()));
        addRenderableWidget(new SolidButton(centerLeft + centerWidth() - PANEL_PADDING - 18, top + 8, 18, 18,
            PixelGlyph.REFRESH, Component.translatable("gui.archweaver.main.refresh"), button -> refresh()));

        addRenderableWidget(new SolidButton(rightLeft + PANEL_PADDING, top + 128, rightWidth - PANEL_PADDING * 2, 24,
            Component.translatable("gui.archweaver.main.open_map"), button -> openMap()));
        addRenderableWidget(new SolidButton(rightLeft + PANEL_PADDING, top + 158, rightWidth - PANEL_PADDING * 2, 22,
            Component.translatable("gui.archweaver.main.manage_regions"), button -> openManagement()));
        for (int index = 0; index < settingButtons.length; index++) {
            int settingIndex = index;
            settingButtons[index] = addRenderableWidget(new SolidButton(rightLeft + PANEL_PADDING,
                top + 246 + index * 28, rightWidth - PANEL_PADDING * 2, 22,
                settingLabel(index), button -> toggleSetting(settingIndex)));
        }
    }

    private int panelLeft() {
        return Math.max(6, (width - Math.min(980, width - 12)) / 2);
    }

    private int centerWidth() {
        int contentWidth = width - 24;
        int sideWidth = Math.min(SIDE_WIDTH, Math.max(148, contentWidth / 5));
        int remaining = contentWidth - sideWidth - GAP * 2;
        return Math.max(250, remaining / 2);
    }

    private void selectFake(int index) {
        selectedFake = index;
        rebuildMainWidgets();
    }

    private void focusFakePlayers() {
        PlatformNetworking.sendToServer(new OpenFakePlayerPagePayload(OpenFakePlayerPagePayload.Page.LIST));
    }

    private void openMap() {
        ClientChunkLoadingState.openMap(ClientChunkLoadingState.MapReturnTarget.MAIN, false, false);
    }

    private void openManagement() {
        ClientChunkLoadingState.openMap(ClientChunkLoadingState.MapReturnTarget.MAIN, true, false);
    }

    private void openSpawn() {
        PlatformNetworking.sendToServer(new OpenFakePlayerPagePayload(OpenFakePlayerPagePayload.Page.SPAWN));
    }

    private void openPresets() {
        PlatformNetworking.sendToServer(new OpenFakePlayerPagePayload(OpenFakePlayerPagePayload.Page.PRESETS));
    }

    private void refresh() {
        PlatformNetworking.sendToServer(ClientChunkLoadingState.request(false, false, false));
    }

    private void toggleSetting(int index) {
        PlatformNetworking.sendToServer(new ToggleGlobalSettingPayload(index));
        for (Button button : settingButtons) if (button != null) button.active = false;
    }

    private Component settingLabel(int index) {
        String key = index == 0 ? "restore_players" : "container_transfer_buttons";
        boolean enabled = (snapshot.globalSettingsMask() & (1 << index)) != 0;
        return Component.translatable("gui.fakeplayer.global.setting_value",
            Component.translatable("gui.fakeplayer.global.setting." + key),
            Component.translatable(enabled ? "gui.fakeplayer.global.enabled" : "gui.fakeplayer.global.disabled"));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int left = panelLeft();
        int top = 24;
        int sideWidth = Math.min(SIDE_WIDTH, Math.max(148, (width - 24) / 5));
        int centerLeft = left + sideWidth + GAP;
        int centerWidth = centerWidth();
        int rightLeft = centerLeft + centerWidth + GAP;
        int rightWidth = Math.max(150, width - rightLeft - left);
        drawPanel(graphics, left, top, sideWidth, height - 48);
        drawPanel(graphics, centerLeft, top, centerWidth, height - 48);
        drawPanel(graphics, rightLeft, top, rightWidth, height - 48);
        graphics.fill(left, top, left + sideWidth, top + 34, 0xFF373737);
        graphics.fill(centerLeft, top, centerLeft + centerWidth, top + 34, 0xFF373737);
        graphics.fill(rightLeft, top, rightLeft + rightWidth, top + 34, 0xFF373737);
        graphics.fill(left, top + 34, left + sideWidth, top + 36, 0xFF8B8B8B);
        graphics.fill(centerLeft, top + 34, centerLeft + centerWidth, top + 36, 0xFF8B8B8B);
        graphics.fill(rightLeft, top + 34, rightLeft + rightWidth, top + 36, 0xFF8B8B8B);
        drawMapPreview(graphics, rightLeft + PANEL_PADDING, top + 42, rightWidth - PANEL_PADDING * 2, 76);
        drawFakeDetail(graphics, centerLeft, top + 348, centerWidth);
        drawLabels(graphics);
    }

    private void drawPanel(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, 0xF0222528);
        graphics.outline(x, y, w, h, 0xFF8B8B8B);
    }

    private void drawMapPreview(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, 0xFF41624F);
        int tile = Math.max(12, Math.min(24, w / 8));
        for (int row = -1; row < h / tile + 1; row++) {
            for (int col = -1; col < w / tile + 1; col++) {
                int color = ((row * 5 + col * 3) & 3) == 0 ? 0xFF52765A : 0xFF5F815F;
                graphics.fill(x + col * tile, y + row * tile, x + col * tile + tile - 1,
                    y + row * tile + tile - 1, color);
            }
        }
        graphics.fill(x + w / 2 - 3, y + h / 2 - 3, x + w / 2 + 4, y + h / 2 + 4, 0xFFFFFFFF);
        graphics.outline(x + w / 2 - tile * 2, y + h / 2 - tile * 2, tile * 4, tile * 4, 0xAA5AD39A);
    }

    private void drawFakeDetail(GuiGraphicsExtractor graphics, int x, int y, int w) {
        graphics.fill(x + 1, y, x + w - 1, y + 1, 0xFF8B8B8B);
        if (snapshot.fakePlayers().isEmpty()) {
            graphics.centeredText(font, Component.translatable("gui.archweaver.main.no_fake_players"), x + w / 2, y + 28, 0xFFFFFFFF);
            return;
        }
        ChunkMapSnapshotPayload.FakePlayerView fake = snapshot.fakePlayers().get(selectedFake);
        graphics.text(font, Component.translatable("gui.archweaver.main.details"), x + PANEL_PADDING, y + 12, 0xFFFFFFFF, false);
        graphics.text(font, Component.literal(fake.name()), x + PANEL_PADDING, y + 30, 0xFFFFFFFF, false);
        graphics.text(font, Component.translatable("gui.archweaver.main.position", fake.x(), fake.y(), fake.z()),
            x + PANEL_PADDING, y + 47, 0xFFFFFFFF, false);
        graphics.text(font, Component.translatable("gui.archweaver.main.loading", fake.loadingActive()
            ? fake.loadingDistance() : 0), x + PANEL_PADDING, y + 64, 0xFFFFFFFF, false);
    }

    private void drawLabels(GuiGraphicsExtractor graphics) {
        int left = panelLeft();
        int sideWidth = Math.min(SIDE_WIDTH, Math.max(148, (width - 24) / 5));
        int centerLeft = left + sideWidth + GAP;
        int centerWidth = centerWidth();
        int rightLeft = centerLeft + centerWidth + GAP;
        graphics.centeredText(font, Component.translatable("gui.archweaver.main.title"), left + sideWidth / 2, 35, 0xFFFFFFFF);
        graphics.centeredText(font, Component.translatable("gui.archweaver.main.fake_players"),
            centerLeft + centerWidth / 2, 35, 0xFFFFFFFF);
        graphics.centeredText(font, Component.translatable("gui.archweaver.main.map_and_loading"),
            rightLeft + (width - rightLeft - left) / 2, 35, 0xFFFFFFFF);
        graphics.text(font, Component.translatable("gui.archweaver.main.online", snapshot.fakePlayers().size()),
            centerLeft + PANEL_PADDING, 54, 0xFFFFFFFF, false);
        graphics.text(font, Component.translatable("gui.archweaver.main.regions", snapshot.managementRegions().size()),
            rightLeft + PANEL_PADDING, 128, 0xFFFFFFFF, false);
        graphics.text(font, Component.translatable("gui.archweaver.main.settings"),
            rightLeft + PANEL_PADDING, 224, 0xFFFFFFFF, false);
        graphics.text(font, Component.translatable("gui.archweaver.main.world", snapshot.dimension()),
            left + PANEL_PADDING, height - 38, 0xFFFFFFFF, false);
    }
}
