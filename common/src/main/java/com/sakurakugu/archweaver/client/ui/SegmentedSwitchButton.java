package com.sakurakugu.archweaver.client.ui;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** 各档均带文字的像素风滑动开关，档位数量随传入的文字数量而定。 */
public final class SegmentedSwitchButton extends Button {
    private static final int LABEL_PADDING = 2; // 文字与每档左右边框之间的间距。
    private final List<Component> labels; // 从左到右每一档显示的文字，档位数量即其长度。
    private final IntSupplier selectedIndex; // 当前选中档位的下标。
    private final IntConsumer onSelect; // 选档变化的回调，参数为选中的档位下标。
    private boolean dragging; // 是否正按住鼠标拖动选档。

    /** 两档开关的便捷构造，等价于左档下标 0、右档下标 1。 */
    public SegmentedSwitchButton(
        int x, int y, int width, int height, Component leftLabel, Component rightLabel,
        BooleanSupplier selectedRight, Consumer<Boolean> onSelect
    ) {
        this(x, y, width, height, List.of(leftLabel, rightLabel),
            () -> selectedRight.getAsBoolean() ? 1 : 0,
            index -> onSelect.accept(index == 1));
    }

    /** 按 labels 的顺序从左到右排列各档，档位数量跟随 labels 的长度。 */
    public SegmentedSwitchButton(
        int x, int y, int width, int height, List<Component> labels,
        IntSupplier selectedIndex, IntConsumer onSelect
    ) {
        super(x, y, width, height, joinLabels(labels), ignored -> { }, DEFAULT_NARRATION);
        if (labels.size() < 2) throw new IllegalArgumentException("档位至少需要两个");
        this.labels = List.copyOf(labels);
        this.selectedIndex = selectedIndex;
        this.onSelect = onSelect;
    }

    /** 把所有档位文字拼成朗读用的描述。 */
    private static Component joinLabels(List<Component> labels) {
        var joined = Component.empty();
        for (int index = 0; index < labels.size(); index++) {
            if (index > 0) joined.append(" / ");
            joined.append(labels.get(index));
        }
        return joined;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        PixelGui.drawLargeSwitch(graphics, getX(), getY(), getWidth(), getHeight(),
            labels.size(), selectedIndex.getAsInt(), hoveredIndex(mouseX, mouseY));
        var font = Minecraft.getInstance().font;
        for (int index = 0; index < labels.size(); index++) {
            PixelGui.Segment segment = segment(index);
            PixelGui.drawCenteredScrollingText(graphics, font, labels.get(index),
                segment.left() + LABEL_PADDING, getY(),
                segment.width() - LABEL_PADDING * 2, getHeight(), 0xFFFFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0 || !isMouseOver(event.x(), event.y())) return false;
        dragging = true;
        updateSelection(event.x());
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (!dragging) return false;
        updateSelection(event.x());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() != 0) return false;
        boolean wasDragging = dragging;
        dragging = false;
        return wasDragging;
    }

    /** 第 index 档的范围，与绘制时用的分界保持一致。 */
    private PixelGui.Segment segment(int index) {
        return PixelGui.segment(getX(), getWidth(), labels.size(), index);
    }

    /** 指针所在的档位下标，未悬停时为 -1，与 {@link #updateSelection} 的判定保持一致。 */
    private int hoveredIndex(int mouseX, int mouseY) {
        if (!isMouseOver(mouseX, mouseY)) return -1;
        return indexAt(mouseX);
    }

    /** 按各档左边界定位指针所在的档位，越过左端归入首档、越过右端归入末档。 */
    private int indexAt(double mouseX) {
        for (int index = labels.size() - 1; index > 0; index--) {
            if (mouseX >= segment(index).left()) return index;
        }
        return 0;
    }

    /** 拖动时按指针所在的档位直接选档。 */
    private void updateSelection(double mouseX) {
        onSelect.accept(indexAt(mouseX));
    }
}
