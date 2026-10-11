package com.sakurakugu.archweaver.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.sakurakugu.archweaver.client.InvisibilityRenderState;
import com.sakurakugu.archweaver.invisibility.InvisibilityLevels;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 只跳过装备与手持层，其他附加层继续遵循原版规则。 */
@Mixin(LivingEntityRenderer.class)
public abstract class InvisibilityRendererMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void archweaver$extractLevel(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo callback) {
        ((InvisibilityRenderState) state).archweaver$setInvisibilityLevel(InvisibilityLevels.level(entity));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @WrapOperation(method = "submit", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/renderer/entity/layers/RenderLayer;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/EntityRenderState;FF)V"))
    private void archweaver$submitVisibleLayer(RenderLayer layer, PoseStack pose, SubmitNodeCollector collector,
                                               int light, EntityRenderState state, float yaw, float pitch,
                                               Operation<Void> original) {
        int level = ((InvisibilityRenderState) state).archweaver$invisibilityLevel();
        if (level >= 2 && (layer instanceof HumanoidArmorLayer || layer instanceof WingsLayer || layer instanceof CustomHeadLayer)) return;
        if (level >= 3 && layer instanceof ItemInHandLayer) return;
        original.call(layer, pose, collector, light, state, yaw, pitch);
    }
}
