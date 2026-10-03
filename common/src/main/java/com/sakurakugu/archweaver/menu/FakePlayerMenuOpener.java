package com.sakurakugu.archweaver.menu;

import com.sakurakugu.archweaver.config.ArchWeaverConfig;
import com.sakurakugu.archweaver.chunkloading.ChunkLoaderManager;
import com.sakurakugu.archweaver.chunkloading.FakePlayerLoadPolicy;
import com.sakurakugu.archweaver.chunkloading.FakePlayerLoadMode;
import com.sakurakugu.archweaver.chunkloading.FakePlayerSimulationService;
import com.sakurakugu.archweaver.entity.FakePlayerActions;
import com.sakurakugu.archweaver.entity.FakePlayerManager;
import com.sakurakugu.archweaver.entity.FakePlayerPossession;
import com.sakurakugu.archweaver.entity.FakeServerPlayer;
import com.sakurakugu.archweaver.persistence.FakePlayerPersistence;
import com.sakurakugu.archweaver.persistence.FakePlayerSavedData;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuConstructor;
import com.sakurakugu.archweaver.platform.PlatformNetworking;

/** 统一创建假人全局菜单和物品栏管理页面。 */
public final class FakePlayerMenuOpener {
    private FakePlayerMenuOpener() {
    }

    public static void openSpawn(ServerPlayer viewer) {
        openSpawnMenu(viewer);
    }

    public static void openPresetManagement(ServerPlayer viewer) {
        openPresetManagement(viewer, false);
    }

    public static void openPresetManagement(ServerPlayer viewer, boolean openGroupsInitially) {
        FakePlayerSavedData savedData = FakePlayerPersistence.data(viewer.level().getServer());
        List<PresetManagementMenu.PresetSummary> presets = savedData.presets().stream()
            .map(preset -> new PresetManagementMenu.PresetSummary(
                preset.id(), preset.description(), preset.player().name()))
            .sorted(java.util.Comparator.comparing(
                PresetManagementMenu.PresetSummary::id, String.CASE_INSENSITIVE_ORDER))
            .toList();
        List<PresetManagementMenu.GroupSummary> groups = savedData.groups().stream()
            .map(group -> new PresetManagementMenu.GroupSummary(group.id(), group.presetIds()))
            .sorted(java.util.Comparator.comparing(
                PresetManagementMenu.GroupSummary::id, String.CASE_INSENSITIVE_ORDER))
            .toList();
        List<String> onlinePlayers = FakePlayerManager.all(viewer.level().getServer()).stream()
            .map(fake -> fake.getGameProfile().name())
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
        viewer.openMenu(
            new ManagementMenuProvider(
                (containerId, inventory, player) -> new PresetManagementMenu(
                    containerId, inventory, openGroupsInitially, presets, groups, onlinePlayers),
                Component.translatable("gui.fakeplayer.preset.title")
            ),
            data -> {
                data.writeBoolean(openGroupsInitially);
                data.writeVarInt(presets.size());
                presets.forEach(preset -> {
                    data.writeUtf(preset.id());
                    data.writeUtf(preset.description());
                    data.writeUtf(preset.playerName());
                });
                data.writeVarInt(groups.size());
                groups.forEach(group -> {
                    data.writeUtf(group.id());
                    writeStrings(data, group.presetIds());
                });
                writeStrings(data, onlinePlayers);
            }
        );
    }

    private static void writeStrings(net.minecraft.network.RegistryFriendlyByteBuf data, List<String> values) {
        data.writeVarInt(values.size());
        values.forEach(data::writeUtf);
    }

    private static void openSpawnMenu(ServerPlayer viewer) {
        viewer.openMenu(
            new ManagementMenuProvider(
                (containerId, inventory, player) -> new GlobalFakePlayerMenu(containerId, inventory),
                Component.translatable("gui.fakeplayer.global.title")
            ),
            data -> { }
        );
    }

    public static void openInventory(ServerPlayer viewer, FakeServerPlayer fake) {
        openStorage(viewer, fake, FakePlayerInventoryMenu.View.INVENTORY);
    }

    public static void openPossessedInventory(ServerPlayer viewer, FakeServerPlayer fake) {
        openStorage(viewer, fake, FakePlayerInventoryMenu.View.POSSESSED_INVENTORY);
    }

    public static void openEnderChest(ServerPlayer viewer, FakeServerPlayer fake) {
        openStorage(viewer, fake, FakePlayerInventoryMenu.View.ENDER_CHEST);
    }

    private static void openStorage(ServerPlayer viewer, FakeServerPlayer fake, FakePlayerInventoryMenu.View view) {
        if (!canManage(viewer, fake)) {
            return;
        }
        boolean possessedByViewer = FakePlayerPossession.isControlling(viewer, fake);
        boolean targetOccupied = FakePlayerPossession.isPossessed(fake);
        FakePlayerLoadPolicy simulation = ChunkLoaderManager.data(viewer.level().getServer())
            .policy(fake.getUUID()).orElse(new FakePlayerLoadPolicy(fake.getUUID(), FakePlayerLoadMode.PLAYER, 0));
        Component title = Component.translatable(
            view == FakePlayerInventoryMenu.View.ENDER_CHEST
                ? "gui.fakeplayer.ender_chest"
                : "gui.fakeplayer.inventory",
            fake.getGameProfile().name()
        );
        viewer.openMenu(
            new ManagementMenuProvider(
                (containerId, inventory, player) -> new FakePlayerInventoryMenu(
                    containerId, inventory, fake, view, possessedByViewer, targetOccupied),
                title
            ),
            data -> {
                data.writeUtf(fake.getGameProfile().name());
                data.writeVarInt(view.ordinal());
                data.writeVarInt(fake.getId());
                data.writeBoolean(possessedByViewer);
                data.writeBoolean(targetOccupied);
                data.writeVarInt(FakePlayerInventoryMenu.automationMask(fake));
                data.writeVarInt(FakePlayerInventoryMenu.continuousControlMask(fake));
                data.writeVarInt(fake.actions().repeatInterval(
                    FakePlayerActions.ScheduledAction.ATTACK));
                data.writeVarInt(fake.actions().repeatInterval(
                    FakePlayerActions.ScheduledAction.USE));
                data.writeVarInt(fake.actions().repeatInterval(
                    FakePlayerActions.ScheduledAction.JUMP));
                data.writeVarInt(Math.round(fake.getXRot()));
                data.writeVarInt(Math.round(fake.getYRot()));
                data.writeVarInt(Math.round(fake.yBodyRot));
                data.writeBoolean(fake.actions().bodyFollowsHead());
                data.writeVarInt(simulation.mode().ordinal());
                data.writeVarInt(simulation.simulationDistance());
                data.writeVarInt(FakePlayerSimulationService.maxSimulationDistance(viewer.level().getServer()));
            }
        );
    }

    private static boolean canManage(ServerPlayer viewer, FakeServerPlayer fake) {
        if (!FakePlayerPossession.isPossessed(fake)) {
            return true;
        }
        viewer.sendSystemMessage(Component.translatable("gui.fakeplayer.possess_locked"));
        return false;
    }

    /** 切换管理容器时由服务端清理旧菜单，客户端直接打开新页面，保持光标位置。 */
    private record ManagementMenuProvider(MenuConstructor constructor, Component title) implements MenuProvider {
        @Override
        public Component getDisplayName() {
            return title;
        }

        @Override
        public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
            return constructor.createMenu(containerId, inventory, player);
        }

        @Override
        public boolean shouldTriggerClientSideContainerClosingOnOpen() {
            return false;
        }
    }
}
