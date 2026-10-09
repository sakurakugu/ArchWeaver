package com.sakurakugu.archweaver.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/** 相机目标列表的三列按钮：名称、类型和距离。 */
public final class TargetButton extends Button {
    private final Entity entity;

    public TargetButton(int x, int y, int width, int height, Entity entity, OnPress onPress) {
        super(x, y, width, height, entity.getDisplayName(), onPress, DEFAULT_NARRATION);
        this.entity = entity;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        PixelGui.drawSolidControl(graphics, getX(), getY(), getWidth(), getHeight(), isMouseOver(mouseX, mouseY));
        var font = Minecraft.getInstance().font;
        int color = active ? 0xFFFFFFFF : 0xFF777777;
        String name = entity.getDisplayName().getString();
        String type = entity.getType().getDescription().getString();
        String distance = String.format(java.util.Locale.ROOT, "%.1fm", Minecraft.getInstance().player.distanceTo(entity));
        int left = getX() + 6;
        int right = getX() + getWidth() - 6;
        graphics.text(font, name, left, getY() + 6, color, false);
        int typeWidth = font.width(type);
        graphics.text(font, type, getX() + (getWidth() - typeWidth) / 2, getY() + 6, color, false);
        int distanceWidth = font.width(distance);
        graphics.text(font, distance, right - distanceWidth, getY() + 6, color, false);
    }
}
