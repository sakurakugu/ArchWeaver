package com.sakurakugu.archweaver.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * 原版 F3+F4 游戏模式切换器的面板样式：不透明标题栏加半透明内容区。
 * 直接拉伸原版贴图，标题栏高度、边框和圆角都与原版一致。
 */
public final class GameModePanel {
    /** 标题栏高度（像素），与原版一致。 */
    public static final int HEADER_HEIGHT = 22;
    private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/gamemode_switcher.png");
    private static final int TEXTURE_SIZE = 128; // 原版贴图为 128×128，实际只用到左上角 125×75。
    private static final int USED_WIDTH = 125; // 贴图使用区域的宽度（像素）。
    private static final int CORNER = 4; // 左右边框列宽（像素），中间列横向拉伸。
    private static final int FOOTER_TOP = 72; // 内容区底角圆角的起始行。
    private static final int FOOTER = 3; // 底角圆角行高（像素），不参与纵向拉伸。

    private final int x; // 面板左上角的横坐标（屏幕像素）。
    private final int y; // 面板左上角的纵坐标（屏幕像素）。
    private final int width; // 面板宽度（像素）。
    private final int height; // 面板高度（像素）。
    private final Component title; // 显示在标题栏中央的标题文本。

    public GameModePanel(int x, int y, int width, int height, Component title) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.title = title;
    }

    /** 绘制边框、标题栏和内容区，标题文字画在标题栏中央。 */
    public void draw(GuiGraphicsExtractor graphics, Font font) {
        int right = x + width;
        int bottom = y + height;
        int middle = width - CORNER * 2;
        // 标题栏高度固定，只有中间列横向拉伸。
        drawSlice(graphics, x, y, CORNER, HEADER_HEIGHT, 0, 0, CORNER, HEADER_HEIGHT);
        drawSlice(graphics, right - CORNER, y, CORNER, HEADER_HEIGHT, USED_WIDTH - CORNER, 0, CORNER, HEADER_HEIGHT);
        drawSlice(graphics, x + CORNER, y, middle, HEADER_HEIGHT, CORNER, 0, USED_WIDTH - CORNER * 2, HEADER_HEIGHT);
        // 内容区：中间行纵向拉伸，底角圆角保持原样。
        int bodyTop = y + HEADER_HEIGHT;
        int bodyHeight = bottom - FOOTER - bodyTop;
        if (bodyHeight < 1) return; // 面板比标题栏还矮时只画标题栏，避免拉伸错位。
        int stretch = FOOTER_TOP - HEADER_HEIGHT; // 贴图中参与纵向拉伸的行数。
        drawSlice(graphics, x, bodyTop, CORNER, bodyHeight, 0, HEADER_HEIGHT, CORNER, stretch);
        drawSlice(graphics, right - CORNER, bodyTop, CORNER, bodyHeight, USED_WIDTH - CORNER, HEADER_HEIGHT, CORNER, stretch);
        drawSlice(graphics, x + CORNER, bodyTop, middle, bodyHeight, CORNER, HEADER_HEIGHT, USED_WIDTH - CORNER * 2, stretch);
        drawSlice(graphics, x, bottom - FOOTER, CORNER, FOOTER, 0, FOOTER_TOP, CORNER, FOOTER);
        drawSlice(graphics, right - CORNER, bottom - FOOTER, CORNER, FOOTER, USED_WIDTH - CORNER, FOOTER_TOP, CORNER, FOOTER);
        drawSlice(graphics, x + CORNER, bottom - FOOTER, middle, FOOTER, CORNER, FOOTER_TOP, USED_WIDTH - CORNER * 2, FOOTER);
        graphics.centeredText(font, title, x + width / 2, y + (HEADER_HEIGHT - font.lineHeight) / 2 + 1, 0xFFFFFFFF);
    }

    /** 把贴图中 (sx, sy) 起的 sw×sh 区域拉伸到以 (dx, dy) 为左上角的 dw×dh 矩形。 */
    private static void drawSlice(
        GuiGraphicsExtractor graphics, int dx, int dy, int dw, int dh, int sx, int sy, int sw, int sh
    ) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, dx, dy, sx, sy, dw, dh, sw, sh, TEXTURE_SIZE, TEXTURE_SIZE);
    }
}
