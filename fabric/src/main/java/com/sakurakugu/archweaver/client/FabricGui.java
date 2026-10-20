package com.sakurakugu.archweaver.client;

import com.sakurakugu.archweaver.client.ui.PlatformGui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;

/** 仅放宽绘制状态和容器坐标的可见性，不修改原版行为。 */
public final class FabricGui implements PlatformGui.Backend {
    public void submit(GuiGraphicsExtractor graphics, GuiElementRenderState state) { graphics.guiRenderState.addGuiElement(state); }
    public ScreenRectangle scissor(GuiGraphicsExtractor graphics) { return graphics.scissorStack.peek(); }
    public int leftPos(AbstractContainerScreen<?> screen) { return screen.leftPos; }
    public int topPos(AbstractContainerScreen<?> screen) { return screen.topPos; }
    public int imageHeight(AbstractContainerScreen<?> screen) { return screen.imageHeight; }
}
