package com.sakurakugu.archweaver.client.ui;

import com.sakurakugu.archweaver.network.TargetInfoPayload;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** 坐标行只展示 XYZ，维度名称放在悬停提示中。 */
public final class TargetPositionDisplay extends Button {
    private final Font font;
    private final Supplier<TargetInfoPayload> info;

    public TargetPositionDisplay(Font font, int x, int y, int width, int height, Supplier<TargetInfoPayload> info) {
        super(x, y, width, height, Component.translatable("gui.archweaver.fakeplayer.info.position"),
            button -> {}, DEFAULT_NARRATION);
        this.font = font;
        this.info = info;
    }

    @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        TargetInfoPayload target = info.get();
        if (target == null) return;
        Component value = Component.literal(target.x() + ", " + target.y() + ", " + target.z());
        int textTop = getY() + (getHeight() - 8) / 2;
        int valueLeft = getX() + font.width(getMessage());
        graphics.text(font, getMessage(), getX(), textTop, 0xFF404040, false);
        if (font.width(value) <= getX() + getWidth() - valueLeft) {
            graphics.text(font, value, valueLeft, textTop, 0xFF404040, false);
        } else {
            PixelGui.drawScrollingText(graphics, font, value, valueLeft, getX() + getWidth(), getY(), getHeight(), 0xFF404040);
        }
        setTooltip(Tooltip.create(Component.translatable("gui.archweaver.fakeplayer.info.position_tooltip",
            target.x(), target.y(), target.z()).append("\n").append(
                Component.translatable("gui.archweaver.fakeplayer.info.dimension", DimensionDisplay.name(target.dimension())))));
    }
}
