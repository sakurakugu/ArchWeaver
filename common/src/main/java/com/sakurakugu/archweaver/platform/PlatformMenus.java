package com.sakurakugu.archweaver.platform;

import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;

/** 菜单附加数据的传输由加载器提供。 */
public final class PlatformMenus {
    private static Opener opener;
    private PlatformMenus() { }
    public static void install(Opener value) { opener = Objects.requireNonNull(value); }
    public static void open(ServerPlayer player, MenuProvider provider, Consumer<RegistryFriendlyByteBuf> data) {
        if (opener == null) throw new IllegalStateException("平台菜单尚未初始化");
        opener.open(player, provider, data);
    }
    @FunctionalInterface
    public interface Opener {
        void open(ServerPlayer player, MenuProvider provider, Consumer<RegistryFriendlyByteBuf> data);
    }
}
