package com.sakurakugu.archweaver.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/** 像素风控件共用的绘制方法。 */
public final class PixelGui {
    private PixelGui() {
    }

    public static void drawSolidControl(
        GuiGraphicsExtractor graphics, int x, int y, int width, int height, boolean hovered
    ) {
        int right = x + width;
        int bottom = y + height;
        graphics.fill(x, y, right, bottom, hovered ? 0xFFFFFFFF : 0xFF373737);
        graphics.fill(x + 1, y + 1, right - 1, bottom - 1, 0xFF8B8B8B);
        graphics.fill(x + 1, y + 1, right - 1, y + 2, 0xFFAAAAAA);
        graphics.fill(x + 1, y + 1, x + 2, bottom - 1, 0xFFAAAAAA);
        graphics.fill(x + 1, bottom - 2, right - 1, bottom - 1, 0xFF565656);
        graphics.fill(right - 2, y + 1, right - 1, bottom - 1, 0xFF565656);
        graphics.fill(x + 1, bottom - 2, x + 2, bottom - 1, 0xFF8B8B8B);
        graphics.fill(right - 2, y + 1, right - 1, y + 2, 0xFF8B8B8B);
    }

    /**
     * 绘制地图顶部多档开关，左右位置使用灰阶滑块区分。
     * segmentCount 为档位数量，selectedIndex 为选中档，hoveredIndex 为指针所在档（-1 表示未悬停）。
     */
    public static void drawLargeSwitch(
        GuiGraphicsExtractor graphics, int x, int y, int width, int height,
        int segmentCount, int selectedIndex, int hoveredIndex
    ) {
        // 悬停时只高亮鼠标所在档，轨道边框保持原色。
        drawSolidControl(graphics, x, y, width, height, false);
        int right = x + width;
        int bottom = y + height;

        // 轨道只保留最外侧边框，避免手柄两端和档位分界处出现空隙。
        int trackLeft = x + 1;
        int trackTop = y + 1;
        int trackRight = right - 1;
        int trackBottom = bottom - 1;
        graphics.fill(trackLeft, trackTop, trackRight, trackBottom, 0xFF565F5B);
        graphics.fill(trackLeft, trackTop, trackRight, trackTop + 1, 0xFF111514);
        graphics.fill(trackLeft, trackTop, trackLeft + 1, trackBottom, 0xFF111514);
        graphics.fill(trackLeft, trackBottom - 1, trackRight, trackBottom, 0xFFA5AEAA);
        graphics.fill(trackRight - 1, trackTop, trackRight, trackBottom, 0xFFA5AEAA);

        // 手柄直接占满选中档，开关端点及档位分界处都不额外缩进。
        Segment handle = segment(x, width, segmentCount, selectedIndex);
        drawSolidControl(graphics, handle.left(), y, handle.width(), height,
            hoveredIndex == selectedIndex);

        // 指针停在未选中的档位时，用白色描边预览点击后将选中的档位。
        if (hoveredIndex >= 0 && hoveredIndex != selectedIndex) {
            Segment hover = segment(x, width, segmentCount, hoveredIndex);
            graphics.fill(hover.left(), y, hover.right(), y + 1, 0xFFFFFFFF);
            graphics.fill(hover.left(), bottom - 1, hover.right(), bottom, 0xFFFFFFFF);
            graphics.fill(hover.left(), y + 1, hover.left() + 1, bottom - 1, 0xFFFFFFFF);
            graphics.fill(hover.right() - 1, y + 1, hover.right(), bottom - 1, 0xFFFFFFFF);
        }
    }

    /** 多档开关中某一档的水平范围。 */
    public record Segment(int left, int right) {
        public int width() {
            return right - left;
        }
    }

    /** 多档开关第 index 档的范围，各档按档位数量均分。 */
    public static Segment segment(int x, int width, int segmentCount, int index) {
        return new Segment(x + width * index / segmentCount,
            x + width * (index + 1) / segmentCount);
    }

    /** 绘制原版物品栏槽位使用的凹陷边框。 */
    public static void drawInventorySlotBackground(
        GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color
    ) {
        int right = x + width;
        int bottom = y + height;
        graphics.fill(x, y, right, y + 1, 0xFF373737);
        graphics.fill(x, y + 1, x + 1, bottom, 0xFF373737);
        graphics.fill(x + 1, bottom - 1, right, bottom, 0xFFFFFFFF);
        graphics.fill(right - 1, y + 1, right, bottom, 0xFFFFFFFF);
        graphics.fill(x + 1, y + 1, right - 1, bottom - 1, color);
        graphics.fill(x, bottom - 1, x + 1, bottom, 0xFF8B8B8B);
        graphics.fill(right - 1, y, right, y + 1, 0xFF8B8B8B);
    }

    public static void drawToggle(
        GuiGraphicsExtractor graphics, int x, int y, boolean enabled, boolean hovered
    ) {
        int right = x + ToggleSwitchButton.SWITCH_WIDTH;
        int bottom = y + ToggleSwitchButton.SWITCH_HEIGHT;
        graphics.fill(x, y, right, bottom, hovered ? 0xFFFFFFFF : 0xFF373737);
        graphics.fill(x + 1, y + 1, right - 1, bottom - 1, enabled ? 0xFF36B54A : 0xFF565656);
        int handleLeft = enabled ? right - ToggleSwitchButton.HANDLE_WIDTH - 2 : x + 2;
        graphics.fill(handleLeft, y + 2, handleLeft + ToggleSwitchButton.HANDLE_WIDTH, bottom - 2, 0xFFFFFFFF);
        graphics.fill(handleLeft + 1, y + 3,
            handleLeft + ToggleSwitchButton.HANDLE_WIDTH - 1, bottom - 3, 0xFFC6C6C6);
    }

    public static void drawCenteredText(
        GuiGraphicsExtractor graphics, Font font, Component message,
        int x, int y, int width, int height, int color
    ) {
        graphics.text(font, message, x + (width - font.width(message)) / 2, y + (height - 8) / 2,
            color, false);
    }

    /** 文字放得下时居中显示，超出区域时改用滚动显示，避免文字被控件边缘裁断。 */
    public static void drawCenteredScrollingText(
        GuiGraphicsExtractor graphics, Font font, Component text,
        int x, int y, int width, int height, int color
    ) {
        if (font.width(text) <= width) {
            drawCenteredText(graphics, font, text, x, y, width, height, color);
        } else {
            drawScrollingText(graphics, font, text, x, x + width, y, height, color);
        }
    }

    /** 使用原版按钮相同的滚动曲线，但关闭文字阴影。 */
    public static void drawScrollingText(
        GuiGraphicsExtractor graphics, Font font, Component text,
        int left, int right, int top, int height, int color
    ) {
        int overflow = font.width(text) - (right - left);
        double time = Util.getMillis() / 1000.0;
        double period = Math.max(overflow * 0.5, 3.0);
        double alpha = Math.sin((Math.PI / 2) * Math.cos((Math.PI * 2) * time / period)) / 2.0 + 0.5;
        int offset = (int) (alpha * overflow);
        graphics.enableScissor(left, top, right, top + height);
        graphics.text(font, text, left - offset, top + (height - 8) / 2, color, false);
        graphics.disableScissor();
    }

    /** 左侧留出连接边，供从容器右侧展开的标签使用。 */
    public static void drawRightTabBackground(
        GuiGraphicsExtractor graphics, int left, int top, int width, int height
    ) {
        int right = left + width;
        int bottom = top + height;
        graphics.fill(left, top, right - 2, top + 1, 0xFF000000);
        graphics.fill(left, top + 1, right - 1, top + 2, 0xFF000000);
        graphics.fill(right - 1, top + 2, right, bottom - 2, 0xFF000000);
        graphics.fill(left, bottom - 2, right - 1, bottom - 1, 0xFF000000);
        graphics.fill(left, bottom - 1, right - 2, bottom, 0xFF000000);
        graphics.fill(left, top + 2, right - 1, bottom - 2, 0xFFC6C6C6);
        graphics.fill(left, top + 1, right - 2, top + 2, 0xFFFFFFFF);
        graphics.fill(right - 2, top + 2, right - 1, bottom - 2, 0xFF565656);
        graphics.fill(left, bottom - 2, right - 2, bottom - 1, 0xFF565656);
    }

    /** 底边留出连接边，供容器顶部的横条使用。 */
    public static void drawTopTabBackground(
        GuiGraphicsExtractor graphics, int left, int top, int width, int height
    ) {
        int right = left + width;
        int bottom = top + height;
        // 外描边：上边两行各收进 1 像素形成圆角，左右两列一直落到容器顶边。
        graphics.fill(left + 2, top, right - 2, top + 1, 0xFF000000);
        graphics.fill(left + 1, top + 1, right - 1, top + 2, 0xFF000000);
        graphics.fill(left, top + 2, left + 1, bottom, 0xFF000000);
        graphics.fill(right - 1, top + 2, right, bottom, 0xFF000000);
        graphics.fill(left + 1, top + 2, right - 1, bottom, 0xFFC6C6C6);
        graphics.fill(left + 2, top + 1, right - 2, top + 2, 0xFFFFFFFF);
        // 左右两条内描边比上边高光低 1 像素，收角处才不会连成一片。
        graphics.fill(left + 1, top + 3, left + 2, bottom - 1, 0xFFFFFFFF);
        graphics.fill(right - 2, top + 3, right - 1, bottom - 1, 0xFF565656);
    }

    /** 右侧留出连接边，供从容器左侧展开的标签使用。 */
    public static void drawLeftTabBackground(
        GuiGraphicsExtractor graphics, int left, int top, int width, int height
    ) {
        int right = left + width;
        int bottom = top + height;
        graphics.fill(left + 2, top, right, top + 1, 0xFF000000);
        graphics.fill(left + 1, top + 1, right, top + 2, 0xFF000000);
        graphics.fill(left, top + 2, left + 1, bottom - 2, 0xFF000000);
        graphics.fill(left + 1, bottom - 2, right, bottom - 1, 0xFF000000);
        graphics.fill(left + 2, bottom - 1, right, bottom, 0xFF000000);
        graphics.fill(left + 1, top + 2, right, bottom - 2, 0xFFC6C6C6);
        graphics.fill(left + 2, top + 1, right, top + 2, 0xFFFFFFFF);
        graphics.fill(left + 1, top + 2, left + 2, bottom - 2, 0xFF565656);
        graphics.fill(left + 2, bottom - 2, right, bottom - 1, 0xFF565656);
    }
}
