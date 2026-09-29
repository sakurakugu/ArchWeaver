package com.sakurakugu.fakeplayer.platform;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** 加载器无关的网络发送边界，由平台入口在初始化时注入实现。 */
public final class PlatformNetworking {
    private static Consumer<CustomPacketPayload> clientSender = payload -> {
        throw new IllegalStateException("平台网络尚未初始化");
    };
    private static BiConsumer<ServerPlayer, CustomPacketPayload> playerSender = (player, payload) -> {
        throw new IllegalStateException("平台网络尚未初始化");
    };

    private PlatformNetworking() {
    }

    public static void installClientSender(Consumer<CustomPacketPayload> sender) {
        clientSender = Objects.requireNonNull(sender);
    }

    public static void installPlayerSender(BiConsumer<ServerPlayer, CustomPacketPayload> sender) {
        playerSender = Objects.requireNonNull(sender);
    }

    public static void sendToServer(CustomPacketPayload payload) {
        clientSender.accept(payload);
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        playerSender.accept(player, payload);
    }
}
