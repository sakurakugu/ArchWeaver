package com.sakurakugu.archweaver.client.camera;

import com.sakurakugu.archweaver.client.camera.CameraPreferences.NumberSetting;
import com.sakurakugu.archweaver.client.camera.CameraPreferences.Toggle;
import com.sakurakugu.archweaver.client.camera.CameraSelection.Category;
import com.sakurakugu.archweaver.client.ui.GameModePanel;
import com.sakurakugu.archweaver.client.ui.IntegerSliderButton;
import com.sakurakugu.archweaver.client.ui.PixelGlyph;
import com.sakurakugu.archweaver.client.ui.SolidButton;
import com.sakurakugu.archweaver.client.ui.TargetButton;
import com.sakurakugu.archweaver.client.ui.ToggleSwitchButton;
import java.util.ArrayList;
import java.util.Comparator;
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
    private static final int TARGET_COLUMNS = 2; // 目标列表每行显示的目标数。
    private static final int TARGET_ROW_TOP = 132; // 目标列表首行的纵坐标（像素）。
    private static final int TARGET_COLUMN_PITCH = 142; // 目标列表相邻两列的间距（像素）。
    private static final int TARGET_ROW_HEIGHT = 20; // 目标列表单行按钮的高度（像素）。
    private static final int TARGET_ROW_PITCH = 22; // 目标列表相邻两行的间距（像素）。
    private static final int TARGET_MAX_ROWS = 8; // 目标列表最多显示的行数，多出来的目标翻页查看。
    private static final int PAGER_HEIGHT = 20; // 翻页按钮的边长（像素）。
    private static final int PAGER_GAP = 6; // 目标列表末行到翻页按钮的距离（像素）。
    private static final int TARGET_LIMIT_STEP = 8; // 目标数滑条每档的步长（个）。
    private static final int TARGET_LIMIT_STEP_MIN = 1; // 最小档位，对应 TARGET_LIMIT 的下界 8 个目标。
    private static final int TARGET_LIMIT_STEP_MAX = 16; // 最大档位，对应 TARGET_LIMIT 的上界 128 个目标。
    private enum Tab { CAMERA, ANGLES, TARGETS, SETTINGS }
    /** 数值设置行的文字标签：不占控件，绘制时按记录的位置画。 */
    private record SettingRow(NumberSetting setting, int y) { }
    private final boolean pickFollow;
    private final List<SettingRow> settingRows = new ArrayList<>();
    private Tab tab;
    private String filter = "";
    private int page;
    private int pages = 1;
    private boolean sortTargetsByDistance;
    private List<Entity> loadedTargets;

    public CameraPanelScreen(boolean pickFollow) {
        super(Component.translatable("camera.archweaver.panel"));
        this.pickFollow = pickFollow;
        tab = pickFollow ? Tab.TARGETS : ClientCamera.orthographic() ? Tab.ANGLES : Tab.CAMERA;
    }

    private int left() { return width / 2 - 140; }
    private int panelLeft() { return left() - PANEL_SIDE; }
    private int panelWidth() { return 280 + PANEL_SIDE * 2; }
    /** 各标签页内容的下边；目标页跟着列表收边，不撑满窗口。 */
    private int contentBottom() {
        return switch (tab) {
            case CAMERA -> 186; // 五项数值设置加一行开关
            case ANGLES -> 178; // 角度按钮加三项数值设置
            case SETTINGS -> 192; // 四行开关加目标数滑条和排序开关
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
        settingRows.clear();
        for (Tab value : Tab.values()) {
            button(Component.translatable("camera.archweaver.tab." + value.name().toLowerCase(Locale.ROOT)),
                left() + value.ordinal() * 70, 32, 68, () -> { tab = value; page = 0; init(); });
        }
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

    private void cameraControls() {
        NumberSetting[] settings = {NumberSetting.SPEED, NumberSetting.DISTANCE, NumberSetting.SHOULDER_DISTANCE,
            NumberSetting.SHOULDER_OFFSET, NumberSetting.ORBIT_SPEED};
        for (int i = 0; i < settings.length; i++) number(settings[i], 56 + i * 22);
        // 隐藏左上角文字与默认选中上一个视角都归设置页，这里只留自动环绕，紧跟数值设置最后一行。
        switchToggle(Toggle.AUTO_ORBIT, 166);
    }

    private void angleControls() {
        for (int i = 0; i < CameraMath.Angle.values().length; i++) {
            var angle = CameraMath.Angle.values()[i];
            button(Component.translatable(angle.key()), left() + i % 5 * 56, 60 + i / 5 * 22, 54, () -> {
                ClientCamera.angle(angle);
                init();
            });
        }
        number(NumberSetting.YAW, 110);
        number(NumberSetting.PITCH, 134);
        number(NumberSetting.SCALE, 158);
    }

    /** 设置页：每个开关和设置各占一行，行距与其它页面的设置行一致。 */
    private void settingsControls() {
        switchToggle(Toggle.BODY_INTERACTION, 62);
        switchToggle(Toggle.BODY_MOVEMENT, 84);
        switchToggle(Toggle.HIDE_HUD_TEXT, 106);
        switchToggle(Toggle.SELECT_PREVIOUS, 128);
        targetLimitSlider(150);
        sortToggle(172);
    }

    private void switchToggle(Toggle setting, int y) {
        switchToggle(setting, left(), y, 280);
    }

    /** 设置行：左侧标签、右侧滑动开关，与主界面设置页一致。 */
    private void switchToggle(Toggle setting, int x, int y, int width) {
        var button = addRenderableWidget(new ToggleSwitchButton(x, y, width, 20,
            Component.translatable("camera.archweaver.toggle." + setting.key()), 0xFFFFFFFF,
            () -> CameraPreferences.get(setting), b -> { CameraPreferences.flip(setting); init(); }));
        button.setTooltip(Tooltip.create(Component.translatable("camera.archweaver.toggle." + setting.key() + ".tooltip")));
    }

    /** 目标数滑条：按档取值，每页最多显示的目标数随档位变化。 */
    private void targetLimitSlider(int y) {
        addRenderableWidget(new IntegerSliderButton(left(), y, 280, 20,
            TARGET_LIMIT_STEP_MIN, TARGET_LIMIT_STEP_MAX, targetLimitStep(),
            step -> Component.translatable("camera.archweaver.target_limit", step * TARGET_LIMIT_STEP),
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
        settingRows.add(new SettingRow(setting, y));
        double initial = numberValue(setting);
        EditBox field = new EditBox(font, left() + VALUE_FIELD_X, y + FIELD_INSET, VALUE_FIELD_WIDTH, FIELD_HEIGHT,
            Component.translatable("camera.archweaver.setting." + setting.key()));
        field.setMaxLength(12);
        field.setValue(String.format(Locale.ROOT, "%.2f", initial));
        field.setResponder(text -> {
            try {
                double value = Double.parseDouble(text);
                if (!Double.isFinite(value) || value < setting.min || value > setting.max) { field.setTextColor(0xFF5555); return; }
                field.setTextColor(0xE0E0E0);
                CameraPreferences.set(setting, value);
                if (setting == NumberSetting.YAW) ClientCamera.rotation(value, numberValue(NumberSetting.PITCH));
                if (setting == NumberSetting.PITCH) ClientCamera.rotation(numberValue(NumberSetting.YAW), value);
            } catch (NumberFormatException ignored) { field.setTextColor(0xFF5555); }
        });
        field.setTooltip(Tooltip.create(Component.literal(setting.min + " … " + setting.max)));
        addRenderableWidget(field);
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
        if (setting == NumberSetting.YAW) ClientCamera.rotation(value, numberValue(NumberSetting.PITCH));
        if (setting == NumberSetting.PITCH) ClientCamera.rotation(numberValue(NumberSetting.YAW), value);
        init();
    }

    private void targetControls() {
        EditBox search = new EditBox(font, left(), 60 + FIELD_INSET, 280, FIELD_HEIGHT,
            Component.translatable("camera.archweaver.search"));
        search.setHint(Component.translatable("camera.archweaver.search"));
        search.setValue(filter);
        search.setResponder(value -> {
            if (!value.equals(filter)) { filter = value; page = 0; rebuildTargets(); }
        });
        addRenderableWidget(search);
        button(Component.translatable("camera.archweaver.body_target"), left(), 84, 138, () -> {
            if (ClientCamera.bodyHit() instanceof net.minecraft.world.phys.EntityHitResult hit && hit.getEntity() != minecraft.player) choose(hit.getEntity());
            else minecraft.player.sendOverlayMessage(Component.translatable("camera.archweaver.no_entity"));
        });
        button(Component.translatable("camera.archweaver.block_center"), left() + 142, 84, 138, () -> {
            if (ClientCamera.category() != Category.ORBIT) ClientCamera.select(Category.ORBIT, 0);
            if (!ClientCamera.useBlockCenter()) minecraft.player.sendOverlayMessage(Component.translatable("camera.archweaver.no_block"));
        });
        button(Component.translatable("camera.archweaver.player_center"), left(), 108, 138, () -> {
            ClientCamera.select(Category.ORBIT, 0);
            ClientCamera.usePlayerCenter();
        });
        button(Component.translatable("camera.archweaver.refix"), left() + 142, 108, 138, () -> {
            ClientCamera.select(Category.FIXED, 0);
            ClientCamera.refix();
        });
        // 目标数与排序在设置页；列表从原来的位置起，每行放两个。
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
        // 数值设置的标签与目标页的页码都只画文字，不占控件。
        for (SettingRow row : settingRows) {
            String label = Component.translatable("camera.archweaver.setting." + row.setting().key()).getString();
            graphics.text(font, Component.literal(font.plainSubstrByWidth(label, VALUE_MINUS_X - 6)),
                left(), row.y() + 6, 0xFFFFFFFF, false);
        }
        if (tab == Tab.TARGETS) {
            graphics.centeredText(font, Component.literal((page + 1) + " / " + pages), left() + 140, pagerY() + 6, 0xFFFFFFFF);
        }
        super.extractRenderState(graphics, x, y, partial);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void removed() { CameraPreferences.save(); ClientCamera.flushPreferences(); super.removed(); }
}
