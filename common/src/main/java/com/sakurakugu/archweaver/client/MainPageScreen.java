package com.sakurakugu.archweaver.client;

import com.sakurakugu.archweaver.client.chunkloading.ChunkMapClientConfig;
import com.sakurakugu.archweaver.client.chunkloading.ClientChunkLoadingState;
import com.sakurakugu.archweaver.client.ui.PixelGlyph;
import com.sakurakugu.archweaver.client.ui.SolidButton;
import com.sakurakugu.archweaver.client.ui.FakePlayerListButton;
import com.sakurakugu.archweaver.client.ui.TitlePanel;
import com.sakurakugu.archweaver.client.ui.ToggleSwitchButton;
import com.sakurakugu.archweaver.config.ArchWeaverConfig;
import com.sakurakugu.archweaver.network.ChunkMapSnapshotPayload;
import com.sakurakugu.archweaver.network.ChunkMapOpenTarget;
import com.sakurakugu.archweaver.network.OpenFakePlayerInventoryPayload;
import com.sakurakugu.archweaver.network.OpenFakePlayerPagePayload;
import com.sakurakugu.archweaver.network.MannequinLifecyclePayload;
import com.sakurakugu.archweaver.network.ToggleGlobalSettingPayload;
import com.sakurakugu.archweaver.platform.PlatformNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/** G 键打开的控制中心：左侧导航，中间内容，右侧详情。 */
public final class MainPageScreen extends Screen {
    private static final int SIDE_WIDTH = 130; // 左侧导航栏宽度，受窗口宽度限制
    private static final int MAX_WIDTH = 900; // 控制中心整体最大宽度，超出时左右居中。
    private static final int GAP = 8; // 面板之间、按钮之间的水平间距。
    private static final int PANEL_PADDING = 8; // 面板内容距离面板边缘的内边距。
    private static final int ROW_HEIGHT = 34; // 列表里每行按钮的高度。
    private static final int ROW_GAP = 4; // 列表里相邻两行按钮的间距。
    private static final int FOOTER_HEIGHT = 22; // 面板底部操作按钮的高度。
    private static final int TOOLBAR_HEIGHT = 18; // 列表上方工具行的高度，与行内的图标按钮同高。
    private static final int PANEL_TOP = 12; // 面板距离窗口顶部、底部的距离。

    private ChunkMapSnapshotPayload snapshot; // 最近一次从服务端同步来的状态快照。
    private UUID selectedFake; // 选中的假人 ID，没有选中时为 null。
    private int selectedRegion = -1; // 选中的管理区域下标，没有选中时为 -1。
    private View view = View.FAKE_PLAYERS; // 中间内容区当前显示的页面。
    private final Button[] settingButtons = new Button[ArchWeaverConfig.GlobalSetting.values().length]; // 全局设置开关按钮，点击后统一置灰。
    private TargetTypeFilter targetTypeFilter = TargetTypeFilter.ALL;
    private LoadFilter loadFilter = LoadFilter.REGISTERED;

    public MainPageScreen(ChunkMapSnapshotPayload snapshot) {
        this(snapshot, View.FAKE_PLAYERS);
    }

    public MainPageScreen(ChunkMapSnapshotPayload snapshot, View initialView) {
        super(Component.translatable("gui.archweaver.main.title"));
        this.snapshot = snapshot;
        this.view = initialView;
        recordView();
    }

    /** 把页面编号还原成页面，越界时回落到假人列表。 */
    public static View fromIndex(int index) {
        View[] views = View.values();
        return index >= 0 && index < views.length ? views[index] : View.FAKE_PLAYERS;
    }

    /** 记住当前页面，下次打开控制中心时直接回到这里。 */
    private void recordView() {
        ChunkMapClientConfig.setMainPageView(view.ordinal());
    }

    @Override
    public void removed() {
        ChunkMapClientConfig.save();
        super.removed();
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
        java.util.Arrays.fill(settingButtons, null);
        int left = panelLeft();
        int top = PANEL_TOP;
        int sideWidth = sideWidth();
        int centerLeft = left + sideWidth + GAP;
        int centerWidth = centerWidth();
        TitlePanel navigationPanel = new TitlePanel(left, top, sideWidth, panelHeight(),
            Component.translatable("gui.archweaver.main.title"));

        addRenderableWidget(new SolidButton(navigationPanel.leftButtonX(), navigationPanel.buttonY(18), 18, 18,
            PixelGlyph.BACK, Component.translatable("gui.back"), button -> ClientScreenNavigation.back(this)));
        addNavigationButton(0, Component.translatable("gui.archweaver.main.fake_players"), View.FAKE_PLAYERS);
        addNavigationButton(1, Component.translatable("gui.archweaver.main.map"), View.MAP);
        addNavigationButton(2, Component.translatable("gui.archweaver.main.settings"), View.SETTINGS);

        if (view == View.FAKE_PLAYERS) {
            int listWidth = Math.max(1, centerWidth - PANEL_PADDING * 2);
            int listTop = fakeListTop();
            java.util.List<ChunkMapSnapshotPayload.FakePlayerView> visible = visibleFakes();
            int count = Math.min(visible.size(), rowCapacity(listTop));
            for (int index = 0; index < count; index++) {
                int fakeIndex = index;
                var fake = visible.get(index);
                addRenderableWidget(new FakePlayerListButton(centerLeft + PANEL_PADDING,
                    listTop + index * (ROW_HEIGHT + ROW_GAP), listWidth, ROW_HEIGHT,
                    fake.id(), fake.alias(), fake.name(),
                    globalSetting(ArchWeaverConfig.GlobalSetting.FAKE_PLAYER_ALIAS_FIRST),
                    globalSetting(ArchWeaverConfig.GlobalSetting.FAKE_PLAYER_EMPTY_ALIAS_MARKER),
                    fake.id().equals(selectedFake), button -> selectFake(visible.get(fakeIndex).id())));
            }
            // 生成假人收成加号，钉在列表左上角；预设管理紧随其后，占满工具行剩余宽度。
            addRenderableWidget(new SolidButton(centerLeft + PANEL_PADDING, toolbarY(),
                TOOLBAR_HEIGHT, TOOLBAR_HEIGHT, PixelGlyph.ADD,
                Component.translatable("gui.archweaver.main.spawn"), button -> openSpawn()));
            int presetLeft = centerLeft + PANEL_PADDING + TOOLBAR_HEIGHT + GAP;
            addRenderableWidget(new SolidButton(presetLeft, toolbarY(),
                Math.max(1, centerLeft + centerWidth - PANEL_PADDING - presetLeft), TOOLBAR_HEIGHT,
                Component.translatable("gui.archweaver.main.presets"), button -> openPresets()));
            int filterLeft = presetLeft;
            int filterWidth = Math.max(1, centerLeft + centerWidth - PANEL_PADDING - filterLeft);
            int filterHalf = Math.max(1, (filterWidth - 2) / 2);
            addRenderableWidget(new SolidButton(filterLeft, toolbarY() + TOOLBAR_HEIGHT + 1,
                filterHalf, TOOLBAR_HEIGHT, Component.literal(targetTypeFilter.label()), button -> cycleTargetType()));
            addRenderableWidget(new SolidButton(filterLeft + filterHalf + 2, toolbarY() + TOOLBAR_HEIGHT + 1,
                Math.max(1, filterWidth - filterHalf - 2), TOOLBAR_HEIGHT,
                Component.literal(loadFilter.label()), button -> cycleLoadFilter()));
            addRenderableWidget(new SolidButton(centerLeft + centerWidth - PANEL_PADDING - 18, top + 4, 18, 18,
                PixelGlyph.REFRESH, Component.translatable("gui.archweaver.main.refresh"), button -> refresh()));
            int rightLeft = centerLeft + centerWidth + GAP;
            int rightWidth = Math.max(1, left + panelWidth() - rightLeft);
            addFakeDetailWidgets(rightLeft, rightWidth);
        } else if (view == View.MAP) {
            addMapWidgets(centerLeft, centerWidth, top);
        } else {
            addSettingsWidgets(centerLeft, centerWidth);
        }
    }

    private void addNavigationButton(int index, Component label, View target) {
        SolidButton button = addRenderableWidget(new SolidButton(panelLeft() + PANEL_PADDING,
            contentTop() + PANEL_PADDING + index * 30, Math.max(1, sideWidth() - PANEL_PADDING * 2), 24, label,
            ignored -> switchView(target)));
        if (view == target) button.setTextColor(0xFF55FF55);
    }

    private void addMapWidgets(int centerLeft, int centerWidth, int top) {
        int listWidth = Math.max(1, centerWidth - PANEL_PADDING * 2);
        int listTop = regionListTop();
        int count = Math.min(snapshot.managementRegions().size(), rowCapacity(listTop));
        for (int index = 0; index < count; index++) {
            int regionIndex = index;
            ChunkMapSnapshotPayload.RegionSummary region = snapshot.managementRegions().get(index);
            SolidButton row = addRenderableWidget(new SolidButton(centerLeft + PANEL_PADDING,
                listTop + index * (ROW_HEIGHT + ROW_GAP), listWidth, ROW_HEIGHT, Component.literal(region.name()),
                button -> selectRegion(regionIndex)));
            if (selectedRegion == index) row.setTextColor(0xFF55FF55);
        }
        int footerY = footerY();
        int half = Math.max(1, (listWidth - GAP) / 2);
        addRenderableWidget(new SolidButton(centerLeft + PANEL_PADDING, footerY, half, FOOTER_HEIGHT,
            Component.translatable("gui.archweaver.main.open_map"), button -> openMap()));
        addRenderableWidget(new SolidButton(centerLeft + PANEL_PADDING + half + GAP, footerY,
            Math.max(1, listWidth - half - GAP), FOOTER_HEIGHT,
            Component.translatable("gui.archweaver.main.manage_regions"), button -> openManagement()));
        addRenderableWidget(new SolidButton(centerLeft + centerWidth - PANEL_PADDING - 18, top + 4, 18, 18,
            PixelGlyph.REFRESH, Component.translatable("gui.archweaver.main.refresh"), button -> refresh()));
    }

    private void addSettingsWidgets(int centerLeft, int centerWidth) {
        int buttonWidth = Math.max(1, centerWidth - PANEL_PADDING * 2);
        for (int index = 0; index < settingButtons.length; index++) {
            int settingIndex = index;
            settingButtons[index] = addRenderableWidget(new ToggleSwitchButton(centerLeft + PANEL_PADDING,
                contentTop() + PANEL_PADDING + index * 32, buttonWidth, 24, settingLabel(index), 0xFFFFFFFF,
                () -> globalSetting(ArchWeaverConfig.GlobalSetting.values()[settingIndex]),
                button -> toggleSetting(settingIndex)));
        }
    }

    /** 面板高度，上下各留出 {@link #PANEL_TOP} 的边距。 */
    private int panelHeight() {
        return height - PANEL_TOP * 2;
    }

    /** 面板内容区的起始纵坐标，跳过标题栏与其下方的分隔线。 */
    private int contentTop() {
        return PANEL_TOP + TitlePanel.HEADER_HEIGHT + 1;
    }

    /** 列表上方工具行的纵坐标，紧贴内容区顶部。 */
    private int toolbarY() {
        return contentTop() + PANEL_PADDING;
    }

    /** 假人列表首行的纵坐标，位于工具行下方。 */
    private int fakeListTop() {
        return toolbarY() + TOOLBAR_HEIGHT * 2 + 1 + GAP;
    }

    /** 区块列表首行的纵坐标，位于内容区顶部的状态文字下方。 */
    private int regionListTop() {
        return contentTop() + PANEL_PADDING + font.lineHeight + 6;
    }

    /** 面板底部操作按钮的纵坐标。 */
    private int footerY() {
        return PANEL_TOP + panelHeight() - PANEL_PADDING - FOOTER_HEIGHT;
    }

    /** 底部状态文字的纵坐标，沿用原来底部操作按钮所在的那一行。 */
    private int statusY() {
        return footerY() + (FOOTER_HEIGHT - font.lineHeight) / 2;
    }

    /** 列表在底部那一行（操作按钮或状态文字）之前能完整容纳的行数。 */
    private int rowCapacity(int listTop) {
        return Math.max(0, (footerY() - GAP - listTop + ROW_GAP) / (ROW_HEIGHT + ROW_GAP));
    }

    /** 控制中心占用的总宽度，受窗口宽度和 {@link #MAX_WIDTH} 限制。 */
    private int panelWidth() {
        return Math.max(1, Math.min(MAX_WIDTH, width - 12));
    }

    private int panelLeft() {
        return Math.max(0, (width - panelWidth()) / 2);
    }

    private int sideWidth() {
        int available = Math.max(1, panelWidth() - GAP * 2);
        return Math.min(SIDE_WIDTH, Math.max(1, available / 4));
    }

    /** 中间内容区与右侧详情区等宽的剩余空间。 */
    private int centerWidth() {
        int remaining = panelWidth() - sideWidth() - GAP * 2;
        return Math.max(1, remaining / 2);
    }

    private void switchView(View target) {
        if (view != target) {
            view = target;
            recordView();
            rebuildMainWidgets();
        }
    }

    private void selectFake(UUID clickedFake) {
        selectedFake = clickedFake.equals(selectedFake) ? null : clickedFake;
        rebuildMainWidgets();
    }

    private void selectRegion(int index) {
        selectedRegion = selectedRegion == index ? -1 : index;
        rebuildMainWidgets();
    }

    private void openMap() {
        ClientChunkLoadingState.openMap(ClientChunkLoadingState.MapReturnTarget.MAIN, ChunkMapOpenTarget.MAP);
    }

    private void openManagement() {
        ClientChunkLoadingState.openMap(ClientChunkLoadingState.MapReturnTarget.MAIN,
            ChunkMapOpenTarget.MANAGEMENT);
    }

    private void openSpawn() {
        PlatformNetworking.sendToServer(new OpenFakePlayerPagePayload(OpenFakePlayerPagePayload.Page.SPAWN));
    }

    private void openPresets() {
        PlatformNetworking.sendToServer(new OpenFakePlayerPagePayload(OpenFakePlayerPagePayload.Page.PRESETS));
    }

    private void openInventory(ChunkMapSnapshotPayload.FakePlayerView fake) {
        PlatformNetworking.sendToServer(new OpenFakePlayerInventoryPayload(fake.name(),
            fake.type() == ChunkMapSnapshotPayload.TargetType.MANNEQUIN));
    }

    /** 在选中的假人详情底部提供直接打开背包的入口。 */
    private void addFakeDetailWidgets(int x, int w) {
        ChunkMapSnapshotPayload.FakePlayerView fake = snapshot.fakePlayers().stream()
            .filter(value -> value.id().equals(selectedFake)).findFirst().orElse(null);
        if (fake == null) return;
        SolidButton openInventoryButton = addRenderableWidget(new SolidButton(x + PANEL_PADDING, footerY(),
            Math.max(1, w - PANEL_PADDING * 2), FOOTER_HEIGHT,
            Component.translatable("gui.archweaver.main.open_inventory"),
            button -> openInventory(fake)));
        openInventoryButton.active = fake.canOpenInventory();
        if (fake.type() == ChunkMapSnapshotPayload.TargetType.MANNEQUIN && !fake.loaded()) {
            addRenderableWidget(new SolidButton(x + PANEL_PADDING, footerY() - FOOTER_HEIGHT - GAP,
                Math.max(1, w - PANEL_PADDING * 2), FOOTER_HEIGHT,
                Component.literal("加载玩偶"), button -> lifecycle(fake, MannequinLifecyclePayload.Action.LOAD)));
        } else if (fake.type() == ChunkMapSnapshotPayload.TargetType.MANNEQUIN) {
            addRenderableWidget(new SolidButton(x + PANEL_PADDING, footerY() - FOOTER_HEIGHT - GAP,
                Math.max(1, w - PANEL_PADDING * 2), FOOTER_HEIGHT,
                Component.literal("卸载玩偶"), button -> lifecycle(fake, MannequinLifecyclePayload.Action.UNLOAD)));
        }
    }

    private void lifecycle(ChunkMapSnapshotPayload.FakePlayerView fake, MannequinLifecyclePayload.Action action) {
        PlatformNetworking.sendToServer(new MannequinLifecyclePayload(fake.name(), action));
    }

    private java.util.List<ChunkMapSnapshotPayload.FakePlayerView> visibleFakes() {
        return snapshot.fakePlayers().stream()
            .filter(fake -> targetTypeFilter == TargetTypeFilter.ALL
                || (targetTypeFilter == TargetTypeFilter.PLAYER && fake.type() == ChunkMapSnapshotPayload.TargetType.PLAYER)
                || (targetTypeFilter == TargetTypeFilter.MANNEQUIN && fake.type() == ChunkMapSnapshotPayload.TargetType.MANNEQUIN))
            .filter(fake -> loadFilter == LoadFilter.REGISTERED || fake.loaded())
            .toList();
    }

    private void cycleTargetType() {
        targetTypeFilter = switch (targetTypeFilter) {
            case ALL -> TargetTypeFilter.PLAYER;
            case PLAYER -> TargetTypeFilter.MANNEQUIN;
            case MANNEQUIN -> TargetTypeFilter.ALL;
        };
        rebuildMainWidgets();
    }

    private void cycleLoadFilter() {
        loadFilter = loadFilter == LoadFilter.REGISTERED ? LoadFilter.LOADED : LoadFilter.REGISTERED;
        rebuildMainWidgets();
    }

    private void refresh() {
        PlatformNetworking.sendToServer(ClientChunkLoadingState.request(ChunkMapOpenTarget.NONE));
    }

    private void toggleSetting(int index) {
        PlatformNetworking.sendToServer(new ToggleGlobalSettingPayload(index));
        for (Button button : settingButtons) if (button != null) button.active = false;
    }

    /** 读取快照里某一项全局设置的开关状态。 */
    private boolean globalSetting(ArchWeaverConfig.GlobalSetting setting) {
        return (snapshot.globalSettingsMask() & (1 << setting.ordinal())) != 0;
    }

    private Component settingLabel(int index) {
        String key = switch (ArchWeaverConfig.GlobalSetting.values()[index]) {
            case RESTORE_FAKE_PLAYERS -> "restore_players";
            case CONTAINER_TRANSFER_BUTTONS -> "container_transfer_buttons";
            case FAKE_PLAYER_ALIAS_FIRST -> "alias_first";
            case FAKE_PLAYER_EMPTY_ALIAS_MARKER -> "empty_alias_marker";
        };
        return Component.translatable("gui.archweaver.fakeplayer.global.setting." + key);
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
        int rightWidth = Math.max(1, left + panelWidth() - rightLeft);
        int panelHeight = panelHeight();
        new TitlePanel(left, top, sideWidth, panelHeight, Component.translatable("gui.archweaver.main.title")).draw(graphics, font);
        new TitlePanel(centerLeft, top, centerWidth, panelHeight, centerTitle()).draw(graphics, font);
        new TitlePanel(rightLeft, top, rightWidth, panelHeight, Component.translatable("gui.archweaver.main.details")).draw(graphics, font);

        int labelY = contentTop() + PANEL_PADDING;
        if (view == View.FAKE_PLAYERS) {
            // 在线人数挪到面板最底部，顶部整行让给生成假人和预设管理。
            graphics.text(font, Component.translatable("gui.archweaver.main.online", visibleFakes().size()),
                centerLeft + PANEL_PADDING, statusY(), 0xFFFFFFFF, false);
            drawFakeDetail(graphics, rightLeft, rightWidth);
        } else if (view == View.MAP) {
            graphics.text(font, Component.translatable("gui.archweaver.main.regions", snapshot.managementRegions().size()),
                centerLeft + PANEL_PADDING, labelY, 0xFFFFFFFF, false);
            drawRegionDetail(graphics, rightLeft, rightWidth);
        } else {
            drawSettingsDetail(graphics, rightLeft, rightWidth);
        }
    }

    private Component centerTitle() {
        return switch (view) {
            case FAKE_PLAYERS -> Component.translatable("gui.archweaver.main.fake_players");
            case MAP -> Component.translatable("gui.archweaver.main.map");
            case SETTINGS -> Component.translatable("gui.archweaver.main.settings");
        };
    }

    private void drawFakeDetail(GuiGraphicsExtractor graphics, int x, int w) {
        int y = contentTop() + PANEL_PADDING;
        ChunkMapSnapshotPayload.FakePlayerView fake = snapshot.fakePlayers().stream()
            .filter(value -> value.id().equals(selectedFake)).findFirst().orElse(null);
        if (fake == null) {
            graphics.centeredText(font, Component.translatable(visibleFakes().isEmpty()
                ? "gui.archweaver.main.no_fake_players" : "gui.archweaver.main.select_fake"), x + w / 2, y + 18, 0xFFFFFFFF);
            return;
        }
        if (!fake.alias().isEmpty()) {
            graphics.text(font, Component.literal(font.plainSubstrByWidth(fake.alias(), Math.max(1, w - PANEL_PADDING * 2))),
                x + PANEL_PADDING, y, 0xFFFFFFFF, false);
            y += 14;
        }
        graphics.text(font, Component.literal(fake.name()), x + PANEL_PADDING, y, 0xFFD0D0D0, false);
        graphics.text(font, Component.translatable("gui.archweaver.main.world", fake.dimension()), x + PANEL_PADDING, y + 18, 0xFFFFFFFF, false);
        graphics.text(font, Component.translatable("gui.archweaver.main.position", fake.x(), fake.y(), fake.z()), x + PANEL_PADDING, y + 36, 0xFFFFFFFF, false);
        if (fake.type() == ChunkMapSnapshotPayload.TargetType.MANNEQUIN) {
            graphics.text(font, Component.literal(fake.loaded() ? "已加载" : "已登记"), x + PANEL_PADDING, y + 54, 0xFFFFFFFF, false);
        } else {
            graphics.text(font, Component.translatable("gui.archweaver.main.loading", fake.loadingActive() ? fake.loadingDistance() : 0), x + PANEL_PADDING, y + 54, 0xFFFFFFFF, false);
            graphics.text(font, Component.translatable("gui.archweaver.main.simulation", fake.simulationDistance()), x + PANEL_PADDING, y + 72, 0xFFFFFFFF, false);
        }
    }

    private void drawRegionDetail(GuiGraphicsExtractor graphics, int x, int w) {
        int y = contentTop() + PANEL_PADDING;
        if (selectedRegion < 0 || selectedRegion >= snapshot.managementRegions().size()) {
            graphics.centeredText(font, Component.translatable("gui.archweaver.main.select_region"), x + w / 2, y + 18, 0xFFFFFFFF);
            return;
        }
        ChunkMapSnapshotPayload.RegionSummary region = snapshot.managementRegions().get(selectedRegion);
        graphics.text(font, Component.literal(region.name()), x + PANEL_PADDING, y, 0xFFFFFFFF, false);
        graphics.text(font, Component.translatable("gui.archweaver.main.world", region.dimension()), x + PANEL_PADDING, y + 18, 0xFFFFFFFF, false);
        graphics.text(font, Component.translatable("gui.archweaver.main.region_position", region.chunkX(), region.chunkZ()), x + PANEL_PADDING, y + 36, 0xFFFFFFFF, false);
        graphics.text(font, Component.translatable("gui.archweaver.main.region_size", region.chunkCount()), x + PANEL_PADDING, y + 54, 0xFFFFFFFF, false);
        graphics.text(font, Component.translatable("gui.archweaver.main.region_status", Component.translatable(region.enabled() ? "gui.archweaver.fakeplayer.global.enabled" : "gui.archweaver.fakeplayer.global.disabled")), x + PANEL_PADDING, y + 72, 0xFFFFFFFF, false);
    }

    private void drawSettingsDetail(GuiGraphicsExtractor graphics, int x, int w) {
        int y = contentTop() + PANEL_PADDING;
        graphics.centeredText(font, Component.translatable("gui.archweaver.main.settings_hint"), x + w / 2, y, 0xFFFFFFFF);
        graphics.text(font, Component.translatable("gui.archweaver.main.settings_count", settingButtons.length), x + PANEL_PADDING, y + 20, 0xFFC6C6C6, false);
    }

    /** 中间内容区可显示的页面，可由命令指定打开时的初始页面。 */
    public enum View {
        FAKE_PLAYERS, // 假人列表页面。
        MAP, // 区块地图页面。
        SETTINGS // 全局设置页面。
    }

    private enum TargetTypeFilter {
        ALL("全部"), PLAYER("玩家"), MANNEQUIN("玩偶");
        private final String label;
        TargetTypeFilter(String label) { this.label = label; }
        String label() { return label; }
    }

    private enum LoadFilter {
        REGISTERED("已登记"), LOADED("已加载");
        private final String label;
        LoadFilter(String label) { this.label = label; }
        String label() { return label; }
    }
}
