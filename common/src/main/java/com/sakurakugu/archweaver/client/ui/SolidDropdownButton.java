package com.sakurakugu.archweaver.client.ui;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

/** 使用纯色像素风边框的下拉选择器。 */
public final class SolidDropdownButton<T> extends Button {
    private static final int OPTION_HEIGHT = 16; // 展开后每个选项的行高。
    private static final int TEXT_PADDING = 4; // 文字距控件左右边缘的内边距。
    private final List<T> options; // 全部可选值，构造时复制为不可变列表。
    private final Function<T, Component> labelFactory; // 把选项值转换成显示文本。
    private final Consumer<T> onSelected; // 用户选中某个选项后的回调。
    private T selected; // 当前选中的选项，必属于 options。
    private boolean open; // 选项列表是否处于展开状态。
    private boolean opensUpward; // 选项列表是否向上展开，用于贴近面板底部的控件。

    public SolidDropdownButton(
        int x,
        int y,
        int width,
        int height,
        List<T> options,
        T selected,
        Function<T, Component> labelFactory,
        Consumer<T> onSelected
    ) {
        super(x, y, width, height, Component.empty(), button -> {}, DEFAULT_NARRATION);
        // selected 必须先判空：options 可能是 List.copyOf 生成的不可变列表，
        // 对它调用 contains(null) 会抛 NPE，而不是这里想要的入参校验。
        if (selected == null || options.isEmpty() || !options.contains(selected)) {
            throw new IllegalArgumentException("下拉选择器必须包含初始选项");
        }
        this.options = List.copyOf(options);
        this.selected = selected;
        this.labelFactory = labelFactory;
        this.onSelected = onSelected;
        updateMessage();
    }

    public T selected() {
        return selected;
    }

    /** 让选项列表向上展开，适合靠近面板底部的控件；返回自身便于链式调用。 */
    public SolidDropdownButton<T> setOpensUpward() {
        opensUpward = true;
        return this;
    }

    /** 选项列表顶边的屏幕坐标。 */
    private int popupScreenTop() {
        return opensUpward ? getY() - options.size() * OPTION_HEIGHT : getY() + getHeight();
    }

    public boolean isOpen() {
        return open;
    }

    /** 只报告当前显示的选项列表，包含伸出所属面板的部分。 */
    public Optional<Rect2i> getPopupArea() {
        if (!open || !visible) {
            return Optional.empty();
        }
        return Optional.of(new Rect2i(getX(), popupScreenTop(), getWidth(), options.size() * OPTION_HEIGHT));
    }

    public void setSelected(T selected) {
        // 同样先判空，避免在不可变选项列表上触发 contains(null) 的 NPE。
        if (selected != null && options.contains(selected)) {
            this.selected = selected;
            updateMessage();
        }
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        PixelGui.drawSolidControl(graphics, getX(), getY(), getWidth(), getHeight(), isMouseOver(mouseX, mouseY));
        Font font = Minecraft.getInstance().font;
        int textLeft = getX() + TEXT_PADDING;
        int textRight = getX() + getWidth() - 10;
        int color = active ? 0xFFFFFFFF : 0xFF777777;
        // 文字放得下时左对齐，放不下则在箭头左侧区域滚动，避免被收窄的边框裁断。
        if (font.width(getMessage()) <= textRight - textLeft) {
            graphics.text(font, getMessage(), textLeft, getY() + (getHeight() - 8) / 2, color, false);
        } else {
            PixelGui.drawScrollingText(graphics, font, getMessage(), textLeft, textRight,
                getY(), getHeight(), color);
        }
        // 箭头在展开时翻转，收起时指向选项列表实际展开的方向。
        String arrow = open == opensUpward ? "▼" : "▲";
        graphics.text(font, Component.literal(arrow),
            getX() + getWidth() - 9, getY() + (getHeight() - 8) / 2 + 1,
            color, false);
    }

    /** 在屏幕的最后绘制阶段调用，确保选项列表不会被其他面板遮住。 */
    public void extractPopup(
        GuiGraphicsExtractor graphics,
        int mouseX,
        int mouseY,
        int graphicsOriginX,
        int graphicsOriginY
    ) {
        if (!open || !visible) {
            return;
        }
        int left = getX() - graphicsOriginX;
        int top = popupScreenTop() - graphicsOriginY;
        int right = left + getWidth();
        int bottom = top + options.size() * OPTION_HEIGHT;
        graphics.fill(left, top, right, bottom, 0xFF202326);
        graphics.outline(left, top, getWidth(), bottom - top, 0xFF8B8B8B);
        for (int index = 0; index < options.size(); index++) {
            int optionTop = top + index * OPTION_HEIGHT;
            int absoluteOptionTop = optionTop + graphicsOriginY;
            if (mouseX >= getX() && mouseX < getX() + getWidth()
                && mouseY >= absoluteOptionTop && mouseY < absoluteOptionTop + OPTION_HEIGHT) {
                graphics.fill(left + 1, optionTop + 1, right - 1, optionTop + OPTION_HEIGHT - 1, 0xFF5A5A5A);
            }
            // 选项文字超出下拉框宽度时裁断，避免盖到后面的面板上。
            graphics.enableScissor(left + 1, optionTop, right - 1, optionTop + OPTION_HEIGHT);
            graphics.text(Minecraft.getInstance().font, labelFactory.apply(options.get(index)),
                left + TEXT_PADDING, optionTop + (OPTION_HEIGHT - 8) / 2, 0xFFFFFFFF, false);
            graphics.disableScissor();
        }
    }

    /** 屏幕在调用父类点击处理前调用，用于接收控件本体之外的选项点击。 */
    public boolean popupMouseClicked(MouseButtonEvent event) {
        if (!open || event.button() != 0) {
            return false;
        }
        int left = getX();
        int top = popupScreenTop();
        int right = left + getWidth();
        int bottom = top + options.size() * OPTION_HEIGHT;
        if (event.x() >= left && event.x() < right && event.y() >= top && event.y() < bottom) {
            int index = (int) ((event.y() - top) / OPTION_HEIGHT);
            selected = options.get(index);
            updateMessage();
            open = false;
            onSelected.accept(selected);
            return true;
        }
        if (!isMouseOver(event.x(), event.y())) {
            open = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0 || !isMouseOver(event.x(), event.y())) {
            return false;
        }
        open = !open;
        return true;
    }

    private void updateMessage() {
        setMessage(labelFactory.apply(selected));
    }
}
