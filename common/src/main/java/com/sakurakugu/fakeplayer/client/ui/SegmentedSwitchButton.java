package com.sakurakugu.fakeplayer.client.ui;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** 左右两档均带文字的像素风滑动开关。 */
public final class SegmentedSwitchButton extends Button {
    private final Component leftLabel;
    private final Component rightLabel;
    private final BooleanSupplier selectedRight;
    private final Consumer<Boolean> onSelect;
    private boolean dragging;

    public SegmentedSwitchButton(
        int x, int y, int width, int height, Component leftLabel, Component rightLabel,
        BooleanSupplier selectedRight, Consumer<Boolean> onSelect
    ) {
        super(x, y, width, height, leftLabel.copy().append(" / ").append(rightLabel),
            ignored -> { }, DEFAULT_NARRATION);
        this.leftLabel = leftLabel;
        this.rightLabel = rightLabel;
        this.selectedRight = selectedRight;
        this.onSelect = onSelect;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        PixelGui.drawLargeSwitch(graphics, getX(), getY(), getWidth(), getHeight(),
            selectedRight.getAsBoolean(), isMouseOver(mouseX, mouseY));
        int halfWidth = getWidth() / 2;
        var font = Minecraft.getInstance().font;
        PixelGui.drawCenteredText(graphics, font, leftLabel,
            getX(), getY(), halfWidth, getHeight(), 0xFFFFFFFF);
        PixelGui.drawCenteredText(graphics, font, rightLabel,
            getX() + halfWidth, getY(), getWidth() - halfWidth, getHeight(), 0xFFFFFFFF);
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

    /** 拖动时按指针所在的半区直接选档。 */
    private void updateSelection(double mouseX) {
        onSelect.accept(mouseX >= getX() + getWidth() / 2);
    }
}
