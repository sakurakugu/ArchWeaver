package com.sakurakugu.archweaver.client;

import com.sakurakugu.archweaver.menu.GlobalFakePlayerMenu;
import com.sakurakugu.archweaver.network.SpawnFakePlayerPayload;
import com.sakurakugu.archweaver.platform.PlatformNetworking;
import com.sakurakugu.archweaver.client.ui.SolidButton;
import com.sakurakugu.archweaver.client.ui.PixelGlyph;
import com.sakurakugu.archweaver.client.ui.TitlePanel;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** 显示假人生成页。 */
public final class GlobalFakePlayerScreen extends ResponsiveContainerScreen<GlobalFakePlayerMenu> {
    private static final int PANEL_WIDTH = 300; // 页面主面板宽度，单位为像素。
    private static final int PANEL_HEIGHT = 240; // 页面主面板高度，单位为像素。
    private static final int BUTTON_HEIGHT = 24; // 生成按钮高度，单位为像素。
    private EditBox nameInput; // 假人名称输入框，最长 16 个字符。
    private Button spawnButton; // 生成假人按钮，名称合法时才可点击。

    public GlobalFakePlayerScreen(GlobalFakePlayerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        rebuildButtons();
    }

    private void rebuildButtons() {
        clearWidgets();
        nameInput = null;
        spawnButton = null;
        int margin = size(50);
        int fieldWidth = Math.max(1, responsiveWidth() - margin * 2);
        nameInput = addRenderableWidget(new EditBox(
            font, leftPos + margin, topPos + s(86), fieldWidth, size(22),
            Component.translatable("gui.fakeplayer.global.spawn_name")
        ));
        nameInput.setMaxLength(16);
        nameInput.setHint(Component.translatable("gui.fakeplayer.global.spawn_name"));
        nameInput.setResponder(value -> updateSpawnButton());
        spawnButton = addRenderableWidget(
            new SolidButton(leftPos + margin, topPos + s(120), fieldWidth, size(BUTTON_HEIGHT),
                Component.translatable("gui.fakeplayer.global.spawn"), button -> submitSpawn())
        );
        updateSpawnButton();
        addRenderableWidget(new SolidButton(titlePanel().leftButtonX(), titlePanel().buttonY(18), 18, 18,
            PixelGlyph.BACK, Component.translatable("gui.back"), button ->
                ClientScreenNavigation.back(this)));
        setInitialFocus(nameInput);
    }

    @Override
    public void onClose() {
        ClientScreenNavigation.back(this);
    }

    private void updateSpawnButton() {
        if (spawnButton != null && nameInput != null) {
            spawnButton.active = nameInput.getValue().matches("[A-Za-z0-9_-]{1,16}");
        }
    }

    private void submitSpawn() {
        if (spawnButton != null && spawnButton.active && nameInput != null) {
            PlatformNetworking.sendToServer(new SpawnFakePlayerPayload(menu.containerId, nameInput.getValue()));
            spawnButton.active = false;
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (!ClientScreenNavigation.extractBackground(this, graphics, partialTick)) {
            graphics.fill(0, 0, width, height, 0xFF22282C);
        }
        Component pageTitle = Component.translatable("gui.fakeplayer.global.spawn_title");
        new TitlePanel(leftPos, topPos, responsiveWidth(), responsiveHeight(), pageTitle).draw(graphics, font);
    }

    /** 生成页使用自绘标题，隐藏容器页面默认的标题和玩家物品栏标签。 */
    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
    }

    private TitlePanel titlePanel() {
        return new TitlePanel(leftPos, topPos, responsiveWidth(), responsiveHeight(),
            Component.translatable("gui.fakeplayer.global.spawn_title"));
    }
}
