package com.sakurakugu.archweaver.client;

import com.sakurakugu.archweaver.menu.PresetManagementMenu;
import com.sakurakugu.archweaver.menu.PresetManagementMenu.GroupSummary;
import com.sakurakugu.archweaver.menu.PresetManagementMenu.PresetSummary;
import com.sakurakugu.archweaver.network.PresetActionPayload;
import com.sakurakugu.archweaver.client.ui.SolidButton;
import com.sakurakugu.archweaver.client.ui.PixelGlyph;
import com.sakurakugu.archweaver.client.ui.TitlePanel;
import com.sakurakugu.archweaver.platform.PlatformNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** 管理假人预设及分组。 */
public final class PresetManagementScreen extends ResponsiveContainerScreen<PresetManagementMenu> {
    private static final int PANEL_WIDTH = 380; // 页面主面板宽度，单位为像素。
    private static final int PANEL_HEIGHT = 270; // 页面主面板高度，单位为像素。
    private static final int PAGE_SIZE = 5; // 每页显示的预设或分组条目数。
    private static final int ROW_HEIGHT = 22; // 列表中单行按钮的高度，单位为像素。

    private boolean showingGroups; // 当前是否位于分组标签页，false 表示预设标签页。
    private int page; // 当前页码，从 0 开始。
    private int selectedIndex = -1; // 选中条目在列表中的下标，-1 表示未选中。
    private int onlinePlayerIndex; // 保存预设时选中的在线玩家下标，越界时取模回绕。
    private int addPresetIndex; // 向分组添加成员时选中的预设下标，越界时取模回绕。
    private int removePresetIndex; // 从分组移除成员时选中的成员下标，越界时取模回绕。

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
        addRenderableWidget(new SolidButton(leftPos + s(16), topPos + s(43), size(100), size(20),
            Component.translatable("gui.fakeplayer.preset.presets"), button -> setTab(false)));
        addRenderableWidget(new SolidButton(leftPos + s(120), topPos + s(43), size(100), size(20),
            Component.translatable("gui.fakeplayer.preset.groups"), button -> setTab(true)));
        TitlePanel titlePanel = titlePanel();
        addRenderableWidget(new SolidButton(titlePanel.leftButtonX(), titlePanel.buttonY(18), 18, 18, PixelGlyph.BACK,
            Component.translatable("gui.fakeplayer.preset.back"), button ->
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
            addRenderableWidget(new SolidButton(leftPos + s(16), topPos + s(72 + (index - first) * 25),
                size(132), size(ROW_HEIGHT), Component.literal(preset.id()), button -> select(selected)));
        }
        PresetSummary selected = selectedPreset();
        if (selected != null) {
            addRenderableWidget(new SolidButton(leftPos + s(164), topPos + s(153), size(88), size(22),
                Component.translatable("gui.fakeplayer.preset.load"), button ->
                    send(PresetActionPayload.Action.LOAD_PRESET, selected.id(), "", "")));
            addRenderableWidget(new SolidButton(leftPos + s(258), topPos + s(153), size(88), size(22),
                Component.translatable("gui.fakeplayer.preset.delete"), button ->
                    send(PresetActionPayload.Action.REMOVE_PRESET, selected.id(), "", "")));
        }

        EditBox id = addRenderableWidget(new EditBox(font, leftPos + s(16), topPos + s(231), size(78), size(20),
            Component.translatable("gui.fakeplayer.preset.preset_id")));
        id.setMaxLength(64);
        id.setHint(Component.translatable("gui.fakeplayer.preset.preset_id"));
        EditBox description = addRenderableWidget(new EditBox(font, leftPos + s(202), topPos + s(231), size(105), size(20),
            Component.translatable("gui.fakeplayer.preset.description")));
        description.setMaxLength(256);
        description.setHint(Component.translatable("gui.fakeplayer.preset.description"));
        Button player = addRenderableWidget(new SolidButton(leftPos + s(98), topPos + s(231), size(100), size(20),
            onlinePlayerLabel(), button -> {
            onlinePlayerIndex = cycle(onlinePlayerIndex, menu.onlinePlayers().size());
            button.setMessage(onlinePlayerLabel());
        }));
        Button save = new SolidButton(leftPos + s(311), topPos + s(231), size(53), size(20),
            Component.translatable("gui.fakeplayer.preset.save"), button -> {
            if (!menu.onlinePlayers().isEmpty()) {
                send(PresetActionPayload.Action.SAVE_PRESET, id.getValue(),
                    menu.onlinePlayers().get(onlinePlayerIndex), description.getValue());
            }
        });
        save.active = !menu.onlinePlayers().isEmpty();
        addRenderableWidget(save);
        player.active = !menu.onlinePlayers().isEmpty();
    }

    private void addGroupWidgets() {
        int first = page * PAGE_SIZE;
        int end = Math.min(first + PAGE_SIZE, menu.groups().size());
        for (int index = first; index < end; index++) {
            int selected = index;
            GroupSummary group = menu.groups().get(index);
            Component label = Component.translatable("gui.fakeplayer.preset.group_entry",
                group.id(), group.presetIds().size());
            addRenderableWidget(new SolidButton(leftPos + s(16), topPos + s(72 + (index - first) * 25),
                size(132), size(ROW_HEIGHT), label, button -> select(selected)));
        }
        GroupSummary selected = selectedGroup();
        if (selected != null) {
            addRenderableWidget(new SolidButton(leftPos + s(158), topPos + s(153), size(61), size(22),
                Component.translatable("gui.fakeplayer.preset.load"), button ->
                    send(PresetActionPayload.Action.LOAD_GROUP, selected.id(), "", "")));
            addRenderableWidget(new SolidButton(leftPos + s(223), topPos + s(153), size(61), size(22),
                Component.translatable("gui.fakeplayer.preset.unload"), button ->
                    send(PresetActionPayload.Action.UNLOAD_GROUP, selected.id(), "", "")));
            addRenderableWidget(new SolidButton(leftPos + s(288), topPos + s(153), size(76), size(22),
                Component.translatable("gui.fakeplayer.preset.delete"), button ->
                    send(PresetActionPayload.Action.REMOVE_GROUP, selected.id(), "", "")));

            Button member = addRenderableWidget(new SolidButton(leftPos + s(158), topPos + s(187), size(140), size(20),
                removePresetLabel(selected), button -> {
                removePresetIndex = cycle(removePresetIndex, selected.presetIds().size());
                button.setMessage(removePresetLabel(selected));
            }));
            Button remove = new SolidButton(leftPos + s(302), topPos + s(187), size(62), size(20),
                Component.translatable("gui.fakeplayer.preset.remove_member"), button -> {
                if (!selected.presetIds().isEmpty()) {
                    send(PresetActionPayload.Action.REMOVE_FROM_GROUP, selected.id(),
                        selected.presetIds().get(Math.min(removePresetIndex, selected.presetIds().size() - 1)), "");
                }
            });
            member.active = !selected.presetIds().isEmpty();
            remove.active = !selected.presetIds().isEmpty();
            addRenderableWidget(remove);
        }

        EditBox groupId = addRenderableWidget(new EditBox(font, leftPos + s(16), topPos + s(218), size(132), size(20),
            Component.translatable("gui.fakeplayer.preset.group_id")));
        groupId.setMaxLength(64);
        groupId.setHint(Component.translatable("gui.fakeplayer.preset.group_id"));
        addRenderableWidget(new SolidButton(leftPos + s(152), topPos + s(218), size(72), size(20),
            Component.translatable("gui.fakeplayer.preset.create"), button ->
                send(PresetActionPayload.Action.CREATE_GROUP, groupId.getValue(), "", "")));

        Button preset = addRenderableWidget(new SolidButton(leftPos + s(228), topPos + s(218), size(85), size(20),
            addPresetLabel(), button -> {
            addPresetIndex = cycle(addPresetIndex, menu.presets().size());
            button.setMessage(addPresetLabel());
        }));
        Button add = new SolidButton(leftPos + s(317), topPos + s(218), size(47), size(20),
            Component.translatable("gui.fakeplayer.preset.add"), button -> {
            GroupSummary group = selectedGroup();
            if (group != null && !menu.presets().isEmpty()) {
                send(PresetActionPayload.Action.ADD_TO_GROUP, group.id(), menu.presets().get(addPresetIndex).id(), "");
            }
        });
        add.active = selected != null && !menu.presets().isEmpty();
        preset.active = !menu.presets().isEmpty();
        addRenderableWidget(add);
    }

    private void addPageButtons() {
        int y = topPos + s(187);
        Button previous = new SolidButton(leftPos + s(16), y, size(30), size(20),
            Component.literal("<"), button -> changePage(-1));
        previous.active = page > 0;
        addRenderableWidget(previous);
        Button next = new SolidButton(leftPos + s(118), y, size(30), size(20),
            Component.literal(">"), button -> changePage(1));
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
        removePresetIndex = 0;
        rebuildControls();
    }

    private void select(int index) {
        selectedIndex = index;
        removePresetIndex = 0;
        rebuildControls();
    }

    private void changePage(int offset) {
        page = Math.max(0, Math.min(page + offset, pageCount() - 1));
        selectedIndex = -1;
        removePresetIndex = 0;
        rebuildControls();
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

    private Component onlinePlayerLabel() {
        return menu.onlinePlayers().isEmpty()
            ? Component.translatable("gui.fakeplayer.preset.no_online")
            : Component.literal(menu.onlinePlayers().get(Math.min(onlinePlayerIndex, menu.onlinePlayers().size() - 1)));
    }

    private Component addPresetLabel() {
        return menu.presets().isEmpty()
            ? Component.translatable("gui.fakeplayer.preset.no_presets")
            : Component.literal(menu.presets().get(Math.min(addPresetIndex, menu.presets().size() - 1)).id());
    }

    private Component removePresetLabel(GroupSummary group) {
        return group.presetIds().isEmpty()
            ? Component.translatable("gui.fakeplayer.preset.no_members")
            : Component.literal(group.presetIds().get(Math.min(removePresetIndex, group.presetIds().size() - 1)));
    }

    private static int cycle(int current, int size) {
        return size == 0 ? 0 : (current + 1) % size;
    }

    private void send(PresetActionPayload.Action action, String first, String second, String third) {
        PlatformNetworking.sendToServer(new PresetActionPayload(menu.containerId, action, first, second, third));
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
        graphics.fill(leftPos + s(156), topPos + s(70), leftPos + s(364), topPos + s(181), 0x802C3033);
    }

    private TitlePanel titlePanel() {
        return new TitlePanel(leftPos, topPos, responsiveWidth(), responsiveHeight(), title);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.centeredText(font, Component.translatable("gui.fakeplayer.preset.page", page + 1, pageCount()),
            leftPos + s(82), topPos + s(192), 0xFFC6C6C6);
        if (showingGroups) {
            drawGroupDetails(graphics);
        } else {
            drawPresetDetails(graphics);
        }
    }

    private void drawPresetDetails(GuiGraphicsExtractor graphics) {
        PresetSummary preset = selectedPreset();
        if (preset == null) {
            graphics.centeredText(font, Component.translatable(menu.presets().isEmpty()
                ? "gui.fakeplayer.preset.no_presets" : "gui.fakeplayer.preset.select_preset"),
                leftPos + s(260), topPos + s(111), 0xFFAAAAAA);
            return;
        }
        graphics.text(font, Component.literal(preset.id()), leftPos + s(166), topPos + s(78), 0xFFFFFFFF, false);
        graphics.text(font, Component.translatable("gui.fakeplayer.preset.player", preset.playerName()),
            leftPos + s(166), topPos + s(96), 0xFFC6C6C6, false);
        String description = preset.description().isBlank()
            ? Component.translatable("gui.fakeplayer.preset.no_description").getString()
            : preset.description();
        graphics.text(font, Component.literal(shorten(description, 31)), leftPos + s(166), topPos + s(116), 0xFFCCCCCC, false);
        if (description.length() > 31) {
            graphics.text(font, Component.literal(shorten(description.substring(31), 31)),
                leftPos + s(166), topPos + s(128), 0xFFCCCCCC, false);
        }
    }

    private void drawGroupDetails(GuiGraphicsExtractor graphics) {
        GroupSummary group = selectedGroup();
        if (group == null) {
            graphics.centeredText(font, Component.translatable(menu.groups().isEmpty()
                ? "gui.fakeplayer.preset.no_groups" : "gui.fakeplayer.preset.select_group"),
                leftPos + s(260), topPos + s(111), 0xFFAAAAAA);
            return;
        }
        graphics.text(font, Component.literal(group.id()), leftPos + s(166), topPos + s(78), 0xFFFFFFFF, false);
        graphics.text(font, Component.translatable("gui.fakeplayer.preset.members"),
            leftPos + s(166), topPos + s(96), 0xFFC6C6C6, false);
        String members = group.presetIds().isEmpty()
            ? Component.translatable("commands.fakeplayer.none").getString()
            : String.join(", ", group.presetIds());
        graphics.text(font, Component.literal(shorten(members, 31)), leftPos + s(166), topPos + s(112), 0xFFCCCCCC, false);
        if (members.length() > 31) {
            graphics.text(font, Component.literal(shorten(members.substring(31), 31)),
                leftPos + s(166), topPos + s(124), 0xFFCCCCCC, false);
        }
    }

    private static String shorten(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, Math.max(0, maximum - 1)) + "…";
    }
}
