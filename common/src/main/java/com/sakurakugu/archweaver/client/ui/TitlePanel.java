package com.sakurakugu.archweaver.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** 上方带标题栏、下方为内容区的像素风面板。 */
public final class TitlePanel {
    /** 所有标题面板统一使用的标题栏高度。 */
    public static final int HEADER_HEIGHT = 36;
    private static final int HEADER_COLOR = 0xFF373737;
    private static final int CONTENT_COLOR = 0xF0222528;
    private static final int BORDER_COLOR = 0xFF8B8B8B;
    private static final int SEPARATOR_HEIGHT = 1;

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final Component title;

    public TitlePanel(int x, int y, int width, int height, Component title) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.title = title;
    }

    /** 返回指定高度按钮在标题栏中的垂直居中位置。 */
    public int buttonY(int buttonHeight) {
        return y + Math.max(0, (HEADER_HEIGHT - buttonHeight) / 2);
    }

    public int leftButtonX() { return x + 16; }

    public int rightButtonX(int buttonWidth) { return x + width - 16 - buttonWidth; }

    /** 绘制面板背景、标题栏、分隔线和边框。 */
    public void draw(GuiGraphicsExtractor graphics, Font font) {
        int right = x + width;
        int bottom = y + height;
        graphics.fill(x, y, right, bottom, CONTENT_COLOR);
        graphics.fill(x, y, right, y + HEADER_HEIGHT, HEADER_COLOR);
        graphics.fill(x, y + HEADER_HEIGHT, right, y + HEADER_HEIGHT + SEPARATOR_HEIGHT, BORDER_COLOR);
        graphics.outline(x, y, width, height, BORDER_COLOR);
        int titleY = y + Math.max(0, (HEADER_HEIGHT - font.lineHeight) / 2);
        graphics.centeredText(font, title, x + width / 2, titleY, 0xFFFFFFFF);
    }
}
