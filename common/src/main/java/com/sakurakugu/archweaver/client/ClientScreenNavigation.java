package com.sakurakugu.archweaver.client;

import com.sakurakugu.archweaver.client.chunkloading.ChunkMapScreen;
import com.sakurakugu.archweaver.client.chunkloading.ClientChunkLoadingState;
import com.sakurakugu.archweaver.menu.FakePlayerInventoryMenu;
import com.sakurakugu.archweaver.network.ChunkMapSnapshotPayload;
import com.sakurakugu.archweaver.network.OpenFakePlayerInventoryPayload;
import com.sakurakugu.archweaver.platform.PlatformNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** 统一记录 ArchWeaver 界面的来源，并处理 Esc 返回。 */
public final class ClientScreenNavigation {
    private static final Map<Screen, BackTarget> BACK_TARGETS = new IdentityHashMap<>();
    private static final Map<Screen, Screen> BACKGROUNDS = new WeakHashMap<>();
    private static BackTarget pendingTarget;
    private static Screen pendingReturnScreen;
    private static Screen lastOpenedScreen;
    private static Screen closingScreen;
    private static BackTarget closingTarget;
    private static int backgroundRefreshTicks;

    private ClientScreenNavigation() {
    }

    /** 由 NeoForge 屏幕打开事件调用，记录新页面的上一级。 */
    public static void onOpening(Screen current, Screen next) {
        if (next == null) return;
        // 服务端重新打开容器时 Opening 事件有时不会带上旧屏幕，使用最近一次屏幕补全来源。
        Screen source = current != null ? current : closingScreen != null ? closingScreen : lastOpenedScreen;
        BackTarget target = pendingTarget;
        Screen returning = pendingReturnScreen;
        pendingTarget = null;
        pendingReturnScreen = null;
        BackTarget sourceTarget = source == closingScreen ? closingTarget : null;
        closingScreen = null;
        closingTarget = null;
        if (target == null) target = targetFor(source, next, sourceTarget);
        if (target != null) BACK_TARGETS.put(next, target);
        if (isOverlay(next)) {
            Screen background = returning != null ? BACKGROUNDS.get(returning)
                : replacesPage(source, next) ? BACKGROUNDS.get(source) : source;
            if (isArchWeaverScreen(background) && background != next) {
                background.clearFocus();
                BACKGROUNDS.put(next, background);
            }
        }
        lastOpenedScreen = next;
    }

    /** 屏幕被替换或关闭后释放来源记录。 */
    public static void onClosing(Screen screen) {
        BackTarget target = BACK_TARGETS.remove(screen);
        if (screen == lastOpenedScreen) {
            closingScreen = screen;
            closingTarget = target;
        }
    }

    /** 玩家离开世界时清空跨世界导航状态。 */
    public static void clear() {
        BACK_TARGETS.clear();
        BACKGROUNDS.clear();
        pendingTarget = null;
        pendingReturnScreen = null;
        lastOpenedScreen = null;
        closingScreen = null;
        closingTarget = null;
        backgroundRefreshTicks = 0;
    }

    /** 在关闭到游戏后清除等待替换的屏幕，避免下一次独立打开页面继承旧来源。 */
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null) {
            lastOpenedScreen = null;
            closingScreen = null;
            closingTarget = null;
            if (pendingTarget == null) BACKGROUNDS.clear();
        } else if (snapshotBackground(minecraft.screen) != null && minecraft.player != null
            && minecraft.getConnection() != null && backgroundRefreshTicks-- <= 0) {
            PlatformNetworking.sendToServer(ClientChunkLoadingState.request(false, false, false));
            backgroundRefreshTicks = 10;
        }
    }

    /** 容器页面保留实际来源；背景只绘制，不接收鼠标和键盘输入。 */
    public static boolean extractBackground(Screen current, GuiGraphicsExtractor graphics, float partialTick) {
        Screen background = BACKGROUNDS.get(current);
        if (background == null) return false;
        if (background.width != current.width || background.height != current.height) {
            background.resize(current.width, current.height);
        }
        graphics.nextStratum();
        background.extractBackground(graphics, Integer.MAX_VALUE, Integer.MAX_VALUE, partialTick);
        graphics.nextStratum();
        if (background instanceof AbstractContainerScreen<?> container) {
            container.extractContents(graphics, Integer.MAX_VALUE, Integer.MAX_VALUE, partialTick);
        } else {
            background.extractRenderState(graphics, Integer.MAX_VALUE, Integer.MAX_VALUE, partialTick);
        }
        graphics.nextStratum();
        graphics.fill(0, 0, current.width, current.height, 0x55000000);
        graphics.nextStratum();
        return true;
    }

    /** 弹层打开期间继续更新后面的地图或主页面。 */
    public static void updateBackground(ChunkMapSnapshotPayload snapshot) {
        updateBackground(Minecraft.getInstance().screen, snapshot);
    }

    public static void updateBackground(Screen current, ChunkMapSnapshotPayload snapshot) {
        Screen background = snapshotBackground(current);
        if (background instanceof ChunkMapScreen map) map.update(snapshot);
        else if (background instanceof MainPageScreen main) main.update(snapshot);
    }

    private static Screen snapshotBackground(Screen current) {
        for (Screen background = BACKGROUNDS.get(current); background != null;
             background = BACKGROUNDS.get(background)) {
            if (background instanceof ChunkMapScreen || background instanceof MainPageScreen) return background;
        }
        return null;
    }

    private static boolean isOverlay(Screen screen) {
        return screen instanceof GlobalFakePlayerScreen || screen instanceof PresetManagementScreen
            || screen instanceof FakePlayerInventoryScreen;
    }

    private static boolean isArchWeaverScreen(Screen screen) {
        return isOverlay(screen) || screen instanceof MainPageScreen || screen instanceof ChunkMapScreen;
    }

    private static boolean replacesPage(Screen current, Screen next) {
        if (current == null || current.getClass() != next.getClass()) return false;
        if (current instanceof FakePlayerInventoryScreen inventory
            && next instanceof FakePlayerInventoryScreen nextInventory) {
            return inventory.getMenu().view() == nextInventory.getMenu().view();
        }
        return true;
    }

    /** 当前页面按统一规则返回；没有来源时直接回到游戏。 */
    public static void back(Screen current) {
        BackTarget target = BACK_TARGETS.remove(current);
        Screen background = BACKGROUNDS.get(current);
        if (target == null || target.kind() == Kind.CLOSE) {
            if (target == null && fallbackBack(current)) return;
            close();
            return;
        }
        switch (target.kind()) {
            case MAIN -> {
                closeContainerForReturn(current);
                if (background instanceof MainPageScreen) Minecraft.getInstance().setScreen(background);
                else ClientChunkLoadingState.openMainScreen();
            }
            case INVENTORY -> {
                pendingTarget = target.parent() == null ? BackTarget.close() : target.parent();
                pendingReturnScreen = background;
                PlatformNetworking.sendToServer(new OpenFakePlayerInventoryPayload(target.name()));
            }
            case MAP -> {
                closeContainerForReturn(current);
                if (background instanceof ChunkMapScreen) Minecraft.getInstance().setScreen(background);
                else ClientChunkLoadingState.openMap(ClientChunkLoadingState.MapReturnTarget.CLOSE, false, false);
            }
            case CLOSE -> close();
        }
    }

    private static boolean fallbackBack(Screen current) {
        if (current instanceof FakePlayerInventoryScreen inventory) {
            if (inventory.getMenu().view() == FakePlayerInventoryMenu.View.ENDER_CHEST) {
                pendingTarget = BackTarget.close();
                PlatformNetworking.sendToServer(new OpenFakePlayerInventoryPayload(
                    inventory.getMenu().targetName()));
            } else {
                ClientChunkLoadingState.openMainScreen();
            }
            return true;
        }
        if (current instanceof GlobalFakePlayerScreen || current instanceof PresetManagementScreen) {
            closeContainerForReturn(current);
            ClientChunkLoadingState.openMainScreen();
            return true;
        }
        return false;
    }

    /** 显式关闭按钮使用此方法，不走返回来源。 */
    public static void close() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!closeContainer(minecraft.screen)) minecraft.setScreen(null);
    }

    private static boolean closeContainer(Screen current) {
        Minecraft minecraft = Minecraft.getInstance();
        if (current instanceof AbstractContainerScreen<?> && minecraft.player != null) {
            minecraft.player.closeContainer();
            return true;
        }
        return false;
    }

    /** 返回其他界面时只关闭容器，避免原版切回游戏后捕获鼠标并将光标居中。 */
    private static void closeContainerForReturn(Screen current) {
        Minecraft minecraft = Minecraft.getInstance();
        if (current instanceof AbstractContainerScreen<?> container && minecraft.player != null
            && minecraft.player.containerMenu == container.getMenu()) {
            minecraft.player.connection.send(new ServerboundContainerClosePacket(container.getMenu().containerId));
            minecraft.player.containerMenu = minecraft.player.inventoryMenu;
        }
    }

    private static BackTarget targetFor(Screen current, Screen next, BackTarget closingInherited) {
        if (current == null) return BackTarget.close();

        BackTarget inherited = closingInherited != null ? closingInherited : BACK_TARGETS.get(current);
        if (next instanceof MainPageScreen) return BackTarget.close();
        if (current instanceof MainPageScreen) return BackTarget.main();
        if (current instanceof GlobalFakePlayerScreen
            && next instanceof FakePlayerInventoryScreen) {
            return inherited == null ? BackTarget.close() : inherited;
        }
        if (current instanceof FakePlayerInventoryScreen inventory
            && next instanceof FakePlayerInventoryScreen nextInventory) {
            if (replacesPage(current, next)) return inherited == null ? BackTarget.close() : inherited;
            if (nextInventory.getMenu().view() == FakePlayerInventoryMenu.View.ENDER_CHEST) {
                return BackTarget.inventory(inventory.getMenu().targetName(), inherited);
            }
            return inherited == null ? BackTarget.close() : inherited;
        }
        if (current instanceof ChunkMapScreen && isOverlay(next)) {
            return BackTarget.map();
        }
        if ((current instanceof GlobalFakePlayerScreen || current instanceof PresetManagementScreen)
            && next.getClass() == current.getClass()) {
            return inherited == null ? BackTarget.close() : inherited;
        }
        return inherited;
    }

    private record BackTarget(Kind kind, String name, BackTarget parent) {
        private static BackTarget close() { return new BackTarget(Kind.CLOSE, "", null); }
        private static BackTarget main() { return new BackTarget(Kind.MAIN, "", null); }
        private static BackTarget inventory(String name, BackTarget parent) {
            return new BackTarget(Kind.INVENTORY, name, parent);
        }
        private static BackTarget map() { return new BackTarget(Kind.MAP, "", null); }
    }

    private enum Kind {
        CLOSE,
        MAIN,
        INVENTORY,
        MAP
    }
}
