package com.sakurakugu.archweaver.platform;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;

/** 业务代码需要的少量平台行为，由加载器实现注入。 */
public final class PlatformHooks {
    private static Consumer<Connection> connectionConfigurator = connection -> { };
    private static BiConsumer<ServerPlayer, Runnable> handSwapHook = (player, action) -> action.run();

    private PlatformHooks() {
    }

    public static void installConnectionConfigurator(Consumer<Connection> configurator) {
        connectionConfigurator = Objects.requireNonNull(configurator);
    }

    public static void installHandSwapHook(BiConsumer<ServerPlayer, Runnable> hook) {
        handSwapHook = Objects.requireNonNull(hook);
    }

    public static void configureConnection(Connection connection) {
        connectionConfigurator.accept(connection);
    }

    public static void swapHands(ServerPlayer player, Runnable vanillaAction) {
        handSwapHook.accept(player, vanillaAction);
    }
}
