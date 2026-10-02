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
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** 显示假人生成页。 */
public final class GlobalFakePlayerScreen extends AbstractContainerScreen<GlobalFakePlayerMenu> {
    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 240;
    private static final int BUTTON_HEIGHT = 24;
    private EditBox nameInput;
    private Button spawnButton;

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
        nameInput = addRenderableWidget(new EditBox(
            font, leftPos + 50, topPos + 86, PANEL_WIDTH - 100, 22,
            Component.translatable("gui.fakeplayer.global.spawn_name")
        ));
        nameInput.setMaxLength(16);
        nameInput.setHint(Component.translatable("gui.fakeplayer.global.spawn_name"));
        nameInput.setResponder(value -> updateSpawnButton());
        spawnButton = addRenderableWidget(
            new SolidButton(leftPos + 50, topPos + 120, PANEL_WIDTH - 100, BUTTON_HEIGHT,
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
            super.extractBackground(graphics, mouseX, mouseY, partialTick);
        }
        Component pageTitle = Component.translatable("gui.fakeplayer.global.spawn_title");
        new TitlePanel(leftPos, topPos, PANEL_WIDTH, PANEL_HEIGHT, pageTitle).draw(graphics, font);
    }

    private TitlePanel titlePanel() {
        return new TitlePanel(leftPos, topPos, PANEL_WIDTH, PANEL_HEIGHT,
            Component.translatable("gui.fakeplayer.global.spawn_title"));
    }
}
