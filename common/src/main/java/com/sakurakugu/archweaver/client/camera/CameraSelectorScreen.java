package com.sakurakugu.archweaver.client.camera;

import com.sakurakugu.archweaver.client.camera.CameraPreferences.Toggle;
import com.sakurakugu.archweaver.client.camera.CameraSelection.Category;
import com.sakurakugu.archweaver.client.ui.GameModePanel;
import com.sakurakugu.archweaver.client.ui.PixelGlyph;
import com.sakurakugu.archweaver.client.ui.CameraModeButton;
import com.sakurakugu.archweaver.client.ui.CameraToggleButton;
import com.sakurakugu.archweaver.client.ui.SolidButton;
import com.sakurakugu.archweaver.client.ui.ViewCube;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Vector3d;

/** F3+F5 的临时选择器，松开调试修饰键才提交视角和角度。 */
public final class CameraSelectorScreen extends Screen {
    private static final Identifier SLOT = Identifier.withDefaultNamespace("gamemode_switcher/slot");
    private static final Identifier SELECTED = Identifier.withDefaultNamespace("gamemode_switcher/selection");
    private static final int TILE_SIZE = 26; // 类别图标方块的边长（像素）。
    private static final int TILE_PITCH = 31; // 相邻两个类别图标的间距（像素）。
    private static final int PANEL_INSET = 7; // 面板左右边框到内容列的间距（像素）。
    private static final int PANEL_TOP_GAP = 24; // 面板顶边到图标行的距离（像素），正好容下标题栏。
    private static final int HINT_GAP = 10; // 内容区下边到提示文字的距离（像素），与原版 F3+F4 一致。
    private static final int PANEL_BOTTOM_GAP = 3; // 提示文字下边到面板底边的距离（像素），与原版 F3+F4 一致。
    private static final int HEADER_BUTTON_SIZE = 20; // 标题栏右侧按钮的边长（像素）。
    private static final int HEADER_BUTTON_MARGIN = 16; // 标题栏右侧按钮到面板右边框的距离（像素）。
    private static final int KEY_COLOR = 0xFF55FFFF; // 提示里按键名的青色，与原版 F3+F4 的提示一致。
    private static final int CUBE_SIZE = 56; // 立方体占用右侧两列，包含角点的命中留白。
    private static final int CUBE_TOP = 58; // 从图标行起算，位于右侧两个子模式按钮下方。
    private static final Item[] ICONS = {Items.SPYGLASS, Items.ENDER_EYE, Items.PAPER, Items.BOW, Items.COMPASS, Items.TRIPWIRE_HOOK, Items.PLAYER_HEAD};
    private static final String[][] MODE_ICONS = {
        {"vanilla_first_person", "vanilla_second_person", "vanilla_third_person"},
        {"free"}, {"orthographic_follow_player", "orthographic_free_camera"},
        {"shoulder_left", "shoulder_right"}, {"orbit"}, {"fixed"}, {"follow"}
    };
    private final CameraSelection preview = ClientCamera.selectionSnapshot();
    private Category selected = preview.category();
    private int selectedMode = preview.mode();
    private final OrthographicPreview rotationPreview = ClientCamera.createSelectorPreview();
    private boolean previewInitialized;
    private ViewCube cube;
    private int firstMouseX = Integer.MIN_VALUE, firstMouseY;

    public CameraSelectorScreen() { super(Component.translatable("camera.archweaver.selector")); }
    private int tileTop() { return Math.max(24, height / 2 - 105); }
    /** 图标行的宽度：面板至少要放得下它。 */
    private int tileRowWidth() { return (Category.values().length - 1) * TILE_PITCH + TILE_SIZE; }
    private int contentLeft() { return width / 2 - tileRowWidth() / 2; }
    /** 图标行与底部提示取宽的那个，英文提示比图标行还长，不能让它顶出面板边框。 */
    private int panelWidth() { return Math.max(tileRowWidth(), font.width(hint())) + PANEL_INSET * 2; }
    private int panelLeft() { return contentLeft() - PANEL_INSET; }
    private int panelTop() { return tileTop() - PANEL_TOP_GAP; }
    /** 最后一行内容的下边：正交立方体与子模式按钮共用内容区。 */
    private int contentBottom() {
        int modes = tileTop() + 30 + 2 * 28 + TILE_SIZE;
        return selected == Category.ORTHOGRAPHIC ? tileTop() + CUBE_TOP + CUBE_SIZE : modes;
    }
    private int hintY() { return Math.min(height - 12, contentBottom() + HINT_GAP); }
    private int panelBottom() { return Math.min(height, hintY() + font.lineHeight + PANEL_BOTTOM_GAP); }
    private int headerButtonX() { return panelLeft() + panelWidth() - HEADER_BUTTON_MARGIN - HEADER_BUTTON_SIZE; }
    private int headerButtonY() { return panelTop() + (GameModePanel.HEADER_HEIGHT - HEADER_BUTTON_SIZE) / 2; }

    @Override
    protected void init() {
        clearWidgets();
        cube = null;
        updatePreview();
        for (Category category : Category.values()) {
            int x = contentLeft() + category.ordinal() * TILE_PITCH;
            for (int i = 0; i < category.modes(); i++) {
                int mode = i;
                addRenderableWidget(new CameraModeButton(x, tileTop() + 30 + i * 28,
                    Identifier.fromNamespaceAndPath("archweaver", "textures/gui/camera/" + MODE_ICONS[category.ordinal()][i] + ".png"),
                    category == selected && i == selectedMode,
                    Component.translatable(category.modeKey(i)), button -> {
                        selected = category;
                        selectedMode = mode;
                        preview.select(category, mode);
                        init();
                    }));
            }
        }
        if (selected == Category.ORTHOGRAPHIC) {
            cube = addRenderableWidget(new ViewCube(contentLeft() + Category.FIXED.ordinal() * TILE_PITCH,
                tileTop() + CUBE_TOP, CUBE_SIZE,
                rotationPreview::yaw, rotationPreview::pitch, rotationPreview::rotation));
        }
        if (selected != Category.ORTHOGRAPHIC && CameraSelection.detachedControls(selected, selectedMode)) {
            // 竖着落在固定机位、跟随视角两列，横着与第三人称正面同一行；这两列没有第三个子模式，不会和模式按钮重叠。
            int toggleY = tileTop() + 30 + 2 * 28;
            addToggle(Toggle.BODY_INTERACTION, contentLeft() + Category.FIXED.ordinal() * TILE_PITCH, toggleY);
            addToggle(Toggle.BODY_MOVEMENT, contentLeft() + Category.FOLLOW.ordinal() * TILE_PITCH, toggleY);
        }
        addRenderableWidget(new SolidButton(headerButtonX(), headerButtonY(), HEADER_BUTTON_SIZE, HEADER_BUTTON_SIZE,
            PixelGlyph.SETTING, Component.translatable("camera.archweaver.open_panel"),
            button -> openPanel()).withoutFrame());
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
        new GameModePanel(panelLeft(), panelTop(), panelWidth(), panelBottom() - panelTop(),
            Component.translatable(selected.key())).draw(graphics, font);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        if (firstMouseX == Integer.MIN_VALUE) { firstMouseX = mouseX; firstMouseY = mouseY; }
        if ((cube == null || !cube.pressed()) && (mouseX != firstMouseX || mouseY != firstMouseY)) {
            for (Category category : Category.values()) {
                int x = contentLeft() + category.ordinal() * TILE_PITCH;
                if (mouseX >= x && mouseX < x + TILE_SIZE && mouseY >= tileTop() && mouseY < tileTop() + TILE_SIZE) select(category);
            }
        }
        for (Category category : Category.values()) {
            int x = contentLeft() + category.ordinal() * TILE_PITCH;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, x, tileTop(), TILE_SIZE, TILE_SIZE);
            graphics.item(new ItemStack(ICONS[category.ordinal()]), x + 5, tileTop() + 5);
            if (category == selected) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SELECTED, x, tileTop(), TILE_SIZE, TILE_SIZE);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partial);
        graphics.centeredText(font, hint(), width / 2, hintY(), 0xFFFFFFFF);
    }

    /** 底部提示：松开 F3 确认、F5 换类别、Esc 取消；面板宽度要放得下它。 */
    private Component hint() {
        return Component.translatable("camera.archweaver.selector_hint", keyName("F5"), keyName("F3"), keyName("Esc"));
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
        init();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (cube != null && cube.pressed()) return true;
        if (verticalAmount == 0) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        preview.cycleView(verticalAmount < 0);
        selected = preview.category();
        selectedMode = preview.mode();
        // 以滚动时的位置为基准，避免静止鼠标的悬停覆盖滚轮选择。
        firstMouseX = (int) mouseX;
        firstMouseY = (int) mouseY;
        init();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (minecraft.options.keyTogglePerspective.matches(event)) {
            if (cube != null && cube.pressed()) return true;
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

    /** 松开 F3 才提交选中的视角与角度。 */
    private void commit() {
        minecraft.setScreen(null);
        if (selected == Category.ORTHOGRAPHIC) ClientCamera.commitSelectorPreview(rotationPreview);
        else ClientCamera.select(selected, selectedMode);
    }

    /** 标题栏的齿轮：按 Esc 的方式退出，预览不提交，再打开相机面板，防止误触改掉当前视角。 */
    private void openPanel() {
        minecraft.setScreen(null);
        minecraft.setScreen(new CameraPanelScreen(false));
    }

    private void updatePreview() {
        if (selected != Category.ORTHOGRAPHIC || minecraft.player == null || minecraft.level == null) {
            ClientCamera.selectorPreview(null);
            return;
        }
        if (!previewInitialized) {
            rotationPreview.initialMode(selectedMode);
            previewInitialized = true;
        } else {
            var eye = minecraft.player.getEyePosition();
            rotationPreview.mode(selectedMode, new Vector3d(eye.x, eye.y, eye.z));
        }
        ClientCamera.selectorPreview(rotationPreview);
    }

    @Override
    public void removed() {
        ClientCamera.selectorPreview(null);
        super.removed();
    }

    @Override public boolean isPauseScreen() { return false; }
}
