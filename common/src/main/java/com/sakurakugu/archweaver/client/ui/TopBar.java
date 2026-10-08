package com.sakurakugu.archweaver.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** 容器顶部的侧栏式横条，样式与左右侧栏标签一致；目前只在其中放玩家/玩偶二态切换。 */
public final class TopBar {
    public static final int HEIGHT = 24; // 横条高度（像素）。
    private static final int PADDING = 3; // 内部控件与横条边框之间的内边距（像素）。
    private static final int OFFSET_X = -3; // 相对容器右半边的横向微调（像素），右边缘与操控按钮列对齐。
    private static final int OFFSET_Y = 1; // 相对容器顶边的纵向微调（像素），横条底边因此压住容器上沿。

    private final int x; // 横条左上角的横坐标（屏幕像素）。
    private final int y; // 横条左上角的纵坐标（屏幕像素）。
    private final int width; // 横条宽度（像素）。

    public TopBar(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    /** 压在容器右上操控区上方的横条：只占右半边，再按微调量偏移。 */
    public static TopBar overRightHalf(int containerLeft, int containerTop, int containerWidth) {
        int half = containerWidth / 2;
        return new TopBar(containerLeft + half + OFFSET_X, topAbove(containerTop) + OFFSET_Y,
            containerWidth - half);
    }

    /** 紧贴容器顶边上方的横条纵坐标。 */
    public static int topAbove(int containerTop) {
        return containerTop - HEIGHT;
    }

    /** 绘制横条背景；底边不描边，与下方容器连成一片。 */
    public void draw(GuiGraphicsExtractor graphics) {
        PixelGui.drawTopTabBackground(graphics, x, y, width, HEIGHT);
    }

    public int contentX() { return x + PADDING; }

    public int contentY() { return y + PADDING; }

    public int contentWidth() { return Math.max(1, width - PADDING * 2); }

    public int contentHeight() { return HEIGHT - PADDING * 2; }
}
