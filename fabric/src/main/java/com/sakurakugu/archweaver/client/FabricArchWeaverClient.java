package com.sakurakugu.archweaver.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.sakurakugu.archweaver.ArchWeaverMod;
import com.sakurakugu.archweaver.network.PossessionStatePayload;
import com.sakurakugu.archweaver.network.StopPossessionPayload;
import com.sakurakugu.archweaver.network.ChunkMapOpenTarget;
import com.sakurakugu.archweaver.network.ChunkMapSnapshotPayload;
import com.sakurakugu.archweaver.network.BodyRotationPayload;
import com.sakurakugu.archweaver.network.MannequinAnglesPayload;
import com.sakurakugu.archweaver.network.SkinSyncPayload;
import com.sakurakugu.archweaver.client.skin.ClientSkinCache;
import com.sakurakugu.archweaver.network.FakePlayerAliasPayload;
import com.sakurakugu.archweaver.network.OpenMainPagePayload;
import com.sakurakugu.archweaver.client.chunkloading.ClientChunkLoadingState;
import com.sakurakugu.archweaver.client.chunkloading.ChunkMapFrontends;
import com.sakurakugu.archweaver.client.chunkloading.MapBackend;
import com.sakurakugu.archweaver.client.chunkloading.ChunkLoadingDebugEntry;
import com.sakurakugu.archweaver.client.chunkloading.ChunkMapClientConfig;
import com.sakurakugu.archweaver.platform.PlatformNetworking;
import com.sakurakugu.archweaver.client.ui.InventorySlotButton;
import com.sakurakugu.archweaver.client.ui.TransferButton;
import com.sakurakugu.archweaver.client.camera.CameraPreferences;
import com.sakurakugu.archweaver.client.camera.ClientCamera;
import com.sakurakugu.archweaver.client.camera.CameraExclusivity;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.gui.components.debug.DebugScreenProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import com.sakurakugu.archweaver.client.ui.PlatformGui;
import com.sakurakugu.archweaver.menu.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;

/** Fabric 客户端的网络、按键、界面和相机接线。 */
public final class FabricArchWeaverClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(
        Identifier.fromNamespaceAndPath(ArchWeaverMod.MOD_ID, "main")
    );
    private static final KeyMapping OPEN_CHUNK_MAP = new KeyMapping(
        "key.archweaver.open_chunk_map", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, CATEGORY
    );
    private static final KeyMapping OPEN_MAIN_PAGE = new KeyMapping(
        "key.archweaver.open_main_page", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY
    );
    private static final KeyMapping STOP_POSSESSION = new KeyMapping(
        "key.archweaver.stop_possession", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY
    );
    private static int refreshTicks;
    private static CreativeModeInventoryScreen creativeInventoryScreen;
    private static Button creativePossessionButton;
    public void onInitializeClient() {
        var config = new FabricClientConfig();
        ChunkMapClientConfig.install(config);
        ChunkMapFrontends.setInstalled(MapBackend.JOURNEYMAP, net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("journeymap"));
        boolean xaeroInstalled = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("xaeroworldmap");
        ChunkMapFrontends.setInstalled(MapBackend.XAERO, xaeroInstalled);
        if (xaeroInstalled) com.sakurakugu.archweaver.compat.xaero.ArchWeaverXaeroMap.install();
        CameraPreferences.install(config);
        CameraExclusivity.install(TweakerooCameraCompat.create());
        PlatformGui.install(new FabricGui());
        PlatformNetworking.installClientSender(ClientPlayNetworking::send);
        KeyMappingHelper.registerKeyMapping(OPEN_CHUNK_MAP);
        KeyMappingHelper.registerKeyMapping(OPEN_MAIN_PAGE);
        KeyMappingHelper.registerKeyMapping(STOP_POSSESSION);
        registerClientPayloads();
        MenuScreens.register(ModMenus.GLOBAL_FAKE_PLAYER.get(), GlobalFakePlayerScreen::new);
        MenuScreens.register(ModMenus.PRESET_MANAGEMENT.get(), PresetManagementScreen::new);
        MenuScreens.register(ModMenus.FAKE_PLAYER_INVENTORY.get(), FakePlayerInventoryScreen::new);
        MenuScreens.register(ModMenus.MANNEQUIN_INVENTORY.get(), MannequinInventoryScreen::new);
        ClientTickEvents.START_CLIENT_TICK.register(minecraft -> {
            ClientCamera.tick(minecraft);
            ClientInvisibility.updateLevel();
        });
        ClientTickEvents.END_CLIENT_TICK.register(FabricArchWeaverClient::clientTick);
        ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> addInventoryButtons(screen));
        ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> ClientInvisibility.apply(entity));
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> ClientInvisibility.remove(entity));
        ClientPlayConnectionEvents.DISCONNECT.register((listener, minecraft) -> ClientInvisibility.clear());
        LevelRenderEvents.END_EXTRACTION.register(context -> {
            var ray = ClientCamera.bodyRay(context.deltaTracker().getGameTimeDeltaPartialTick(false));
            if (ray == null || ray.start().distanceToSqr(ray.end()) < 1.0E-8) return;
            try (var ignored = context.levelRenderer().collectPerFrameGizmos()) {
                Gizmos.line(ray.start(), ray.end(), 0xCCFFFF55, 1.5F);
            }
        });
        DebugScreenEntries.register(ChunkLoadingDebugEntry.ID, new ChunkLoadingDebugEntry());
        for (var profile : java.util.List.of(DebugScreenProfile.DEFAULT, DebugScreenProfile.PERFORMANCE)) {
            var entries = new java.util.HashMap<>(DebugScreenEntries.PROFILES.get(profile));
            entries.put(ChunkLoadingDebugEntry.ID, DebugScreenEntryStatus.ALWAYS_ON);
            DebugScreenEntries.PROFILES.put(profile, entries);
        }
    }
    private static void registerClientPayloads() {
        ClientPlayNetworking.registerGlobalReceiver(com.sakurakugu.archweaver.network.InvisibilityLevelPayload.TYPE,
            (payload, context) -> ClientInvisibility.accept(payload));
        ClientPlayNetworking.registerGlobalReceiver(com.sakurakugu.archweaver.network.ChunkMapApplyResultPayload.TYPE,
            (payload, context) -> ClientChunkLoadingState.acceptResult(payload));
        ClientPlayNetworking.registerGlobalReceiver(com.sakurakugu.archweaver.network.ChunkMapRequestFailedPayload.TYPE,
            (payload, context) -> ClientChunkLoadingState.requestFailed(payload));
        ClientPlayNetworking.registerGlobalReceiver(ChunkMapSnapshotPayload.TYPE,
            (payload, context) -> ClientChunkLoadingState.accept(payload));
        ClientPlayNetworking.registerGlobalReceiver(OpenMainPagePayload.TYPE,
            (payload, context) -> ClientChunkLoadingState.openMainScreen(mainViewOf(payload.view())));
        ClientPlayNetworking.registerGlobalReceiver(PossessionStatePayload.TYPE,
            (payload, context) -> ClientPossession.accept(payload));
        ClientPlayNetworking.registerGlobalReceiver(FakePlayerAliasPayload.TYPE,
            (payload, context) -> ClientFakePlayerAliases.accept(payload));
        ClientPlayNetworking.registerGlobalReceiver(BodyRotationPayload.TYPE,
            (payload, context) -> ClientBodyRotation.accept(payload));
        ClientPlayNetworking.registerGlobalReceiver(com.sakurakugu.archweaver.network.TargetInfoPayload.TYPE, (payload, context) -> {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null && player.containerMenu instanceof com.sakurakugu.archweaver.menu.TargetInfoMenu menu)
                menu.acceptTargetInfo(payload);
        });
        ClientPlayNetworking.registerGlobalReceiver(MannequinAnglesPayload.TYPE,
            (payload, context) -> ClientMannequinAngles.set(payload.id(), payload.leftArm(), payload.rightArm(),
                payload.leftLeg(), payload.rightLeg()));
        ClientPlayNetworking.registerGlobalReceiver(SkinSyncPayload.TYPE, (payload, context) -> ClientSkinCache.accept(payload));
    }
    private static MainPageScreen.View mainViewOf(OpenMainPagePayload.View view) {
        return view == OpenMainPagePayload.View.MAP
            ? MainPageScreen.View.MAP : MainPageScreen.View.FAKE_PLAYERS;
    }
    private static void clientTick(Minecraft minecraft) {
        ClientScreenNavigation.tick();
        ChunkMapFrontends.tick();
        ClientPossession.tick(minecraft);
        ClientBodyRotation.tick(minecraft);
        updateCreativePossessionButton(minecraft);
        while (STOP_POSSESSION.consumeClick()) {
            if (ClientPossession.active()) {
                PlatformNetworking.sendToServer(new StopPossessionPayload());
            }
        }
        while (OPEN_CHUNK_MAP.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                ClientChunkLoadingState.openMap(ClientChunkLoadingState.MapReturnTarget.CLOSE,
                    ChunkMapOpenTarget.MAP);
            }
        }
        while (OPEN_MAIN_PAGE.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                ClientChunkLoadingState.openMainScreen();
            }
        }
        if (minecraft.player == null) {
            ClientChunkLoadingState.clear();
            ClientFakePlayerAliases.clear();
            ClientMannequinAngles.clear();
            ClientSkinCache.clearAll();
            refreshTicks = 0;
        } else if (!minecraft.debugEntries.isOverlayVisible()) {
            refreshTicks = 0;
        } else if (refreshTicks-- <= 0) {
            PlatformNetworking.sendToServer(ClientChunkLoadingState.request(ChunkMapOpenTarget.NONE));
            refreshTicks = 40;
        }
    }
    private static void updateCreativePossessionButton(Minecraft minecraft) {
        if (!(minecraft.screen instanceof CreativeModeInventoryScreen screen)
            || screen != creativeInventoryScreen || creativePossessionButton == null) {
            return;
        }
        creativePossessionButton.visible = ClientPossession.active() && screen.isInventoryOpen();
    }
    private static void addInventoryButtons(net.minecraft.client.gui.screens.Screen screen) {
        if (screen instanceof AbstractContainerScreen<?> containerScreen
            && TransferButton.supports(containerScreen)
            && !(containerScreen instanceof FakePlayerInventoryScreen)
            && !(containerScreen instanceof GlobalFakePlayerScreen)
            && ClientGlobalSettings.containerTransferButtons()) {
            TransferButton.forContainer(containerScreen).forEach(Screens.getWidgets(screen)::add);
        }
        if (!(screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen)
            || !ClientPossession.active()) {
            return;
        }
        int buttonX = screen.width / 2
            + (screen instanceof CreativeModeInventoryScreen ? 28 : -12);
        int buttonY = screen.height / 2 
            + (screen instanceof CreativeModeInventoryScreen ? -50 : -40);
        Button button = new InventorySlotButton(
            buttonX,
            buttonY,
            FakePlayerInventoryScreen.POSSESSION_EXIT_ICON,
            Component.translatable("gui.archweaver.fakeplayer.stop_possessing"),
            clicked -> PlatformNetworking.sendToServer(new StopPossessionPayload())
        );
        if (screen instanceof CreativeModeInventoryScreen creativeScreen) {
            creativeInventoryScreen = creativeScreen;
            creativePossessionButton = button;
            button.visible = creativeScreen.isInventoryOpen();
        }
        Screens.getWidgets(screen).add(button);
    }
}
