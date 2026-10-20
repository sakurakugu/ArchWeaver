package com.sakurakugu.archweaver;

import com.sakurakugu.archweaver.chunkloading.ChunkLoaderManager;
import com.sakurakugu.archweaver.chunkloading.FabricChunkTicketService;
import com.sakurakugu.archweaver.config.ArchWeaverConfig;
import com.sakurakugu.archweaver.config.FabricServerConfig;
import com.sakurakugu.archweaver.event.FabricEvents;
import com.sakurakugu.archweaver.invisibility.InvisibilitySync;
import com.sakurakugu.archweaver.menu.FabricMenus;
import com.sakurakugu.archweaver.network.FabricNetworking;
import com.sakurakugu.archweaver.network.InvisibilityLevelPayload;
import com.sakurakugu.archweaver.platform.PlatformNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.level.ServerPlayer;

/** Fabric 公共入口，专用服务器不加载客户端实现。 */
public final class FabricArchWeaver implements ModInitializer {
    public void onInitialize() {
        ArchWeaverConfig.install(FabricServerConfig.INSTANCE);
        var tickets = new FabricChunkTicketService();
        ChunkLoaderManager.installTicketService(tickets);
        FabricNetworking.register();
        FabricMenus.register();
        InvisibilitySync.install(entity -> {
            var payload = new InvisibilityLevelPayload(entity);
            PlayerLookup.tracking(entity).forEach(player -> PlatformNetworking.sendToPlayer(player, payload));
            if (entity instanceof ServerPlayer player) PlatformNetworking.sendToPlayer(player, payload);
        });
        FabricEvents.register(tickets);
    }
}
