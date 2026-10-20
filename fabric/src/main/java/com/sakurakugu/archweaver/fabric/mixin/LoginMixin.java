package com.sakurakugu.archweaver.fabric.mixin;

import com.mojang.authlib.GameProfile;
import com.sakurakugu.archweaver.entity.FakePlayerManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 登录核验在服务器线程进行，先让同档案假人下线并保留驻留记录。 */
@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class LoginMixin {
    @Shadow @Final private MinecraftServer server;
    @Inject(method = "verifyLoginAndFinishConnectionSetup", at = @At("HEAD"))
    private void archweaver$removeFake(GameProfile profile, CallbackInfo callback) {
        var fake = FakePlayerManager.find(server, profile);
        if (fake != null) FakePlayerManager.remove(fake, false);
    }
}
