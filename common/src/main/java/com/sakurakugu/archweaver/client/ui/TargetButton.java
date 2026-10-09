package com.sakurakugu.archweaver.client.ui;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/** 相机目标列表的按钮：左侧名称、右侧距离，实体类型与 UUID 放在悬停提示里。 */
public final class TargetButton extends Button {
    private static final int PADDING = 6; // 名称与距离相对两侧的内边距（像素）。
    private static final int GAP = 4; // 名称与距离之间的最小间距（像素）。
    private static final String ELLIPSIS = "…"; // 名称放不下时的省略号。
    private final Entity entity;

    public TargetButton(int x, int y, int width, int height, Entity entity, OnPress onPress) {
        super(x, y, width, height, entity.getDisplayName(), onPress, DEFAULT_NARRATION);
        this.entity = entity;
        setTooltip(Tooltip.create(Component.literal(entity.getType().getDescription().getString())
            .append("\n").append(entity.getUUID().toString())));
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        PixelGui.drawSolidControl(graphics, getX(), getY(), getWidth(), getHeight(), isMouseOver(mouseX, mouseY));
        var font = Minecraft.getInstance().font;
        int color = active ? 0xFFFFFFFF : 0xFF777777;
        String distance = String.format(Locale.ROOT, "%.1fm", Minecraft.getInstance().player.distanceTo(entity));
        int left = getX() + PADDING;
        int distanceWidth = font.width(distance);
        int nameWidth = getWidth() - PADDING * 2 - distanceWidth - GAP;
        graphics.text(font, trim(font, entity.getDisplayName().getString(), nameWidth), left, getY() + 6, color, false);
        graphics.text(font, distance, getX() + getWidth() - PADDING - distanceWidth, getY() + 6, color, false);
    }

    /** 名称超出可用宽度时截断并补省略号，避免压到右侧的距离。 */
    private static String trim(Font font, String name, int width) {
        if (font.width(name) <= width) return name;
        return font.plainSubstrByWidth(name, width - font.width(ELLIPSIS)) + ELLIPSIS;
    }
}
