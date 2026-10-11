package com.sakurakugu.archweaver.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sakurakugu.archweaver.invisibility.InvisibilityLevels;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 第一人称同样隐藏主副手，包括地图和使用中的物品。 */
@Mixin(ItemInHandRenderer.class)
public abstract class InvisibilityFirstPersonMixin {
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void archweaver$hideHands(float partialTick, PoseStack pose, SubmitNodeCollector collector,
                                     LocalPlayer player, int light, CallbackInfo callback) {
        if (InvisibilityLevels.level(player) >= 3) callback.cancel();
    }
}
