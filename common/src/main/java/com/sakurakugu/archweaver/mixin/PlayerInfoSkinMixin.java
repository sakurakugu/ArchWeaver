package com.sakurakugu.archweaver.mixin;

import com.sakurakugu.archweaver.client.skin.ClientSkinCache;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 让 Tab 列表中的假人也显示自定义皮肤。 */
@Mixin(PlayerInfo.class)
public abstract class PlayerInfoSkinMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true, require = 0)
    private void archweaver$skin(CallbackInfoReturnable<PlayerSkin> callback) {
        PlayerInfo info = (PlayerInfo) (Object) this;
        callback.setReturnValue(ClientSkinCache.apply(info.getProfile().id(), callback.getReturnValue()));
    }
}
