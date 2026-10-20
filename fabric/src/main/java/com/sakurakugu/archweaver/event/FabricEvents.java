package com.sakurakugu.archweaver.event;

import com.sakurakugu.archweaver.chunkloading.*;
import com.sakurakugu.archweaver.command.ChunkLoaderCommand;
import com.sakurakugu.archweaver.command.FakePlayerCommand;
import com.sakurakugu.archweaver.config.ArchWeaverConfig;
import com.sakurakugu.archweaver.config.FabricServerConfig;
import com.sakurakugu.archweaver.entity.*;
import com.sakurakugu.archweaver.menu.FakePlayerMenuOpener;
import com.sakurakugu.archweaver.network.InvisibilityLevelPayload;
import com.sakurakugu.archweaver.persistence.FakePlayerPersistence;
import com.sakurakugu.archweaver.platform.PlatformNetworking;
import com.sakurakugu.archweaver.skin.SkinService;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.Mannequin;

/** Fabric 生命周期与交互事件。 */
public final class FabricEvents {
    private FabricEvents() { }
    public static void register(FabricChunkTicketService tickets) {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, selection) -> {
            FakePlayerCommand.register(dispatcher);
            ChunkLoaderCommand.register(dispatcher);
        });
        ServerLifecycleEvents.SERVER_STARTING.register(FabricServerConfig.INSTANCE::load);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            FakePlayerSimulationService.discardStale(server);
            FakePlayerPossession.discardStale(server);
            FakePlayerPersistence.restore(server);
            ChunkLoaderManager.reconcile(server);
            FakePlayerSimulationService.reconcile(server);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            FakePlayerSimulationService.tick(server);
            if (server.getTickCount() % 100 == 0) MannequinManager.captureAll(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (server.overworld() == null) return;
            MannequinManager.captureAll(server);
            FakePlayerPossession.stopAll(server);
            FakePlayerSimulationService.revokeAll(server);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> tickets.clear());
        UseEntityCallback.EVENT.register((player, level, hand, target, hit) -> {
            if (!(player instanceof ServerPlayer viewer)
                || !ArchWeaverConfig.canUseCommands(viewer.createCommandSourceStack())) return InteractionResult.PASS;
            if (target instanceof Mannequin mannequin
                && mannequin.entityTags().contains(MannequinManager.MANAGED_TAG)
                && MannequinManager.data(viewer.level().getServer()).find(mannequin.getUUID()).isPresent()) {
                FakePlayerMenuOpener.openMannequinInventory(viewer, mannequin.getUUID());
                return InteractionResult.SUCCESS;
            }
            if (!(target instanceof FakeServerPlayer fake)) return InteractionResult.PASS;
            if (!FakePlayerPossession.canOpenMenu(viewer, fake)) return InteractionResult.FAIL;
            FakePlayerMenuOpener.openInventory(viewer, fake);
            return InteractionResult.SUCCESS;
        });
        // 玩家事件也覆盖无网络客户端的假人；LEAVE 在原版保存 playerdata 之前触发。
        ServerPlayerEvents.JOIN.register(FabricEvents::joined);
        ServerPlayerEvents.LEAVE.register(FabricEvents::left);
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> syncSelf(newPlayer));
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> syncSelf(player));
        EntityTrackingEvents.START_TRACKING.register((entity, viewer) -> {
            if (entity instanceof Mannequin mannequin
                && MannequinManager.data(viewer.level().getServer()).find(mannequin.getUUID()).isPresent()) {
                PlatformNetworking.sendToPlayer(viewer, MannequinManager.anglesPayload(viewer.level().getServer(), mannequin.getUUID()));
                MannequinManager.sendBodyRotation(viewer, mannequin);
                viewer.level().getServer().getPlayerList().sendActiveEffects(mannequin, viewer.connection);
            }
            if (entity instanceof FakeServerPlayer fake) {
                fake.actions().syncBodyRotation(viewer);
                FakePlayerAliasSync.syncTo(viewer, fake);
                fake.getActiveEffects().forEach(effect -> viewer.connection.send(new ClientboundUpdateMobEffectPacket(fake.getId(), effect, false)));
            }
            if (entity instanceof LivingEntity living) PlatformNetworking.sendToPlayer(viewer, new InvisibilityLevelPayload(living));
        });
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (entity instanceof FakeServerPlayer fake && FakePlayerPossession.handleShellDeath(fake, source)) return false;
            return !(entity instanceof ServerPlayer player && FakePlayerPossession.handleActiveBodyDeath(player, source));
        });

    }
    private static void joined(ServerPlayer player) {
        ExternalFakePlayerSupport.track(player);
        if (!(player instanceof FakeServerPlayer)) MannequinManager.syncAnglesTo(player);
        SkinService.syncTo(player);
        FakePlayerPossession.syncTo(player);
        if (player instanceof FakeServerPlayer fake) FakePlayerAliasSync.broadcast(fake);
        else FakePlayerAliasSync.syncTo(player);
        if (FakePlayerManager.isFakePlayer(player)) SkinService.syncFakePresence(player.level().getServer(), player.getUUID(), true);
        syncSelf(player);
    }
    private static void left(ServerPlayer player) {
        if (player instanceof FakeServerPlayer fake) FakePlayerAliasSync.clear(fake);
        if (FakePlayerManager.isFakePlayer(player)) SkinService.syncFakePresence(player.level().getServer(), player.getUUID(), false);
        if (!(player instanceof FakeServerPlayer) && !FakePlayerPossession.stop(player)) FakePlayerPossession.discard(player);
    }
    private static void syncSelf(ServerPlayer player) { PlatformNetworking.sendToPlayer(player, new InvisibilityLevelPayload(player)); }
}
