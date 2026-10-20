package com.sakurakugu.archweaver.compat.xaero.mixin;

import com.sakurakugu.archweaver.client.chunkloading.ChunkMapViewport;
import com.sakurakugu.archweaver.compat.xaero.ArchWeaverXaeroMap;
import com.sakurakugu.archweaver.compat.xaero.XaeroMapAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.lib.client.gui.ScreenBase;
import xaero.map.animation.Animation;
import xaero.map.animation.SlowingAnimation;
import xaero.map.gui.GuiCaveModeOptions;
import xaero.map.gui.GuiMap;
import xaero.map.gui.GuiMapSwitching;
import xaero.map.gui.dropdown.rightclick.GuiRightClickMenu;

/** 钩子限定于 1.47.0 的原生地图，原有按钮、菜单、平移与缩放继续交给 Xaero。 */
@Mixin(value = GuiMap.class, remap = false)
public abstract class XaeroGuiMapMixin extends ScreenBase implements XaeroMapAccess {
    @Shadow private double cameraX, cameraZ, scale, userScale;
    @Shadow private static double destScale;
    @Shadow private static boolean attachedCamera;
    @Shadow private boolean shouldResetCameraPos, overWaypointsMenu, overPlayersMenu, hopMenu;
    @Shadow private int[] cameraDestination;
    @Shadow private SlowingAnimation cameraDestinationAnimX, cameraDestinationAnimZ;
    @Shadow private Animation zoomAnim;
    @Shadow @Final private GuiMapSwitching mapSwitchingGui;
    @Shadow private GuiCaveModeOptions caveModeOptions;
    @Shadow private GuiRightClickMenu rightClickMenu;
    @Shadow private void applyZoomLimits() { }
    @Shadow private double getScaleMultiplier(int size) { return 1; }

    protected XaeroGuiMapMixin(Screen parent, Screen escape, Component title) { super(parent, escape, title); }

    @Override public ChunkMapViewport archweaver$viewport() {
        var window = Minecraft.getInstance().getWindow();
        return ChunkMapViewport.xaero(cameraX, cameraZ, scale, window.getWidth(), window.getHeight(), width, height);
    }

    @Override public boolean archweaver$inputBlocked() {
        return overWaypointsMenu || overPlayersMenu || rightClickMenu != null || mapSwitchingGui.active
            || caveModeOptions.isEnabled() || hopMenu
            || ((XaeroScreenBaseAccessor) this).archweaver$openDropdown() != null;
    }

    @Override public void archweaver$center(double x, double z, double spanX, double spanZ) {
        attachedCamera = false;
        shouldResetCameraPos = false;
        cameraDestination = null;
        cameraDestinationAnimX = cameraDestinationAnimZ = null;
        cameraX = x; cameraZ = z;
        if (spanX > 0 && spanZ > 0) {
            var window = Minecraft.getInstance().getWindow();
            double multiplier = getScaleMultiplier(Math.min(window.getWidth(), window.getHeight()));
            // 留出工具栏与原生信息栏空间；只缩小到能容纳区域，不改变用户已有的远景缩放。
            destScale = Math.min(userScale, Math.min(window.getWidth() * .75 / spanX,
                window.getHeight() * .65 / spanZ) / multiplier);
            applyZoomLimits();
            userScale = destScale;
            scale = userScale * multiplier;
            zoomAnim = null;
        }
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void archweaver$init(CallbackInfo ci) { ArchWeaverXaeroMap.init((GuiMap) (Object) this); }

    // 地图纹理提交之后还会更新摄像机和缩放；必须等本帧地图绘制完成，覆盖物才不会落后一帧。
    // 此处 GUI 矩阵已恢复，后面的原生文字、侧栏和按钮仍绘制在覆盖物之上。
    @Inject(method = "extractRenderState", at = @At(value = "INVOKE",
        target = "Lxaero/map/graphics/ImprovedFramebuffer;bindDefaultFramebuffer(Lnet/minecraft/client/Minecraft;)V",
        shift = At.Shift.AFTER))
    private void archweaver$overlay(GuiGraphicsExtractor graphics, int x, int y, float tick, CallbackInfo ci) {
        ArchWeaverXaeroMap.renderMap((GuiMap) (Object) this, graphics, x, y);
    }

    @Inject(method = "extractRenderState", at = @At(value = "INVOKE",
        target = "Lxaero/lib/client/gui/ScreenBase;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private void archweaver$status(GuiGraphicsExtractor graphics, int x, int y, float tick, CallbackInfo ci) {
        ArchWeaverXaeroMap.renderStatus((GuiMap) (Object) this, graphics, x, y);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void archweaver$click(MouseButtonEvent event, boolean twice, CallbackInfoReturnable<Boolean> cir) {
        if (ArchWeaverXaeroMap.click((GuiMap) (Object) this, event)) cir.setReturnValue(true);
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void archweaver$release(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (ArchWeaverXaeroMap.release((GuiMap) (Object) this, event.button())) cir.setReturnValue(true);
    }

    // GuiMap 没有声明拖动方法，直接覆写继承的方法，避免向不存在的目标方法注入。
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return ArchWeaverXaeroMap.drag((GuiMap) (Object) this, event) || super.mouseDragged(event, dx, dy);
    }
}
