package com.sakurakugu.archweaver.client.camera;

import com.sakurakugu.archweaver.client.camera.CameraPreferences.Toggle;
import com.sakurakugu.archweaver.client.camera.CameraSelection.Category;
import com.sakurakugu.archweaver.client.ui.GameModePanel;
import com.sakurakugu.archweaver.client.ui.PixelGlyph;
import com.sakurakugu.archweaver.client.ui.CameraModeButton;
import com.sakurakugu.archweaver.client.ui.CameraToggleButton;
import com.sakurakugu.archweaver.client.ui.SolidButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** F3+F5 的临时选择器，松开调试修饰键才提交视角和角度。 */
public final class CameraSelectorScreen extends Screen {
    private static final Identifier SLOT = Identifier.withDefaultNamespace("gamemode_switcher/slot");
    private static final Identifier SELECTED = Identifier.withDefaultNamespace("gamemode_switcher/selection");
    private static final int PANEL_WIDTH = 284; // 面板宽度（像素）。
    private static final int PANEL_INSET = 7; // 面板左右边框到内容列的间距（像素）。
    private static final int PANEL_TOP_GAP = 24; // 面板顶边到图标行的距离（像素），正好容下标题栏。
    private static final int HINT_GAP = 10; // 内容区下边到提示文字的距离（像素），与原版 F3+F4 一致。
    private static final int PANEL_BOTTOM_GAP = 3; // 提示文字下边到面板底边的距离（像素），与原版 F3+F4 一致。
    private static final int HEADER_BUTTON_SIZE = 20; // 标题栏右侧按钮的边长（像素）。
    private static final int HEADER_BUTTON_MARGIN = 16; // 标题栏右侧按钮到面板右边框的距离（像素）。
    private static final int KEY_COLOR = 0xFF55FFFF; // 提示里按键名的青色，与原版 F3+F4 的提示一致。
    private static final Item[] ICONS = {Items.SPYGLASS, Items.ENDER_EYE, Items.PAPER, Items.BOW, Items.COMPASS, Items.TRIPWIRE_HOOK, Items.PLAYER_HEAD};
    private static final String[][] MODE_ICONS = {
        {"vanilla_first_person", "vanilla_second_person", "vanilla_third_person"},
        {"free"}, {"orthographic_follow_player", "orthographic_free_camera"},
        {"shoulder_left", "shoulder_right"}, {"orbit"}, {"fixed"}, {"follow"}
    };
    private final CameraSelection preview = ClientCamera.selectionSnapshot();
    private Category selected = preview.category();
    private int selectedMode = preview.mode();
    private CameraMath.Angle selectedAngle;
    private boolean openPanel;
    private int firstMouseX = Integer.MIN_VALUE, firstMouseY;

    public CameraSelectorScreen() { super(Component.translatable("camera.archweaver.selector")); }
    private int tileTop() { return Math.max(24, height / 2 - 105); }
    private int tileLeft() { return width / 2 - 106; }
    private int contentLeft() { return width / 2 - 135; }
    private int panelLeft() { return contentLeft() - PANEL_INSET; }
    private int panelTop() { return tileTop() - PANEL_TOP_GAP; }
    /** 最后一行内容的下边：三行子模式按钮，正交视角下面还多两行角度按钮。 */
    private int contentBottom() {
        int modes = tileTop() + 30 + 2 * 28 + 26;
        return selected == Category.ORTHOGRAPHIC ? tileTop() + 30 + 3 * 28 + 5 + 22 + 20 : modes;
    }
    private int hintY() { return Math.min(height - 12, contentBottom() + HINT_GAP); }
    private int panelBottom() { return Math.min(height, hintY() + font.lineHeight + PANEL_BOTTOM_GAP); }
    private int headerButtonX() { return panelLeft() + PANEL_WIDTH - HEADER_BUTTON_MARGIN - HEADER_BUTTON_SIZE; }
    private int headerButtonY() { return panelTop() + (GameModePanel.HEADER_HEIGHT - HEADER_BUTTON_SIZE) / 2; }

    @Override
    protected void init() {
        clearWidgets();
        for (Category category : Category.values()) {
            int x = tileLeft() + category.ordinal() * 31;
            for (int i = 0; i < category.modes(); i++) {
                int mode = i;
                addRenderableWidget(new CameraModeButton(x, tileTop() + 30 + i * 28,
                    Identifier.fromNamespaceAndPath("archweaver", "textures/gui/camera/" + MODE_ICONS[category.ordinal()][i] + ".png"),
                    category == selected && i == selectedMode,
                    Component.translatable(category.modeKey(i)), button -> {
                        selected = category;
                        selectedMode = mode;
                        preview.select(category, mode);
                        selectedAngle = null;
                        init();
                    }));
            }
        }
        int top = tileTop() + 30 + 3 * 28 + 5;
        if (selected == Category.ORTHOGRAPHIC) {
            for (int i = 0; i < CameraMath.Angle.values().length; i++) {
                var angle = CameraMath.Angle.values()[i];
                addRenderableWidget(Button.builder(Component.translatable(angle.key()).withColor(angle == selectedAngle ? 0x55FF55 : 0xFFFFFF), button -> {
                    selectedAngle = angle;
                    init();
                }).bounds(contentLeft() + i % 5 * 54, top + i / 5 * 22, 52, 20).build());
            }
            top += 46;
        }
        if (CameraSelection.detachedControls(selected, selectedMode)) {
            // 竖着落在固定机位、跟随视角两列，横着与第三人称正面同一行；这两列没有第三个子模式，不会和模式按钮重叠。
            int toggleY = tileTop() + 30 + 2 * 28;
            addToggle(Toggle.BODY_INTERACTION, tileLeft() + Category.FIXED.ordinal() * 31, toggleY);
            addToggle(Toggle.BODY_MOVEMENT, tileLeft() + Category.FOLLOW.ordinal() * 31, toggleY);
        }
        addRenderableWidget(new SolidButton(headerButtonX(), headerButtonY(), HEADER_BUTTON_SIZE, HEADER_BUTTON_SIZE,
            PixelGlyph.SETTING, Component.translatable("camera.archweaver.open_panel"), button -> {
            openPanel = true;
            commit();
        }).withoutFrame());
    }

    private void addToggle(Toggle toggle, int x, int y) {
        // 图标按钮不再显示文字，标签挪到提示的第一行。
        Component tooltip = toggleLabel(toggle).copy()
            .append("\n").append(Component.translatable("camera.archweaver.toggle." + toggle.key() + ".tooltip"));
        addRenderableWidget(new CameraToggleButton(x, y, toggleIcon(toggle), CameraPreferences.get(toggle), tooltip, button -> {
            CameraPreferences.flip(toggle);
            init();
        }));
    }

    private static Identifier toggleIcon(Toggle toggle) {
        return Identifier.fromNamespaceAndPath("archweaver", "textures/gui/camera/" + toggle.key() + ".png");
    }

    /** 开关的「标签：开/关」文本；用开关图形显示状态时只取 {@code camera.archweaver.toggle.<键>}。 */
    static Component toggleLabel(Toggle toggle) {
        return Component.translatable("camera.archweaver.toggle.state",
            Component.translatable("camera.archweaver.toggle." + toggle.key()),
            Component.translatable(CameraPreferences.get(toggle) ? "options.on" : "options.off"));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        // 面板与标题都取自原版 F3+F4 的游戏模式切换器，标题栏中央显示当前类别。
        new GameModePanel(panelLeft(), panelTop(), PANEL_WIDTH, panelBottom() - panelTop(),
            Component.translatable(selected.key())).draw(graphics, font);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        if (firstMouseX == Integer.MIN_VALUE) { firstMouseX = mouseX; firstMouseY = mouseY; }
        if (mouseX != firstMouseX || mouseY != firstMouseY) {
            for (Category category : Category.values()) {
                int x = tileLeft() + category.ordinal() * 31;
                if (mouseX >= x && mouseX < x + 26 && mouseY >= tileTop() && mouseY < tileTop() + 26) select(category);
            }
        }
        for (Category category : Category.values()) {
            int x = tileLeft() + category.ordinal() * 31;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, x, tileTop(), 26, 26);
            graphics.item(new ItemStack(ICONS[category.ordinal()]), x + 5, tileTop() + 5);
            if (category == selected) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SELECTED, x, tileTop(), 26, 26);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partial);
        graphics.centeredText(font, Component.translatable("camera.archweaver.selector_hint",
            keyName("F5"), keyName("F3"), keyName("Esc")), width / 2, hintY(), 0xFFFFFFFF);
    }

    /** 提示里的按键名：与原版 F3+F4 一样用青色标出。 */
    private static Component keyName(String name) {
        return Component.literal(name).withColor(KEY_COLOR);
    }

    private void select(Category value) {
        if (selected == value) return;
        selected = value;
        selectedMode = preview.mode(value);
        preview.select(selected, selectedMode);
        selectedAngle = null;
        init();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount == 0) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        preview.cycleView(verticalAmount < 0);
        selected = preview.category();
        selectedMode = preview.mode();
        selectedAngle = null;
        // 以滚动时的位置为基准，避免静止鼠标的悬停覆盖滚轮选择。
        firstMouseX = (int) mouseX;
        firstMouseY = (int) mouseY;
        init();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (minecraft.options.keyTogglePerspective.matches(event)) {
            select(selected.next());
            firstMouseX = Integer.MIN_VALUE;
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (minecraft.options.keyDebugModifier.matches(event)) { commit(); return true; }
        return super.keyReleased(event);
    }

    @Override
    public void tick() {
        // 窗口失焦或修饰键释放事件丢失时取消，避免留下卡住的临时界面。
        if (!minecraft.isWindowActive()) onClose();
    }

    private void commit() {
        minecraft.setScreen(null);
        if (ClientCamera.select(selected, selectedMode)) {
            if (selectedAngle != null) ClientCamera.angle(selectedAngle);
            if (openPanel) minecraft.setScreen(new CameraPanelScreen(false));
        }
    }

    @Override public boolean isPauseScreen() { return false; }
}
