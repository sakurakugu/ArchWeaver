package com.sakurakugu.archweaver.platform;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

/** 注册协议与服务端主线程处理器，保留各平台自己的调度方式。 */
public interface PayloadRegistration {
    <T extends CustomPacketPayload> void playToServer(CustomPacketPayload.Type<T> type,
        StreamCodec<? super RegistryFriendlyByteBuf, T> codec, Handler<T> handler);
    <T extends CustomPacketPayload> void playToClient(CustomPacketPayload.Type<T> type,
        StreamCodec<? super RegistryFriendlyByteBuf, T> codec);

    @FunctionalInterface
    interface Handler<T extends CustomPacketPayload> { void handle(T payload, Context context); }
    @FunctionalInterface
    interface Context { Player player(); }
}
