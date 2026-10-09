package com.sakurakugu.archweaver.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.sakurakugu.archweaver.ArchWeaverMod;
import com.sakurakugu.archweaver.network.PossessionStatePayload;
import com.sakurakugu.archweaver.network.StopPossessionPayload;
import com.sakurakugu.archweaver.network.ChunkMapOpenTarget;
import com.sakurakugu.archweaver.network.ChunkMapSnapshotPayload;
import com.sakurakugu.archweaver.network.BodyRotationPayload;
import com.sakurakugu.archweaver.network.MannequinAnglesPayload;
import com.sakurakugu.archweaver.network.FakePlayerAliasPayload;
import com.sakurakugu.archweaver.network.OpenMainPagePayload;
import com.sakurakugu.archweaver.client.chunkloading.ClientChunkLoadingState;
import com.sakurakugu.archweaver.client.chunkloading.ChunkLoadingDebugEntry;
import com.sakurakugu.archweaver.client.chunkloading.ChunkMapClientConfig;
import com.sakurakugu.archweaver.config.NeoForgeConfigs;
import com.sakurakugu.archweaver.platform.PlatformNetworking;
import com.sakurakugu.archweaver.client.ui.InventorySlotButton;
import com.sakurakugu.archweaver.client.ui.TransferButton;
import com.sakurakugu.archweaver.client.camera.CameraPreferences;
import com.sakurakugu.archweaver.client.camera.ClientCamera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.gui.components.debug.DebugScreenProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterDebugEntriesEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/** 客户端入口，负责注册并处理客户端快捷键。 */
@Mod(value = ArchWeaverMod.MOD_ID, dist = Dist.CLIENT)
public final class ArchWeaverClientMod {
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

    public ArchWeaverClientMod(IEventBus modBus, ModContainer container) {
        ChunkMapClientConfig.install(NeoForgeConfigs.CLIENT);
        CameraPreferences.install(NeoForgeConfigs.CLIENT);
        PlatformNetworking.installClientSender(payload -> net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(payload));
        container.registerConfig(ModConfig.Type.CLIENT, NeoForgeConfigs.CLIENT.spec(), ArchWeaverMod.MOD_ID + "-client.toml");
        modBus.addListener(ArchWeaverClientMod::registerKeys);
        modBus.addListener(ArchWeaverClientMod::registerDebugEntries);
        modBus.addListener(ArchWeaverClientMod::registerClientPayloads);
        NeoForge.EVENT_BUS.addListener(ArchWeaverClientMod::clientTick);
        NeoForge.EVENT_BUS.addListener(ArchWeaverClientMod::cameraTick);
        NeoForge.EVENT_BUS.addListener(ArchWeaverClientMod::addInventoryButtons);
        NeoForge.EVENT_BUS.addListener(ArchWeaverClientMod::trackScreenOpening);
        NeoForge.EVENT_BUS.addListener(ArchWeaverClientMod::trackScreenClosing);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(OPEN_CHUNK_MAP);
        event.register(OPEN_MAIN_PAGE);
        event.register(STOP_POSSESSION);
    }

    private static void cameraTick(ClientTickEvent.Pre event) {
        ClientCamera.tick(Minecraft.getInstance());
    }

    private static void registerDebugEntries(RegisterDebugEntriesEvent event) {
        event.register(ChunkLoadingDebugEntry.ID, new ChunkLoadingDebugEntry());
        event.includeInProfile(ChunkLoadingDebugEntry.ID, DebugScreenProfile.DEFAULT,
            DebugScreenEntryStatus.ALWAYS_ON);
        event.includeInProfile(ChunkLoadingDebugEntry.ID, DebugScreenProfile.PERFORMANCE,
            DebugScreenEntryStatus.ALWAYS_ON);
    }

    private static void registerClientPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(ChunkMapSnapshotPayload.TYPE,
            (payload, context) -> ClientChunkLoadingState.accept(payload));
        event.register(OpenMainPagePayload.TYPE,
            (payload, context) -> ClientChunkLoadingState.openMainScreen(mainViewOf(payload.view())));
        event.register(PossessionStatePayload.TYPE,
            (payload, context) -> ClientPossession.accept(payload));
        event.register(FakePlayerAliasPayload.TYPE,
            (payload, context) -> ClientFakePlayerAliases.accept(payload));
        event.register(BodyRotationPayload.TYPE,
            (payload, context) -> ClientBodyRotation.accept(payload));
        event.register(com.sakurakugu.archweaver.network.TargetInfoPayload.TYPE, (payload, context) -> {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null && player.containerMenu instanceof com.sakurakugu.archweaver.menu.TargetInfoMenu menu)
                menu.acceptTargetInfo(payload);
        });
        event.register(MannequinAnglesPayload.TYPE,
            (payload, context) -> ClientMannequinAngles.set(payload.id(), payload.leftArm(), payload.rightArm(),
                payload.leftLeg(), payload.rightLeg()));
    }

    /** 网络包里的页面枚举措意与客户端界面类型解耦，在这里做一次映射。 */
    private static MainPageScreen.View mainViewOf(OpenMainPagePayload.View view) {
        return view == OpenMainPagePayload.View.MAP
            ? MainPageScreen.View.MAP : MainPageScreen.View.FAKE_PLAYERS;
    }

    private static void trackScreenOpening(ScreenEvent.Opening event) {
        ClientScreenNavigation.onOpening(event.getCurrentScreen(), event.getNewScreen());
    }

    private static void trackScreenClosing(ScreenEvent.Closing event) {
        ClientScreenNavigation.onClosing(event.getScreen());
    }

    private static void clientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientScreenNavigation.tick();
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

    /** 附身期间在原版个人背包中提供可见的退出入口。 */
    private static void addInventoryButtons(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?> containerScreen
            && TransferButton.supports(containerScreen)
            && !(containerScreen instanceof FakePlayerInventoryScreen)
            && !(containerScreen instanceof GlobalFakePlayerScreen)
            && ClientGlobalSettings.containerTransferButtons()) {
            TransferButton.forContainer(containerScreen).forEach(event::addListener);
        }
        if (!(event.getScreen() instanceof InventoryScreen || event.getScreen() instanceof CreativeModeInventoryScreen)
            || !ClientPossession.active()) {
            return;
        }
        var screen = event.getScreen();
        int buttonX = screen.width / 2
            + (screen instanceof CreativeModeInventoryScreen ? 28 : -12);
        int buttonY = screen.height / 2 
            + (screen instanceof CreativeModeInventoryScreen ? -50 : -40);;
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
        event.addListener(button);
    }
}
