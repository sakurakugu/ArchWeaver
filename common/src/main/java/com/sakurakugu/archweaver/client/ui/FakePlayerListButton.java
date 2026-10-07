package com.sakurakugu.archweaver.client.ui;

import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;

/** 假人列表行：左侧头像，右侧两行文本，上下顺序跟随全局的「别名优先」设置。 */
public final class FakePlayerListButton extends Button {
    private final UUID id;
    private final Component alias;
    private final Component name;
    private final boolean aliasFirst;
    private final boolean selected;

    public FakePlayerListButton(int x, int y, int width, int height, UUID id, String alias, String name,
                                boolean aliasFirst, boolean emptyAliasMarker, boolean selected, OnPress onPress) {
        super(x, y, width, height, Component.literal(name), onPress, DEFAULT_NARRATION);
        this.id = id;
        this.aliasFirst = aliasFirst;
        // 别名为空时优先显示「假人」占位，未开启占位才退回「无别名」。
        this.alias = !alias.isEmpty() ? Component.literal(alias)
            : Component.translatable(emptyAliasMarker
                ? "gui.archweaver.fakeplayer.marker" : "gui.archweaver.fakeplayer.info.alias_unset");
        this.name = Component.literal(name);
        this.selected = selected;
        Component description = aliasFirst
            ? this.alias.copy().append("\n").append(this.name)
            : this.name.copy().append("\n").append(this.alias);
        setMessage(description);
        setTooltip(Tooltip.create(description));
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        PixelGui.drawSolidControl(graphics, getX(), getY(), getWidth(), getHeight(), isMouseOver(mouseX, mouseY));
        Minecraft minecraft = Minecraft.getInstance();
        var connection = minecraft.getConnection();
        var info = connection == null ? null : connection.getPlayerInfo(id);
        var skin = info == null ? DefaultPlayerSkin.get(id) : info.getSkin();
        // 与 Tab 使用同一份已同步皮肤，尚未收到玩家信息时使用原版默认头像。
        graphics.enableScissor(getX() + 2, getY() + 2, getRight() - 2, getBottom() - 2);
        PlayerFaceExtractor.extractRenderState(graphics, skin, getX() + 5, getY() + (getHeight() - 24) / 2, 24);
        graphics.disableScissor();
        int left = getX() + 35;
        int right = getRight() - 5;
        if (right <= left) {
            return;
        }
        // 优先的那一行沿用原来的高亮色，另一行保持暗灰，因此「别名优先」开启时与旧外观一致。
        drawLine(graphics, aliasFirst ? alias : name, left, right, getY() + 5, selected ? 0xFF55FF55 : 0xFFFFFFFF);
        drawLine(graphics, aliasFirst ? name : alias, left, right, getY() + 19, 0xFFD0D0D0);
    }

    private void drawLine(GuiGraphicsExtractor graphics, Component text, int left, int right, int top, int color) {
        var font = Minecraft.getInstance().font;
        if (font.width(text) > right - left) {
            PixelGui.drawScrollingText(graphics, font, text, left, right, top, 10, color);
        } else {
            graphics.text(font, text, left, top + 1, color, false);
        }
    }
}
