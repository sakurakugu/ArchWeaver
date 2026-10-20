package com.sakurakugu.archweaver.network;

import com.sakurakugu.archweaver.platform.PayloadRegistration;
import com.sakurakugu.archweaver.platform.PlatformNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Fabric 网络注册；回调由 Fabric 调度到服务器主线程。 */
public final class FabricNetworking implements PayloadRegistration {
    public static void register() {
        ServerNetworking.register(new FabricNetworking());
        PlatformNetworking.installChannelChecker(ServerPlayNetworking::canSend);
        PlatformNetworking.installPlayerSender((player, payload) -> {
            if (ServerPlayNetworking.canSend(player, payload.type())) ServerPlayNetworking.send(player, payload);
        });
    }
    public <T extends CustomPacketPayload> void playToServer(CustomPacketPayload.Type<T> type,
        StreamCodec<? super RegistryFriendlyByteBuf, T> codec, Handler<T> handler) {
        PayloadTypeRegistry.serverboundPlay().register(type, codec);
        ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) -> handler.handle(payload, context::player));
    }
    public <T extends CustomPacketPayload> void playToClient(CustomPacketPayload.Type<T> type,
        StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        PayloadTypeRegistry.clientboundPlay().register(type, codec);
    }
}
