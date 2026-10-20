package com.sakurakugu.archweaver.fabric.mixin;

import com.sakurakugu.archweaver.entity.FakePlayerPossession;
import com.sakurakugu.archweaver.entity.FakeServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 附身时允许同维度传送，但禁止跨维度拆散两具身体。 */
@Mixin(ServerPlayer.class)
public abstract class PossessionDimensionMixin {
    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",
        at = @At("HEAD"), cancellable = true)
    private void archweaver$guardDimension(TeleportTransition transition, CallbackInfoReturnable<ServerPlayer> callback) {
        var player = (ServerPlayer) (Object) this;
        if (transition.newLevel() == player.level()) return;
        if (player instanceof FakeServerPlayer fake && FakePlayerPossession.isPossessed(fake)) callback.setReturnValue(null);
        else if (FakePlayerPossession.isPossessing(player)) {
            player.sendSystemMessage(Component.translatable("gui.archweaver.fakeplayer.possess_no_dimension"));
            callback.setReturnValue(null);
        }
    }
}
