package com.sakurakugu.archweaver.client.camera;

import com.sakurakugu.archweaver.client.ClientScreenNavigation;
import com.sakurakugu.archweaver.client.camera.CameraPreferences.NumberSetting;
import com.sakurakugu.archweaver.client.camera.CameraPreferences.Toggle;
import com.sakurakugu.archweaver.client.camera.CameraSelection.Category;
import com.sakurakugu.archweaver.client.ui.GameModePanel;
import com.sakurakugu.archweaver.client.ui.IntegerSliderButton;
import com.sakurakugu.archweaver.client.ui.PixelGlyph;
import com.sakurakugu.archweaver.client.ui.SegmentedSwitchButton;
import com.sakurakugu.archweaver.client.ui.SolidButton;
import com.sakurakugu.archweaver.client.ui.TargetButton;
import com.sakurakugu.archweaver.client.ui.ToggleSwitchButton;
import com.sakurakugu.archweaver.client.ui.ViewCube;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** 不暂停世界的相机面板；分页限制控件数量，兼容较小的 GUI 分辨率。 */
public final class CameraPanelScreen extends Screen {
    private static final int PANEL_TOP = 8; // 面板顶边（像素），标题栏从这里开始。
    private static final int PANEL_SIDE = 8; // 面板左右边框到内容列的间距（像素）。
    private static final int CONTENT_WIDTH = 280; // 内容列宽度（像素），各行的控件都按它左右对齐。
    private static final int PANEL_BOTTOM = 4; // 面板底边到屏幕底边的距离（像素）。
    private static final int PANEL_PADDING = 10; // 内容下边到面板底边的距离（像素），面板据此收边。
    private static final int VALUE_MINUS_X = 164; // 减号相对内容列左边的位置（像素）。
    private static final int VALUE_FIELD_X = 184; // 数值输入框相对内容列左边的位置（像素）。
    private static final int VALUE_FIELD_WIDTH = 76; // 数值输入框宽度（像素）。
    private static final int VALUE_PLUS_X = 262; // 加号相对内容列左边的位置（像素）。
    private static final int VALUE_BUTTON_WIDTH = 18; // 加减号按钮宽度（像素）。
    private static final int FIELD_HEIGHT = 18; // 输入框高度（像素）：比同一行的按钮上下各收 1px。
    private static final int FIELD_INSET = 1; // 输入框相对所在行上缘下移的距离（像素），据此保持垂直居中。
    private static final int HEADER_MARGIN = 16; // 标题栏按钮到面板边框的距离（像素），与视角选择器一致。
    private static final int HEADER_BUTTON_SIZE = 20; // 标题栏按钮的边长（像素）。
    private static final int TAB_TOP = 32; // 顶部标签开关的纵坐标（像素），与视角选择器标题栏下沿对齐。
    private static final int TAB_HEIGHT = 20; // 顶部标签开关的高度（像素），四档按内容列宽均分。
    private static final int TARGET_COLUMNS = 2; // 目标列表每行显示的目标数。
    private static final int SEARCH_ROW_TOP = 60; // 搜索框与其右侧快捷动作按钮所在行的纵坐标（像素）。
    private static final int QUICK_ACTION_SIZE = 20; // 快捷动作图标按钮的边长（像素），与同排的输入框等高。
    private static final int QUICK_ACTION_GAP = 2; // 相邻两个快捷动作按钮的间距（像素）。
    private static final int QUICK_ACTION_COUNT = 4; // 快捷动作按钮的数量。
    private static final int QUICK_ACTIONS_WIDTH = QUICK_ACTION_COUNT * QUICK_ACTION_SIZE
        + (QUICK_ACTION_COUNT - 1) * QUICK_ACTION_GAP; // 快捷动作按钮整排占的宽度（像素）。
    private static final int SEARCH_ACTION_GAP = 6; // 搜索框到右侧第一个快捷动作按钮的间距（像素）。
    private static final int TARGET_ROW_TOP = 88; // 目标列表首行的纵坐标（像素），紧跟在搜索行下面。
    private static final int TARGET_COLUMN_PITCH = 142; // 目标列表相邻两列的间距（像素）。
    private static final int TARGET_ROW_HEIGHT = 20; // 目标列表单行按钮的高度（像素）。
    private static final int TARGET_ROW_PITCH = 22; // 目标列表相邻两行的间距（像素）。
    private static final int TARGET_MAX_ROWS = 8; // 目标列表最多显示的行数，多出来的目标翻页查看。
    private static final int PAGER_HEIGHT = 20; // 翻页按钮的边长（像素）。
    private static final int PAGER_GAP = 6; // 目标列表末行到翻页按钮的距离（像素）。
    private static final int TARGET_LIMIT_STEP = 8; // 目标数滑条每档的步长（个）。
    private static final int TARGET_LIMIT_STEP_MIN = 1; // 最小档位，对应 TARGET_LIMIT 的下界 8 个目标。
    private static final int TARGET_LIMIT_STEP_MAX = 16; // 最大档位，对应 TARGET_LIMIT 的上界 128 个目标。
    private static final int DIVIDER_TOP = 154; // 目标数上方分割线的纵坐标（像素），两侧留白比开关组那边宽。
    private static final int DIVIDER_COLOR = 0x60FFFFFF; // 分割线颜色：半透明白，压在原版面板的深色底上。
    private static final int TARGET_LIMIT_ROW_TOP = 162; // 目标数一行的纵坐标（像素），上方是分割线。
    private static final int TARGET_LIMIT_SLIDER_WIDTH = 140; // 目标数滑条的宽度（像素），贴着内容列右边缘。
    private static final int TARGET_LIMIT_LABEL_GAP = 6; // 目标数文字到滑条之间留出的间距（像素）。
    private static final int SORT_TOGGLE_TOP = 184; // 按距离排序开关的纵坐标（像素），紧跟目标数一行。
    private enum Tab { CAMERA, ANGLES, TARGETS, SETTINGS }
    /** 行标签：只画文字、不占控件，绘制时按记录的位置画，放不下的部分截断。 */
    private record RowLabel(Component text, int x, int y, int maxWidth) { }
    private final boolean pickFollow;
    private final List<RowLabel> rowLabels = new ArrayList<>();
    private final EnumMap<NumberSetting, EditBox> angleFields = new EnumMap<>(NumberSetting.class);
    private boolean syncingAngleFields;
    private Tab tab;
    private String filter = "";
    private int page;
    private int pages = 1;
    private boolean sortTargetsByDistance;
    private List<Entity> loadedTargets;

    public CameraPanelScreen(boolean pickFollow) {
        this(pickFollow, pickFollow ? Tab.TARGETS : ClientCamera.orthographic() ? Tab.ANGLES : Tab.CAMERA);
    }

    /** 控制中心的「相机设置」入口：进去直接停在设置页。 */
    public static CameraPanelScreen settings() {
        return new CameraPanelScreen(false, Tab.SETTINGS);
    }

    private CameraPanelScreen(boolean pickFollow, Tab initialTab) {
        super(Component.translatable("camera.archweaver.panel"));
        this.pickFollow = pickFollow;
        tab = initialTab;
    }

    private int left() { return width / 2 - CONTENT_WIDTH / 2; }
    /** 内容列右边缘：滑条、图标按钮这些贴右边缘的控件都按它定位。 */
    private int contentRight() { return left() + CONTENT_WIDTH; }
    private int panelLeft() { return left() - PANEL_SIDE; }
    private int panelWidth() { return CONTENT_WIDTH + PANEL_SIDE * 2; }
    /** 各标签页内容的下边；目标页跟着列表收边，不撑满窗口。 */
    private int contentBottom() {
        return switch (tab) {
            case CAMERA -> 186; // 五项数值设置加一行开关
            case ANGLES -> 182; // 视图立方体加三项数值设置
            case SETTINGS -> SORT_TOGGLE_TOP + 20; // 四行开关加目标数一行和排序开关
            case TARGETS -> pagerY() + PAGER_HEIGHT; // 列表末行下面就是翻页按钮
        };
    }
    /** 目标列表能铺到的最低处：再往下依次是翻页按钮和面板下边框。 */
    private int targetListBottom() {
        return height - PANEL_BOTTOM - PANEL_PADDING - PAGER_HEIGHT - PAGER_GAP;
    }
    /** 目标列表的行数：最多 {@link #TARGET_MAX_ROWS} 行，窗口太矮时按可用高度减少。 */
    private int targetRows() {
        return Math.clamp((targetListBottom() - TARGET_ROW_TOP - TARGET_ROW_HEIGHT) / TARGET_ROW_PITCH + 1,
            1, TARGET_MAX_ROWS);
    }
    /** 翻页按钮紧跟着列表末行，面板再往下收一个内边距，目标页不会拉满整个窗口。 */
    private int pagerY() {
        return TARGET_ROW_TOP + (targetRows() - 1) * TARGET_ROW_PITCH + TARGET_ROW_HEIGHT + PAGER_GAP;
    }
    /** 面板底边：贴着当前页的内容，不再一律拉到窗口底部。 */
    private int panelBottom() { return Math.min(height - PANEL_BOTTOM, contentBottom() + PANEL_PADDING); }
    private int panelHeight() { return panelBottom() - PANEL_TOP; }
    private Component panelTitle() {
        return title.copy().append(" · ").append(Component.translatable(ClientCamera.category().key()));
    }

    @Override
    protected void init() {
        clearWidgets();
        rowLabels.clear();
        angleFields.clear();
        // 四个标签页改用一个四档开关，档位与 Tab 的顺序一致。
        addRenderableWidget(new SegmentedSwitchButton(left(), TAB_TOP, CONTENT_WIDTH, TAB_HEIGHT, tabLabels(),
            () -> tab.ordinal(), this::selectTab));
        switch (tab) {
            case CAMERA -> cameraControls();
            case ANGLES -> angleControls();
            case TARGETS -> targetControls();
            case SETTINGS -> settingsControls();
        }
        // 关闭按钮改到标题栏左上角的返回图标。
        addRenderableWidget(new SolidButton(panelLeft() + HEADER_MARGIN,
            PANEL_TOP + (GameModePanel.HEADER_HEIGHT - HEADER_BUTTON_SIZE) / 2, HEADER_BUTTON_SIZE, HEADER_BUTTON_SIZE,
            PixelGlyph.BACK, Component.translatable("camera.archweaver.back"), button -> onClose()).withoutFrame());
    }

    /** 顶部开关每一档的文字，按 {@link Tab} 的顺序从左到右排列。 */
    private static List<Component> tabLabels() {
        List<Component> labels = new ArrayList<>();
        for (Tab value : Tab.values()) {
            labels.add(Component.translatable("camera.archweaver.tab." + value.name().toLowerCase(Locale.ROOT)));
        }
        return labels;
    }

    /** 切到某一档标签页：重建控件，切回原来那档时不做事。 */
    private void selectTab(int index) {
        if (tab.ordinal() == index) return;
        tab = Tab.values()[index];
        page = 0;
        init();
    }

    private void cameraControls() {
        NumberSetting[] settings = {NumberSetting.SPEED, NumberSetting.DISTANCE, NumberSetting.SHOULDER_DISTANCE,
            NumberSetting.SHOULDER_OFFSET, NumberSetting.ORBIT_SPEED};
        for (int i = 0; i < settings.length; i++) number(settings[i], 56 + i * 22);
        // 隐藏左上角文字与默认选中上一个视角都归设置页，这里只留自动环绕，紧跟数值设置最后一行。
        switchToggle(Toggle.AUTO_ORBIT, 166);
    }

    private void angleControls() {
        addRenderableWidget(new ViewCube(left() + 112, 56, 56,
            () -> numberValue(NumberSetting.YAW), () -> numberValue(NumberSetting.PITCH), (yaw, pitch) -> {
                ClientCamera.rotationAroundCenter(yaw, pitch);
                syncAngleFields();
            }));
        number(NumberSetting.YAW, 118);
        number(NumberSetting.PITCH, 140);
        number(NumberSetting.SCALE, 162);
    }

    /** 只同步数值文字，不重建控件，避免拖动立方体时丢失鼠标捕获。 */
    private void syncAngleFields() {
        syncingAngleFields = true;
        try {
            angleFields.forEach((setting, field) -> {
                field.setValue(String.format(Locale.ROOT, "%.2f", numberValue(setting)));
                field.setTextColor(0xE0E0E0);
            });
        } finally {
            syncingAngleFields = false;
        }
    }

    /** 设置页：开关组在上，目标数一行用分割线隔开，行距与其它页面的设置行一致。 */
    private void settingsControls() {
        switchToggle(Toggle.BODY_INTERACTION, 62);
        switchToggle(Toggle.BODY_MOVEMENT, 84);
        switchToggle(Toggle.HIDE_HUD_TEXT, 106);
        switchToggle(Toggle.SELECT_PREVIOUS, 128);
        targetLimitSlider(TARGET_LIMIT_ROW_TOP);
        sortToggle(SORT_TOGGLE_TOP);
    }

    private void switchToggle(Toggle setting, int y) {
        switchToggle(setting, left(), y, CONTENT_WIDTH);
    }

    /** 设置行：左侧标签、右侧滑动开关，与主界面设置页一致。 */
    private void switchToggle(Toggle setting, int x, int y, int width) {
        var button = addRenderableWidget(new ToggleSwitchButton(x, y, width, 20,
            Component.translatable("camera.archweaver.toggle." + setting.key()), 0xFFFFFFFF,
            () -> CameraPreferences.get(setting), b -> { CameraPreferences.flip(setting); init(); }));
        button.setTooltip(Tooltip.create(Component.translatable("camera.archweaver.toggle." + setting.key() + ".tooltip")));
    }

    /** 目标数一行：左边只写标签，右边滑条里直接显示个数，按档取值。 */
    private void targetLimitSlider(int y) {
        int sliderX = contentRight() - TARGET_LIMIT_SLIDER_WIDTH;
        rowLabels.add(new RowLabel(Component.translatable("camera.archweaver.setting." + NumberSetting.TARGET_LIMIT.key()),
            left(), y, sliderX - left() - TARGET_LIMIT_LABEL_GAP));
        addRenderableWidget(new IntegerSliderButton(sliderX, y, TARGET_LIMIT_SLIDER_WIDTH, 20,
            TARGET_LIMIT_STEP_MIN, TARGET_LIMIT_STEP_MAX, targetLimitStep(),
            step -> Component.literal(Integer.toString(step * TARGET_LIMIT_STEP)),
            step -> {
                // 拖动中不重建控件，分页数等切回目标页时再按新的目标数计算。
                CameraPreferences.set(NumberSetting.TARGET_LIMIT, step * TARGET_LIMIT_STEP);
                page = 0;
            }));
    }

    /** 目标数占的滑条档位：配置里按「个」存，滑条只停在 8 个一档的档位上。 */
    private static int targetLimitStep() {
        return Math.clamp((int) Math.round(CameraPreferences.get(NumberSetting.TARGET_LIMIT) / TARGET_LIMIT_STEP),
            TARGET_LIMIT_STEP_MIN, TARGET_LIMIT_STEP_MAX);
    }

    /** 目标页每页最多显示的目标数（个）。 */
    private static int targetLimit() { return targetLimitStep() * TARGET_LIMIT_STEP; }

    /** 按距离排序：与其它设置行同一套滑动开关。 */
    private void sortToggle(int y) {
        addRenderableWidget(new ToggleSwitchButton(left(), y, 280, 20,
            Component.translatable("camera.archweaver.toggle.sort_by_distance"), 0xFFFFFFFF,
            () -> sortTargetsByDistance,
            button -> { sortTargetsByDistance = !sortTargetsByDistance; page = 0; init(); }));
    }

    /** 数值设置行：左侧只留文字标签，减号与加号夹住输入框。 */
    private void number(NumberSetting setting, int y) {
        rowLabels.add(new RowLabel(Component.translatable("camera.archweaver.setting." + setting.key()),
            left(), y, VALUE_MINUS_X - 6));
        double initial = numberValue(setting);
        EditBox field = new EditBox(font, left() + VALUE_FIELD_X, y + FIELD_INSET, VALUE_FIELD_WIDTH, FIELD_HEIGHT,
            Component.translatable("camera.archweaver.setting." + setting.key()));
        field.setMaxLength(12);
        field.setValue(String.format(Locale.ROOT, "%.2f", initial));
        field.setResponder(text -> {
            if (syncingAngleFields) return;
            try {
                double value = Double.parseDouble(text);
                if (!Double.isFinite(value) || value < setting.min || value > setting.max) { field.setTextColor(0xFF5555); return; }
                field.setTextColor(0xE0E0E0);
                CameraPreferences.set(setting, value);
                if (setting == NumberSetting.YAW) ClientCamera.rotationAroundCenter(value, numberValue(NumberSetting.PITCH));
                if (setting == NumberSetting.PITCH) ClientCamera.rotationAroundCenter(numberValue(NumberSetting.YAW), value);
            } catch (NumberFormatException ignored) { field.setTextColor(0xFF5555); }
        });
        field.setTooltip(Tooltip.create(Component.literal(setting.min + " … " + setting.max)));
        addRenderableWidget(field);
        if (setting == NumberSetting.YAW || setting == NumberSetting.PITCH) angleFields.put(setting, field);
        button(Component.literal("−"), left() + VALUE_MINUS_X, y, VALUE_BUTTON_WIDTH, () -> adjust(setting, -1));
        button(Component.literal("+"), left() + VALUE_PLUS_X, y, VALUE_BUTTON_WIDTH, () -> adjust(setting, 1));
    }

    private static double numberValue(NumberSetting setting) {
        if (ClientCamera.active()) {
            if (setting == NumberSetting.YAW) return ClientCamera.yaw();
            if (setting == NumberSetting.PITCH) return ClientCamera.pitch();
        }
        return CameraPreferences.get(setting);
    }

    private void adjust(NumberSetting setting, int direction) {
        double value = setting.clamp(numberValue(setting) + direction * (setting == NumberSetting.SPEED || setting == NumberSetting.SHOULDER_OFFSET ? 0.1 : 1));
        CameraPreferences.set(setting, value);
        if (setting == NumberSetting.YAW) ClientCamera.rotationAroundCenter(value, numberValue(NumberSetting.PITCH));
        if (setting == NumberSetting.PITCH) ClientCamera.rotationAroundCenter(numberValue(NumberSetting.YAW), value);
        init();
    }

    private void targetControls() {
        EditBox search = new EditBox(font, left(), SEARCH_ROW_TOP + FIELD_INSET,
            CONTENT_WIDTH - QUICK_ACTIONS_WIDTH - SEARCH_ACTION_GAP, FIELD_HEIGHT,
            Component.translatable("camera.archweaver.search"));
        search.setHint(Component.translatable("camera.archweaver.search"));
        search.setValue(filter);
        search.setResponder(value -> {
            if (!value.equals(filter)) { filter = value; page = 0; rebuildTargets(); }
        });
        addRenderableWidget(search);
        // 四个快捷动作收成图标按钮，排在搜索框右边，列表因此多出两行。
        quickAction(PixelGlyph.RETICLE, "camera.archweaver.body_target", 0, () -> {
            if (ClientCamera.bodyHit() instanceof net.minecraft.world.phys.EntityHitResult hit && hit.getEntity() != minecraft.player) choose(hit.getEntity());
            else minecraft.player.sendOverlayMessage(Component.translatable("camera.archweaver.no_entity"));
        });
        quickAction(PixelGlyph.CUBE, "camera.archweaver.block_center", 1, () -> {
            if (ClientCamera.category() != Category.ORBIT) ClientCamera.select(Category.ORBIT, 0);
            if (!ClientCamera.useBlockCenter()) minecraft.player.sendOverlayMessage(Component.translatable("camera.archweaver.no_block"));
        });
        quickAction(PixelGlyph.PERSON, "camera.archweaver.player_center", 2, () -> {
            ClientCamera.select(Category.ORBIT, 0);
            ClientCamera.usePlayerCenter();
        });
        quickAction(PixelGlyph.CAMERA, "camera.archweaver.refix", 3, () -> {
            ClientCamera.select(Category.FIXED, 0);
            ClientCamera.refix();
        });
        // 目标数与排序在设置页；列表紧跟在搜索行下面，每行放两个。
        List<Entity> targets = targets();
        int capacity = targetRows() * TARGET_COLUMNS;
        int visibleCount = Math.min(targetLimit(), targets.size());
        pages = Math.max(1, (visibleCount + capacity - 1) / capacity);
        page = Math.clamp(page, 0, pages - 1);
        for (int i = page * capacity; i < Math.min(visibleCount, (page + 1) * capacity); i++) {
            Entity entity = targets.get(i);
            int slot = i - page * capacity;
            int x = left() + slot % TARGET_COLUMNS * TARGET_COLUMN_PITCH;
            int y = TARGET_ROW_TOP + slot / TARGET_COLUMNS * TARGET_ROW_PITCH;
            addRenderableWidget(new TargetButton(x, y, 138, TARGET_ROW_HEIGHT, entity, button -> choose(entity)));
        }
        pageButton(PixelGlyph.ARROW_LEFT, "gui.archweaver.page.previous", left(), () -> { page = Math.max(0, page - 1); init(); });
        pageButton(PixelGlyph.ARROW_RIGHT, "gui.archweaver.page.next", left() + 256, () -> { page = Math.min(pages - 1, page + 1); init(); });
    }

    /** 快捷动作图标按钮：只画图标，说明文字放进 tooltip，朗读同样取自 tooltip。 */
    private void quickAction(PixelGlyph glyph, String tooltip, int index, Runnable action) {
        addRenderableWidget(new SolidButton(
            contentRight() - QUICK_ACTIONS_WIDTH + index * (QUICK_ACTION_SIZE + QUICK_ACTION_GAP), SEARCH_ROW_TOP,
            QUICK_ACTION_SIZE, QUICK_ACTION_SIZE, glyph, Component.translatable(tooltip), button -> action.run()));
    }

    /** 翻页按钮：图标按钮不留文字，提示与朗读都取自 tooltip。 */
    private void pageButton(PixelGlyph glyph, String tooltip, int x, Runnable action) {
        var label = Component.translatable(tooltip);
        // 左右翻页按钮保持正方形，避免图标按钮被拉成长条。
        addRenderableWidget(new SolidButton(x, pagerY(), PAGER_HEIGHT, PAGER_HEIGHT, glyph, label, button -> action.run()));
    }

    private void rebuildTargets() {
        // 重建列表同时保留搜索框焦点与光标，支持连续输入。
        init();
        for (var child : children()) if (child instanceof EditBox box) {
            setFocused(box);
            box.moveCursorToEnd(false);
            break;
        }
    }

    private List<Entity> targets() {
        if (loadedTargets == null) {
            loadedTargets = new ArrayList<>();
            if (minecraft.level == null) return loadedTargets;
            for (Entity entity : minecraft.level.entitiesForRendering()) {
                if (entity != minecraft.player && entity.isAlive() && !entity.isRemoved()) loadedTargets.add(entity);
            }
        }
        List<Entity> result = new ArrayList<>();
        String query = filter.toLowerCase(Locale.ROOT);
        for (Entity entity : loadedTargets) if (entity.isAlive() && !entity.isRemoved()
            && (entity.getDisplayName().getString() + " " + entity.getType().getDescription().getString()).toLowerCase(Locale.ROOT).contains(query)) result.add(entity);
        if (sortTargetsByDistance && minecraft.player != null) result.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(minecraft.player)));
        else result.sort(Comparator.<Entity, Boolean>comparing(e -> !(e instanceof Player)).thenComparing(e -> e.getDisplayName().getString()).thenComparing(Entity::getId));
        return result;
    }

    private void choose(Entity entity) {
        if (entity.isRemoved() || !entity.isAlive()) return;
        ClientCamera.follow(entity);
        if (pickFollow) onClose();
    }

    private Button button(Component label, int x, int y, int width, Runnable action) {
        return addRenderableWidget(new SolidButton(x, y, width, 20, label, button -> action.run()));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int x, int y, float partial) {
        // 与视角选择器同一套原版 F3+F4 面板，标题栏中央显示面板名和当前类别。
        new GameModePanel(panelLeft(), PANEL_TOP, panelWidth(), panelHeight(), panelTitle()).draw(graphics, font);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float partial) {
        // 行标签、分割线与目标页的页码都只画文字或线，不占控件。
        for (RowLabel row : rowLabels) {
            graphics.text(font, Component.literal(font.plainSubstrByWidth(row.text().getString(), row.maxWidth())),
                row.x(), row.y() + 6, 0xFFFFFFFF, false);
        }
        if (tab == Tab.SETTINGS) {
            // 分割开关组与目标数：两端对齐上下两行的控件，只留面板自身的内边距，不和边框相连。
            graphics.fill(left(), DIVIDER_TOP, contentRight(), DIVIDER_TOP + 1, DIVIDER_COLOR);
        }
        if (tab == Tab.TARGETS) {
            graphics.centeredText(font, Component.literal((page + 1) + " / " + pages), left() + 140, pagerY() + 6, 0xFFFFFFFF);
        }
        super.extractRenderState(graphics, x, y, partial);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void removed() { CameraPreferences.save(); ClientCamera.flushPreferences(); super.removed(); }
    /** 从控制中心打开时回到控制中心，直接在世界里打开时退回游戏。 */
    @Override public void onClose() { ClientScreenNavigation.back(this); }
}
