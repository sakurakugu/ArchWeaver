package com.sakurakugu.archweaver.client.camera;

import com.sakurakugu.archweaver.client.camera.CameraPreferences.Toggle;
import com.sakurakugu.archweaver.client.camera.CameraSelection.Category;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
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
    private static final Item[] ICONS = {Items.SPYGLASS, Items.ENDER_EYE, Items.PAPER, Items.BOW, Items.COMPASS, Items.TRIPWIRE_HOOK, Items.PLAYER_HEAD};
    private final CameraSelection preview = ClientCamera.selectionSnapshot();
    private Category selected = preview.category();
    private int selectedMode = preview.mode();
    private CameraMath.Angle selectedAngle;
    private boolean openPanel;
    private int firstMouseX = Integer.MIN_VALUE, firstMouseY;

    public CameraSelectorScreen() { super(Component.translatable("camera.archweaver.selector")); }
    private int tileTop() { return Math.max(28, height / 2 - 85); }
    private int tileLeft() { return width / 2 - 106; }
    private int contentLeft() { return width / 2 - 135; }

    @Override
    protected void init() {
        clearWidgets();
        int top = tileTop() + 35;
        int modeWidth = Math.min(130, 270 / selected.modes());
        for (int i = 0; i < selected.modes(); i++) {
            int mode = i;
            addRenderableWidget(Button.builder(Component.translatable(selected.modeKey(i)).withColor(i == selectedMode ? 0x55FF55 : 0xFFFFFF), button -> {
                selectedMode = mode;
                preview.select(selected, selectedMode);
                init();
            }).bounds(width / 2 - modeWidth * selected.modes() / 2 + i * modeWidth, top, modeWidth - 2, 20).build());
        }
        top += 25;
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
            addToggle(Toggle.BODY_INTERACTION, contentLeft(), top);
            addToggle(Toggle.BODY_MOVEMENT, contentLeft() + 136, top);
            top += 24;
        }
        addRenderableWidget(Button.builder(Component.translatable("camera.archweaver.open_panel"), button -> {
            openPanel = true;
            commit();
        }).bounds(width / 2 - 65, top, 130, 20).build());
    }

    private void addToggle(Toggle toggle, int x, int y) {
        var button = Button.builder(toggleLabel(toggle), b -> {
            CameraPreferences.flip(toggle);
            b.setMessage(toggleLabel(toggle));
        }).bounds(x, y, 134, 20).build();
        button.setTooltip(Tooltip.create(Component.translatable("camera.archweaver.toggle." + toggle.key() + ".tooltip")));
        addRenderableWidget(button);
    }

    static Component toggleLabel(Toggle toggle) {
        return Component.translatable("camera.archweaver.toggle." + toggle.key(), Component.translatable(CameraPreferences.get(toggle) ? "options.on" : "options.off"));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        graphics.fill(contentLeft() - 7, tileTop() - 24, contentLeft() + 277, Math.min(height, tileTop() + 178), 0xDA202020);
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
        graphics.centeredText(font, Component.translatable(selected.key()), width / 2, tileTop() - 18, 0xFFFFFFFF);
        for (Category category : Category.values()) {
            int x = tileLeft() + category.ordinal() * 31;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, x, tileTop(), 26, 26);
            graphics.item(new ItemStack(ICONS[category.ordinal()]), x + 5, tileTop() + 5);
            if (category == selected) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SELECTED, x, tileTop(), 26, 26);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partial);
        graphics.centeredText(font, Component.translatable("camera.archweaver.selector_hint"), width / 2,
            Math.min(height - 12, tileTop() + 158), 0xFFAAAAAA);
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
