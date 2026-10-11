package com.sakurakugu.archweaver.client.ui;

import com.sakurakugu.archweaver.client.skin.ClientSkinCache;
import com.sakurakugu.archweaver.skin.SkinSavedData;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** 当前皮肤与待输入的玩家名分开显示，长文本截断后可通过悬停查看。 */
public final class SkinStatusDisplay extends Button {
    private final Font font;
    private final Supplier<UUID> target;
    private final SkinSavedData.TargetType type;
    private ClientSkinCache.Source lastSource;
    private boolean initialized;

    public SkinStatusDisplay(Font font, int x, int y, int width, Supplier<UUID> target, SkinSavedData.TargetType type) {
        super(x, y, width, 14, Component.empty(), button -> {}, DEFAULT_NARRATION);
        this.font = font;
        this.target = target;
        this.type = type;
        updateSource();
    }

    private void updateSource() {
        ClientSkinCache.Source source = ClientSkinCache.source(target.get(), type);
        if (initialized && Objects.equals(source, lastSource)) return;
        initialized = true;
        lastSource = source;
        Component label = source == null ? Component.translatable("gui.archweaver.skin.initial")
            : source.kind() == SkinSavedData.Kind.PNG ? Component.translatable("gui.archweaver.skin.custom_png")
            : source.sourceName().isBlank() ? Component.translatable("gui.archweaver.skin.player")
            : Component.literal(source.sourceName());
        setMessage(Component.translatable("gui.archweaver.skin.current", label));
        var tooltip = getMessage().copy();
        if (source != null && !source.fileName().isBlank()) {
            tooltip.append("\n").append(source.fileName());
        }
        setTooltip(Tooltip.create(tooltip));
    }

    @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        updateSource();
        String text = getMessage().getString();
        if (font.width(text) > getWidth()) {
            text = font.plainSubstrByWidth(text, Math.max(0, getWidth() - font.width("…"))) + "…";
        }
        graphics.text(font, text, getX(), getY() + 3, 0xFF404040, false);
    }
}
