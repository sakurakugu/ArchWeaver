package com.sakurakugu.archweaver.client;

import com.sakurakugu.archweaver.client.chunkloading.ChunkMapScreen;
import com.sakurakugu.archweaver.client.chunkloading.ChunkMapManagementScreen;
import com.sakurakugu.archweaver.client.chunkloading.ChunkMapSettingsScreen;
import com.sakurakugu.archweaver.client.chunkloading.ClientChunkLoadingState;
import com.sakurakugu.archweaver.menu.FakePlayerInventoryMenu;
import com.sakurakugu.archweaver.network.ChunkMapSnapshotPayload;
import com.sakurakugu.archweaver.network.ChunkMapOpenTarget;
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

/** 统一管理 ArchWeaver 页面栈、容器页面返回和弹层背景。 */
public final class ClientScreenNavigation {
    private static final Map<Screen, NavigationEntry> ENTRIES = new IdentityHashMap<>();
    private static final Map<Screen, Screen> BACKGROUNDS = new WeakHashMap<>();
    /** 服务端异步打开页面前指定的父级，页面打开后消费。 */
    private static NavigationEntry pendingParent;
    private static boolean pendingParentSpecified;
    private static Screen pendingReturnScreen;
    private static Screen lastOpenedScreen;
    private static Screen closingScreen;
    private static NavigationEntry closingEntry;
    private static int backgroundRefreshTicks;

    private ClientScreenNavigation() {
    }

    /** 页面打开事件把新页面挂到当前页面上，形成可任意加深的导航栈。 */
    public static void onOpening(Screen current, Screen next) {
        if (next == null) return;
        // 返回到仍然存在的父页面时复用原节点，避免把返回动作再次压入栈。
        NavigationEntry existing = ENTRIES.get(next);
        if (existing != null) {
            lastOpenedScreen = next;
            closingScreen = null;
            closingEntry = null;
            return;
        }

        // 当前屏幕为空时，只有显式指定了父页面的异步跳转才允许沿用旧来源。
        // 世界内直接打开容器的事件也会以 null 作为当前屏幕，不能把上一次主页面
        // 当成这次打开的父级，否则关闭容器会错误地再次打开主页面。
        Screen source = current != null ? current
            : pendingParentSpecified
                ? closingScreen != null ? closingScreen : lastOpenedScreen
                : null;
        NavigationEntry sourceEntry = source == closingScreen ? closingEntry : ENTRIES.get(source);
        Screen returning = pendingReturnScreen;
        NavigationEntry parent = pendingParent;
        boolean hasPendingParent = pendingParentSpecified;
        pendingParent = null;
        pendingParentSpecified = false;
        pendingReturnScreen = null;
        if (!hasPendingParent) parent = inferParent(source, next, sourceEntry);

        NavigationEntry entry = new NavigationEntry(next, routeFor(next), parent);
        ENTRIES.put(next, entry);
        if (isOverlay(next)) {
            Screen background = returning != null ? BACKGROUNDS.get(returning)
                : replacesPage(source, next) ? BACKGROUNDS.get(source) : source;
            if (isArchWeaverScreen(background) && background != next) {
                background.clearFocus();
                BACKGROUNDS.put(next, background);
            }
        }
        lastOpenedScreen = next;
        closingScreen = null;
        closingEntry = null;
    }

    /** 显式登记由异步或独立流程打开的子页面，保证首次绘制和关闭时已有父级与背景。 */
    public static void registerLayer(Screen parent, Screen layer) {
        if (layer == null) return;
        if (parent != null && !ENTRIES.containsKey(parent)) {
            ENTRIES.put(parent, new NavigationEntry(parent, routeFor(parent), null));
        }
        onOpening(parent, layer);
    }

    /** 页面关闭时保留节点，父页面返回时仍可复用其状态。 */
    public static void onClosing(Screen screen) {
        if (screen == null) return;
        if (screen == lastOpenedScreen) {
            closingScreen = screen;
            closingEntry = ENTRIES.get(screen);
        }
    }

    /** 玩家离开世界时清空所有页面和异步跳转状态。 */
    public static void clear() {
        ENTRIES.clear();
        BACKGROUNDS.clear();
        pendingParent = null;
        pendingParentSpecified = false;
        pendingReturnScreen = null;
        lastOpenedScreen = null;
        closingScreen = null;
        closingEntry = null;
        backgroundRefreshTicks = 0;
    }

    /** 在关闭到游戏后清除页面栈，服务端异步切页期间保留栈。 */
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null) {
            if (!pendingParentSpecified) {
                ENTRIES.clear();
                BACKGROUNDS.clear();
            }
            lastOpenedScreen = null;
            closingScreen = null;
            closingEntry = null;
        } else if (snapshotBackground(minecraft.screen) != null && minecraft.player != null
            && minecraft.getConnection() != null && backgroundRefreshTicks-- <= 0) {
            PlatformNetworking.sendToServer(ClientChunkLoadingState.request(ChunkMapOpenTarget.NONE));
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
        if (current instanceof ChunkMapSettingsScreen settings) settings.update(snapshot);
        else if (current instanceof ChunkMapManagementScreen management) management.update(snapshot);
        else if (current instanceof ChunkMapScreen map) map.update(snapshot);
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
            || screen instanceof FakePlayerInventoryScreen || screen instanceof ChunkMapSettingsScreen
            || screen instanceof ChunkMapManagementScreen;
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

    /** 当前页面返回到父级；父级是同一实例时直接复用，否则按路由重新请求。 */
    public static void back(Screen current) {
        NavigationEntry entry = ENTRIES.get(current);
        if (entry == null) {
            if (fallbackBack(current)) return;
            close();
            return;
        }

        NavigationEntry parent = entry.parent();
        Screen background = BACKGROUNDS.get(current);
        ENTRIES.remove(current);
        BACKGROUNDS.remove(current);
        closeContainerForReturn(current);
        if (parent == null) {
            if (current instanceof FakePlayerInventoryScreen inventory) {
                if (inventory.getMenu().view() == FakePlayerInventoryMenu.View.ENDER_CHEST) {
                    openRoute(Route.inventory(inventory.getMenu().targetName()), null, background);
                    return;
                }
                returnToGame(current);
                return;
            }
            if (current instanceof GlobalFakePlayerScreen || current instanceof PresetManagementScreen) {
                openRoute(Route.main(), null, background);
                return;
            }
            close();
            return;
        }

        if (canReuse(parent.screen())) {
            Minecraft.getInstance().setScreen(parent.screen());
        } else {
            openRoute(parent.route(), parent.parent(), background);
        }
    }

    private static boolean fallbackBack(Screen current) {
        if (current instanceof FakePlayerInventoryScreen inventory) {
            if (inventory.getMenu().view() == FakePlayerInventoryMenu.View.ENDER_CHEST) {
                openRoute(Route.inventory(inventory.getMenu().targetName()), null, BACKGROUNDS.get(current));
            } else {
                returnToGame(current);
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

    /** 关闭从世界直接打开的容器并回到游戏，不重新打开 ArchWeaver 页面。 */
    private static void returnToGame(Screen current) {
        closeContainerForReturn(current);
        Minecraft.getInstance().setScreen(null);
    }

    private static boolean canReuse(Screen screen) {
        return screen instanceof MainPageScreen || screen instanceof ChunkMapScreen
            || screen instanceof ChunkMapSettingsScreen || screen instanceof ChunkMapManagementScreen;
    }

    private static NavigationEntry inferParent(Screen current, Screen next, NavigationEntry sourceEntry) {
        if (current == null || sourceEntry == null) return null;
        if (next instanceof MainPageScreen) return null;
        if (replacesPage(current, next)) return sourceEntry.parent();
        if (current instanceof GlobalFakePlayerScreen && next instanceof FakePlayerInventoryScreen) {
            return sourceEntry.parent();
        }
        if (current instanceof FakePlayerInventoryScreen
            && next instanceof FakePlayerInventoryScreen nextInventory
            && nextInventory.getMenu().view() == FakePlayerInventoryMenu.View.ENDER_CHEST) {
            return sourceEntry;
        }
        return sourceEntry;
    }

    private static Route routeFor(Screen screen) {
        if (screen instanceof MainPageScreen) return Route.main();
        if (screen instanceof ChunkMapScreen) return Route.map();
        if (screen instanceof ChunkMapSettingsScreen || screen instanceof ChunkMapManagementScreen) return Route.map();
        if (screen instanceof FakePlayerInventoryScreen inventory) {
            return Route.inventory(inventory.getMenu().targetName());
        }
        if (screen instanceof GlobalFakePlayerScreen) return Route.main();
        if (screen instanceof PresetManagementScreen) return Route.main();
        return Route.close();
    }

    private static void openRoute(Route route, NavigationEntry parent, Screen returning) {
        pendingParent = parent;
        pendingParentSpecified = true;
        pendingReturnScreen = returning;
        switch (route.kind()) {
            case MAIN -> ClientChunkLoadingState.openMainScreen();
            case MAP -> ClientChunkLoadingState.openMap(ClientChunkLoadingState.MapReturnTarget.CLOSE,
                ChunkMapOpenTarget.MAP);
            case INVENTORY -> PlatformNetworking.sendToServer(new OpenFakePlayerInventoryPayload(route.name()));
            case CLOSE -> close();
        }
    }

    private record NavigationEntry(Screen screen, Route route, NavigationEntry parent) {
    }

    private record Route(Kind kind, String name) {
        private static Route main() { return new Route(Kind.MAIN, ""); }
        private static Route map() { return new Route(Kind.MAP, ""); }
        private static Route inventory(String name) { return new Route(Kind.INVENTORY, name); }
        private static Route close() { return new Route(Kind.CLOSE, ""); }
    }

    private enum Kind {
        MAIN,
        MAP,
        INVENTORY,
        CLOSE
    }
}
