package com.sakurakugu.archweaver.entity;

import com.sakurakugu.archweaver.config.ArchWeaverConfig;
import com.sakurakugu.archweaver.network.FakePlayerAliasPayload;
import com.sakurakugu.archweaver.platform.PlatformNetworking;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** 向观察者同步别名和显示顺序，同时刷新原版 Tab 名称。 */
public final class FakePlayerAliasSync {
    private FakePlayerAliasSync() {
    }

    public static void syncTo(ServerPlayer viewer, FakeServerPlayer fake) {
        if (!(viewer instanceof FakeServerPlayer)) {
            PlatformNetworking.sendToPlayer(viewer, new FakePlayerAliasPayload(
                fake.getUUID(), fake.alias(), ArchWeaverConfig.fakePlayerAliasFirst(),
                fake.alias().isEmpty() && ArchWeaverConfig.fakePlayerEmptyAliasMarker()));
        }
    }

    public static void syncTo(ServerPlayer viewer) {
        for (ServerPlayer player : viewer.level().getServer().getPlayerList().getPlayers()) {
            if (player instanceof FakeServerPlayer fake) syncTo(viewer, fake);
        }
    }

    public static void broadcast(FakeServerPlayer fake) {
        MinecraftServer server = fake.level().getServer();
        server.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(
            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME, fake));
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) syncTo(viewer, fake);
    }

    public static void refreshAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player instanceof FakeServerPlayer fake) broadcast(fake);
        }
    }

    /** 别名与顺序本身没变、只是显示开关变化时，只重发头顶名牌，不重发 Tab 名称。 */
    public static void resyncViewers(MinecraftServer server) {
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) syncTo(viewer);
    }

    /** 同 UUID 的真人接替假人登录时，不能继续沿用假人的头顶别名。 */
    public static void clear(FakeServerPlayer fake) {
        var payload = new FakePlayerAliasPayload(
            fake.getUUID(), "", ArchWeaverConfig.fakePlayerAliasFirst(), false);
        for (ServerPlayer viewer : fake.level().getServer().getPlayerList().getPlayers()) {
            if (!(viewer instanceof FakeServerPlayer)) PlatformNetworking.sendToPlayer(viewer, payload);
        }
    }
}
