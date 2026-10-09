package com.sakurakugu.archweaver.mixin;

import com.sakurakugu.archweaver.client.camera.ClientCamera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 区分观察相机准星和身体的实际交互目标。 */
@Mixin(Gui.class)
public abstract class CameraHudMixin {
    @Inject(method = "extractCrosshair", at = @At("HEAD"))
    private void archweaver$cameraHud(GuiGraphicsExtractor graphics, DeltaTracker tracker, CallbackInfo ci) {
        if (!ClientCamera.active()) return;
        Minecraft mc = Minecraft.getInstance();
        graphics.text(mc.font, Component.translatable(ClientCamera.category().modeKey(ClientCamera.mode())), 5, 5, 0xFFFFFFFF);
        int cx = graphics.guiWidth() / 2, cy = graphics.guiHeight() / 2;
        graphics.fill(cx - 3, cy, cx + 4, cy + 1, 0xAAFFFFFF);
        graphics.fill(cx, cy - 3, cx + 1, cy + 4, 0xAAFFFFFF);
        if (ClientCamera.blockInteraction() || mc.hitResult == null || mc.hitResult.getType() == HitResult.Type.MISS) return;
        Component target = mc.hitResult instanceof EntityHitResult entity ? entity.getEntity().getDisplayName()
            : mc.hitResult instanceof BlockHitResult block ? Component.literal(block.getBlockPos().toShortString()) : Component.empty();
        graphics.text(mc.font, Component.translatable("camera.archweaver.body_target_hint", target), 5, 18, 0xFFFFFF55);
        var projected = mc.gameRenderer.projectPointToScreen(mc.hitResult.getLocation());
        if (projected.z >= 0 && projected.z <= 1 && Math.abs(projected.x) <= 1 && Math.abs(projected.y) <= 1) {
            int x = (int) ((projected.x + 1) * graphics.guiWidth() / 2), y = (int) ((1 - projected.y) * graphics.guiHeight() / 2);
            graphics.fill(x - 2, y - 2, x + 3, y + 3, 0xFFFFFF55);
        }
    }
}
