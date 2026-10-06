package com.sakurakugu.archweaver.event;

import com.sakurakugu.archweaver.ArchWeaverMod;
import com.sakurakugu.archweaver.command.FakePlayerCommand;
import com.sakurakugu.archweaver.command.ChunkLoaderCommand;
import com.sakurakugu.archweaver.chunkloading.ChunkLoaderManager;
import com.sakurakugu.archweaver.chunkloading.FakePlayerSimulationService;
import com.sakurakugu.archweaver.config.ArchWeaverConfig;
import com.sakurakugu.archweaver.entity.FakePlayerManager;
import com.sakurakugu.archweaver.entity.FakePlayerAliasSync;
import com.sakurakugu.archweaver.entity.FakePlayerPossession;
import com.sakurakugu.archweaver.entity.FakeServerPlayer;
import com.sakurakugu.archweaver.menu.FakePlayerMenuOpener;
import com.sakurakugu.archweaver.persistence.FakePlayerPersistence;
import java.util.concurrent.CompletableFuture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerNegotiationEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** 处理服务端通用事件，包括命令注册和假玩家交互。 */
@EventBusSubscriber(modid = ArchWeaverMod.MOD_ID)
public final class CommonEvents {
    private CommonEvents() {
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        FakePlayerCommand.register(event.getDispatcher());
        ChunkLoaderCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void serverStarted(ServerStartedEvent event) {
        // 上一实例若未正常清理，在这里丢弃其运行时状态，避免继续持有已关闭世界的引用。
        FakePlayerSimulationService.discardStale(event.getServer());
        FakePlayerPossession.discardStale(event.getServer());
        FakePlayerPersistence.restore(event.getServer());
        ChunkLoaderManager.reconcile(event.getServer());
        FakePlayerSimulationService.reconcile(event.getServer());
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        FakePlayerSimulationService.tick(event.getServer());
    }

    @SubscribeEvent
    public static void serverStopping(ServerStoppingEvent event) {
        // 原版即将保存 playerdata，必须先把真人恢复到附身前的位置。
        FakePlayerPossession.stopAll(event.getServer());
        // 区块票据必须在 level 关闭前撤销，随服务端一起丢弃的还有模拟范围的运行时状态。
        FakePlayerSimulationService.revokeAll(event.getServer());
    }

    @SubscribeEvent
    public static void removeFakePlayerBeforeLogin(PlayerNegotiationEvent event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        CompletableFuture<Void> removal = new CompletableFuture<>();
        event.enqueueWork(removal);
        // 登录协商可能来自网络线程，玩家列表只能交给服务器线程修改。
        server.execute(() -> {
            try {
                FakeServerPlayer fake = FakePlayerManager.find(server, event.getProfile());
                if (fake != null) {
                    // 保留驻留记录，假人只会在服务器下次启动时按名称和 UUID 占用情况决定是否恢复。
                    FakePlayerManager.remove(fake, false);
                }
                removal.complete(null);
            } catch (RuntimeException exception) {
                ArchWeaverMod.LOGGER.error("真玩家 {} 登录时移除同名假玩家失败", event.getProfile().name(), exception);
                removal.completeExceptionally(exception);
            }
        });
    }

    @SubscribeEvent
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        // 仅服务端真实玩家右键假玩家时打开物品栏管理页面。
        if (!(event.getEntity() instanceof ServerPlayer viewer) || !(event.getTarget() instanceof FakeServerPlayer fake)) {
            return;
        }
        if (!ArchWeaverConfig.canUseCommands(viewer.createCommandSourceStack())) {
            return;
        }
        if (!FakePlayerPossession.canOpenMenu(viewer, fake)) {
            // 附身中的躯壳只允许与其交换的玩家打开页面。
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
            return;
        }

        FakePlayerMenuOpener.openInventory(viewer, fake);
        // 阻止原版继续处理右键实体，避免同时触发物品交互。
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FakePlayerPossession.syncTo(player);
            if (player instanceof FakeServerPlayer fake) FakePlayerAliasSync.broadcast(fake);
            else FakePlayerAliasSync.syncTo(player);
        }
    }

    @SubscribeEvent
    public static void startTrackingFakePlayer(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer viewer
            && event.getTarget() instanceof FakeServerPlayer fake) {
            fake.actions().syncBodyRotation(viewer);
            FakePlayerAliasSync.syncTo(viewer, fake);
            fake.getActiveEffects().forEach(effect -> viewer.connection.send(
                new ClientboundUpdateMobEffectPacket(fake.getId(), effect, false)));
        }
    }

    @SubscribeEvent
    public static void playerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof FakeServerPlayer fake) FakePlayerAliasSync.clear(fake);
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakeServerPlayer)) {
            // 真人退出前先恢复附身前的身体状态，确保原版保存的 playerdata 仍是真人原来的位置。
            if (!FakePlayerPossession.stop(player)) {
                FakePlayerPossession.discard(player);
            }
        }
    }

    @SubscribeEvent
    public static void preventDimensionTravel(EntityTravelToDimensionEvent event) {
        if (event.getEntity() instanceof FakeServerPlayer fake && FakePlayerPossession.isPossessed(fake)) {
            event.setCanceled(true);
        } else if (event.getEntity() instanceof ServerPlayer player
            && FakePlayerPossession.isPossessing(player)) {
            player.sendSystemMessage(Component.translatable("gui.archweaver.fakeplayer.possess_no_dimension"));
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void possessionDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof FakeServerPlayer fake
            && FakePlayerPossession.handleShellDeath(fake, event.getSource())) {
            event.setCanceled(true);
        } else if (event.getEntity() instanceof ServerPlayer player
            && FakePlayerPossession.handleActiveBodyDeath(player, event.getSource())) {
            event.setCanceled(true);
        }
    }
}
