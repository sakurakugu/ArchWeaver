package com.sakurakugu.archweaver.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** 相机开关按钮：常驻原版槽位外框，开启时中间的图标染成白色、关闭时压暗，悬停时外框高亮。 */
public final class CameraToggleButton extends Button {
    private static final Identifier SLOT = Identifier.withDefaultNamespace("gamemode_switcher/slot");
    private static final Identifier SELECTED = Identifier.withDefaultNamespace("gamemode_switcher/selection");
    private static final int SIZE = 26; // 按钮边长（像素），与模式按钮一致。
    private static final int ICON_INSET = 3; // 图标相对槽位外框的内缩（像素）。
    private static final int ICON_SIZE = SIZE - ICON_INSET * 2; // 图标边长（像素）。
    private static final int ON_COLOR = 0xFFFFFFFF; // 开启状态图标染成的白色，与关闭态拉开对比。
    private static final int OFF_COLOR = 0xFF8B8B8B; // 关闭状态图标的暗灰色，与 PixelGui 的边框同色。
    private final Identifier icon;
    private final boolean enabled;

    public CameraToggleButton(int x, int y, Identifier icon, boolean enabled, Component tooltip, OnPress onPress) {
        super(x, y, SIZE, SIZE, tooltip, onPress, DEFAULT_NARRATION);
        this.icon = icon;
        this.enabled = enabled;
        setTooltip(Tooltip.create(tooltip));
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, getX(), getY(), SIZE, SIZE);
        graphics.blit(RenderPipelines.GUI_TEXTURED, icon, getX() + ICON_INSET, getY() + ICON_INSET, 0.0F, 0.0F,
            ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE, enabled ? ON_COLOR : OFF_COLOR);
        if (isMouseOver(mouseX, mouseY)) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SELECTED, getX(), getY(), SIZE, SIZE);
        }
    }
}
