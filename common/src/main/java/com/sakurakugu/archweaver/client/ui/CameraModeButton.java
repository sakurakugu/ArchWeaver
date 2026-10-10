package com.sakurakugu.archweaver.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** 相机选择器中使用图标和物品栏外框的模式按钮。 */
public final class CameraModeButton extends Button {
    private static final Identifier SLOT = Identifier.withDefaultNamespace("gamemode_switcher/slot");
    private static final Identifier SELECTED = Identifier.withDefaultNamespace("gamemode_switcher/selection");
    private final Identifier icon;
    private final boolean selected;

    public CameraModeButton(int x, int y, Identifier icon, boolean selected, Component tooltip, OnPress onPress) {
        super(x, y, 26, 26, tooltip, onPress, DEFAULT_NARRATION);
        this.icon = icon;
        this.selected = selected;
        setTooltip(net.minecraft.client.gui.components.Tooltip.create(tooltip));
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, getX(), getY(), 26, 26);
        graphics.blit(RenderPipelines.GUI_TEXTURED, icon, getX() + 3, getY() + 3,
            0.0F, 0.0F, 20, 20, 20, 20);
        if (active && (selected || isMouseOver(mouseX, mouseY))) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SELECTED, getX(), getY(), 26, 26);
        }
        if (!active) graphics.fill(getX(), getY(), getX() + 26, getY() + 26, 0xA0404040);
    }
}
