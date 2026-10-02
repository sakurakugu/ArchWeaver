package com.sakurakugu.archweaver.client;

import com.sakurakugu.archweaver.client.chunkloading.ClientChunkLoadingState;
import com.sakurakugu.archweaver.client.ui.PixelGlyph;
import com.sakurakugu.archweaver.client.ui.SolidButton;
import com.sakurakugu.archweaver.client.ui.TitlePanel;
import com.sakurakugu.archweaver.network.ChunkMapSnapshotPayload;
import com.sakurakugu.archweaver.network.OpenFakePlayerPagePayload;
import com.sakurakugu.archweaver.network.ToggleGlobalSettingPayload;
import com.sakurakugu.archweaver.platform.PlatformNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/** G 键打开的控制中心：左侧导航，中间内容，右侧详情。 */
public final class MainPageScreen extends Screen {
    private static final int SIDE_WIDTH = 190;
    private static final int GAP = 8;
    private static final int PANEL_PADDING = 12;
    private static final int ROW_HEIGHT = 34;
    private static final int PANEL_TOP = 24;
    private static View pendingView;

    private ChunkMapSnapshotPayload snapshot;
    private UUID selectedFake;
    private int selectedRegion = -1;
    private View view = View.FAKE_PLAYERS;
    private final Button[] settingButtons = new Button[2];

    public MainPageScreen(ChunkMapSnapshotPayload snapshot) {
        this(snapshot, consumePendingView());
    }

    private MainPageScreen(ChunkMapSnapshotPayload snapshot, View initialView) {
        super(Component.translatable("gui.archweaver.main.title"));
        this.snapshot = snapshot;
        view = initialView;
    }

    /** 二级页面返回时保留进入二级页面前的中心视图。 */
    private static View consumePendingView() {
        View result = pendingView;
        pendingView = null;
        return result == null ? View.FAKE_PLAYERS : result;
    }

    private static void rememberView(View value) {
        pendingView = value;
    }

    public static void clearPendingView() {
        pendingView = null;
    }

    public void update(ChunkMapSnapshotPayload value) {
        snapshot = value;
        if (snapshot.fakePlayers().stream().noneMatch(fake -> fake.id().equals(selectedFake))) selectedFake = null;
        if (selectedRegion >= snapshot.managementRegions().size()) selectedRegion = -1;
        rebuildMainWidgets();
    }

    @Override
    protected void init() {
        rebuildMainWidgets();
    }

    private void rebuildMainWidgets() {
        clearWidgets();
        settingButtons[0] = null;
        settingButtons[1] = null;
        int left = panelLeft();
        int top = PANEL_TOP;
        int sideWidth = sideWidth();
        int centerLeft = left + sideWidth + GAP;
        int centerWidth = centerWidth();
        TitlePanel navigationPanel = new TitlePanel(left, top, sideWidth, height - 48,
            Component.translatable("gui.archweaver.main.title"));

        addRenderableWidget(new SolidButton(navigationPanel.leftButtonX(), navigationPanel.buttonY(18), 18, 18,
            PixelGlyph.BACK, Component.translatable("gui.back"), button -> ClientScreenNavigation.back(this)));
        addNavigationButton(left, top, sideWidth, 0, Component.translatable("gui.archweaver.main.fake_players"), View.FAKE_PLAYERS);
        addNavigationButton(left, top, sideWidth, 1, Component.translatable("gui.archweaver.main.map"), View.MAP);
        addNavigationButton(left, top, sideWidth, 2, Component.translatable("gui.archweaver.main.settings"), View.SETTINGS);

        if (view == View.FAKE_PLAYERS) {
            int listWidth = centerWidth - PANEL_PADDING * 2;
            int listTop = top + 58;
            int count = Math.min(snapshot.fakePlayers().size(), Math.max(0, (height - 198) / (ROW_HEIGHT + 4)));
            for (int index = 0; index < count; index++) {
                int fakeIndex = index;
                SolidButton row = addRenderableWidget(new SolidButton(centerLeft + PANEL_PADDING,
                    listTop + index * (ROW_HEIGHT + 4), listWidth, ROW_HEIGHT,
                    Component.literal(snapshot.fakePlayers().get(index).name()), button -> selectFake(fakeIndex)));
                if (snapshot.fakePlayers().get(index).id().equals(selectedFake)) row.setTextColor(0xFF55FF55);
            }
            int footerY = top + height - 82;
            int presetButtonWidth = (listWidth - GAP) / 2;
            addRenderableWidget(new SolidButton(centerLeft + PANEL_PADDING, footerY, presetButtonWidth, 22,
                Component.translatable("gui.archweaver.main.presets"), button -> openPresets()));
            addRenderableWidget(new SolidButton(centerLeft + PANEL_PADDING + presetButtonWidth + GAP, footerY,
                listWidth - presetButtonWidth - GAP, 22, Component.translatable("gui.archweaver.main.spawn"), button -> openSpawn()));
            addRenderableWidget(new SolidButton(centerLeft + centerWidth - PANEL_PADDING - 18, top + 9, 18, 18,
                PixelGlyph.REFRESH, Component.translatable("gui.archweaver.main.refresh"), button -> refresh()));
        } else if (view == View.MAP) {
            addMapWidgets(centerLeft, centerWidth, top);
        } else {
            addSettingsWidgets(centerLeft, centerWidth, top);
        }
    }

    private void addNavigationButton(int left, int top, int sideWidth, int index, Component label, View target) {
        SolidButton button = addRenderableWidget(new SolidButton(left + PANEL_PADDING, top + 54 + index * 30,
            sideWidth - PANEL_PADDING * 2, 24, label, ignored -> switchView(target)));
        if (view == target) button.setTextColor(0xFF55FF55);
    }

    private void addMapWidgets(int centerLeft, int centerWidth, int top) {
        int listWidth = centerWidth - PANEL_PADDING * 2;
        int listTop = top + 76;
        int count = Math.min(snapshot.managementRegions().size(), Math.max(0, (height - 216) / (ROW_HEIGHT + 4)));
        for (int index = 0; index < count; index++) {
            int regionIndex = index;
            ChunkMapSnapshotPayload.RegionSummary region = snapshot.managementRegions().get(index);
            SolidButton row = addRenderableWidget(new SolidButton(centerLeft + PANEL_PADDING,
                listTop + index * (ROW_HEIGHT + 4), listWidth, ROW_HEIGHT, Component.literal(region.name()),
                button -> selectRegion(regionIndex)));
            if (selectedRegion == index) row.setTextColor(0xFF55FF55);
        }
        int footerY = top + height - 82;
        int half = (listWidth - GAP) / 2;
        addRenderableWidget(new SolidButton(centerLeft + PANEL_PADDING, footerY, half, 22,
            Component.translatable("gui.archweaver.main.open_map"), button -> openMap()));
        addRenderableWidget(new SolidButton(centerLeft + PANEL_PADDING + half + GAP, footerY,
            listWidth - half - GAP, 22, Component.translatable("gui.archweaver.main.manage_regions"), button -> openManagement()));
        addRenderableWidget(new SolidButton(centerLeft + centerWidth - PANEL_PADDING - 18, top + 9, 18, 18,
            PixelGlyph.REFRESH, Component.translatable("gui.archweaver.main.refresh"), button -> refresh()));
    }

    private void addSettingsWidgets(int centerLeft, int centerWidth, int top) {
        int buttonWidth = centerWidth - PANEL_PADDING * 2;
        for (int index = 0; index < settingButtons.length; index++) {
            int settingIndex = index;
            settingButtons[index] = addRenderableWidget(new SolidButton(centerLeft + PANEL_PADDING,
                top + 62 + index * 32, buttonWidth, 24, settingLabel(index), button -> toggleSetting(settingIndex)));
        }
    }

    private int panelLeft() {
        return Math.max(6, (width - Math.min(980, width - 12)) / 2);
    }

    private int sideWidth() {
        return Math.min(SIDE_WIDTH, Math.max(148, (width - 24) / 5));
    }

    private int centerWidth() {
        int contentWidth = width - 24;
        int remaining = contentWidth - sideWidth() - GAP * 2;
        return Math.max(250, remaining / 2);
    }

    private void switchView(View target) {
        if (view != target) {
            view = target;
            rebuildMainWidgets();
        }
    }

    private void selectFake(int index) {
        UUID clickedFake = snapshot.fakePlayers().get(index).id();
        selectedFake = clickedFake.equals(selectedFake) ? null : clickedFake;
        rebuildMainWidgets();
    }

    private void selectRegion(int index) {
        selectedRegion = selectedRegion == index ? -1 : index;
        rebuildMainWidgets();
    }

    private void openMap() {
        rememberView(view);
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
        int top = PANEL_TOP;
        int sideWidth = sideWidth();
        int centerLeft = left + sideWidth + GAP;
        int centerWidth = centerWidth();
        int rightLeft = centerLeft + centerWidth + GAP;
        int rightWidth = Math.max(150, width - rightLeft - left);
        int panelHeight = height - 48;
        new TitlePanel(left, top, sideWidth, panelHeight, Component.translatable("gui.archweaver.main.title")).draw(graphics, font);
        new TitlePanel(centerLeft, top, centerWidth, panelHeight, centerTitle()).draw(graphics, font);
        new TitlePanel(rightLeft, top, rightWidth, panelHeight, Component.translatable("gui.archweaver.main.details")).draw(graphics, font);

        if (view == View.FAKE_PLAYERS) {
            graphics.text(font, Component.translatable("gui.archweaver.main.online", snapshot.fakePlayers().size()),
                centerLeft + PANEL_PADDING, top + 44, 0xFFFFFFFF, false);
            drawFakeDetail(graphics, rightLeft, top + 48, rightWidth);
        } else if (view == View.MAP) {
            graphics.text(font, Component.translatable("gui.archweaver.main.regions", snapshot.managementRegions().size()),
                centerLeft + PANEL_PADDING, top + 44, 0xFFFFFFFF, false);
            drawRegionDetail(graphics, rightLeft, top + 48, rightWidth);
        } else {
            drawSettingsDetail(graphics, rightLeft, top + 48, rightWidth);
        }
    }

    private Component centerTitle() {
        return switch (view) {
            case FAKE_PLAYERS -> Component.translatable("gui.archweaver.main.fake_players");
            case MAP -> Component.translatable("gui.archweaver.main.map");
            case SETTINGS -> Component.translatable("gui.archweaver.main.settings");
        };
    }

    private void drawFakeDetail(GuiGraphicsExtractor graphics, int x, int y, int w) {
        ChunkMapSnapshotPayload.FakePlayerView fake = snapshot.fakePlayers().stream()
            .filter(value -> value.id().equals(selectedFake)).findFirst().orElse(null);
        if (fake == null) {
            graphics.centeredText(font, Component.translatable(snapshot.fakePlayers().isEmpty()
                ? "gui.archweaver.main.no_fake_players" : "gui.archweaver.main.select_fake"), x + w / 2, y + 32, 0xFFFFFFFF);
            return;
        }
        graphics.text(font, Component.literal(fake.name()), x + PANEL_PADDING, y + 12, 0xFFFFFFFF, false);
        graphics.text(font, Component.translatable("gui.archweaver.main.world", fake.dimension()), x + PANEL_PADDING, y + 32, 0xFFFFFFFF, false);
        graphics.text(font, Component.translatable("gui.archweaver.main.position", fake.x(), fake.y(), fake.z()), x + PANEL_PADDING, y + 50, 0xFFFFFFFF, false);
        graphics.text(font, Component.translatable("gui.archweaver.main.loading", fake.loadingActive() ? fake.loadingDistance() : 0), x + PANEL_PADDING, y + 68, 0xFFFFFFFF, false);
        graphics.text(font, Component.translatable("gui.archweaver.main.simulation", fake.simulationDistance()), x + PANEL_PADDING, y + 86, 0xFFFFFFFF, false);
    }

    private void drawRegionDetail(GuiGraphicsExtractor graphics, int x, int y, int w) {
        if (selectedRegion < 0 || selectedRegion >= snapshot.managementRegions().size()) {
            graphics.centeredText(font, Component.translatable("gui.archweaver.main.select_region"), x + w / 2, y + 32, 0xFFFFFFFF);
            return;
        }
        ChunkMapSnapshotPayload.RegionSummary region = snapshot.managementRegions().get(selectedRegion);
        graphics.text(font, Component.literal(region.name()), x + PANEL_PADDING, y + 12, 0xFFFFFFFF, false);
        graphics.text(font, Component.translatable("gui.archweaver.main.world", region.dimension()), x + PANEL_PADDING, y + 32, 0xFFFFFFFF, false);
        graphics.text(font, Component.translatable("gui.archweaver.main.region_position", region.chunkX(), region.chunkZ()), x + PANEL_PADDING, y + 50, 0xFFFFFFFF, false);
        graphics.text(font, Component.translatable("gui.archweaver.main.region_size", region.chunkCount(), region.radius()), x + PANEL_PADDING, y + 68, 0xFFFFFFFF, false);
        graphics.text(font, Component.translatable("gui.archweaver.main.region_status", Component.translatable(region.enabled() ? "gui.fakeplayer.global.enabled" : "gui.fakeplayer.global.disabled")), x + PANEL_PADDING, y + 86, 0xFFFFFFFF, false);
    }

    private void drawSettingsDetail(GuiGraphicsExtractor graphics, int x, int y, int w) {
        graphics.centeredText(font, Component.translatable("gui.archweaver.main.settings_hint"), x + w / 2, y + 32, 0xFFFFFFFF);
        graphics.text(font, Component.translatable("gui.archweaver.main.settings_count", settingButtons.length), x + PANEL_PADDING, y + 66, 0xFFC6C6C6, false);
    }

    private enum View {
        FAKE_PLAYERS,
        MAP,
        SETTINGS
    }
}
