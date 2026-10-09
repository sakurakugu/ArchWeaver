package com.sakurakugu.archweaver.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** 使用纯色像素风边框的按钮。 */
public final class SolidButton extends Button {
    private static final int DIM_COLOR = 0xFF8B8B8B; // 无底板时的默认图标色，与像素风边框同色。
    private static final int BRIGHT_COLOR = 0xFFFFFFFF; // 无底板时鼠标悬停的图标色。
    private final PixelGlyph glyph; // 按钮中央绘制的像素图标；为 null 时改为绘制文字。
    private int textColor = 0xFFFFFFFF; // 文字或图标的颜色，默认不透明白色。
    private boolean framed = true; // 是否绘制像素风底板；无底板时只保留图标。

    public SolidButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        glyph = null;
    }

    public SolidButton(int x, int y, int width, int height, PixelGlyph glyph,
                         Component tooltip, OnPress onPress) {
        super(x, y, width, height, tooltip, onPress, DEFAULT_NARRATION);
        this.glyph = glyph;
        setTooltip(Tooltip.create(tooltip));
    }

    /** 设置文字颜色，用于按状态切换红/绿/白等反馈；底色保持不变。 */
    public void setTextColor(int textColor) {
        this.textColor = textColor;
    }

    /** 去掉像素风底板，只留中间的图标：平时暗色，鼠标悬停变亮。 */
    public SolidButton withoutFrame() {
        framed = false;
        return this;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = isMouseOver(mouseX, mouseY);
        if (framed) PixelGui.drawSolidControl(graphics, getX(), getY(), getWidth(), getHeight(), hovered);
        int color = active ? (framed ? textColor : hovered ? BRIGHT_COLOR : DIM_COLOR) : 0xFF777777;
        if (glyph == null) {
            PixelGui.drawCenteredScrollingText(graphics, Minecraft.getInstance().font, getMessage(),
                getX(), getY(), getWidth(), getHeight(), color);
        } else {
            glyph.drawCentered(graphics, getX(), getY(), getWidth(), getHeight(), color);
        }
    }
}
