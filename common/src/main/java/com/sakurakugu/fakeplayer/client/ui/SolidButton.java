package com.sakurakugu.fakeplayer.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** 使用纯色像素风边框的按钮。 */
public final class SolidButton extends Button {
    private final PixelGlyph glyph;
    private int textColor = 0xFFFFFFFF;

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

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        PixelGui.drawSolidControl(graphics, getX(), getY(), getWidth(), getHeight(), isMouseOver(mouseX, mouseY));
        int color = active ? textColor : 0xFF777777;
        if (glyph == null) {
            PixelGui.drawCenteredText(graphics, Minecraft.getInstance().font, getMessage(),
                getX(), getY(), getWidth(), getHeight(), color);
        } else {
            glyph.drawCentered(graphics, getX(), getY(), getWidth(), getHeight(), color);
        }
    }
}
