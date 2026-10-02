package com.sakurakugu.archweaver.client.chunkloading;

import com.sakurakugu.archweaver.client.ClientScreenNavigation;
import com.sakurakugu.archweaver.client.ui.PixelGlyph;
import com.sakurakugu.archweaver.client.ui.SolidButton;
import com.sakurakugu.archweaver.client.ui.TitlePanel;
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
    private static final int PANEL_HEIGHT = 286; // 管理面板的高度。
    private static final int PAGE_SIZE = 6; // 区域列表每页显示的行数。
    private ChunkMapSnapshotPayload snapshot; // 最近一次从服务端同步来的状态快照。
    private int page; // 区域列表的当前页码，从 0 开始。
    private int selectedIndex = -1; // 当前选中区域在列表中的下标，-1 表示没有选中任何区域。
    private Action confirmation; // 等待用户二次确认的操作，null 表示当前没有待确认的操作。

    public ChunkMapManagementScreen(ChunkMapSnapshotPayload snapshot) {
        super(Component.translatable("gui.fakeplayer.chunkloader.title"));
        this.snapshot = snapshot;
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
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        TitlePanel titlePanel = new TitlePanel(left, top, PANEL_WIDTH, PANEL_HEIGHT,
            Component.translatable("gui.fakeplayer.chunkloader.title"));
        addRenderableWidget(new SolidButton(titlePanel.leftButtonX(), titlePanel.buttonY(18), 18, 18,
            PixelGlyph.BACK, Component.translatable("gui.back"), button -> onClose()));
        addRenderableWidget(new SolidButton(left + 280, top + 9, 64, 20,
            Component.translatable("gui.fakeplayer.chunkloader.backup"),
            button -> sendManagementAction(Action.BACKUP, "", 0)));
        addRenderableWidget(new SolidButton(left + 348, top + 9, 66, 20,
            Component.translatable(confirmation == Action.RESTORE
                ? "gui.fakeplayer.chunkloader.confirm_restore" : "gui.fakeplayer.chunkloader.restore"),
            button -> confirmOrSend(Action.RESTORE, "")));
        int first = page * PAGE_SIZE;
        int end = Math.min(first + PAGE_SIZE, regions().size());
        for (int index = first; index < end; index++) {
            int selected = index;
            var region = regions().get(index);
            Component label = Component.literal((region.enabled() ? "[+] " : "[-] ") + region.name());
            addRenderableWidget(new SolidButton(left + 16, top + 48 + (index - first) * 27, 145, 22,
                label, button -> selectRegion(selected)));
        }
        addManagementPageButtons(left, top);
        addSelectedRegionControls(left, top);
        addCreateRegionControls(left, top);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (!ClientScreenNavigation.extractBackground(this, graphics, partialTick)) {
            graphics.fill(0, 0, width, height, 0xFF22282C);
        }
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        new TitlePanel(left, top, PANEL_WIDTH, PANEL_HEIGHT,
            Component.translatable("gui.fakeplayer.chunkloader.title")).draw(graphics, font);
        graphics.fill(left + 174, top + 48, left + 414, top + 192, 0x802C3033);
        graphics.fill(left, top + 240, left + PANEL_WIDTH, top + 242, 0xFF565656);
        graphics.centeredText(font, Component.translatable("gui.fakeplayer.chunkloader.page",
            page + 1, managementPageCount()), left + 88, top + 219, 0xFFC6C6C6);
        var selected = selectedRegion();
        if (selected == null) {
            graphics.centeredText(font, Component.translatable(regions().isEmpty()
                ? "gui.fakeplayer.chunkloader.empty" : "gui.fakeplayer.chunkloader.select"),
                left + 294, top + 107, 0xFFAAAAAA);
        } else {
            graphics.text(font, Component.literal(selected.name()), left + 184, top + 58, 0xFFFFFFFF, false);
            graphics.text(font, Component.literal(selected.dimension()), left + 184, top + 76, 0xFFC6C6C6, false);
            graphics.text(font, Component.translatable("gui.fakeplayer.chunkloader.position",
                selected.chunkX() << 4, 0, selected.chunkZ() << 4), left + 184, top + 94, 0xFFCCCCCC, false);
            graphics.text(font, Component.translatable("gui.fakeplayer.chunkloader.chunks", selected.chunkCount()),
                left + 184, top + 112, 0xFFCCCCCC, false);
        }
        graphics.text(font, Component.translatable("gui.fakeplayer.chunkloader.create_here"),
            left + 16, top + 243, 0xFFC6C6C6, false);
    }

    private void addSelectedRegionControls(int left, int top) {
        var selected = selectedRegion();
        if (selected == null) return;
        EditBox radius = addRenderableWidget(new EditBox(font, left + 180, top + 138, 52, 20,
            Component.translatable("gui.fakeplayer.chunkloader.radius")));
        radius.setMaxLength(2);
        radius.setValue(Integer.toString(selected.radius()));
        radius.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        addRenderableWidget(new SolidButton(left + 236, top + 138, 168, 20,
            Component.translatable("gui.fakeplayer.chunkloader.apply"), button ->
                sendManagementAction(Action.CONFIGURE, selected.name(), parseRadius(radius))));
        addRenderableWidget(new SolidButton(left + 180, top + 166, 105, 20,
            Component.translatable(selected.enabled() ? "gui.fakeplayer.chunkloader.disable"
                : "gui.fakeplayer.chunkloader.enable"), button ->
            sendManagementAction(selected.enabled() ? Action.DISABLE : Action.ENABLE, selected.name(), 0)));
        addRenderableWidget(new SolidButton(left + 289, top + 166, 115, 20,
            Component.translatable(confirmation == Action.REMOVE
                ? "gui.fakeplayer.chunkloader.confirm_remove" : "gui.fakeplayer.chunkloader.remove"),
            button -> confirmOrSend(Action.REMOVE, selected.name())));
    }

    private void addCreateRegionControls(int left, int top) {
        EditBox name = addRenderableWidget(new EditBox(font, left + 16, top + 251, 125, 20,
            Component.translatable("gui.fakeplayer.chunkloader.name")));
        name.setMaxLength(32);
        name.setHint(Component.translatable("gui.fakeplayer.chunkloader.name"));
        EditBox radius = addRenderableWidget(new EditBox(font, left + 145, top + 251, 48, 20,
            Component.translatable("gui.fakeplayer.chunkloader.radius")));
        radius.setMaxLength(2);
        radius.setValue("0");
        radius.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        addRenderableWidget(new SolidButton(left + 197, top + 251, 217, 20,
            Component.translatable("gui.fakeplayer.chunkloader.add"), button ->
                sendManagementAction(Action.ADD, name.getValue(), parseRadius(radius))));
    }

    private void addManagementPageButtons(int left, int top) {
        Button previous = new SolidButton(left + 16, top + 214, 32, 20, Component.literal("<"),
            button -> changeManagementPage(-1));
        previous.active = page > 0;
        addRenderableWidget(previous);
        Button next = new SolidButton(left + 129, top + 214, 32, 20, Component.literal(">"),
            button -> changeManagementPage(1));
        next.active = page + 1 < managementPageCount();
        addRenderableWidget(next);
    }

    private void selectRegion(int index) {
        selectedIndex = index;
        confirmation = null;
        rebuildWidgets();
    }

    private void changeManagementPage(int offset) {
        page = Math.max(0, Math.min(page + offset, managementPageCount() - 1));
        selectedIndex = -1;
        confirmation = null;
        rebuildWidgets();
    }

    private void confirmOrSend(Action action, String name) {
        if (confirmation != action) {
            confirmation = action;
            rebuildWidgets();
            return;
        }
        sendManagementAction(action, name, 0);
    }

    private void sendManagementAction(Action action, String name, int radius) {
        PlatformNetworking.sendToServer(new ChunkLoaderActionPayload(action, name, radius));
    }

    private int parseRadius(EditBox box) {
        try {
            return Math.min(Integer.parseInt(box.getValue()), snapshot.maximumRadius());
        } catch (NumberFormatException exception) {
            return 0;
        }
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
