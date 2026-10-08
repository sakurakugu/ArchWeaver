package com.sakurakugu.archweaver.entity;

import com.sakurakugu.archweaver.persistence.FakePlayerPersistence;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;

/**
 * 对 Carpet/Curtain 假玩家提供不编译期依赖的识别和生命周期桥接。
 * 外部模组不存在时该类完全不产生额外行为。
 */
public final class ExternalFakePlayerSupport {
    private ExternalFakePlayerSupport() { }

    public static boolean isExternalFake(ServerPlayer player) {
        if (player == null) return false;
        if (player instanceof FakeServerPlayer) return false;
        String name = player.getClass().getName();
        return name.equals("carpet.patches.EntityPlayerMPFake")
            || name.endsWith("EntityPlayerMPFake")
            || implementsNamedInterface(player, "ServerPlayerInterface")
            || implementsNamedInterface(player, "FakePlayer");
    }

    private static boolean implementsNamedInterface(Object value, String suffix) {
        for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass()) {
            for (Class<?> iface : type.getInterfaces()) {
                if (iface.getName().endsWith(suffix)) return true;
            }
        }
        return false;
    }

    /** 将外部假玩家当前状态登记到 ArchWeaver 清单。 */
    public static void track(ServerPlayer player) {
        if (!isExternalFake(player)) return;
        FakePlayerPersistence.track(player);
    }

    /** 通过原版玩家列表卸载外部假玩家，保留登记和存档。 */
    public static void unload(ServerPlayer player) {
        if (!isExternalFake(player)) return;
        FakePlayerPersistence.save(player);
        FakePlayerPersistence.data(player.level().getServer()).setRestoreOnRestart(player.getUUID(), false);
        player.level().getServer().getPlayerList().remove(player);
    }

    /** 尝试调用 Carpet 的静态 createFake，供登记记录重新加载。 */
    public static boolean load(MinecraftServer server, String name, double x, double y, double z,
                               float yaw, float pitch, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                               net.minecraft.world.level.GameType gameType, boolean flying) {
        try {
            Class<?> type = Class.forName("carpet.patches.EntityPlayerMPFake");
            Method method = type.getMethod("createFake", String.class, MinecraftServer.class,
                net.minecraft.world.phys.Vec3.class, double.class, double.class,
                net.minecraft.resources.ResourceKey.class, net.minecraft.world.level.GameType.class, boolean.class);
            Object result = method.invoke(null, name, server, new net.minecraft.world.phys.Vec3(x, y, z),
                (double) yaw, (double) pitch, dimension, gameType, flying);
            return Boolean.TRUE.equals(result);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }
}
