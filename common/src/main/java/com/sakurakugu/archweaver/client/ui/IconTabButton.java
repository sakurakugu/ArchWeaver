package com.sakurakugu.archweaver.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** 使用物品或纹理图标的紧凑标签按钮。 */
public final class IconTabButton extends Button {
    private final ItemStack item; // 用作图标的物品；与 texture 二选一，为 null 时改用纹理。
    private final Identifier texture; // 用作图标的纹理；与 item 二选一，为 null 时改用物品。
    private final int iconOffsetX; // 图标相对默认位置（左内边距 2 像素）的横向偏移，单位为像素。
    private final int iconOffsetY; // 图标相对垂直居中位置的纵向偏移，单位为像素。

    public IconTabButton(
        int x, int y, int width, int height, ItemStack item, Component message, OnPress onPress
    ) {
        this(x, y, width, height, item, null, 0, 0, message, onPress);
    }

    public IconTabButton(
        int x, int y, int width, int height, ItemStack item, int iconOffsetX,
        Component message, OnPress onPress
    ) {
        this(x, y, width, height, item, iconOffsetX, 0, message, onPress);
    }

    public IconTabButton(
        int x, int y, int width, int height, ItemStack item, int iconOffsetX, int iconOffsetY,
        Component message, OnPress onPress
    ) {
        this(x, y, width, height, item, null, iconOffsetX, iconOffsetY, message, onPress);
    }

    public IconTabButton(
        int x, int y, int width, int height, Identifier texture, Component message, OnPress onPress
    ) {
        this(x, y, width, height, null, texture, 0, 0, message, onPress);
    }

    private IconTabButton(
        int x, int y, int width, int height, ItemStack item, Identifier texture,
        int iconOffsetX, int iconOffsetY, Component message, OnPress onPress
    ) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.item = item;
        this.texture = texture;
        this.iconOffsetX = iconOffsetX;
        this.iconOffsetY = iconOffsetY;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int iconX = getX() + 2 + iconOffsetX;
        int iconY = getY() + (getHeight() - 16) / 2 + iconOffsetY;
        if (texture != null) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture, iconX, iconY,
                0.0F, 0.0F, 16, 16, 16, 16);
        } else {
            graphics.item(item, iconX, iconY);
        }
    }
}
