package com.sakurakugu.archweaver.client;

import com.sakurakugu.archweaver.menu.PresetManagementMenu;
import com.sakurakugu.archweaver.menu.PresetManagementMenu.GroupSummary;
import com.sakurakugu.archweaver.menu.PresetManagementMenu.PresetSummary;
import com.sakurakugu.archweaver.network.PresetActionPayload;
import com.sakurakugu.archweaver.client.ui.SolidButton;
import com.sakurakugu.archweaver.client.ui.SolidDropdownButton;
import com.sakurakugu.archweaver.client.ui.PixelGlyph;
import com.sakurakugu.archweaver.client.ui.TitlePanel;
import com.sakurakugu.archweaver.platform.PlatformNetworking;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** 管理假人预设及分组。 */
public final class PresetManagementScreen extends ResponsiveContainerScreen<PresetManagementMenu> {
    private static final int PANEL_WIDTH = 380; // 页面主面板宽度，单位为像素。
    private static final int PANEL_HEIGHT = 270; // 页面主面板高度，单位为像素。
    private static final int PAGE_SIZE = 6; // 每页显示的预设或分组条目数。
    private static final int ROW_HEIGHT = 22; // 列表中单行按钮的高度，单位为像素。
    private static final int CONTENT_TOP = 34; // 内容区上缘：标签页按钮紧跟标题栏分隔线下方 8px。
    private static final int CONTENT_BOTTOM = 262; // 内容区下缘：距面板底边 8px，与左右内边距一致。
    private static final int LIST_TOP = 60; // 列表首行纵坐标，在标签页按钮下方留 6px。
    private static final int ROW_PITCH = 26; // 列表行距：6 行正好铺满标签页和翻页按钮之间的高度。
    private static final int DETAIL_TOP = 58; // 详情区上缘，比列表首行高 2px。
    private static final int DETAIL_BOTTOM = 199; // 详情区下缘，比列表末行高 13px。
    private static final int DETAIL_ACTION_Y = 171; // 详情区操作按钮纵坐标，距详情区底边 6px。
    private static final int PAGER_Y = 217; // 翻页按钮纵坐标，与列表末行、底部表单各隔 5px。

    private boolean showingGroups; // 当前是否位于分组标签页，false 表示预设标签页。
    private int page; // 当前页码，从 0 开始。
    private int selectedIndex = -1; // 选中条目在列表中的下标，-1 表示未选中。
    private String onlinePlayer; // 保存预设时选中的在线玩家，null 表示没有在线玩家。
    private String addPreset; // 向分组添加成员时选中的预设，null 表示没有预设。
    private String removePreset; // 从分组移除成员时选中的成员，null 表示分组为空。
    private PresetActionPayload.Action confirmation; // 等待二次确认的破坏性操作，null 表示没有待确认操作。
    private String confirmationTarget; // 待确认操作的目标 id，切换目标时确认状态失效。
    /** 当前页面上展开时会盖住其它控件的下拉框，需要在最后阶段绘制选项列表。 */
    private final List<SolidDropdownButton<?>> dropdowns = new ArrayList<>();

    public PresetManagementScreen(PresetManagementMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PANEL_WIDTH, PANEL_HEIGHT);
        showingGroups = menu.openGroupsInitially();
    }

    @Override
    protected void init() {
        super.init();
        rebuildControls();
    }

    private void rebuildControls() {
        clearWidgets();
        dropdowns.clear();
        addRenderableWidget(new SolidButton(leftPos + s(8), topPos + s(CONTENT_TOP), size(100), size(20),
            Component.translatable("gui.archweaver.preset.presets"), button -> setTab(false)));
        addRenderableWidget(new SolidButton(leftPos + s(112), topPos + s(CONTENT_TOP), size(100), size(20),
            Component.translatable("gui.archweaver.preset.groups"), button -> setTab(true)));
        TitlePanel titlePanel = titlePanel();
        addRenderableWidget(new SolidButton(titlePanel.leftButtonX(), titlePanel.buttonY(18), 18, 18, PixelGlyph.BACK,
            Component.translatable("gui.archweaver.preset.back"), button ->
                ClientScreenNavigation.back(this)));
        if (showingGroups) {
            addGroupWidgets();
        } else {
            addPresetWidgets();
        }
        addPageButtons();
    }

    private void addPresetWidgets() {
        int first = page * PAGE_SIZE;
        int end = Math.min(first + PAGE_SIZE, menu.presets().size());
        for (int index = first; index < end; index++) {
            int selected = index;
            PresetSummary preset = menu.presets().get(index);
            addRenderableWidget(new SolidButton(leftPos + s(8), topPos + s(LIST_TOP + (index - first) * ROW_PITCH),
                size(132), size(ROW_HEIGHT), Component.literal(preset.id()), button -> select(selected)));
        }
        PresetSummary selected = selectedPreset();
        if (selected != null) {
            addRenderableWidget(new SolidButton(leftPos + s(156), topPos + s(DETAIL_ACTION_Y), size(100), size(22),
                Component.translatable("gui.archweaver.preset.load"), button ->
                    send(PresetActionPayload.Action.LOAD_PRESET, selected.id(), "", "")));
            addRenderableWidget(new SolidButton(leftPos + s(262), topPos + s(DETAIL_ACTION_Y), size(100), size(22),
                Component.translatable(isConfirming(PresetActionPayload.Action.REMOVE_PRESET, selected.id())
                    ? "gui.archweaver.preset.confirm_delete" : "gui.archweaver.preset.delete"),
                button -> confirmOrSend(PresetActionPayload.Action.REMOVE_PRESET, selected.id(),
                    selected.id(), "", "")));
        }

        // 底部表单压在内容区下缘，距面板底边只剩 8px。
        EditBox id = addRenderableWidget(new EditBox(font, leftPos + s(8), topPos + s(CONTENT_BOTTOM - 20),
            size(78), size(20), Component.translatable("gui.archweaver.preset.preset_id")));
        id.setMaxLength(64);
        id.setHint(Component.translatable("gui.archweaver.preset.preset_id"));
        EditBox description = addRenderableWidget(new EditBox(font, leftPos + s(194), topPos + s(CONTENT_BOTTOM - 20),
            size(121), size(20), Component.translatable("gui.archweaver.preset.description")));
        description.setMaxLength(256);
        description.setHint(Component.translatable("gui.archweaver.preset.description"));
        addOnlinePlayerDropdown(leftPos + s(90), topPos + s(CONTENT_BOTTOM - 20), size(100), size(20));
        Button save = new SolidButton(leftPos + s(319), topPos + s(CONTENT_BOTTOM - 20), size(53), size(20),
            Component.translatable("gui.archweaver.preset.save"), button -> {
            if (onlinePlayer != null) {
                send(PresetActionPayload.Action.SAVE_PRESET, id.getValue(), onlinePlayer, description.getValue());
            }
        });
        save.active = onlinePlayer != null;
        addRenderableWidget(save);
    }

    private void addGroupWidgets() {
        int first = page * PAGE_SIZE;
        int end = Math.min(first + PAGE_SIZE, menu.groups().size());
        for (int index = first; index < end; index++) {
            int selected = index;
            GroupSummary group = menu.groups().get(index);
            Component label = Component.translatable("gui.archweaver.preset.group_entry",
                group.id(), group.presetIds().size());
            addRenderableWidget(new SolidButton(leftPos + s(8), topPos + s(LIST_TOP + (index - first) * ROW_PITCH),
                size(132), size(ROW_HEIGHT), label, button -> select(selected)));
        }
        GroupSummary selected = selectedGroup();
        if (selected != null) {
            addRenderableWidget(new SolidButton(leftPos + s(150), topPos + s(DETAIL_ACTION_Y), size(61), size(22),
                Component.translatable("gui.archweaver.preset.load"), button ->
                    send(PresetActionPayload.Action.LOAD_GROUP, selected.id(), "", "")));
            addRenderableWidget(new SolidButton(leftPos + s(215), topPos + s(DETAIL_ACTION_Y), size(61), size(22),
                Component.translatable("gui.archweaver.preset.unload"), button ->
                    send(PresetActionPayload.Action.UNLOAD_GROUP, selected.id(), "", "")));
            addRenderableWidget(new SolidButton(leftPos + s(280), topPos + s(DETAIL_ACTION_Y), size(92), size(22),
                Component.translatable(isConfirming(PresetActionPayload.Action.REMOVE_GROUP, selected.id())
                    ? "gui.archweaver.preset.confirm_delete" : "gui.archweaver.preset.delete"),
                button -> confirmOrSend(PresetActionPayload.Action.REMOVE_GROUP, selected.id(),
                    selected.id(), "", "")));

            addMemberDropdown(selected, leftPos + s(150), topPos + s(PAGER_Y), size(156), size(20));
            Button remove = new SolidButton(leftPos + s(310), topPos + s(PAGER_Y), size(62), size(20),
                Component.translatable("gui.archweaver.preset.remove_member"), button -> {
                if (removePreset != null) {
                    send(PresetActionPayload.Action.REMOVE_FROM_GROUP, selected.id(), removePreset, "");
                }
            });
            remove.active = removePreset != null;
            addRenderableWidget(remove);
        }

        // 两个标签页的底部表单纯平，切换标签页时不会上下跳。
        EditBox groupId = addRenderableWidget(new EditBox(font, leftPos + s(8), topPos + s(CONTENT_BOTTOM - 20),
            size(132), size(20), Component.translatable("gui.archweaver.preset.group_id")));
        groupId.setMaxLength(64);
        groupId.setHint(Component.translatable("gui.archweaver.preset.group_id"));
        addRenderableWidget(new SolidButton(leftPos + s(144), topPos + s(CONTENT_BOTTOM - 20), size(72), size(20),
            Component.translatable("gui.archweaver.preset.create"), button ->
                send(PresetActionPayload.Action.CREATE_GROUP, groupId.getValue(), "", "")));

        addPresetDropdown(leftPos + s(220), topPos + s(CONTENT_BOTTOM - 20), size(101), size(20));
        Button add = new SolidButton(leftPos + s(325), topPos + s(CONTENT_BOTTOM - 20), size(47), size(20),
            Component.translatable("gui.archweaver.preset.add"), button -> {
            GroupSummary group = selectedGroup();
            if (group != null && addPreset != null) {
                send(PresetActionPayload.Action.ADD_TO_GROUP, group.id(), addPreset, "");
            }
        });
        add.active = selected != null && addPreset != null;
        addRenderableWidget(add);
    }

    /** 在线玩家下拉框；没有在线玩家时退化成禁用的占位按钮。 */
    private void addOnlinePlayerDropdown(int x, int y, int width, int height) {
        List<String> options = menu.onlinePlayers();
        onlinePlayer = options.isEmpty() ? null : pick(options, onlinePlayer);
        addOptionSelector(x, y, width, height, options, onlinePlayer,
            "gui.archweaver.preset.no_online", value -> onlinePlayer = value);
    }

    /** 供加入分组使用的预设下拉框；没有预设时退化成禁用的占位按钮。 */
    private void addPresetDropdown(int x, int y, int width, int height) {
        List<String> options = menu.presets().stream().map(PresetSummary::id).toList();
        addPreset = options.isEmpty() ? null : pick(options, addPreset);
        addOptionSelector(x, y, width, height, options, addPreset,
            "gui.archweaver.preset.no_presets", value -> addPreset = value);
    }

    /** 分组内成员下拉框；分组为空时退化成禁用的占位按钮。 */
    private void addMemberDropdown(GroupSummary group, int x, int y, int width, int height) {
        List<String> options = group.presetIds();
        removePreset = options.isEmpty() ? null : pick(options, removePreset);
        addOptionSelector(x, y, width, height, options, removePreset,
            "gui.archweaver.preset.no_members", value -> removePreset = value);
    }

    /**
     * 有选项时放置一个下拉选择器，否则放置禁用的占位按钮。
     * 下拉框会被记录到 {@link #dropdowns}，由页面在绘制末尾展开选项列表。
     */
    private void addOptionSelector(int x, int y, int width, int height, List<String> options, String selected,
                                   String emptyKey, Consumer<String> onSelected) {
        if (options.isEmpty()) {
            Button placeholder = addRenderableWidget(new SolidButton(x, y, width, height,
                Component.translatable(emptyKey), button -> { }));
            placeholder.active = false;
            return;
        }
        // 这些控件都贴近面板底部，选项列表一律向上展开，避免超出面板和屏幕。
        dropdowns.add(addRenderableWidget(new SolidDropdownButton<>(x, y, width, height, options,
            selected, Component::literal, onSelected).setOpensUpward()));
    }

    /**
     * 选项列表变化后尽量保留原选择，否则回落到第一项。
     * 这里必须先判空：菜单里的列表是 {@code List.copyOf} 生成的不可变列表，
     * 而 {@code ImmutableCollections.contains(null)} 会抛 NPE，不像 ArrayList 那样返回 false。
     */
    private static String pick(List<String> options, String previous) {
        return previous != null && options.contains(previous) ? previous : options.get(0);
    }

    private void addPageButtons() {
        // 正方形按钮贴住列表左右边缘，页码居中。
        int y = topPos + s(PAGER_Y);
        Button previous = new SolidButton(leftPos + s(8), y, size(20), size(20),
            PixelGlyph.ARROW_LEFT, Component.translatable("gui.archweaver.page.previous"), button -> changePage(-1));
        previous.active = page > 0;
        addRenderableWidget(previous);
        Button next = new SolidButton(leftPos + s(120), y, size(20), size(20),
            PixelGlyph.ARROW_RIGHT, Component.translatable("gui.archweaver.page.next"), button -> changePage(1));
        next.active = page + 1 < pageCount();
        addRenderableWidget(next);
    }

    private void setTab(boolean groups) {
        if (showingGroups == groups) {
            return;
        }
        showingGroups = groups;
        page = 0;
        selectedIndex = -1;
        clearConfirmation();
        rebuildControls();
    }

    private void select(int index) {
        selectedIndex = index;
        clearConfirmation();
        rebuildControls();
    }

    private void changePage(int offset) {
        page = Math.max(0, Math.min(page + offset, pageCount() - 1));
        selectedIndex = -1;
        clearConfirmation();
        rebuildControls();
    }

    /** 破坏性操作需要点两次：第一次切换为确认文案，第二次才真正提交。 */
    private void confirmOrSend(PresetActionPayload.Action action, String target,
                               String first, String second, String third) {
        if (!isConfirming(action, target)) {
            confirmation = action;
            confirmationTarget = target;
            rebuildControls();
            return;
        }
        clearConfirmation();
        send(action, first, second, third);
    }

    private boolean isConfirming(PresetActionPayload.Action action, String target) {
        return confirmation == action && target != null && target.equals(confirmationTarget);
    }

    private void clearConfirmation() {
        confirmation = null;
        confirmationTarget = null;
    }

    private int pageCount() {
        int size = showingGroups ? menu.groups().size() : menu.presets().size();
        return Math.max(1, (size + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private PresetSummary selectedPreset() {
        return selectedIndex >= 0 && selectedIndex < menu.presets().size()
            ? menu.presets().get(selectedIndex) : null;
    }

    private GroupSummary selectedGroup() {
        return selectedIndex >= 0 && selectedIndex < menu.groups().size()
            ? menu.groups().get(selectedIndex) : null;
    }

    private void send(PresetActionPayload.Action action, String first, String second, String third) {
        PlatformNetworking.sendToServer(new PresetActionPayload(menu.containerId, action, first, second, third));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // 展开的选项列表画在控件之外，先于父类处理，避免点击被下面的控件抢走。
        for (SolidDropdownButton<?> dropdown : dropdowns) {
            if (dropdown.popupMouseClicked(event)) {
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void onClose() {
        ClientScreenNavigation.back(this);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (!ClientScreenNavigation.extractBackground(this, graphics, partialTick)) {
            graphics.fill(0, 0, width, height, 0xFF22282C);
        }
        new TitlePanel(leftPos, topPos, responsiveWidth(), responsiveHeight(), title).draw(graphics, font);
        graphics.fill(leftPos + s(148), topPos + s(DETAIL_TOP), leftPos + s(372), topPos + s(DETAIL_BOTTOM), 0x802C3033);
    }

    private TitlePanel titlePanel() {
        return new TitlePanel(leftPos, topPos, responsiveWidth(), responsiveHeight(), title);
    }

    /**
     * 本方法在父类 translate(leftPos, topPos) 之后调用，因此这里的坐标相对面板左上角，
     * 不能再叠加 leftPos/topPos。
     */
    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.centeredText(font, Component.translatable("gui.archweaver.preset.page", page + 1, pageCount()),
            s(74), s(PAGER_Y + 5), 0xFFC6C6C6);
        if (showingGroups) {
            drawGroupDetails(graphics);
        } else {
            drawPresetDetails(graphics);
        }
        // 选项列表必须最后绘制，否则会被早绘制的控件盖住。
        for (SolidDropdownButton<?> dropdown : dropdowns) {
            dropdown.extractPopup(graphics, mouseX, mouseY, leftPos, topPos);
        }
    }

    private void drawPresetDetails(GuiGraphicsExtractor graphics) {
        PresetSummary preset = selectedPreset();
        if (preset == null) {
            graphics.centeredText(font, Component.translatable(menu.presets().isEmpty()
                ? "gui.archweaver.preset.no_presets" : "gui.archweaver.preset.select_preset"),
                s(260), s(124), 0xFFAAAAAA);
            return;
        }
        graphics.text(font, Component.literal(preset.id()), s(158), s(72), 0xFFFFFFFF, false);
        graphics.text(font, Component.translatable("gui.archweaver.preset.player", preset.playerName()),
            s(158), s(94), 0xFFC6C6C6, false);
        String description = preset.description().isBlank()
            ? Component.translatable("gui.archweaver.preset.no_description").getString()
            : preset.description();
        graphics.text(font, Component.literal(shorten(description, 31)), s(158), s(116), 0xFFCCCCCC, false);
        if (description.length() > 31) {
            graphics.text(font, Component.literal(shorten(description.substring(31), 31)),
                s(158), s(138), 0xFFCCCCCC, false);
        }
    }

    private void drawGroupDetails(GuiGraphicsExtractor graphics) {
        GroupSummary group = selectedGroup();
        if (group == null) {
            graphics.centeredText(font, Component.translatable(menu.groups().isEmpty()
                ? "gui.archweaver.preset.no_groups" : "gui.archweaver.preset.select_group"),
                s(260), s(124), 0xFFAAAAAA);
            return;
        }
        graphics.text(font, Component.literal(group.id()), s(158), s(72), 0xFFFFFFFF, false);
        graphics.text(font, Component.translatable("gui.archweaver.preset.members"),
            s(158), s(94), 0xFFC6C6C6, false);
        String members = group.presetIds().isEmpty()
            ? Component.translatable("commands.archweaver.fakeplayer.none").getString()
            : String.join(", ", group.presetIds());
        graphics.text(font, Component.literal(shorten(members, 31)), s(158), s(116), 0xFFCCCCCC, false);
        if (members.length() > 31) {
            graphics.text(font, Component.literal(shorten(members.substring(31), 31)),
                s(158), s(138), 0xFFCCCCCC, false);
        }
    }

    private static String shorten(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, Math.max(0, maximum - 1)) + "…";
    }
}
