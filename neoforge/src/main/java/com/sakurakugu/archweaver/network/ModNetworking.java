package com.sakurakugu.archweaver.network;

import com.sakurakugu.archweaver.config.ArchWeaverConfig;
import com.sakurakugu.archweaver.command.FakePlayerCommand;
import com.sakurakugu.archweaver.menu.FakePlayerMenuOpener;
import com.sakurakugu.archweaver.menu.PresetManagementActions;
import com.sakurakugu.archweaver.menu.PresetManagementMenu;
import com.sakurakugu.archweaver.menu.ChunkLoaderActions;
import com.sakurakugu.archweaver.menu.GlobalFakePlayerMenu;
import com.sakurakugu.archweaver.menu.FakePlayerInventoryMenu;
import com.sakurakugu.archweaver.menu.FakePlayerManagementActions;
import com.sakurakugu.archweaver.entity.FakePlayerPossession;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.PacketDistributor;
import com.sakurakugu.archweaver.chunkloading.ChunkLoaderManager;
import com.sakurakugu.archweaver.chunkloading.ChunkLoadApplicationService;
import com.sakurakugu.archweaver.chunkloading.FakePlayerSimulationService;
import net.minecraft.network.chat.Component;

/** 注册客户端与服务端之间的假人菜单请求。 */
public final class ModNetworking {
    private ModNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("2");
        registrar.playToServer(
            OpenFakePlayerPagePayload.TYPE,
            OpenFakePlayerPagePayload.STREAM_CODEC,
            (payload, context) -> {
                if (context.player() instanceof ServerPlayer player
                    && ArchWeaverConfig.canUseCommands(player.createCommandSourceStack())) {
                    switch (payload.page()) {
                        case SPAWN -> FakePlayerMenuOpener.openSpawn(player);
                        case PRESETS -> FakePlayerMenuOpener.openPresetManagement(player);
                    }
                }
            }
        );
        registrar.playToServer(
            OpenFakePlayerInventoryPayload.TYPE,
            OpenFakePlayerInventoryPayload.STREAM_CODEC,
            (payload, context) -> {
                if (context.player() instanceof ServerPlayer player
                    && ArchWeaverConfig.canUseCommands(player.createCommandSourceStack())) {
                    var fake = com.sakurakugu.archweaver.entity.FakePlayerManager.find(
                        player.level().getServer(), payload.targetName());
                    if (fake != null) FakePlayerMenuOpener.openInventory(player, fake);
                }
            }
        );
        registrar.playToServer(
            ToggleGlobalSettingPayload.TYPE,
            ToggleGlobalSettingPayload.STREAM_CODEC,
            (payload, context) -> {
                if (context.player() instanceof ServerPlayer player
                    && ArchWeaverConfig.canUseCommands(player.createCommandSourceStack())
                    && ArchWeaverConfig.toggleGlobalSetting(payload.settingIndex())) {
                    // 别名与顺序没变、只换了显示开关时只重发头顶名牌，改动 Tab 顺序时才连 Tab 一起刷新。
                    switch (ArchWeaverConfig.GlobalSetting.values()[payload.settingIndex()]) {
                        case FAKE_PLAYER_ALIAS_FIRST ->
                            com.sakurakugu.archweaver.entity.FakePlayerAliasSync.refreshAll(player.level().getServer());
                        case FAKE_PLAYER_EMPTY_ALIAS_MARKER ->
                            com.sakurakugu.archweaver.entity.FakePlayerAliasSync.resyncViewers(player.level().getServer());
                        default -> { }
                    }
                    PacketDistributor.sendToPlayer(player, ChunkMapSnapshotPayload.create(player,
                        ChunkLoaderManager.data(player.level().getServer()), ChunkMapOpenTarget.NONE));
                }
            }
        );
        registrar.playToServer(
            ToggleFakePlayerRestorePayload.TYPE,
            ToggleFakePlayerRestorePayload.STREAM_CODEC,
            (payload, context) -> {
                if (context.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof FakePlayerInventoryMenu menu
                    && player.containerMenu.containerId == payload.containerId()
                    && menu.target() != null
                    && menu.target().getUUID().equals(payload.fakePlayerId())
                    && ArchWeaverConfig.canUseCommands(player.createCommandSourceStack())
                    && menu.stillValid(player)) {
                    com.sakurakugu.archweaver.persistence.FakePlayerPersistence.toggleRestoreOnRestart(
                        player.level().getServer(), payload.fakePlayerId());
                    PacketDistributor.sendToPlayer(player, ChunkMapSnapshotPayload.create(player,
                        ChunkLoaderManager.data(player.level().getServer()), ChunkMapOpenTarget.NONE));
                }
            }
        );
        registrar.playToServer(
            PresetActionPayload.TYPE,
            PresetActionPayload.STREAM_CODEC,
            (payload, context) -> {
                if (context.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof PresetManagementMenu
                    && player.containerMenu.containerId == payload.containerId()
                    && ArchWeaverConfig.canUseCommands(player.createCommandSourceStack())) {
                    PresetManagementActions.handle(player, payload);
                }
            }
        );
        registrar.playToServer(
            SpawnFakePlayerPayload.TYPE,
            SpawnFakePlayerPayload.STREAM_CODEC,
            (payload, context) -> {
                if (context.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof GlobalFakePlayerMenu
                    && player.containerMenu.containerId == payload.containerId()
                    && ArchWeaverConfig.canUseCommands(player.createCommandSourceStack())) {
                    FakePlayerCommand.spawnFromMenu(player, payload.name());
                }
            }
        );
        registrar.playToServer(
            RenameFakePlayerPayload.TYPE,
            RenameFakePlayerPayload.STREAM_CODEC,
            (payload, context) -> {
                if (context.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof FakePlayerInventoryMenu
                    && player.containerMenu.containerId == payload.containerId()
                    && ArchWeaverConfig.canUseCommands(player.createCommandSourceStack())) {
                    FakePlayerManagementActions.rename(player, payload.name());
                }
            }
        );
        registrar.playToServer(
            SetFakePlayerAliasPayload.TYPE,
            SetFakePlayerAliasPayload.STREAM_CODEC,
            (payload, context) -> {
                if (context.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof FakePlayerInventoryMenu
                    && player.containerMenu.containerId == payload.containerId()
                    && ArchWeaverConfig.canUseCommands(player.createCommandSourceStack())) {
                    FakePlayerManagementActions.setAlias(player, payload.alias());
                }
            }
        );
        registrar.playToServer(
            FakePlayerSimulationPayload.TYPE,
            FakePlayerSimulationPayload.STREAM_CODEC,
            (payload, context) -> {
                if (context.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof FakePlayerInventoryMenu menu
                    && player.containerMenu.containerId == payload.containerId()
                    && ArchWeaverConfig.canUseCommands(player.createCommandSourceStack())
                    && menu.target() != null
                    && menu.stillValid(player)) {
                    var result = FakePlayerSimulationService.setPolicy(player.level().getServer(), menu.target().getUUID(),
                        payload.mode(), payload.distance());
                    if (result.successful()) menu.broadcastChanges();
                    else player.sendSystemMessage(Component.literal(result.reason()));
                }
            }
        );
        registrar.playToServer(
            FakePlayerViewRotationPayload.TYPE,
            FakePlayerViewRotationPayload.STREAM_CODEC,
            (payload, context) -> {
                if (context.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof FakePlayerInventoryMenu menu
                    && player.containerMenu.containerId == payload.containerId()) {
                    menu.setViewRotation(player, payload.pitch(), payload.yaw());
                }
            }
        );
        registrar.playToServer(
            ChunkLoaderActionPayload.TYPE,
            ChunkLoaderActionPayload.STREAM_CODEC,
            (payload, context) -> {
                if (context.player() instanceof ServerPlayer player
                    && ArchWeaverConfig.canUseCommands(player.createCommandSourceStack())) {
                    ChunkLoaderActions.handle(player, payload);
                }
            }
        );
        registrar.playToServer(
            ApplyChunkLoadEditsPayload.TYPE,
            ApplyChunkLoadEditsPayload.STREAM_CODEC,
            (payload, context) -> {
                if (context.player() instanceof ServerPlayer player
                    && ArchWeaverConfig.canUseCommands(player.createCommandSourceStack())) {
                    var result = ChunkLoadApplicationService.apply(player, payload);
                    if (!result.successful()) player.sendSystemMessage(net.minecraft.network.chat.Component.literal(result.reason()));
                    PacketDistributor.sendToPlayer(player, ChunkMapSnapshotPayload.create(player,
                        ChunkLoaderManager.data(player.level().getServer()), ChunkMapOpenTarget.NONE));
                }
            }
        );
        registrar.playToServer(
            RequestChunkMapPayload.TYPE,
            RequestChunkMapPayload.STREAM_CODEC,
            (payload, context) -> {
                if (context.player() instanceof ServerPlayer player
                    && ArchWeaverConfig.canUseCommands(player.createCommandSourceStack())) {
                    var data = ChunkLoaderManager.data(player.level().getServer());
                    PacketDistributor.sendToPlayer(player,
                        ChunkMapSnapshotPayload.create(player, data,
                            payload.openTarget(),
                            payload.knownRevision(), payload.knownDimension()));
                }
            }
        );
        registrar.playToServer(
            StopPossessionPayload.TYPE,
            StopPossessionPayload.STREAM_CODEC,
            (payload, context) -> {
                if (context.player() instanceof ServerPlayer player) {
                    FakePlayerPossession.stop(player);
                }
            }
        );
        registrar.playToClient(ChunkMapSnapshotPayload.TYPE, ChunkMapSnapshotPayload.STREAM_CODEC);
        registrar.playToClient(OpenMainPagePayload.TYPE, OpenMainPagePayload.STREAM_CODEC);
        registrar.playToClient(PossessionStatePayload.TYPE, PossessionStatePayload.STREAM_CODEC);
        registrar.playToClient(BodyRotationPayload.TYPE, BodyRotationPayload.STREAM_CODEC);
        registrar.playToClient(FakePlayerAliasPayload.TYPE, FakePlayerAliasPayload.STREAM_CODEC);
    }
}
