package com.sakurakugu.archweaver.client.chunkloading;

import com.sakurakugu.archweaver.client.ui.SolidButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 保存成功后才执行离开动作；失败、超时和取消都保留原草稿。 */
public final class UnsavedChunkMapScreen extends Screen {
    private final Screen previous;
    private final ChunkLoadMapController controller;
    private final Runnable continuation;
    private boolean saving;
    private Button save;
    private Button discard;

    public UnsavedChunkMapScreen(Screen previous, ChunkLoadMapController controller, Runnable continuation) {
        super(Component.translatable("gui.archweaver.chunkloader.unsaved"));
        this.previous = previous;
        this.controller = controller;
        this.continuation = continuation;
    }

    @Override protected void init() {
        int x = width / 2 - 150;
        int y = height / 2 + 10;
        save = addRenderableWidget(new SolidButton(x, y, 96, 20,
            Component.translatable("gui.archweaver.chunkloader.save_continue"), button -> {
                saving = true;
                controller.apply();
            }));
        discard = addRenderableWidget(new SolidButton(x + 102, y, 96, 20,
            Component.translatable("gui.archweaver.chunkloader.discard_continue"), button -> {
                if (controller.awaitingApply()) return;
                controller.clearDraft();
                continuation.run();
            }));
        addRenderableWidget(new SolidButton(x + 204, y, 96, 20,
            Component.translatable("gui.cancel"), button -> onClose()));
    }

    @Override public void tick() {
        save.active = !controller.awaitingApply();
        discard.active = !controller.awaitingApply();
        if (saving && !controller.awaitingApply()) {
            saving = false;
            if (!controller.dirty()) continuation.run();
        }
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xF022282C);
        graphics.centeredText(font, title, width / 2, height / 2 - 35, 0xFFFFFFFF);
        if (!controller.failure().isEmpty()) {
            graphics.centeredText(font, Component.literal(controller.failure()), width / 2, height / 2 - 16, 0xFFFF7777);
        }
    }

    @Override public void onClose() {
        ChunkMapFrontends.runWithoutGuard(() -> minecraft.setScreen(previous));
    }
}
