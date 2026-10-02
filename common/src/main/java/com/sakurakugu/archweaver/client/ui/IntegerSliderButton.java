package com.sakurakugu.archweaver.client.ui;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import net.minecraft.network.chat.Component;

/** 将滑块位置映射到闭区间整数值的纯色滑动条。 */
public final class IntegerSliderButton extends SolidSliderButton {
    private int minimum; // 取值范围的闭区间下界（含）。
    private int maximum; // 取值范围的闭区间上界（含），必须大于 minimum。
    private int selectedValue; // 当前选中的整数值，始终落在 [minimum, maximum] 内。
    private final IntFunction<Component> messageFactory; // 把当前整数值格式化为滑条上显示的文字。
    private final IntConsumer onValueChanged; // 用户拖动改变数值时的回调，程序化设值不触发。

    public IntegerSliderButton(
        int x,
        int y,
        int width,
        int height,
        int minimum,
        int maximum,
        int selectedValue,
        IntFunction<Component> messageFactory,
        IntConsumer onValueChanged
    ) {
        super(x, y, width, height, Component.empty(), normalize(minimum, maximum, selectedValue));
        this.minimum = minimum;
        this.maximum = maximum;
        this.selectedValue = Math.clamp(selectedValue, minimum, maximum);
        this.messageFactory = messageFactory;
        this.onValueChanged = onValueChanged;
        updateMessage();
    }

    public int selectedValue() {
        return selectedValue;
    }

    /** 更新取值范围和当前值，但不触发用户操作回调。 */
    public void setRange(int minimum, int maximum, int selectedValue) {
        validateRange(minimum, maximum);
        this.minimum = minimum;
        this.maximum = maximum;
        this.selectedValue = Math.clamp(selectedValue, minimum, maximum);
        value = normalize(minimum, maximum, this.selectedValue);
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        if (messageFactory != null) {
            setMessage(messageFactory.apply(selectedValue));
        }
    }

    @Override
    protected void applyValue() {
        int nextValue = minimum + (int) Math.round(value * (maximum - minimum));
        if (nextValue == selectedValue) {
            return;
        }
        selectedValue = nextValue;
        onValueChanged.accept(selectedValue);
    }

    private static double normalize(int minimum, int maximum, int selectedValue) {
        validateRange(minimum, maximum);
        return (double) (Math.clamp(selectedValue, minimum, maximum) - minimum) / (maximum - minimum);
    }

    private static void validateRange(int minimum, int maximum) {
        if (minimum >= maximum) {
            throw new IllegalArgumentException("滑动条最大值必须大于最小值");
        }
    }
}
