package com.sakurakugu.archweaver.network;

import com.sakurakugu.archweaver.platform.PayloadRegistration;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** NeoForge 只适配注册过程，业务校验由两种加载器共享。 */
public final class ModNetworking {
    private ModNetworking() { }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2");
        ServerNetworking.register(new PayloadRegistration() {
            public <T extends CustomPacketPayload> void playToServer(CustomPacketPayload.Type<T> type,
                    StreamCodec<? super RegistryFriendlyByteBuf, T> codec, Handler<T> handler) {
                registrar.playToServer(type, codec, (payload, context) -> handler.handle(payload, context::player));
            }
            public <T extends CustomPacketPayload> void playToClient(CustomPacketPayload.Type<T> type,
                    StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
                registrar.playToClient(type, codec);
            }
        });
    }
}
