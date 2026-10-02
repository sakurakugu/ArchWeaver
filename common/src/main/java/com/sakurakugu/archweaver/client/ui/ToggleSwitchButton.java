package com.sakurakugu.archweaver.client.ui;

import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** 左侧显示标签、右侧显示当前状态的紧凑开关。 */
public final class ToggleSwitchButton extends Button {
    static final int SWITCH_WIDTH = 24; // 右侧开关图形的宽度。
    static final int SWITCH_HEIGHT = 12; // 右侧开关图形的高度。
    static final int HANDLE_WIDTH = 8; // 开关内部滑块手柄的宽度。
    private static final int BOXED_LEFT_PADDING = 4; // 带外框时文字距左边缘的内边距。
    private static final int BOXED_RIGHT_PADDING = 4; // 带外框时开关距右边缘的内边距。
    private static final int BOXED_LABEL_GAP = 3; // 带外框时文字与开关之间的间距。

    private final BooleanSupplier enabled; // 开关当前开/关状态的来源，绘制时实时读取。
    private final boolean boxed; // 是否绘制包住文字与开关的纯色外框。
    private final int labelColor; // 标签文字颜色：面板上是浅色背景、地图上是深色背景，得分开传。

    public static int preferredBoxedWidth(Font font, Component message) {
        return BOXED_LEFT_PADDING + font.width(message) + BOXED_LABEL_GAP + SWITCH_WIDTH + BOXED_RIGHT_PADDING;
    }

    public ToggleSwitchButton(
        int x, int y, int width, int height, Component message,
        BooleanSupplier enabled, OnPress onPress
    ) {
        this(x, y, width, height, message, 0xFF404040, enabled, onPress);
    }

    public ToggleSwitchButton(
        int x, int y, int width, int height, Component message, int labelColor,
        BooleanSupplier enabled, OnPress onPress
    ) {
        this(x, y, width, height, message, labelColor, enabled, onPress, false);
    }

    /** 给文字和开关共用一个外框，适合地图顶部的紧凑布局。 */
    public ToggleSwitchButton(
        int x, int y, int width, int height, Component message, int labelColor,
        BooleanSupplier enabled, OnPress onPress, boolean boxed
    ) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.enabled = enabled;
        this.labelColor = labelColor;
        this.boxed = boxed;
        setTooltip(Tooltip.create(message));
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        if (boxed) PixelGui.drawSolidControl(graphics, getX(), getY(), getWidth(), getHeight(),
            isMouseOver(mouseX, mouseY));
        int padding = boxed ? BOXED_LEFT_PADDING : 0;
        int labelX = getX() + padding;
        int switchX = getX() + getWidth() - (boxed ? BOXED_RIGHT_PADDING : 0) - SWITCH_WIDTH;
        int switchY = getY() + (getHeight() - SWITCH_HEIGHT) / 2;
        int labelRight = switchX - (boxed ? BOXED_LABEL_GAP : 4);
        if (font.width(getMessage()) <= labelRight - labelX) {
            int textX = boxed ? labelRight - font.width(getMessage()) : labelX;
            graphics.text(font, getMessage(), textX, getY() + (getHeight() - 8) / 2, labelColor, false);
        } else {
            PixelGui.drawScrollingText(graphics, font, getMessage(),
                labelX, labelRight, getY(), getHeight(), labelColor);
        }
        PixelGui.drawToggle(graphics, switchX, switchY, enabled.getAsBoolean(), isMouseOver(mouseX, mouseY));
    }
}
