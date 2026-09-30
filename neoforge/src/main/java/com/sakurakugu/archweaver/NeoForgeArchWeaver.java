package com.sakurakugu.archweaver;

import com.sakurakugu.archweaver.chunkloading.ChunkLoaderManager;
import com.sakurakugu.archweaver.chunkloading.NeoForgeChunkTicketService;
import com.sakurakugu.archweaver.config.ArchWeaverConfig;
import com.sakurakugu.archweaver.config.NeoForgeConfigs;
import com.sakurakugu.archweaver.menu.NeoForgeMenus;
import com.sakurakugu.archweaver.network.ModNetworking;
import com.sakurakugu.archweaver.platform.PlatformHooks;
import com.sakurakugu.archweaver.platform.PlatformNetworking;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** NeoForge 服务端入口，只负责把平台实现注入通用代码。 */
@Mod(ArchWeaverMod.MOD_ID)
public final class NeoForgeArchWeaver {
    public NeoForgeArchWeaver(IEventBus modBus, ModContainer container) {
        ArchWeaverConfig.install(NeoForgeConfigs.SERVER);
        ChunkLoaderManager.installTicketService(new NeoForgeChunkTicketService());
        PlatformNetworking.installPlayerSender((player, payload) -> PacketDistributor.sendToPlayer(player, payload));
        PlatformHooks.installConnectionConfigurator(NetworkRegistry::configureMockConnection);
        PlatformHooks.installHandSwapHook(NeoForgeArchWeaver::swapHands);
        container.registerConfig(ModConfig.Type.SERVER, NeoForgeConfigs.SERVER.spec());
        NeoForgeMenus.register(modBus);
        modBus.addListener(ModNetworking::register);
        modBus.addListener(NeoForgeChunkTicketService::register);
    }

    private static void swapHands(ServerPlayer player, Runnable ignored) {
        var event = CommonHooks.onLivingSwapHandItems(player);
        if (event.isCanceled()) return;
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, event.getItemSwappedToOffHand());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, event.getItemSwappedToMainHand());
        player.stopUsingItem();
        player.resetLastActionTime();
    }
}
