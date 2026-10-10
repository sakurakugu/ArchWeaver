package com.sakurakugu.archweaver.mixin;

import com.sakurakugu.archweaver.client.camera.ClientCamera;
import com.sakurakugu.archweaver.client.camera.CameraExclusivity;
import com.sakurakugu.archweaver.client.camera.BodyAimIndicators;
import com.sakurakugu.archweaver.client.camera.CameraPreferences;
import com.sakurakugu.archweaver.client.camera.CameraPreferences.Toggle;
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

/** 用取景框和命中准星区分观察方向与身体实际交互目标。 */
@Mixin(Gui.class)
public abstract class CameraHudMixin {
    @Inject(method = "extractCrosshair", at = @At("HEAD"))
    private void archweaver$cameraHud(GuiGraphicsExtractor graphics, DeltaTracker tracker, CallbackInfo ci) {
        if (CameraExclusivity.shouldYield() || !ClientCamera.active()) return;
        Minecraft mc = Minecraft.getInstance();
        boolean hideText = CameraPreferences.get(Toggle.HIDE_HUD_TEXT);
        if (!hideText) graphics.text(mc.font, Component.translatable(ClientCamera.category().modeKey(ClientCamera.mode())), 5, 5, 0xFFFFFFFF);
        int cx = graphics.guiWidth() / 2, cy = graphics.guiHeight() / 2;
        for (int dx : new int[] {-1, 1}) for (int dy : new int[] {-1, 1}) {
            int x = cx + dx * 7, y = cy + dy * 7;
            graphics.fill(Math.min(x, x - dx * 3), y, Math.max(x, x - dx * 3) + 1, y + 1, 0xAAFFFFFF);
            graphics.fill(x, Math.min(y, y - dy * 3), x + 1, Math.max(y, y - dy * 3) + 1, 0xAAFFFFFF);
        }
        if (ClientCamera.blockInteraction() || mc.hitResult == null || mc.hitResult.getType() == HitResult.Type.MISS) return;
        Component target = mc.hitResult instanceof EntityHitResult entity ? entity.getEntity().getDisplayName()
            : mc.hitResult instanceof BlockHitResult block ? Component.literal(block.getBlockPos().toShortString()) : Component.empty();
        if (!hideText) graphics.text(mc.font, Component.translatable("camera.archweaver.body_target_hint", target), 5, 18, 0xFFFFFF55);
        if (!BodyAimIndicators.showCrosshair(ClientCamera.category(), ClientCamera.blockInteraction(),
            CameraPreferences.get(Toggle.SHOW_BODY_CROSSHAIR))) return;
        var projected = mc.gameRenderer.projectPointToScreen(mc.hitResult.getLocation());
        if (projected.z >= 0 && projected.z <= 1 && Math.abs(projected.x) <= 1 && Math.abs(projected.y) <= 1) {
            int x = (int) ((projected.x + 1) * graphics.guiWidth() / 2), y = (int) ((1 - projected.y) * graphics.guiHeight() / 2);
            graphics.fill(x - 4, y, x - 1, y + 1, 0xFFFFFF55);
            graphics.fill(x + 2, y, x + 5, y + 1, 0xFFFFFF55);
            graphics.fill(x, y - 4, x + 1, y - 1, 0xFFFFFF55);
            graphics.fill(x, y + 2, x + 1, y + 5, 0xFFFFFF55);
        }
    }
}
