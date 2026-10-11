package com.sakurakugu.archweaver.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.sakurakugu.archweaver.client.ClientFakePlayerAliases;
import com.sakurakugu.archweaver.client.FakePlayerNameTagState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 使用两次原版名牌提交显示别名，沿用可见性、潜行透明度与计分板布局。 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererAliasMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void archweaver$extractAlias(Entity entity, EntityRenderState state, float partialTick, CallbackInfo callback) {
        ((FakePlayerNameTagState) state).archweaver$setAliasNameTag(
            entity instanceof AbstractClientPlayer player && state.nameTag != null
                ? ClientFakePlayerAliases.nameTag(player) : null);
    }

    @WrapOperation(
        method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitNameTag(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/phys/Vec3;ILnet/minecraft/network/chat/Component;ZIDLnet/minecraft/client/renderer/state/level/CameraRenderState;)V"),
        require = 2
    )
    private void archweaver$submitAlias(
        SubmitNodeCollector collector, PoseStack pose, Vec3 attachment, int offset, Component name,
        boolean seeThrough, int lightCoords, double distance, CameraRenderState camera,
        Operation<Void> original,
        EntityRenderState state, PoseStack originalPose, SubmitNodeCollector originalCollector,
        CameraRenderState originalCamera, int originalOffset
    ) {
        var alias = ((FakePlayerNameTagState) state).archweaver$aliasNameTag();
        if (alias == null || name != state.nameTag) {
            original.call(collector, pose, attachment, offset, name, seeThrough, lightCoords, distance, camera);
            return;
        }
        // 别名为空时收到的包一定带着占位标记，用「假人」占据原别名那一行。
        Component aliasName = alias.alias().isEmpty()
            ? Component.translatable("gui.archweaver.fakeplayer.marker").withStyle(ChatFormatting.GRAY)
            : Component.literal(alias.alias()).withStyle(ChatFormatting.GRAY);
        Component lower = alias.aliasFirst() ? name : aliasName;
        Component upper = alias.aliasFirst() ? aliasName : name;
        pose.pushPose();
        original.call(collector, pose, attachment, offset, lower, seeThrough, lightCoords, distance, camera);
        // 与原版计分板行使用相同的行距，向上增加一行，避免压住人物头部。
        pose.translate(0.0F, 9.0F * 1.15F * EntityRenderer.NAMETAG_SCALE, 0.0F);
        original.call(collector, pose, attachment, offset, upper, seeThrough, lightCoords, distance, camera);
        pose.popPose();
    }
}
