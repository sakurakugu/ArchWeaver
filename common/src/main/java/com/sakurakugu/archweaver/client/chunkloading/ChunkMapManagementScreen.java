package com.sakurakugu.archweaver.client.chunkloading;

import com.sakurakugu.archweaver.client.ui.DimensionDisplay;

import com.sakurakugu.archweaver.client.ClientScreenNavigation;
import com.sakurakugu.archweaver.client.ui.PixelGlyph;
import com.sakurakugu.archweaver.client.ui.SolidButton;
import com.sakurakugu.archweaver.client.ui.TitlePanel;
import com.sakurakugu.archweaver.chunkloading.ChunkLoaderSavedData;
import com.sakurakugu.archweaver.network.ChunkLoaderActionPayload;
import com.sakurakugu.archweaver.network.ChunkLoaderActionPayload.Action;
import com.sakurakugu.archweaver.network.ChunkMapSnapshotPayload;
import com.sakurakugu.archweaver.platform.PlatformNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 区块加载区域管理页。所有管理操作和分页状态都属于这个页面。 */
public final class ChunkMapManagementScreen extends Screen {
    private static final int PANEL_WIDTH = 430; // 管理面板的宽度。
    private static final int PANEL_HEIGHT = 242; // 管理面板的高度，只包住翻页按钮。
    private static final int PAGE_SIZE = 6; // 区域列表每页显示的行数。
    private static final int CONTENT_TOP = 40; // 内容区上缘：标题栏分隔线下方留 14px。
    private static final int CONTENT_BOTTOM = 234; // 内容区下缘：距面板底边 8px，与左右内边距一致。
    private static final int ROW_PITCH = 29; // 列表行距：让 6 行均匀铺满撑高后的内容区。
    private ChunkMapSnapshotPayload snapshot; // 最近一次从服务端同步来的状态快照。
    private final ChunkLoadMapFrontend map; // 从地图打开时，选中区域同步高亮到地图。
    private int page; // 区域列表的当前页码，从 0 开始。
    private int selectedIndex = -1; // 当前选中区域在列表中的下标，-1 表示没有选中任何区域。
    private Action confirmation; // 等待用户二次确认的操作，null 表示当前没有待确认的操作。
    private float layoutScale = 1.0F; // 当前面板相对设计尺寸的缩放比例。
    private int layoutWidth = PANEL_WIDTH; // 当前面板实际宽度。
    private int layoutHeight = PANEL_HEIGHT; // 当前面板实际高度。

    public ChunkMapManagementScreen(ChunkMapSnapshotPayload snapshot, ChunkLoadMapFrontend map) {
        super(Component.translatable("gui.archweaver.chunkloader.title"));
        this.snapshot = snapshot;
        this.map = map;
    }

    public void update(ChunkMapSnapshotPayload value) {
        var previous = snapshot;
        snapshot = value;
        if (previous.revision() != value.revision()
            || !value.managementRegions().equals(previous.managementRegions())) {
            selectedIndex = Math.min(selectedIndex, value.managementRegions().size() - 1);
            confirmation = null;
            rebuildWidgets();
        }
    }

    @Override
    protected void init() {
        updateLayout();
        int left = panelLeft();
        int top = panelTop();
        TitlePanel titlePanel = new TitlePanel(left, top, layoutWidth, layoutHeight,
            Component.translatable("gui.archweaver.chunkloader.title"));
        addRenderableWidget(new SolidButton(titlePanel.leftButtonX(), titlePanel.buttonY(size(18)), size(18), size(18),
            PixelGlyph.BACK, Component.translatable("gui.back"), button -> onClose()));
        // 备份/恢复贴着标题栏右缘，并与标题栏垂直居中。
        addRenderableWidget(new SolidButton(left + s(288), titlePanel.buttonY(size(20)), size(64), size(20),
            Component.translatable("gui.archweaver.chunkloader.backup"),
            button -> sendManagementAction(Action.BACKUP, "")));
        addRenderableWidget(new SolidButton(left + s(356), titlePanel.buttonY(size(20)), size(66), size(20),
            Component.translatable(confirmation == Action.RESTORE
                ? "gui.archweaver.chunkloader.confirm_restore" : "gui.archweaver.chunkloader.restore"),
            button -> confirmOrSend(Action.RESTORE, "")));
        int first = page * PAGE_SIZE;
        int end = Math.min(first + PAGE_SIZE, regions().size());
        for (int index = first; index < end; index++) {
            int selected = index;
            var region = regions().get(index);
            Component label = Component.literal((region.enabled() ? "[+] " : "[-] ") + region.name());
            SolidButton row = new SolidButton(left + s(8), top + s(CONTENT_TOP + (index - first) * ROW_PITCH),
                size(145), size(22), label, button -> selectRegion(selected));
            if (index == selectedIndex) row.setTextColor(0xFFF6DC70);
            addRenderableWidget(row);
        }
        addManagementPageButtons(left, top);
        addSelectedRegionControls(left, top);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (!ClientScreenNavigation.extractBackground(this, graphics, partialTick)) {
            graphics.fill(0, 0, width, height, 0xFF22282C);
        }
        updateLayout();
        int left = panelLeft();
        int top = panelTop();
        new TitlePanel(left, top, layoutWidth, layoutHeight,
            Component.translatable("gui.archweaver.chunkloader.title")).draw(graphics, font);
        // 详情区跟着列表一起上移，下缘停在最后一行上方 13px 处。
        graphics.fill(left + s(166), top + s(CONTENT_TOP), left + s(422), top + s(194), 0x802C3033);
        graphics.centeredText(font, Component.translatable("gui.archweaver.chunkloader.page",
            page + 1, managementPageCount()), left + s(80), top + s(CONTENT_BOTTOM - 15), 0xFFC6C6C6);
        var selected = selectedRegion();
        if (selected == null) {
            graphics.centeredText(font, Component.translatable(regions().isEmpty()
                ? "gui.archweaver.chunkloader.empty" : "gui.archweaver.chunkloader.select"),
                left + s(294), top + s(104), 0xFFAAAAAA);
        } else {
            graphics.text(font, Component.literal(selected.name()), left + s(176), top + s(52), 0xFFFFFFFF, false);
            graphics.text(font, DimensionDisplay.name(selected.dimension()), left + s(176), top + s(72), 0xFFC6C6C6, false);
            graphics.text(font, Component.translatable("gui.archweaver.chunkloader.position",
                selected.chunkX() << 4, 0, selected.chunkZ() << 4), left + s(176), top + s(92), 0xFFCCCCCC, false);
            graphics.text(font, Component.translatable("gui.archweaver.chunkloader.chunks", selected.chunkCount()),
                left + s(176), top + s(112), 0xFFCCCCCC, false);
        }
    }

    private void addSelectedRegionControls(int left, int top) {
        var selected = selectedRegion();
        if (selected == null) return;
        EditBox name = addRenderableWidget(new EditBox(font, left + s(172), top + s(140), size(141), size(20),
            Component.translatable("gui.archweaver.chunkloader.name")));
        name.setMaxLength(ChunkLoaderSavedData.MAX_NAME_LENGTH);
        name.setValue(selected.name());
        name.setFilter(value -> value.codePoints().allMatch(ChunkMapManagementScreen::isNameCharacter));
        addRenderableWidget(new SolidButton(left + s(317), top + s(140), size(95), size(20),
            Component.translatable("gui.archweaver.chunkloader.rename"), button ->
                sendRename(selected.name(), name.getValue())));
        addRenderableWidget(new SolidButton(left + s(172), top + s(168), size(121), size(20),
            Component.translatable(selected.enabled() ? "gui.archweaver.chunkloader.disable"
                : "gui.archweaver.chunkloader.enable"), button ->
            sendManagementAction(selected.enabled() ? Action.DISABLE : Action.ENABLE, selected.name())));
        addRenderableWidget(new SolidButton(left + s(297), top + s(168), size(115), size(20),
            Component.translatable(confirmation == Action.REMOVE
                ? "gui.archweaver.chunkloader.confirm_remove" : "gui.archweaver.chunkloader.remove"),
            button -> confirmOrSend(Action.REMOVE, selected.name())));
        if (map != null) {
            Button view = new SolidButton(left + s(172), top + s(202), size(240), size(22),
                Component.translatable("gui.archweaver.chunkloader.view_on_map"), button -> {
                    if (map.highlightRegion(selected.name(), selected.dimension())) {
                        map.focusHighlightedRegion();
                        if (!map.journeyMap()) onClose();
                    }
                });
            view.active = map.supportsDimension(selected.dimension());
            addRenderableWidget(view);
        }
    }

    private void addManagementPageButtons(int left, int top) {
        // 翻页按钮压在内容区下缘，离面板底边只剩 8px；正方形按钮贴住列表左右边缘，页码居中。
        int y = top + s(CONTENT_BOTTOM - 20);
        Button previous = new SolidButton(left + s(8), y, size(20), size(20),
            PixelGlyph.ARROW_LEFT, Component.translatable("gui.archweaver.page.previous"),
            button -> changeManagementPage(-1));
        previous.active = page > 0;
        addRenderableWidget(previous);
        Button next = new SolidButton(left + s(133), y, size(20), size(20),
            PixelGlyph.ARROW_RIGHT, Component.translatable("gui.archweaver.page.next"),
            button -> changeManagementPage(1));
        next.active = page + 1 < managementPageCount();
        addRenderableWidget(next);
    }

    private void selectRegion(int index) {
        selectedIndex = selectedIndex == index ? -1 : index;
        if (map != null) {
            var region = selectedRegion();
            if (region == null) map.clearHighlightedRegion();
            else map.highlightRegion(region.name(), region.dimension());
        }
        confirmation = null;
        rebuildWidgets();
    }

    private void changeManagementPage(int offset) {
        page = Math.max(0, Math.min(page + offset, managementPageCount() - 1));
        selectedIndex = -1;
        if (map != null) map.clearHighlightedRegion();
        confirmation = null;
        rebuildWidgets();
    }

    private void confirmOrSend(Action action, String name) {
        if (confirmation != action) {
            confirmation = action;
            rebuildWidgets();
            return;
        }
        sendManagementAction(action, name);
    }

    private void sendManagementAction(Action action, String name) {
        PlatformNetworking.sendToServer(new ChunkLoaderActionPayload(action, name, "", snapshot.dimension()));
    }

    /** 改名只提交新名称，形状、维度和启停状态由服务端按原区域保留。 */
    private void sendRename(String name, String newName) {
        PlatformNetworking.sendToServer(new ChunkLoaderActionPayload(Action.RENAME, name, newName, snapshot.dimension()));
    }

    /** 根据当前逻辑屏幕计算面板尺寸，保证设计稿和所有控件使用同一个缩放比例。 */
    private void updateLayout() {
        float availableWidth = Math.max(1.0F, width - 12.0F);
        float availableHeight = Math.max(1.0F, height - 12.0F);
        layoutScale = Math.min(1.0F, Math.min(
            availableWidth / PANEL_WIDTH, availableHeight / PANEL_HEIGHT));
        layoutWidth = Math.max(1, Math.round(PANEL_WIDTH * layoutScale));
        layoutHeight = Math.max(1, Math.round(PANEL_HEIGHT * layoutScale));
    }

    private int panelLeft() {
        return (width - layoutWidth) / 2;
    }

    private int panelTop() {
        return (height - layoutHeight) / 2;
    }

    private int s(int value) {
        return Math.round(value * layoutScale);
    }

    private int size(int value) {
        return Math.max(1, s(value));
    }

    /** 与区域名称的服务端校验保持一致，输入时直接挡掉不可能通过校验的字符。 */
    private static boolean isNameCharacter(int codePoint) {
        return codePoint == '_' || codePoint == '-' || Character.isLetterOrDigit(codePoint);
    }

    private int managementPageCount() {
        return Math.max(1, (regions().size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private java.util.List<ChunkMapSnapshotPayload.RegionSummary> regions() {
        return snapshot.managementRegions();
    }

    private ChunkMapSnapshotPayload.RegionSummary selectedRegion() {
        return selectedIndex >= 0 && selectedIndex < regions().size() ? regions().get(selectedIndex) : null;
    }

    @Override
    public void onClose() {
        ClientScreenNavigation.back(this);
    }
}
