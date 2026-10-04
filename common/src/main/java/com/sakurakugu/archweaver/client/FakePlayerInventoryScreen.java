package com.sakurakugu.archweaver.client;

import com.sakurakugu.archweaver.ArchWeaverMod;
import com.sakurakugu.archweaver.menu.FakePlayerInventoryMenu;
import com.sakurakugu.archweaver.chunkloading.FakePlayerLoadMode;
import com.sakurakugu.archweaver.network.RenameFakePlayerPayload;
import com.sakurakugu.archweaver.network.FakePlayerSimulationPayload;
import com.sakurakugu.archweaver.network.FakePlayerViewRotationPayload;
import com.sakurakugu.archweaver.platform.PlatformNetworking;
import com.sakurakugu.archweaver.client.ui.HotbarSelector;
import com.sakurakugu.archweaver.client.ui.IntegerSliderButton;
import com.sakurakugu.archweaver.client.ui.SolidButton;
import com.sakurakugu.archweaver.client.ui.SolidDropdownButton;
import com.sakurakugu.archweaver.client.ui.InventorySlotButton;
import com.sakurakugu.archweaver.client.ui.OverlayPanelManager;
import com.sakurakugu.archweaver.client.ui.PixelGui;
import com.sakurakugu.archweaver.client.ui.RotationPad;
import com.sakurakugu.archweaver.client.ui.ToggleSwitchButton;
import com.sakurakugu.archweaver.client.ui.SegmentedSwitchButton;
import com.sakurakugu.archweaver.client.ui.TransferButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import java.util.Locale;

/** 绘制假人完整物品栏；末影箱使用原版三行容器界面。 */
public final class FakePlayerInventoryScreen extends AbstractContainerScreen<FakePlayerInventoryMenu> {
    private static final Identifier CONTAINER_BACKGROUND =
        Identifier.withDefaultNamespace("textures/gui/container/generic_54.png"); // 原版通用容器背景贴图，用于末影箱视图与操作者背包区域。
    private static final Identifier INVENTORY_BACKGROUND =
        Identifier.withDefaultNamespace("textures/gui/container/inventory.png"); // 原版玩家物品栏背景贴图。
    private static final Identifier DROP_TAB_ICON =
        Identifier.fromNamespaceAndPath(ArchWeaverMod.MOD_ID, "textures/gui/drop_tab.png"); // Q 键丢弃面板的标签图标。
    public static final Identifier POSSESSION_ENTER_ICON =
        Identifier.fromNamespaceAndPath(ArchWeaverMod.MOD_ID, "textures/gui/possession_enter.png"); // 附身按钮图标，表示进入附身状态。
    public static final Identifier POSSESSION_EXIT_ICON =
        Identifier.fromNamespaceAndPath(ArchWeaverMod.MOD_ID, "textures/gui/possession_exit.png"); // 附身按钮图标，表示退出附身状态。
    private static final Identifier HEART_CONTAINER_SPRITE =
        Identifier.withDefaultNamespace("hud/heart/container"); // 生命值空槽图标。
    private static final Identifier HEART_FULL_SPRITE =
        Identifier.withDefaultNamespace("hud/heart/full"); // 满颗心的生命值图标。
    private static final Identifier HEART_HALF_SPRITE =
        Identifier.withDefaultNamespace("hud/heart/half"); // 半颗心的生命值图标。
    private static final Identifier ARMOR_EMPTY_SPRITE =
        Identifier.withDefaultNamespace("hud/armor_empty"); // 护甲空槽图标。
    private static final Identifier ARMOR_HALF_SPRITE =
        Identifier.withDefaultNamespace("hud/armor_half"); // 半格护甲图标。
    private static final Identifier ARMOR_FULL_SPRITE =
        Identifier.withDefaultNamespace("hud/armor_full"); // 满格护甲图标。
    private static final Identifier FOOD_EMPTY_SPRITE =
        Identifier.withDefaultNamespace("hud/food_empty"); // 饥饿值空槽图标。
    private static final Identifier FOOD_HALF_SPRITE =
        Identifier.withDefaultNamespace("hud/food_half"); // 半格饥饿值图标。
    private static final Identifier FOOD_FULL_SPRITE =
        Identifier.withDefaultNamespace("hud/food_full"); // 满格饥饿值图标。
    private static final Identifier AIR_EMPTY_SPRITE =
        Identifier.withDefaultNamespace("hud/air_empty"); // 氧气空泡图标。
    private static final Identifier AIR_FULL_SPRITE =
        Identifier.withDefaultNamespace("hud/air"); // 满格氧气泡图标。
    private static final Identifier APPLESKIN_ICONS =
        Identifier.fromNamespaceAndPath(ArchWeaverMod.MOD_ID, "textures/gui/appleskin_icons.png"); // AppleSkin 饱和度图标贴图，四列分别对应不同饱和度级别。
    private static final Identifier EXPERIENCE_ORB_TEXTURE =
        Identifier.withDefaultNamespace("textures/entity/experience/experience_orb.png"); // 原版经验球贴图，用于绘制经验图标。
    private static final int STATUS_ICON_COUNT = 10; // 每行状态条绘制的图标数量，对应原版 10 颗心与 10 格饥饿值。
    private static final int STATUS_ICON_SIZE = 9; // 单个状态图标的边长，单位为像素。
    private static final int STATUS_ICON_SPACING = 8; // 相邻状态图标之间的水平间距，单位为像素。
    private static final int TARGET_INVENTORY_HEIGHT = 159; // 假人自身物品栏区域的高度，单位为像素。
    private static final int HOTBAR_SELECTOR_TOP = 159; // 快捷栏选择区顶边相对物品栏顶边的偏移，单位为像素。
    private static final int HOTBAR_SELECTOR_HEIGHT = 5; // 快捷栏选择区的高度，单位为像素。
    // 选择区整体比快捷栏第一格左移 1 像素，这样才对的齐。
    private static final int HOTBAR_SELECTOR_LEFT = 7; // 快捷栏选择区左边相对物品栏左边缘的偏移，单位为像素。
    private static final int VIEWER_SECTION_TOP = 164; // 操作者背包区域顶边相对物品栏顶边的偏移，单位为像素。
    // 普通管理页面不开放假人的 2x2 合成区。
    private static final int CRAFTING_AREA_LEFT = 97; // 合成区遮盖矩形的左边偏移，单位为像素。
    private static final int CRAFTING_AREA_TOP = 17; // 合成区遮盖矩形的顶边偏移，单位为像素。
    private static final int CRAFTING_AREA_WIDTH = 76; // 合成区遮盖矩形的宽度，单位为像素。
    private static final int CRAFTING_AREA_HEIGHT = 55; // 合成区遮盖矩形的高度，单位为像素。
    private static final int CONTROL_LEFT = 96; // 九宫格操控按钮第一列的左边偏移，单位为像素。
    private static final int CONTROL_TOP = 18; // 九宫格操控按钮第一行的顶边偏移，单位为像素。
    private static final int CONTROL_SIZE = 18; // 单个操控按钮的边长，单位为像素。
    private static final int SNEAK_BUTTON_LEFT = 153; // 潜行、跳跃与升降按钮所在列的左边偏移，单位为像素。

    // 三个按钮纵向排列，附身按钮正好位于末影箱下方和副手槽上方。
    private static final int ACTION_BUTTON_LEFT = 76; // 移除、末影箱与附身按钮所在列的左边偏移，单位为像素。
    private static final int ACTION_BUTTON_TOP = 7; // 第一个动作按钮的顶边偏移，单位为像素。
    private static final int ACTION_BUTTON_WIDTH = 18; // 单个动作按钮的宽度，单位为像素。
    private static final int ACTION_BUTTON_HEIGHT = 18; // 单个动作按钮的高度，单位为像素。
    private static final int ACTION_BUTTON_GAP = 0; // 相邻动作按钮之间的垂直间距，单位为像素。
    private static final int DROP_TAB_WIDTH = 21; // 侧栏面板标签的宽度，单位为像素。
    private static final int DROP_TAB_HEIGHT = 24; // 侧栏面板标签的高度，单位为像素。
    private static final int PANEL_GAP = 2; // 相邻侧栏面板之间的垂直间距，单位为像素。
    private static final OverlayPanelManager.Layout AIM_PANEL_LAYOUT = panelLayout(8, 94, 234); // 视觉朝向面板的布局。
    private static final OverlayPanelManager.Layout CONTINUOUS_PANEL_LAYOUT = nextPanelLayout(
        AIM_PANEL_LAYOUT, 94, 219); // 持续控制面板的布局，紧接 AIM_PANEL_LAYOUT 下方。
    private static final OverlayPanelManager.Layout INFO_PANEL_LAYOUT = nextPanelLayout(
        CONTINUOUS_PANEL_LAYOUT, 132, 160); // 假人信息面板的布局，紧接 CONTINUOUS_PANEL_LAYOUT 下方。
    private static final OverlayPanelManager.Layout DROP_PANEL_LAYOUT = nextPanelLayout(
        INFO_PANEL_LAYOUT, 94, 109); // Q 键丢弃面板的布局，紧接 INFO_PANEL_LAYOUT 下方。
    private static final int TRANSFER_BUTTON_LEFT = 144; // 物品转移按钮组的左边偏移，单位为像素。
    private static final int TRANSFER_BUTTON_TOP = 165; // 普通视图下转移按钮的顶边偏移，单位为像素。
    private static final int ENDER_CHEST_TRANSFER_BUTTON_TOP = 73; // 末影箱视图下转移按钮的顶边偏移，单位为像素。
    // 侧栏依次放置视觉朝向、持续控制、假人信息、Q 键丢弃、自动化和骑乘标签。
    private static final OverlayPanelManager.Layout AUTOMATION_PANEL_LAYOUT = nextPanelLayout(
        DROP_PANEL_LAYOUT, 94, 97); // 自动化面板的布局，紧接 DROP_PANEL_LAYOUT 下方。
    private static final int AUTOMATION_BUTTON_HEIGHT = 16; // 自动化开关按钮的高度，单位为像素。
    private static final OverlayPanelManager.Layout MOUNT_PANEL_LAYOUT = nextPanelLayout(
        AUTOMATION_PANEL_LAYOUT, 94, 83); // 骑乘面板的布局，紧接 AUTOMATION_PANEL_LAYOUT 下方。
    private static final OverlayPanelManager.Layout SIMULATION_PANEL_LAYOUT = panelLayout(8, 100, 92); // 模拟面板的布局，绘制在物品栏左侧。
    // 应用按钮三态文字色：红=有未保存改动，绿=已保存，白=无需保存。
    private static final int APPLY_DIRTY_COLOR = 0xFFFF5555; // 应用按钮“有未保存改动”状态的文字色，ARGB 红色。
    private static final int APPLY_SAVED_COLOR = 0xFF55FF55; // 应用按钮“刚保存成功”状态的文字色，ARGB 绿色。
    private static final int APPLY_IDLE_COLOR = 0xFFFFFFFF; // 应用按钮“无需保存”状态的文字色，ARGB 白色。
    private static final int MOUNT_BUTTON_HEIGHT = 16; // 骑乘面板按钮的高度，单位为像素。
    private static final int AIM_PAD_SIZE = 62; // 视角摇杆与方向摇杆的边长，单位为像素。
    private static final int CONTINUOUS_BUTTON_HEIGHT = 16; // 持续控制开关按钮的高度，单位为像素。
    private static final int CONTINUOUS_SLIDER_HEIGHT = 14; // 持续控制间隔滑条的高度，单位为像素。
    private static final String AIM_PANEL_ID = "aim"; // 视觉朝向面板的唯一标识。
    private static final String CONTINUOUS_PANEL_ID = "continuous"; // 持续控制面板的唯一标识。
    private static final String INFO_PANEL_ID = "info"; // 假人信息面板的唯一标识。
    private static final String DROP_PANEL_ID = "drop"; // Q 键丢弃面板的唯一标识。
    private static final String AUTOMATION_PANEL_ID = "automation"; // 自动化面板的唯一标识。
    private static final String MOUNT_PANEL_ID = "mount"; // 骑乘面板的唯一标识。
    private static final String SIMULATION_PANEL_ID = "simulation"; // 模拟面板的唯一标识。
    private static final String[] AUTOMATION_KEYS = {
        "auto_replenishment", "shulker_replenishment", "auto_replace_tools", "auto_fishing"
    }; // 自动化开关的语言键后缀，顺序与 ACTION_AUTO_REPLENISHMENT 起的动作 ID 一一对应。
    private static final String[] CONTINUOUS_KEYS = {
        "move_forward", "move_backward", "move_left", "move_right", "attack", "use", "jump"
    }; // 持续控制开关的语言键后缀，前 4 项为移动，后 3 项各附带一个间隔滑条。
    private static final int[] CONTINUOUS_ACTIONS = {
        FakePlayerInventoryMenu.ACTION_TOGGLE_MOVE_FORWARD,
        FakePlayerInventoryMenu.ACTION_TOGGLE_MOVE_BACKWARD,
        FakePlayerInventoryMenu.ACTION_TOGGLE_MOVE_LEFT,
        FakePlayerInventoryMenu.ACTION_TOGGLE_MOVE_RIGHT,
        FakePlayerInventoryMenu.ACTION_TOGGLE_ATTACK,
        FakePlayerInventoryMenu.ACTION_TOGGLE_USE,
        FakePlayerInventoryMenu.ACTION_TOGGLE_JUMP
    }; // 与 CONTINUOUS_KEYS 一一对应的持续控制切换动作 ID。

    private OverlayPanelManager panelManager; // 侧栏浮层面板管理器，负责面板的展开与遮挡顺序。
    // 按界面从上到下注册，展开的面板会遮挡并禁用其下方的标签。
    private OverlayPanelManager.Panel aimPanel; // 视觉朝向面板，含视角摇杆、方向摇杆与角度输入框。
    private OverlayPanelManager.Panel continuousPanel; // 持续控制面板，含移动、攻击、使用与跳跃开关及间隔滑条。
    private OverlayPanelManager.Panel infoPanel; // 假人信息面板，含名称、游戏模式与状态条。
    private OverlayPanelManager.Panel dropPanel; // Q 键丢弃面板。
    private OverlayPanelManager.Panel automationPanel; // 自动化面板，含自动补货等开关。
    private OverlayPanelManager.Panel mountPanel; // 骑乘面板，含骑乘、乘坐任意实体与下马按钮。
    private OverlayPanelManager.Panel simulationPanel; // 模拟面板，控制假人的区块加载模式与距离。
    private boolean continuousDrop; // 是否开启连续丢弃，为 true 时持续执行丢弃动作。
    private boolean percentageDrop; // 丢弃模式是否为百分比，false 表示按数量。
    private int dropAmount = 1; // 按数量丢弃时使用的数量，取值范围为 1 到 MAX_DROP_AMOUNT。
    private int dropPercentage = 100; // 按百分比丢弃时使用的百分比，取值范围为 1 到 MAX_DROP_PERCENTAGE。
    private int heldAction = -1; // 当前按下的操控按钮动作 ID，-1 表示没有按下的按钮。
    private int heldTicks; // 操控按钮已按住的游戏刻数，达到阈值后触发长按连续动作。
    private boolean heldStarted; // 长按是否已转为连续动作，避免重复发送。
    private IntegerSliderButton dropAmountSlider; // 丢弃数量或百分比的滑条。
    private Button dropModeButton; // 切换丢弃模式（数量/百分比）的按钮。
    private Button flyUpButton; // 飞行上升按钮，仅假人处于飞行状态时可见。
    private Button flyDownButton; // 飞行下降按钮，仅假人处于飞行状态时可见。
    private Button jumpButton; // 中间的操控按钮，飞行状态下由跳跃切换为关闭飞行。
    private Boolean jumpButtonFlying; // 跳跃按钮上次应用的飞行状态，null 表示尚未初始化，避免每刻重建文字与提示。
    private RotationPad aimPad; // 视角摇杆，用于调整俯仰角与偏航角。
    private RotationPad directionPad; // 机身方向摇杆，用于调整身体朝向。
    private EditBox pitchInput; // 俯仰角输入框，取值范围 -90 到 90。
    private EditBox yawInput; // 偏航角输入框，取值范围 -180 到 179。
    private ToggleSwitchButton bodyFollowsHeadButton; // “身体跟随头部”开关按钮。
    private boolean syncingAimInputs; // 是否正在同步角度输入框，用于避免回调递归。
    private EditBox nameInput; // 假人名称输入框，最长 16 个字符。
    private SolidDropdownButton<GameType> gameModeButton; // 游戏模式下拉框，选项为四种原版游戏模式。
    private FakePlayerLoadMode simulationMode = FakePlayerLoadMode.PLAYER; // 面板中当前选择的区块加载模式，默认为玩家模式。
    private int simulationDistance; // 面板中当前选择的模拟距离，单位为区块。
    private boolean simulationStateInitialized; // 是否已用服务端数据初始化过模拟面板，避免重建界面时覆盖用户改动。
    private SolidButton simulationApplyButton; // 模拟面板的应用按钮，用于刷新其文字颜色。
    private boolean simulationApplied; // 模拟设置上一次应用后是否尚未改动，用于显示已保存颜色。
    private int lastSentPitch; // 最近一次发送到服务端的俯仰角，用于去重。
    private int lastSentYaw; // 最近一次发送到服务端的偏航角，用于去重。

    private static OverlayPanelManager.Layout panelLayout(int top, int width, int height) {
        return new OverlayPanelManager.Layout(top, width, height, DROP_TAB_WIDTH, DROP_TAB_HEIGHT);
    }

    private static OverlayPanelManager.Layout nextPanelLayout(
        OverlayPanelManager.Layout previous, int width, int height
    ) {
        return panelLayout(previous.top() + previous.tabHeight() + PANEL_GAP, width, height);
    }

    public FakePlayerInventoryScreen(FakePlayerInventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, menu.screenWidth(), menu.screenHeight());
        lastSentPitch = menu.pitch();
        lastSentYaw = menu.yaw();
    }

    /** Esc 返回上一级。 */
    @Override
    public void onClose() {
        ClientScreenNavigation.back(this);
    }

    @Override
    protected void init() {
        String openPanelId = panelManager == null ? null : panelManager.openPanelId();
        boolean simulationPanelOpen = simulationPanel != null && simulationPanel.isOpen();
        super.init();
        if (menu.view() == FakePlayerInventoryMenu.View.ENDER_CHEST) {
            addTransferButtons(ENDER_CHEST_TRANSFER_BUTTON_TOP);
            return;
        }
        if (menu.view() == FakePlayerInventoryMenu.View.POSSESSED_INVENTORY) {
            addRenderableWidget(
                new InventorySlotButton(
                    leftPos + ACTION_BUTTON_LEFT,
                    topPos + ACTION_BUTTON_TOP + (ACTION_BUTTON_HEIGHT + ACTION_BUTTON_GAP) * 2,
                    POSSESSION_EXIT_ICON,
                    Component.translatable("gui.archweaver.fakeplayer.stop_possessing"),
                    button -> sendAction(FakePlayerInventoryMenu.ACTION_POSSESS)
                )
            );
            return;
        }
        addRenderableWidget(
            new InventorySlotButton(
                leftPos + ACTION_BUTTON_LEFT,
                topPos + ACTION_BUTTON_TOP,
                new ItemStack(Items.BARRIER),
                Component.translatable("gui.archweaver.fakeplayer.remove"),
                button -> sendAction(FakePlayerInventoryMenu.ACTION_REMOVE)
            )
        );
        InventorySlotButton possessButton = addRenderableWidget(
            new InventorySlotButton(
                leftPos + ACTION_BUTTON_LEFT,
                topPos + ACTION_BUTTON_TOP + (ACTION_BUTTON_HEIGHT + ACTION_BUTTON_GAP) * 2,
                menu.possessedByViewer() ? POSSESSION_EXIT_ICON : POSSESSION_ENTER_ICON,
                menu.targetOccupied() && !menu.possessedByViewer() ? new ItemStack(Items.BARRIER) : null,
                Component.translatable(menu.possessedByViewer()
                    ? "gui.archweaver.fakeplayer.stop_possessing"
                    : menu.targetOccupied() ? "gui.archweaver.fakeplayer.possess_disabled" : "gui.archweaver.fakeplayer.possess"),
                button -> sendAction(FakePlayerInventoryMenu.ACTION_POSSESS)
            )
        );
        if (menu.targetOccupied() && !menu.possessedByViewer()) {
            possessButton.active = false;
        }
        addRenderableWidget(
            new InventorySlotButton(
                leftPos + ACTION_BUTTON_LEFT,
                topPos + ACTION_BUTTON_TOP + ACTION_BUTTON_HEIGHT + ACTION_BUTTON_GAP,
                new ItemStack(Items.ENDER_CHEST),
                Component.translatable("gui.archweaver.fakeplayer.open_ender_chest"),
                button -> sendAction(FakePlayerInventoryMenu.ACTION_ENDER_CHEST)
            )
        );
        addTransferButtons(TRANSFER_BUTTON_TOP);
        addRenderableWidget(new HotbarSelector(
            leftPos + HOTBAR_SELECTOR_LEFT,
            topPos + HOTBAR_SELECTOR_TOP,
            HOTBAR_SELECTOR_HEIGHT,
            menu::selectedHotbarSlot,
            slot -> {
                if (minecraft.gameMode != null) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, slot);
                }
            }
        ));
        addControlButtons();
        createPanels();
        addInfoPanel();
        addAimPanel();
        addSimulationPanel();
        addAutomationPanel();
        addMountPanel();
        addContinuousPanel();
        addDropPanel();
        panelManager.restoreOpenPanel(openPanelId);
        simulationPanel.setOpen(simulationPanelOpen);
    }

    private void createPanels() {
        int panelLeft = leftPos + imageWidth;
        panelManager = new OverlayPanelManager(font);
        aimPanel = panelManager.addRightPanel(AIM_PANEL_ID, panelLeft, topPos, AIM_PANEL_LAYOUT,
            Component.translatable("gui.archweaver.fakeplayer.look.title"));
        continuousPanel = panelManager.addRightPanel(CONTINUOUS_PANEL_ID, panelLeft, topPos, CONTINUOUS_PANEL_LAYOUT,
            Component.translatable("gui.archweaver.fakeplayer.continuous.title"));
        infoPanel = panelManager.addRightPanel(INFO_PANEL_ID, panelLeft, topPos, INFO_PANEL_LAYOUT,
            Component.translatable("gui.archweaver.fakeplayer.info.title"));
        dropPanel = panelManager.addRightPanel(DROP_PANEL_ID, panelLeft, topPos, DROP_PANEL_LAYOUT,
            Component.translatable("gui.archweaver.fakeplayer.drop_panel_title"));
        automationPanel = panelManager.addRightPanel(
            AUTOMATION_PANEL_ID, panelLeft, topPos, AUTOMATION_PANEL_LAYOUT,
            Component.translatable("gui.archweaver.fakeplayer.automation.title"));
        mountPanel = panelManager.addRightPanel(MOUNT_PANEL_ID, panelLeft, topPos, MOUNT_PANEL_LAYOUT,
            Component.translatable("gui.archweaver.fakeplayer.mount.title"));
    }

    private void addAutomationPanel() {
        int panelLeft = automationPanel.getX();
        addRenderableWidget(automationPanel);
        int automationTop = automationPanel.getY() + 21;
        ToggleSwitchButton[] automationButtons = new ToggleSwitchButton[AUTOMATION_KEYS.length];
        for (int index = 0; index < AUTOMATION_KEYS.length; index++) {
            int actionId = FakePlayerInventoryMenu.ACTION_AUTO_REPLENISHMENT + index;
            int automationIndex = index;
            automationButtons[index] = addRenderableWidget(new ToggleSwitchButton(
                panelLeft + 6,
                automationTop + index * (AUTOMATION_BUTTON_HEIGHT + 2),
                automationPanel.contentWidth() - 12,
                AUTOMATION_BUTTON_HEIGHT,
                Component.translatable("gui.archweaver.fakeplayer.automation." + AUTOMATION_KEYS[index]),
                () -> menu.automationEnabled(automationIndex),
                button -> sendAction(actionId)
            ));
        }
        addRenderableWidget(automationPanel.createTab(new ItemStack(Items.REPEATER)));
        automationPanel.bindContents(automationButtons);
    }

    private void addMountPanel() {
        int panelLeft = mountPanel.getX();
        addRenderableWidget(mountPanel);
        int mountTop = mountPanel.getY();
        Button mountButton = addRenderableWidget(new SolidButton(
            panelLeft + 6, mountTop + 21, mountPanel.contentWidth() - 12, MOUNT_BUTTON_HEIGHT,
            Component.translatable("gui.archweaver.fakeplayer.mount.mount"),
            button -> sendAction(FakePlayerInventoryMenu.ACTION_MOUNT)
        ));
        Button mountAnythingButton = addRenderableWidget(new SolidButton(
            panelLeft + 6, mountTop + 39, mountPanel.contentWidth() - 12, MOUNT_BUTTON_HEIGHT,
            Component.translatable("gui.archweaver.fakeplayer.mount.mount_anything"),
            button -> sendAction(FakePlayerInventoryMenu.ACTION_MOUNT_ANYTHING)
        ));
        Button dismountButton = addRenderableWidget(new SolidButton(
            panelLeft + 6, mountTop + 57, mountPanel.contentWidth() - 12, MOUNT_BUTTON_HEIGHT,
            Component.translatable("gui.archweaver.fakeplayer.mount.dismount"),
            button -> sendAction(FakePlayerInventoryMenu.ACTION_DISMOUNT)
        ));
        // 马鞍贴图的视觉重心偏下，单独向左上修正 1 像素。
        addRenderableWidget(mountPanel.createTab(new ItemStack(Items.SADDLE), -1, -1));
        mountPanel.bindContents(mountButton, mountAnythingButton, dismountButton);
    }

    private void addContinuousPanel() {
        int panelLeft = continuousPanel.getX();
        addRenderableWidget(continuousPanel);
        int continuousTop = continuousPanel.getY() + 21;
        ToggleSwitchButton[] continuousButtons = new ToggleSwitchButton[CONTINUOUS_KEYS.length];
        for (int index = 0; index < CONTINUOUS_KEYS.length; index++) {
            int controlIndex = index;
            int buttonTop = index < 4
                ? continuousTop + index * (CONTINUOUS_BUTTON_HEIGHT + 2)
                : continuousTop + 4 * (CONTINUOUS_BUTTON_HEIGHT + 2)
                    + (index - 4) * (CONTINUOUS_BUTTON_HEIGHT + CONTINUOUS_SLIDER_HEIGHT + 4);
            continuousButtons[index] = addRenderableWidget(new ToggleSwitchButton(
                panelLeft + 6,
                buttonTop,
                continuousPanel.contentWidth() - 12,
                CONTINUOUS_BUTTON_HEIGHT,
                Component.translatable("gui.archweaver.fakeplayer.continuous." + CONTINUOUS_KEYS[index]),
                () -> menu.continuousControlEnabled(controlIndex),
                button -> sendAction(CONTINUOUS_ACTIONS[controlIndex])
            ));
        }
        IntegerSliderButton[] intervalSliders = new IntegerSliderButton[3];
        for (int index = 0; index < intervalSliders.length; index++) {
            int controlIndex = index;
            int buttonTop = continuousTop + 4 * (CONTINUOUS_BUTTON_HEIGHT + 2)
                + index * (CONTINUOUS_BUTTON_HEIGHT + CONTINUOUS_SLIDER_HEIGHT + 4);
            intervalSliders[index] = addRenderableWidget(new IntegerSliderButton(
                panelLeft + 6,
                buttonTop + CONTINUOUS_BUTTON_HEIGHT + 2,
                continuousPanel.contentWidth() - 12,
                CONTINUOUS_SLIDER_HEIGHT,
                1,
                FakePlayerInventoryMenu.MAX_CONTINUOUS_INTERVAL,
                menu.continuousInterval(controlIndex),
                value -> Component.translatable("gui.archweaver.fakeplayer.continuous.interval", value),
                value -> sendAction(FakePlayerInventoryMenu.continuousIntervalActionId(controlIndex, value))
            ));
            intervalSliders[index].setTooltip(Tooltip.create(
                Component.translatable("gui.archweaver.fakeplayer.continuous.interval_tooltip")));
        }
        Button stopAllContinuousButton = addRenderableWidget(new SolidButton(
            panelLeft + 6,
            continuousTop + 4 * (CONTINUOUS_BUTTON_HEIGHT + 2)
                + intervalSliders.length * (CONTINUOUS_BUTTON_HEIGHT + CONTINUOUS_SLIDER_HEIGHT + 4) + 2,
            continuousPanel.contentWidth() - 12,
            CONTINUOUS_BUTTON_HEIGHT,
            Component.translatable("gui.archweaver.fakeplayer.stop"),
            button -> sendAction(FakePlayerInventoryMenu.ACTION_STOP_ALL)
        ));
        addRenderableWidget(continuousPanel.createTab(new ItemStack(Items.CLOCK)));
        AbstractWidget[] continuousContents =
            new AbstractWidget[continuousButtons.length + intervalSliders.length + 1];
        System.arraycopy(continuousButtons, 0, continuousContents, 0, continuousButtons.length);
        System.arraycopy(intervalSliders, 0, continuousContents, continuousButtons.length,
            intervalSliders.length);
        continuousContents[continuousContents.length - 1] = stopAllContinuousButton;
        continuousPanel.bindContents(continuousContents);
    }

    private void addDropPanel() {
        int panelLeft = dropPanel.getX();
        int panelTop = dropPanel.getY();
        addRenderableWidget(dropPanel);
        dropPanel.setContentRenderer((graphics, x, y) -> graphics.text(font,
            Component.translatable("gui.archweaver.fakeplayer.drop_amount"), x + 6, y + 31, 0xFF404040, false));
        addRenderableWidget(dropPanel.createTab(
            DROP_TAB_ICON, Component.translatable("gui.archweaver.fakeplayer.drop_tab")));

        dropModeButton = addRenderableWidget(
            new SolidButton(panelLeft + 74, panelTop + 28, 14, 14, dropModeMessage(), button -> toggleDropMode())
        );
        updateDropModeTooltip();
        dropAmountSlider = addRenderableWidget(new IntegerSliderButton(
            panelLeft + 6,
            panelTop + 46,
            dropPanel.contentWidth() - 12,
            16,
            1,
            FakePlayerInventoryMenu.MAX_DROP_AMOUNT,
            dropAmount,
            value -> Component.literal(value + (percentageDrop ? "%" : "")),
            value -> {
                if (percentageDrop) {
                    dropPercentage = value;
                } else {
                    dropAmount = value;
                }
            }
        ));
        ToggleSwitchButton continuousDropButton = addRenderableWidget(
            new ToggleSwitchButton(panelLeft + 6, panelTop + 67, dropPanel.contentWidth() - 12, 16,
                Component.translatable("gui.archweaver.fakeplayer.drop_continuous"), () -> continuousDrop, button -> {
                continuousDrop = !continuousDrop;
            })
        );
        Button executeDropButton = addRenderableWidget(
            new SolidButton(
                panelLeft + 6,
                panelTop + 88,
                dropPanel.contentWidth() - 12,
                16,
                Component.translatable("gui.archweaver.fakeplayer.drop_execute"),
                button -> sendAction(FakePlayerInventoryMenu.dropActionId(
                    currentDropValue(), percentageDrop, continuousDrop))
            )
        );
        dropPanel.bindContents(dropModeButton, dropAmountSlider,
            continuousDropButton, executeDropButton);
    }

    private void addInfoPanel() {
        int left = infoPanel.getX();
        int top = infoPanel.getY();
        addRenderableWidget(infoPanel);
        infoPanel.setContentRenderer(this::drawInfoPanelContents);
        nameInput = addRenderableWidget(new EditBox(font, left + 6, top + 28, 86, 16,
            Component.translatable("gui.archweaver.fakeplayer.info.name")));
        nameInput.setMaxLength(16);
        nameInput.setValue(menu.targetName());
        nameInput.setHint(Component.translatable("gui.archweaver.fakeplayer.info.name"));
        Button renameButton = addRenderableWidget(new SolidButton(
            left + 96, top + 28, 28, 16,
            Component.translatable("gui.archweaver.fakeplayer.info.rename"),
            button -> submitRename()
        ));
        // 收窄下拉框，右边界与名称、复制、经验等控件对齐在 left + 124。
        gameModeButton = addRenderableWidget(new SolidDropdownButton<>(
            left + 68, top + 47, 56, 16,
            java.util.List.of(GameType.SURVIVAL, GameType.CREATIVE, GameType.ADVENTURE, GameType.SPECTATOR),
            gameType(), this::gameModeName,
            gameType -> sendAction(FakePlayerInventoryMenu.ACTION_SET_GAME_MODE_BASE + gameType.getId())
        ));
        ExperienceDisplay experienceDisplay = addRenderableWidget(new ExperienceDisplay(
            left + 7, top + 122, 117, 16
        ));
        CoordinateDisplay coordinateDisplay = addRenderableWidget(new CoordinateDisplay(
            left + 7, top + 138, 85, 15
        ));
        Button copyPositionButton = addRenderableWidget(new SolidButton(
            left + 96, top + 139, 28, 14,
            Component.translatable("gui.archweaver.fakeplayer.info.copy"),
            button -> copyPosition()
        ));
        addRenderableWidget(infoPanel.createTab(new ItemStack(Items.NAME_TAG)));
        infoPanel.bindContents(nameInput, renameButton, gameModeButton, experienceDisplay,
            coordinateDisplay, copyPositionButton);
    }

    private void submitRename() {
        String name = nameInput.getValue().trim();
        if (!name.equals(menu.targetName())) {
            PlatformNetworking.sendToServer(new RenameFakePlayerPayload(menu.containerId, name));
        }
    }

    private void addSimulationPanel() {
        int left = leftPos - SIMULATION_PANEL_LAYOUT.width();
        int top = topPos + 8;
        OverlayPanelManager simulationPanelManager = new OverlayPanelManager(font);
        simulationPanel = simulationPanelManager.addLeftPanel(
            SIMULATION_PANEL_ID, left, top - SIMULATION_PANEL_LAYOUT.top(),
            SIMULATION_PANEL_LAYOUT, Component.translatable("gui.archweaver.fakeplayer.simulation.title"));
        addRenderableWidget(simulationPanel);
        if (!simulationStateInitialized) {
            simulationMode = menu.simulationMode();
            simulationDistance = savedSimulationDistance();
            simulationStateInitialized = true;
        }
        IntegerSliderButton[] distanceControl = new IntegerSliderButton[1];
        SegmentedSwitchButton mode = addRenderableWidget(new SegmentedSwitchButton(
            left + 6, top + 24, simulationPanel.contentWidth() - 12, 18,
            Component.translatable("gui.archweaver.fakeplayer.simulation.auto"),
            Component.translatable("gui.archweaver.fakeplayer.simulation.manual"),
            () -> simulationMode == FakePlayerLoadMode.DOLL,
            doll -> {
                simulationMode = doll ? FakePlayerLoadMode.DOLL : FakePlayerLoadMode.PLAYER;
                if (distanceControl[0] != null) distanceControl[0].active = doll;
            }));
        mode.setTooltip(Tooltip.create(Component.translatable("gui.archweaver.fakeplayer.simulation.mode_tooltip")));
        IntegerSliderButton slider = addRenderableWidget(new IntegerSliderButton(
            left + 6, top + 47, simulationPanel.contentWidth() - 12, 16,
            0, Math.max(1, menu.simulationDistanceLimit()),
            Math.min(simulationDistance, menu.simulationDistanceLimit()),
            value -> Component.translatable("gui.archweaver.fakeplayer.simulation.distance", value),
            value -> simulationDistance = value));
        slider.active = simulationMode == FakePlayerLoadMode.DOLL;
        distanceControl[0] = slider;
        SolidButton apply = addRenderableWidget(new SolidButton(
            left + 6, top + 70, simulationPanel.contentWidth() - 12, 16,
            Component.translatable("gui.archweaver.fakeplayer.simulation.apply"), button -> {
                PlatformNetworking.sendToServer(new FakePlayerSimulationPayload(
                    menu.containerId, simulationMode, simulationDistance));
                simulationApplied = true;
            }));
        simulationApplyButton = apply;
        updateSimulationApplyColor();
        addRenderableWidget(simulationPanel.createTab(new ItemStack(Items.GRASS_BLOCK), 2));
        simulationPanel.bindContents(mode, slider, apply);
    }

    /** 服务端已保存的模拟距离；存档值可能大于当前服务器上限，取夹紧后的值。 */
    private int savedSimulationDistance() {
        return Math.min(menu.simulationDistance(), menu.simulationDistanceLimit());
    }

    /** 应用按钮文字色：与已保存策略不一致为红，刚保存成功为绿，其余为白。 */
    private void updateSimulationApplyColor() {
        if (simulationApplyButton == null) return;
        if (simulationMode != menu.simulationMode()
            || (simulationMode == FakePlayerLoadMode.DOLL
                && simulationDistance != savedSimulationDistance())) {
            simulationApplyButton.setTextColor(APPLY_DIRTY_COLOR);
        } else {
            simulationApplyButton.setTextColor(simulationApplied ? APPLY_SAVED_COLOR : APPLY_IDLE_COLOR);
        }
    }

    private void copyPosition() {
        minecraft.keyboardHandler.setClipboard(String.format(Locale.ROOT, "%s %s %s",
            menu.positionX(), menu.positionY(), menu.positionZ()));
    }

    private Component positionValue() {
        return Component.literal(String.format(Locale.ROOT, "%s, %s, %s",
            menu.positionX(), menu.positionY(), menu.positionZ()));
    }

    /** 在假人物品栏的空白区域添加移动和即时动作操控杆。 */
    private void addControlButtons() {
        addControlButton(0, 0, "↶", FakePlayerInventoryMenu.ACTION_TURN_LEFT);
        addControlButton(1, 0, "↑", FakePlayerInventoryMenu.ACTION_MOVE_FORWARD);
        addControlButton(2, 0, "↷", FakePlayerInventoryMenu.ACTION_TURN_RIGHT);
        addControlButton(0, 1, "←", FakePlayerInventoryMenu.ACTION_MOVE_LEFT);
        addControlButton(1, 1, "S", FakePlayerInventoryMenu.ACTION_SNEAK);
        addControlButton(2, 1, "→", FakePlayerInventoryMenu.ACTION_MOVE_RIGHT);
        addControlButton(0, 2, "L", FakePlayerInventoryMenu.ACTION_ATTACK_ONCE);
        addControlButton(1, 2, "↓", FakePlayerInventoryMenu.ACTION_MOVE_BACKWARD);
        addControlButton(2, 2, "R", FakePlayerInventoryMenu.ACTION_USE_ONCE);

        jumpButton = addRenderableWidget(new SolidButton(
            leftPos + SNEAK_BUTTON_LEFT,
            topPos + CONTROL_TOP + CONTROL_SIZE,
            CONTROL_SIZE,
            CONTROL_SIZE,
            Component.literal("J"),
            button -> pressJumpButton()
        ));

        flyUpButton = addControlButtonAt(
            leftPos + SNEAK_BUTTON_LEFT,
            topPos + CONTROL_TOP,
            "↑",
            FakePlayerInventoryMenu.ACTION_FLY_UP
        );
        flyUpButton.setTooltip(Tooltip.create(Component.translatable("gui.archweaver.fakeplayer.fly_up")));
        flyDownButton = addControlButtonAt(
            leftPos + SNEAK_BUTTON_LEFT,
            topPos + CONTROL_TOP + CONTROL_SIZE * 2,
            "↓",
            FakePlayerInventoryMenu.ACTION_FLY_DOWN
        );
        flyDownButton.setTooltip(Tooltip.create(Component.translatable("gui.archweaver.fakeplayer.fly_down")));
        updateFlyingButtons();
    }

    private void addAimPanel() {
        int x = aimPanel.getX();
        int y = aimPanel.getY();
        addRenderableWidget(aimPanel);
        aimPanel.setContentRenderer(this::drawAimPanelContents);
        bodyFollowsHeadButton = addRenderableWidget(new ToggleSwitchButton(
            x + 6, y + 20, aimPanel.contentWidth() - 12, 16,
            Component.translatable("gui.archweaver.fakeplayer.look.body_follows_head"),
            menu::bodyFollowsHead,
            button -> sendAction(FakePlayerInventoryMenu.ACTION_TOGGLE_BODY_FOLLOWS_HEAD)
        ));
        bodyFollowsHeadButton.setTooltip(Tooltip.create(
            Component.translatable("gui.archweaver.fakeplayer.look.body_follows_head_tooltip")));
        aimPad = addRenderableWidget(new RotationPad(
            x + 16, y + 50, AIM_PAD_SIZE, RotationPad.Mode.VIEW,
            menu::pitch, menu::yaw, menu::bodyYaw, menu::bodyFollowsHead,
            selectedYaw -> sendAction(FakePlayerInventoryMenu.bodyYawAction(selectedYaw)),
            this::sendViewRotation));
        directionPad = addRenderableWidget(new RotationPad(
            x + 16, y + 124, AIM_PAD_SIZE, RotationPad.Mode.BODY,
            menu::pitch, menu::yaw, menu::bodyYaw, menu::bodyFollowsHead,
            selectedYaw -> sendAction(FakePlayerInventoryMenu.bodyYawAction(selectedYaw)),
            this::sendViewRotation));
        pitchInput = addRenderableWidget(new EditBox(font, x + 36, y + 192, 52, 16,
            Component.translatable("gui.archweaver.fakeplayer.look_pitch")));
        yawInput = addRenderableWidget(new EditBox(font, x + 36, y + 212, 52, 16,
            Component.translatable("gui.archweaver.fakeplayer.look_yaw")));
        pitchInput.setValue(Integer.toString(menu.pitch()));
        yawInput.setValue(Integer.toString(menu.yaw()));
        pitchInput.setFilter(value -> value.matches("-?\\d{0,3}"));
        yawInput.setFilter(value -> value.matches("-?\\d{0,3}"));
        pitchInput.setResponder(value -> {
            if (syncingAimInputs) return;
            submitAngleInput(pitchInput, value, -90, 90, true);
        });
        yawInput.setResponder(value -> {
            if (syncingAimInputs) return;
            submitAngleInput(yawInput, value, -180, 179, false);
        });
        addRenderableWidget(aimPanel.createTab(new ItemStack(Items.COMPASS)));
        aimPanel.bindContents(bodyFollowsHeadButton, aimPad, directionPad, pitchInput, yawInput);
    }

    /** 修正超出范围的角度输入，并把最终值发送到服务端。 */
    private void submitAngleInput(EditBox input, String value, int minimum, int maximum, boolean pitch) {
        try {
            int angle = Integer.parseInt(value);
            int clamped = Math.clamp(angle, minimum, maximum);
            if (angle != clamped) {
                syncingAimInputs = true;
                input.setValue(Integer.toString(clamped));
                syncingAimInputs = false;
            }
            sendViewRotation(
                pitch ? clamped : lastSentPitch,
                pitch ? lastSentYaw : clamped
            );
        } catch (NumberFormatException ignored) {
            // 空输入和单独的负号是编辑过程中的合法中间状态。
        }
    }

    private void addControlButton(int column, int row, String label, int actionId) {
        addControlButtonAt(
            leftPos + CONTROL_LEFT + column * CONTROL_SIZE,
            topPos + CONTROL_TOP + row * CONTROL_SIZE,
            label,
            actionId
        );
    }

    private Button addControlButtonAt(int x, int y, String label, int actionId) {
        return addRenderableWidget(new SolidButton(
            x,
            y,
            CONTROL_SIZE,
            CONTROL_SIZE,
            Component.literal(label),
            ignored -> {
                sendAction(actionId);
                heldAction = actionId;
                heldTicks = 0;
                heldStarted = false;
            }
        ));
    }

    /** 中间的操控按钮：平时跳跃，飞行状态下改为关闭飞行；长按跳跃沿用连续动作。 */
    private void pressJumpButton() {
        if (menu.isFlying()) {
            sendAction(FakePlayerInventoryMenu.ACTION_TOGGLE_FLIGHT);
            return;
        }
        sendAction(FakePlayerInventoryMenu.ACTION_JUMP);
        heldAction = FakePlayerInventoryMenu.ACTION_JUMP;
        heldTicks = 0;
        heldStarted = false;
    }

    private void updateFlyingButtons() {
        if (flyUpButton == null || flyDownButton == null || jumpButton == null) {
            return;
        }
        boolean flying = menu.isFlying();
        flyUpButton.visible = flying;
        flyDownButton.visible = flying;
        // 旁观模式必须保持飞行，因此飞行状态下禁用关闭飞行的按钮。
        jumpButton.active = !(flying && gameType() == GameType.SPECTATOR);
        if (jumpButtonFlying != null && jumpButtonFlying == flying) {
            return;
        }
        jumpButtonFlying = flying;
        jumpButton.setMessage(Component.literal(flying ? "F" : "J"));
        jumpButton.setTooltip(Tooltip.create(Component.translatable(
            flying ? "gui.archweaver.fakeplayer.stop_flying" : "gui.archweaver.fakeplayer.jump")));
    }

    private void addTransferButtons(int buttonTop) {
        addRenderableWidget(new TransferButton(
            leftPos + TRANSFER_BUTTON_LEFT,
            topPos + buttonTop,
            TransferButton.Direction.TO_CONTAINER,
            (transferAll, includeHotbar) -> sendAction(
                transferActionId(true, transferAll, includeHotbar))
        ));
        addRenderableWidget(new TransferButton(
            leftPos + TRANSFER_BUTTON_LEFT + TransferButton.SIZE,
            topPos + buttonTop,
            TransferButton.Direction.TO_INVENTORY,
            (transferAll, includeHotbar) -> sendAction(
                transferActionId(false, transferAll, includeHotbar))
        ));
    }

    private Component dropModeMessage() {
        return Component.literal(percentageDrop ? "%" : "#");
    }

    private void toggleDropMode() {
        percentageDrop = !percentageDrop;
        dropModeButton.setMessage(dropModeMessage());
        updateDropModeTooltip();
        int maximum = percentageDrop
            ? FakePlayerInventoryMenu.MAX_DROP_PERCENTAGE
            : FakePlayerInventoryMenu.MAX_DROP_AMOUNT;
        dropAmountSlider.setRange(1, maximum, currentDropValue());
    }

    private void updateDropModeTooltip() {
        dropModeButton.setTooltip(Tooltip.create(Component.translatable(percentageDrop
            ? "gui.archweaver.fakeplayer.drop_mode_percentage"
            : "gui.archweaver.fakeplayer.drop_mode_amount")));
    }

    private int currentDropValue() {
        return percentageDrop ? dropPercentage : dropAmount;
    }

    private void sendAction(int actionId) {
        if (minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, actionId);
        }
    }

    private void sendViewRotation(int pitch, int yaw) {
        int clampedPitch = Math.clamp(pitch, -90, 90);
        int wrappedYaw = Math.floorMod(yaw + 180, 360) - 180;
        if (clampedPitch == lastSentPitch && wrappedYaw == lastSentYaw) {
            return;
        }
        lastSentPitch = clampedPitch;
        lastSentYaw = wrappedYaw;
        PlatformNetworking.sendToServer(new FakePlayerViewRotationPayload(
            menu.containerId, clampedPitch, wrappedYaw));
    }

    private static int transferActionId(boolean toTarget, boolean transferAll, boolean includeHotbar) {
        if (toTarget) {
            return includeHotbar
                ? transferAll
                    ? FakePlayerInventoryMenu.ACTION_TRANSFER_TO_TARGET_ALL_WITH_HOTBAR
                    : FakePlayerInventoryMenu.ACTION_TRANSFER_TO_TARGET_MATCHING_WITH_HOTBAR
                : transferAll
                    ? FakePlayerInventoryMenu.ACTION_TRANSFER_TO_TARGET_ALL
                    : FakePlayerInventoryMenu.ACTION_TRANSFER_TO_TARGET_MATCHING;
        }
        return includeHotbar
            ? transferAll
                ? FakePlayerInventoryMenu.ACTION_TRANSFER_TO_VIEWER_ALL_WITH_HOTBAR
                : FakePlayerInventoryMenu.ACTION_TRANSFER_TO_VIEWER_MATCHING_WITH_HOTBAR
            : transferAll
                ? FakePlayerInventoryMenu.ACTION_TRANSFER_TO_VIEWER_ALL
                : FakePlayerInventoryMenu.ACTION_TRANSFER_TO_VIEWER_MATCHING;
    }

    /** 标签保持固定，仅在空间不足时滚动坐标值。 */
    private final class CoordinateDisplay extends Button {
        private CoordinateDisplay(int x, int y, int width, int height) {
            super(x, y, width, height, Component.translatable("gui.archweaver.fakeplayer.info.position"),
                button -> {}, DEFAULT_NARRATION);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            Component label = getMessage();
            Component value = positionValue();
            int textTop = getY() + (getHeight() - 8) / 2;
            int valueLeft = getX() + font.width(label);
            int valueRight = getX() + getWidth();
            graphics.text(font, label, getX(), textTop, 0xFF404040, false);
            if (font.width(value) <= valueRight - valueLeft) {
                graphics.text(font, value, valueLeft, textTop, 0xFF404040, false);
            } else {
                PixelGui.drawScrollingText(graphics, font, value,
                    valueLeft, valueRight, getY(), getHeight(), 0xFF404040);
            }
            setTooltip(Tooltip.create(Component.translatable("gui.archweaver.fakeplayer.info.position_tooltip",
                menu.positionX(), menu.positionY(), menu.positionZ())));
        }
    }

    /** 显示当前等级，详细经验值通过悬停提示查看。 */
    private final class ExperienceDisplay extends Button {
        private ExperienceDisplay(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), button -> {}, DEFAULT_NARRATION);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            Component label = Component.translatable("gui.archweaver.fakeplayer.info.experience");
            graphics.text(font, label, getX(), getY() + 4, 0xFF404040, false);

            int iconX = getX() + font.width(label) + 4;
            // 使用原版最大经验球的图块，并补上实体渲染时使用的黄绿色着色。
            graphics.blit(RenderPipelines.GUI_TEXTURED, EXPERIENCE_ORB_TEXTURE,
                iconX, getY() + 4, 32.0F, 32.0F, 9, 9, 16, 16, 64, 64, 0xFF80FF20);
            graphics.text(font, Component.translatable("gui.archweaver.fakeplayer.info.experience_level",
                menu.experienceLevel()), iconX + 13, getY() + 4, 0xFF80FF20, true);

            int remaining = Math.max(0, menu.experienceNeeded() - menu.experiencePoints());
            setTooltip(Tooltip.create(Component.translatable("gui.archweaver.fakeplayer.info.experience_tooltip",
                menu.experiencePoints(), remaining, menu.totalExperience())));
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateFlyingButtons();
        syncAimInputs();
        updateSimulationApplyColor();
        if (heldAction < 0) {
            return;
        }
        heldTicks++;
        if (!heldStarted && heldTicks >= 5) {
            int continuous = continuousAction(heldAction);
            if (continuous >= 0) {
                sendAction(continuous);
                heldStarted = true;
            }
        }
    }

    private static int continuousAction(int action) {
        return switch (action) {
            case FakePlayerInventoryMenu.ACTION_TURN_LEFT -> FakePlayerInventoryMenu.ACTION_TURN_LEFT_HELD;
            case FakePlayerInventoryMenu.ACTION_MOVE_FORWARD -> FakePlayerInventoryMenu.ACTION_MOVE_FORWARD_HELD;
            case FakePlayerInventoryMenu.ACTION_TURN_RIGHT -> FakePlayerInventoryMenu.ACTION_TURN_RIGHT_HELD;
            case FakePlayerInventoryMenu.ACTION_MOVE_LEFT -> FakePlayerInventoryMenu.ACTION_MOVE_LEFT_HELD;
            case FakePlayerInventoryMenu.ACTION_JUMP -> FakePlayerInventoryMenu.ACTION_JUMP_HELD;
            case FakePlayerInventoryMenu.ACTION_MOVE_RIGHT -> FakePlayerInventoryMenu.ACTION_MOVE_RIGHT_HELD;
            case FakePlayerInventoryMenu.ACTION_ATTACK_ONCE -> FakePlayerInventoryMenu.ACTION_ATTACK_HELD;
            case FakePlayerInventoryMenu.ACTION_MOVE_BACKWARD -> FakePlayerInventoryMenu.ACTION_MOVE_BACKWARD_HELD;
            case FakePlayerInventoryMenu.ACTION_USE_ONCE -> FakePlayerInventoryMenu.ACTION_USE_HELD;
            case FakePlayerInventoryMenu.ACTION_FLY_UP -> FakePlayerInventoryMenu.ACTION_FLY_UP_HELD;
            case FakePlayerInventoryMenu.ACTION_FLY_DOWN -> FakePlayerInventoryMenu.ACTION_FLY_DOWN_HELD;
            default -> -1;
        };
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (menu.view() == FakePlayerInventoryMenu.View.ENDER_CHEST
            || !ClientScreenNavigation.extractBackground(this, graphics, partialTick)) {
            super.extractBackground(graphics, mouseX, mouseY, partialTick);
        }
        if (menu.view() == FakePlayerInventoryMenu.View.ENDER_CHEST) {
            // 与原版 ContainerScreen 的三行容器背景保持一致。
            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                CONTAINER_BACKGROUND,
                leftPos,
                topPos,
                0.0F,
                0.0F,
                imageWidth,
                71,
                256,
                256
            );
            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                CONTAINER_BACKGROUND,
                leftPos,
                topPos + 71,
                0.0F,
                126.0F,
                imageWidth,
                96,
                256,
                256
            );
            return;
        }

        if (menu.view() == FakePlayerInventoryMenu.View.POSSESSED_INVENTORY) {
            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                INVENTORY_BACKGROUND,
                leftPos,
                topPos,
                0.0F,
                0.0F,
                imageWidth,
                imageHeight,
                256,
                256
            );
            drawTargetEntity(graphics, mouseX, mouseY);
            return;
        }

        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            INVENTORY_BACKGROUND,
            leftPos,
            topPos,
            0.0F,
            0.0F,
            176,
            TARGET_INVENTORY_HEIGHT,
            256,
            256
        );
        clearCraftingArea(graphics);
        // 裁掉假人背包底部边框，再像原版箱子一样拼接操作者背包区域。
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            CONTAINER_BACKGROUND,
            leftPos,
            topPos + VIEWER_SECTION_TOP,
            0.0F,
            126.0F,
            176,
            96,
            256,
            256
        );
        graphics.fill(
            leftPos + 1,
            topPos + TARGET_INVENTORY_HEIGHT,
            leftPos + imageWidth - 1,
            topPos + VIEWER_SECTION_TOP,
            0xFFC6C6C6
        );

        // 从下到上绘制，让排在前面的标签和展开面板保持在最上层。
        mountPanel.drawBackground(graphics);
        automationPanel.drawBackground(graphics);
        dropPanel.drawBackground(graphics);
        infoPanel.drawBackground(graphics);
        continuousPanel.drawBackground(graphics);
        aimPanel.drawBackground(graphics);
        simulationPanel.drawBackground(graphics);

        drawTargetEntity(graphics, mouseX, mouseY);
        drawSelectorAreaSideBorders(graphics);
    }

    private void clearCraftingArea(GuiGraphicsExtractor graphics) {
        graphics.fill(
            leftPos + CRAFTING_AREA_LEFT,
            topPos + CRAFTING_AREA_TOP,
            leftPos + CRAFTING_AREA_LEFT + CRAFTING_AREA_WIDTH,
            topPos + CRAFTING_AREA_TOP + CRAFTING_AREA_HEIGHT,
            0xFFC6C6C6
        );
    }

    private void drawTargetEntity(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (minecraft.level == null) {
            return;
        }
        Entity entity = minecraft.level.getEntity(menu.targetEntityId());
        if (entity instanceof LivingEntity livingEntity) {
            InventoryScreen.extractEntityInInventoryFollowsMouse(
                graphics,
                leftPos + 26,
                topPos + 8,
                leftPos + 75,
                topPos + 78,
                30,
                0.0625F,
                mouseX,
                mouseY,
                livingEntity
            );
        }
    }

    private void drawInfoPanelContents(GuiGraphicsExtractor graphics, int left, int top) {
        int labelLeft = left + 7;
        int statusLeft = labelLeft + statusLabelWidth() + 4;
        int line = top + 66;
        drawStatusLabel(graphics, "gui.archweaver.fakeplayer.info.health", labelLeft, line);
        drawHealth(graphics, statusLeft, line);
        drawStatusLabel(graphics, "gui.archweaver.fakeplayer.info.food", labelLeft, line + 15);
        drawFood(graphics, statusLeft, line + 15);
        drawSaturation(graphics, statusLeft, line + 15);
        drawStatusLabel(graphics, "gui.archweaver.fakeplayer.info.armor", labelLeft, line + 30);
        drawArmor(graphics, statusLeft, line + 30);
        drawStatusLabel(graphics, "gui.archweaver.fakeplayer.info.air", labelLeft, line + 45);
        drawAir(graphics, statusLeft, line + 45);
        graphics.text(font, Component.translatable("gui.archweaver.fakeplayer.info.game_mode"), labelLeft, top + 51,
            0xFF404040, false);
    }

    private int statusLabelWidth() {
        int width = font.width(Component.translatable("gui.archweaver.fakeplayer.info.health"));
        width = Math.max(width, font.width(Component.translatable("gui.archweaver.fakeplayer.info.food")));
        width = Math.max(width, font.width(Component.translatable("gui.archweaver.fakeplayer.info.armor")));
        return Math.max(width, font.width(Component.translatable("gui.archweaver.fakeplayer.info.air")));
    }

    private void drawStatusLabel(GuiGraphicsExtractor graphics, String key, int left, int top) {
        graphics.text(font, Component.translatable(key), left, top, 0xFF404040, false);
    }

    /** 按原版 HUD 的取整和半颗心规则绘制生命值。 */
    private void drawHealth(GuiGraphicsExtractor graphics, int left, int top) {
        int health = (int) Math.ceil(menu.health());
        int containers = Math.min(STATUS_ICON_COUNT, (int) Math.ceil(menu.maxHealth() / 2.0F));
        for (int index = 0; index < containers; index++) {
            int x = left + index * STATUS_ICON_SPACING;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_CONTAINER_SPRITE,
                x, top, STATUS_ICON_SIZE, STATUS_ICON_SIZE);
            int halfHealth = index * 2;
            if (halfHealth < health) {
                Identifier sprite = halfHealth + 1 == health ? HEART_HALF_SPRITE : HEART_FULL_SPRITE;
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite,
                    x, top, STATUS_ICON_SIZE, STATUS_ICON_SIZE);
            }
        }
    }

    private void drawArmor(GuiGraphicsExtractor graphics, int left, int top) {
        drawStatusRow(graphics, left, top, menu.armor(),
            ARMOR_EMPTY_SPRITE, ARMOR_HALF_SPRITE, ARMOR_FULL_SPRITE, false);
    }

    private void drawFood(GuiGraphicsExtractor graphics, int left, int top) {
        int food = Math.clamp(menu.food(), 0, STATUS_ICON_COUNT * 2);
        for (int index = 0; index < STATUS_ICON_COUNT; index++) {
            int x = left + (STATUS_ICON_COUNT - 1 - index) * STATUS_ICON_SPACING;
            // 原版先绘制空槽轮廓，再将完整或半格食物叠加在上面。
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FOOD_EMPTY_SPRITE,
                x, top, STATUS_ICON_SIZE, STATUS_ICON_SIZE);
            int halfFood = index * 2;
            if (halfFood < food) {
                Identifier sprite = halfFood + 1 == food ? FOOD_HALF_SPRITE : FOOD_FULL_SPRITE;
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite,
                    x, top, STATUS_ICON_SIZE, STATUS_ICON_SIZE);
            }
        }
    }

    /** 使用 AppleSkin 的四级饱和度图标，从右向左绘制。 */
    private void drawSaturation(GuiGraphicsExtractor graphics, int left, int top) {
        float saturation = Math.clamp(menu.saturation(), 0.0F, STATUS_ICON_COUNT * 2.0F);
        int iconCount = (int) Math.ceil(saturation / 2.0F);
        for (int index = 0; index < iconCount; index++) {
            float effectiveValue = saturation / 2.0F - index;
            int textureX = effectiveValue >= 1.0F ? 27
                : effectiveValue > 0.5F ? 18 : effectiveValue > 0.25F ? 9 : 0;
            graphics.blit(RenderPipelines.GUI_TEXTURED, APPLESKIN_ICONS,
                left + (STATUS_ICON_COUNT - 1 - index) * STATUS_ICON_SPACING,
                top, textureX, 0.0F, STATUS_ICON_SIZE, STATUS_ICON_SIZE, 256, 256);
        }
    }

    private void drawAir(GuiGraphicsExtractor graphics, int left, int top) {
        int maxAir = menu.maxAirSupply();
        int bubbles = maxAir <= 0 ? 0
            : (int) Math.ceil((double) Math.clamp(menu.airSupply(), 0, maxAir) * STATUS_ICON_COUNT / maxAir);
        for (int index = 0; index < STATUS_ICON_COUNT; index++) {
            Identifier sprite = index < bubbles ? AIR_FULL_SPRITE : AIR_EMPTY_SPRITE;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite,
                left + (STATUS_ICON_COUNT - 1 - index) * STATUS_ICON_SPACING,
                top, STATUS_ICON_SIZE, STATUS_ICON_SIZE);
        }
    }

    private void drawStatusRow(
        GuiGraphicsExtractor graphics,
        int left,
        int top,
        int value,
        Identifier emptySprite,
        Identifier halfSprite,
        Identifier fullSprite,
        boolean rightToLeft
    ) {
        int clampedValue = Math.clamp(value, 0, STATUS_ICON_COUNT * 2);
        for (int index = 0; index < STATUS_ICON_COUNT; index++) {
            int halfValue = index * 2;
            Identifier sprite = halfValue + 1 < clampedValue
                ? fullSprite
                : halfValue + 1 == clampedValue ? halfSprite : emptySprite;
            int column = rightToLeft ? STATUS_ICON_COUNT - 1 - index : index;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite,
                left + column * STATUS_ICON_SPACING, top, STATUS_ICON_SIZE, STATUS_ICON_SIZE);
        }
    }

    private GameType gameType() {
        return GameType.byId(menu.gameMode());
    }

    private Component gameModeName(GameType gameType) {
        String id = switch (gameType) {
            case CREATIVE -> "creative";
            case ADVENTURE -> "adventure";
            case SPECTATOR -> "spectator";
            case SURVIVAL -> "survival";
        };
        return Component.translatable("selectWorld.gameMode." + id);
    }

    private void syncAimInputs() {
        if (pitchInput == null || yawInput == null) return;
        syncingAimInputs = true;
        if (!pitchInput.isFocused()) {
            pitchInput.setValue(Integer.toString(menu.pitch()));
        }
        if (!yawInput.isFocused()) {
            yawInput.setValue(Integer.toString(menu.yaw()));
        }
        syncingAimInputs = false;
    }

    private void drawAimPanelContents(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.text(font, Component.translatable("gui.archweaver.fakeplayer.look.view"), x + 16, y + 41,
            0xFF606060, false);
        graphics.text(font, Component.translatable("gui.archweaver.fakeplayer.look.direction"), x + 16, y + 115,
            0xFF606060, false);
        graphics.text(font, Component.translatable("gui.archweaver.fakeplayer.look_pitch"), x + 6, y + 197,
            0xFF404040, false);
        graphics.text(font, Component.translatable("gui.archweaver.fakeplayer.look_yaw"), x + 6, y + 217,
            0xFF404040, false);
    }

    /** 给快捷栏选择区左右两侧绘制外边框，左侧黑边内为高光，右侧黑边内为阴影。 */
    private void drawSelectorAreaSideBorders(GuiGraphicsExtractor graphics) {
        int top = topPos + TARGET_INVENTORY_HEIGHT;
        int bottom = topPos + VIEWER_SECTION_TOP;
        graphics.fill(leftPos, top, leftPos + 1, bottom, 0xFF000000);
        graphics.fill(leftPos + 1, top, leftPos + 3, bottom, 0xFFFFFFFF);
        graphics.fill(leftPos + imageWidth - 3, top, leftPos + imageWidth - 1, bottom, 0xFF555555);
        graphics.fill(leftPos + imageWidth - 1, top, leftPos + imageWidth, bottom, 0xFF000000);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (gameModeButton != null && gameModeButton.popupMouseClicked(event)) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && heldAction >= 0) {
            if (heldStarted) {
                sendAction(FakePlayerInventoryMenu.ACTION_STOP_HELD);
            }
            resetHeldAction();
        }
        return super.mouseReleased(event);
    }

    @Override
    public void removed() {
        if (heldStarted) {
            sendAction(FakePlayerInventoryMenu.ACTION_STOP_HELD);
        }
        resetHeldAction();
        super.removed();
    }

    private void resetHeldAction() {
        heldAction = -1;
        heldStarted = false;
        heldTicks = 0;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (menu.view() == FakePlayerInventoryMenu.View.ENDER_CHEST) {
            super.extractLabels(graphics, mouseX, mouseY);
            return;
        }
        if (menu.view() == FakePlayerInventoryMenu.View.POSSESSED_INVENTORY) {
            return;
        }

        graphics.text(font, title, 97, 6, -12566464, false);
        graphics.text(font, playerInventoryTitle, 8, 167, -12566464, false);
        if (gameModeButton != null) {
            gameModeButton.extractPopup(graphics, mouseX, mouseY, leftPos, topPos);
        }
    }
}
