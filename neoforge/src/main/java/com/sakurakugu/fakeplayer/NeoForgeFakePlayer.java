package com.sakurakugu.fakeplayer;

import com.sakurakugu.fakeplayer.chunkloading.ChunkLoaderManager;
import com.sakurakugu.fakeplayer.chunkloading.NeoForgeChunkTicketService;
import com.sakurakugu.fakeplayer.config.FakePlayerConfig;
import com.sakurakugu.fakeplayer.config.NeoForgeConfigs;
import com.sakurakugu.fakeplayer.menu.NeoForgeMenus;
import com.sakurakugu.fakeplayer.network.ModNetworking;
import com.sakurakugu.fakeplayer.platform.PlatformHooks;
import com.sakurakugu.fakeplayer.platform.PlatformNetworking;
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
@Mod(FakePlayerMod.MOD_ID)
public final class NeoForgeFakePlayer {
    public NeoForgeFakePlayer(IEventBus modBus, ModContainer container) {
        FakePlayerConfig.install(NeoForgeConfigs.SERVER);
        ChunkLoaderManager.installTicketService(new NeoForgeChunkTicketService());
        PlatformNetworking.installPlayerSender((player, payload) -> PacketDistributor.sendToPlayer(player, payload));
        PlatformHooks.installConnectionConfigurator(NetworkRegistry::configureMockConnection);
        PlatformHooks.installHandSwapHook(NeoForgeFakePlayer::swapHands);
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
