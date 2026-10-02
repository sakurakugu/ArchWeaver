package com.sakurakugu.archweaver.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** 可复用的像素图标，负责自身尺寸和绘制。 */
public enum PixelGlyph {
    // 这个可以用 tool/pixel-editor.html 绘制
    /** 左上箭头向右下回弯的撤回图标。 */
    UNDO(12, 11) {
        @Override
        protected void draw(GuiGraphicsExtractor graphics, int x, int y, int color) {
            graphics.fill(x + 2, y, x + 3, y + 6, color);
            graphics.fill(x + 1, y + 1, x + 2, y + 5, color);
            graphics.fill(x, y + 2, x + 1, y + 4, color);
            graphics.fill(x + 3, y + 2, x + 10, y + 4, color);
            graphics.fill(x + 10, y + 3, x + 11, y + 10, color);
            graphics.fill(x + 8, y + 4, x + 10, y + 5, color);
            graphics.fill(x + 9, y + 5, x + 10, y + 11, color);
            graphics.fill(x + 8, y + 8, x + 9, y + 11, color);
            graphics.fill(x + 3, y + 9, x + 8, y + 11, color);
        }
    },
    /** 实心软盘，快门和标签镂空。 */
    SAVE(12, 12) {
        @Override
        protected void draw(GuiGraphicsExtractor graphics, int x, int y, int color) {
            graphics.fill(x, y, x + 10, y + 1, color);
            graphics.fill(x, y + 1, x + 1, y + 12, color);
            graphics.fill(x + 2, y + 1, x + 3, y + 5, color);
            graphics.fill(x + 8, y + 1, x + 9, y + 5, color);
            graphics.fill(x + 10, y + 1, x + 11, y + 2, color);
            graphics.fill(x + 6, y + 2, x + 7, y + 5, color);
            graphics.fill(x + 11, y + 2, x + 12, y + 12, color);
            graphics.fill(x + 3, y + 4, x + 6, y + 5, color);
            graphics.fill(x + 7, y + 4, x + 8, y + 5, color);
            graphics.fill(x + 2, y + 6, x + 10, y + 7, color);
            graphics.fill(x + 2, y + 7, x + 3, y + 12, color);
            graphics.fill(x + 9, y + 7, x + 10, y + 12, color);
            graphics.fill(x + 1, y + 11, x + 2, y + 12, color);
            graphics.fill(x + 3, y + 11, x + 9, y + 12, color);
            graphics.fill(x + 10, y + 11, x + 11, y + 12, color);
        }
    },
    /** 由像素点组成的设置图标。 */
    SETTING(12, 12) {
        @Override
        protected void draw(GuiGraphicsExtractor graphics, int x, int y, int color) {
            graphics.fill(x + 5, y + 1, x + 7, y + 4, color);
            graphics.fill(x + 2, y + 2, x + 4, y + 4, color);
            graphics.fill(x + 8, y + 2, x + 10, y + 4, color);
            graphics.fill(x + 4, y + 3, x + 5, y + 5, color);
            graphics.fill(x + 7, y + 3, x + 8, y + 5, color);
            graphics.fill(x + 3, y + 4, x + 4, y + 10, color);
            graphics.fill(x + 8, y + 4, x + 9, y + 10, color);
            graphics.fill(x + 1, y + 5, x + 3, y + 7, color);
            graphics.fill(x + 9, y + 5, x + 11, y + 7, color);
            graphics.fill(x + 4, y + 7, x + 5, y + 9, color);
            graphics.fill(x + 7, y + 7, x + 8, y + 9, color);
            graphics.fill(x + 2, y + 8, x + 3, y + 10, color);
            graphics.fill(x + 5, y + 8, x + 7, y + 11, color);
            graphics.fill(x + 9, y + 8, x + 10, y + 10, color);
        }
    },
    /** 返回上一级页面的像素图标。 */
    BACK(12, 12) {
        @Override
        protected void draw(GuiGraphicsExtractor graphics, int x, int y, int color) {
            graphics.fill(x + 6, y + 1, x + 8, y + 3, color);
            graphics.fill(x + 5, y + 2, x + 6, y + 5, color);
            graphics.fill(x + 4, y + 3, x + 5, y + 9, color);
            graphics.fill(x + 6, y + 3, x + 7, y + 4, color);
            graphics.fill(x + 3, y + 4, x + 4, y + 8, color);
            graphics.fill(x + 2, y + 5, x + 3, y + 7, color);
            graphics.fill(x + 5, y + 7, x + 6, y + 10, color);
            graphics.fill(x + 6, y + 8, x + 7, y + 11, color);
            graphics.fill(x + 7, y + 9, x + 8, y + 11, color);
        }
    },
    /** 刷新内容的像素图标。 */
    REFRESH(12, 12) {
        @Override
        protected void draw(GuiGraphicsExtractor graphics, int x, int y, int color) {
            graphics.fill(x + 3, y + 1, x + 9, y + 3, color);
            graphics.fill(x + 2, y + 2, x + 3, y + 7, color);
            graphics.fill(x + 9, y + 2, x + 10, y + 4, color);
            graphics.fill(x + 1, y + 3, x + 2, y + 7, color);
            graphics.fill(x + 3, y + 3, x + 4, y + 4, color);
            graphics.fill(x + 8, y + 3, x + 9, y + 4, color);
            graphics.fill(x + 10, y + 3, x + 11, y + 4, color);
            graphics.fill(x, y + 5, x + 1, y + 6, color);
            graphics.fill(x + 3, y + 5, x + 4, y + 6, color);
            graphics.fill(x + 9, y + 5, x + 11, y + 9, color);
            graphics.fill(x + 8, y + 6, x + 9, y + 7, color);
            graphics.fill(x + 11, y + 6, x + 12, y + 7, color);
            graphics.fill(x + 1, y + 8, x + 4, y + 9, color);
            graphics.fill(x + 8, y + 8, x + 9, y + 11, color);
            graphics.fill(x + 2, y + 9, x + 8, y + 10, color);
            graphics.fill(x + 9, y + 9, x + 10, y + 10, color);
            graphics.fill(x + 3, y + 10, x + 8, y + 11, color);
        }
    },
    CLOSE(10, 12) {
        @Override
        protected void draw(GuiGraphicsExtractor graphics, int x, int y, int color) {
            for (int offset = 0; offset < 7; offset++) {
                graphics.fill(x + 1 + offset, y + 2 + offset, x + 3 + offset, y + 4 + offset, color);
                graphics.fill(x + 7 - offset, y + 2 + offset, x + 9 - offset, y + 4 + offset, color);
            }
        }
    };

    private final int width;
    private final int height;

    PixelGlyph(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void drawCentered(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        draw(graphics, x + (width - this.width) / 2, y + (height - this.height) / 2, color);
    }

    protected abstract void draw(GuiGraphicsExtractor graphics, int x, int y, int color);
}
