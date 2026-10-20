package com.sakurakugu.archweaver.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;

/** 自定义绘制与容器坐标的客户端平台边界。 */
public final class PlatformGui {
    private static Backend backend;
    private PlatformGui() { }
    public static void install(Backend value) { backend = java.util.Objects.requireNonNull(value); }
    public static void submit(GuiGraphicsExtractor graphics, GuiElementRenderState state) { backend.submit(graphics, state); }
    public static ScreenRectangle scissor(GuiGraphicsExtractor graphics) { return backend.scissor(graphics); }
    public static int leftPos(AbstractContainerScreen<?> screen) { return backend.leftPos(screen); }
    public static int topPos(AbstractContainerScreen<?> screen) { return backend.topPos(screen); }
    public static int imageHeight(AbstractContainerScreen<?> screen) { return backend.imageHeight(screen); }
    public interface Backend {
        void submit(GuiGraphicsExtractor graphics, GuiElementRenderState state);
        ScreenRectangle scissor(GuiGraphicsExtractor graphics);
        int leftPos(AbstractContainerScreen<?> screen);
        int topPos(AbstractContainerScreen<?> screen);
        int imageHeight(AbstractContainerScreen<?> screen);
    }
}
