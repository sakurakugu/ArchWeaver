package com.sakurakugu.archweaver.client;

import com.sakurakugu.archweaver.ArchWeaverMod;
import com.sakurakugu.archweaver.client.ui.IntegerSliderButton;
import com.sakurakugu.archweaver.client.ui.TargetPositionDisplay;
import com.sakurakugu.archweaver.entity.FakePlayerAlias;
import com.sakurakugu.archweaver.network.RenameFakePlayerPayload;
import com.sakurakugu.archweaver.network.SetFakePlayerAliasPayload;
import net.minecraft.client.gui.components.Button;
import com.sakurakugu.archweaver.client.ui.InventorySlotButton;
import com.sakurakugu.archweaver.client.ui.OverlayPanelManager;
import com.sakurakugu.archweaver.client.ui.PixelGui;
import com.sakurakugu.archweaver.client.ui.PixelGlyph;
import com.sakurakugu.archweaver.client.ui.RotationPad;
import com.sakurakugu.archweaver.client.ui.SegmentedSwitchButton;
import com.sakurakugu.archweaver.client.ui.SolidButton;
import com.sakurakugu.archweaver.client.ui.SolidDropdownButton;
import com.sakurakugu.archweaver.client.ui.ToggleSwitchButton;
import com.sakurakugu.archweaver.client.ui.TopBar;
import com.sakurakugu.archweaver.menu.MannequinInventoryMenu;
import com.sakurakugu.archweaver.mixin.MannequinInvoker;
import com.sakurakugu.archweaver.network.AvatarSkinPartPayload;
import com.sakurakugu.archweaver.network.MannequinLifecyclePayload;
import com.sakurakugu.archweaver.network.MannequinSettingsPayload;
import com.sakurakugu.archweaver.network.TargetTypePayload;
import com.sakurakugu.archweaver.persistence.MannequinSavedData;
import com.sakurakugu.archweaver.platform.PlatformNetworking;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** 玩偶背包中央展示当前姿态的大预览，两侧提供装备和设置。 */
public final class MannequinInventoryScreen extends AbstractContainerScreen<MannequinInventoryMenu> {
    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/inventory.png");
    private static final Identifier CONTAINER_BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final FontDescription EFFECT_DURATION_FONT = new FontDescription.Resource(
        Identifier.fromNamespaceAndPath(ArchWeaverMod.MOD_ID, "effect_duration")); // 时间专用字体，将星号字形调整为与数字等高。
    private static final Pose[] POSES = {Pose.STANDING, Pose.CROUCHING, Pose.SWIMMING, Pose.FALL_FLYING, Pose.SLEEPING};
    private static final String[] POSE_KEYS = {"standing", "crouching", "swimming", "fall_flying", "sleeping"};
    // 移除按钮位于双手装备列上方，不占用中央预览。
    private static final int ACTION_BUTTON_LEFT = MannequinInventoryMenu.HAND_SLOT_LEFT - 1;
    private static final int ACTION_BUTTON_TOP = 7; // 移除按钮的顶边偏移，单位为像素。
    private static final int PREVIEW_LEFT = 26;
    private static final int PREVIEW_RIGHT = 150;
    private static final int PREVIEW_TOP = 7;
    private static final int PREVIEW_BOTTOM = 118;
    private static final int PREVIEW_SCALE = 52; // 放大模型，并给伸展的四肢留出空间。
    // 预览正下方的旋转滑条：压到很窄的一条，横向占满整个模型区域，只转画面中的模型，不动实体朝向。
    private static final int PREVIEW_ROTATION_SLIDER_HEIGHT = 8; // 滑条高度，只占一条细边。
    private static final int PREVIEW_ROTATION_MIN = -180; // 滑条取值下界（含），与上界合起来正好一周。
    private static final int PREVIEW_ROTATION_MAX = 179; // 滑条取值上界（含）。
    private static final int SIDE_BAR_HEIGHT = 264; // 左侧面板竖排后占用的高度，窄窗口按它把标签整体上移。
    // 视角与朝向面板沿用假人页面的尺寸与内部排布。
    private static final int LOOK_PANEL_WIDTH = 94; // 面板宽度，单位为像素。
    private static final int LOOK_PANEL_HEIGHT = 234; // 面板高度，单位为像素。
    private static final int LOOK_PAD_LEFT = 16; // 两个摇杆相对面板左边缘的偏移。
    private static final int LOOK_PAD_SIZE = 62; // 摇杆的边长，单位为像素。
    private static final int LOOK_VIEW_PAD_TOP = 50; // 视角摇杆相对面板顶边的偏移。
    private static final int LOOK_BODY_PAD_TOP = 124; // 方向摇杆相对面板顶边的偏移。
    private static final int LOOK_INPUT_LEFT = 36; // 角度输入框相对面板左边缘的偏移。
    private static final int LOOK_INPUT_WIDTH = 52; // 角度输入框的宽度，单位为像素。
    private static final int LOOK_PITCH_INPUT_TOP = 192; // 俯仰角输入框相对面板顶边的偏移。
    private static final int LOOK_YAW_INPUT_TOP = 212; // 偏航角输入框相对面板顶边的偏移。
    // 四肢角度面板：每个肢体的 X、Y、Z 三条滑动条竖排成一列，名称行右端放该肢体的重置按钮。
    private static final int LIMB_SLIDER_HEIGHT = 16; // 单条滑动条的高度，单位为像素。
    private static final int LIMB_SLIDER_STEP = 18; // 同一肢体相邻两条滑动条的偏移，含 2 像素间隙。
    private static final int LIMB_LABEL_TOP = 25; // 肢体名称相对面板顶边的偏移。
    private static final int LIMB_RESET_SIZE = 16; // 名称行重置按钮的边长，边框内正好放下 12 像素图标。
    private static final int LIMB_RESET_TOP = 22; // 重置按钮相对面板顶边的偏移，与名称行垂直居中。
    private static final int LIMB_SLIDER_TOP = 40; // 第一组滑动条相对面板顶边的偏移，让开上方的重置按钮。
    private static final int LIMB_GROUP_STEP = 72; // 同一面板内第二组肢体相对第一组名称行的偏移。
    private static final int LIMB_PANEL_HEIGHT = LIMB_SLIDER_TOP + LIMB_GROUP_STEP
        + LIMB_SLIDER_STEP * 2 + LIMB_SLIDER_HEIGHT + 7; // 两组滑动条加底部内边距后的面板高度。
    // 药水效果网格：固定显示三行三列，超出部分整行滚动。
    private static final int EFFECTS_COLUMNS = 3; // 网格列数。
    private static final int EFFECTS_VISIBLE_ROWS = 3; // 同时显示的行数。
    private static final int EFFECT_CELL_WIDTH = 32; // 单元格宽度，单元格之间不留横向间距。
    private static final int EFFECT_CELL_HEIGHT = 32; // 单元格高度，单元格之间不留纵向间距。
    private static final int EFFECT_GRID_PADDING = 5; // 网格左右和底部的内边距，包含面板边框。
    private static final int EFFECT_GRID_TOP = 23; // 网格相对面板顶部的偏移。
    private static final int EFFECT_GRID_WIDTH = EFFECTS_COLUMNS * EFFECT_CELL_WIDTH; // 网格可见区域宽度。
    private static final int EFFECT_GRID_HEIGHT = EFFECTS_VISIBLE_ROWS * EFFECT_CELL_HEIGHT; // 网格可见区域高度。
    private static final int EFFECT_SCROLLBAR_GAP = 2; // 网格与滚动条之间的间距。
    private static final int EFFECT_SCROLLBAR_WIDTH = 4; // 始终预留的滚动条宽度。
    private static final int EFFECTS_PANEL_TOP = 34; // 药水效果面板紧接玩偶设置标签下方。
    private static final int EFFECTS_PANEL_WIDTH = EFFECT_GRID_PADDING * 2
        + EFFECT_GRID_WIDTH + EFFECT_SCROLLBAR_GAP + EFFECT_SCROLLBAR_WIDTH; // 由网格宽度推出的面板宽度。
    private static final int EFFECTS_PANEL_HEIGHT = EFFECT_GRID_TOP + EFFECT_GRID_HEIGHT
        + EFFECT_GRID_PADDING; // 底部边距与左侧相同，不再额外留空。
    private static final String LOOK_PANEL_ID = "look"; // 视角与朝向面板的唯一标识。
    private static final String EFFECTS_PANEL_ID = "effects"; // 药水效果面板的唯一标识。
    // 皮肤部件面板：七个开关竖排，行距与按钮等高，靠开关图形自身的上下留白分隔。
    private static final int SKIN_TOGGLE_TOP = 25; // 第一个开关相对面板顶边的偏移。
    private static final int SKIN_TOGGLE_HEIGHT = 18; // 单个开关按钮的高度，单位为像素。
    private static final int SKIN_TOGGLE_STEP = 18; // 相邻开关的偏移，按钮之间不再留空隙。
    private static final int SKIN_PANEL_BOTTOM_PADDING = 7; // 最后一个开关下方的内边距，与左列其他面板一致。
    private static final int SKIN_PANEL_HEIGHT = SKIN_TOGGLE_TOP
        + SKIN_TOGGLE_STEP * (PlayerModelPart.values().length - 1) + SKIN_TOGGLE_HEIGHT
        + SKIN_PANEL_BOTTOM_PADDING; // 全部开关加底部内边距后的面板高度。
    // 玩偶信息面板：身份两行固定在上方，生物行为开启时按生命、护甲、氧气依次排开，
    // 坐标行始终跟在三行之后，与假人页面的坐标行位置一致；三行都不显示时坐标行上移、面板收缩。
    private static final int INFO_PANEL_WIDTH = 132; // 面板宽度，单位为像素。
    private static final int INFO_NAME_ROW = 28; // 名称输入行相对面板顶边的偏移。
    private static final int INFO_ALIAS_ROW = 47; // 别名输入行相对面板顶边的偏移。
    private static final int INFO_STATUS_ROW = 68; // 生命值行相对面板顶边的偏移，也是未开生物行为时坐标行的基准偏移。
    private static final int INFO_STATUS_STEP = 15; // 生命、护甲、氧气三行的行距，状态图标高 9 像素，行间留 6 像素。
    private static final int INFO_POSITION_LIFT = 3; // 坐标行在信息区末尾之上再上移的距离，让坐标贴近护甲与氧气。
    private static final int INFO_POSITION_HEIGHT = 15; // 坐标行的高度，复制按钮高 14 并与行顶对齐。
    private static final int INFO_PANEL_BOTTOM_PADDING = 7; // 坐标行下方的内边距。
    private static final int INFO_PANEL_HEIGHT = INFO_STATUS_ROW + INFO_STATUS_STEP * 3 - INFO_POSITION_LIFT
        + INFO_POSITION_HEIGHT + INFO_PANEL_BOTTOM_PADDING; // 生命、护甲、氧气都在时的面板高度，用于初始化时锚定面板。
    private OverlayPanelManager panels;
    private OverlayPanelManager rightPanels;
    private OverlayPanelManager.Panel settingsPanel, posePanel, armsPanel, legsPanel, skinPanel, effectsPanel, lookPanel, infoPanel;
    private EditBox nameInput, aliasInput;
    private TargetPositionDisplay positionDisplay; // 坐标行，位置随信息区实际高度下移。
    private Button copyPositionButton; // 坐标复制按钮，始终与坐标行同一行。
    private String syncedName, syncedAlias;
    private SolidDropdownButton<Pose> presetButton;
    private SolidButton resetAnglesButton;
    private RotationPad viewPad; // 视角摇杆，用于调整俯仰角与偏航角。
    private RotationPad directionPad; // 方向摇杆，用于调整身体朝向。
    private ToggleSwitchButton bodyFollowsHeadButton; // “头身联动”开关。
    private EditBox pitchInput; // 俯仰角输入框，取值范围 -90 到 90。
    private EditBox yawInput; // 偏航角输入框，取值范围 -180 到 179。
    private boolean syncingLookInputs; // 是否正在同步角度输入框，用于避免回调递归。
    private int lastSentPitch; // 最近一次提交的俯仰角，用于去重。
    private int lastSentYaw; // 最近一次提交的偏航角，用于去重。
    private int effectsScrollRow; // 药水效果网格当前显示的首行。
    private boolean draggingEffectsScrollbar; // 是否正在拖动药水效果滚动条。
    private double effectsScrollbarGrabOffset; // 鼠标按下位置相对滚动条滑块顶部的偏移。
    private final int[][] angles = new int[4][3];
    private final IntegerSliderButton[][] angleSliders = new IntegerSliderButton[4][3];
    private int previewRotation; // 预览正下方滑条控制的画面旋转角度，只参与这一处渲染，不影响实体朝向。

    public MannequinInventoryScreen(MannequinInventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, MannequinInventoryMenu.IMAGE_WIDTH, MannequinInventoryMenu.IMAGE_HEIGHT);
    }

    @Override protected void init() {
        String open = panels == null ? null : panels.openPanelId();
        String openRight = rightPanels == null ? null : rightPanels.openPanelId();
        super.init();
        // 窄窗口至少为左侧设置预留空间，常规窗口保持原版背包的居中位置。
        leftPos = Math.max(leftPos, Math.min(136, width - imageWidth - 4));
        panels = new OverlayPanelManager(font);
        rightPanels = new OverlayPanelManager(font);
        // 顶栏只放玩家/玩偶二态切换：当前是玩偶，选到玩家一侧即转换实体类型。
        addTopBar();
        addPreviewRotationSlider();
        // 复刻假人页面的移除按钮：只卸载玩偶实体，登记记录保留，回列表页后仍可重新加载。
        addRenderableWidget(new InventorySlotButton(leftPos + ACTION_BUTTON_LEFT, topPos + ACTION_BUTTON_TOP,
            new ItemStack(Items.BARRIER), Component.translatable("gui.archweaver.mannequin.remove"), button -> {
                PlatformNetworking.sendToServer(new MannequinLifecyclePayload(menu.mannequinName(),
                    MannequinLifecyclePayload.Action.UNLOAD));
                ClientScreenNavigation.back(this);
            }));
        settingsPanel = panel("settings", 8, 100, 70,
            Component.translatable("gui.archweaver.mannequin.settings"), Items.ARMOR_STAND);
        var immovable = addRenderableWidget(new ToggleSwitchButton(settingsPanel.getX() + 6, settingsPanel.getY() + 25,
            settingsPanel.contentWidth() - 12, 18,
            Component.translatable("gui.archweaver.mannequin.immovable"), () -> menu.mannequin() != null && ((MannequinInvoker) menu.mannequin()).archweaver$getImmovable(),
            button -> { if (menu.mannequin() != null) send(MannequinSettingsPayload.Action.IMMOVABLE, 0,
                !((MannequinInvoker) menu.mannequin()).archweaver$getImmovable()); }));
        var biological = addRenderableWidget(new ToggleSwitchButton(settingsPanel.getX() + 6, settingsPanel.getY() + 45,
            settingsPanel.contentWidth() - 12, 18,
            Component.translatable("gui.archweaver.mannequin.biological_behavior"), () -> menu.mannequin() != null && !menu.mannequin().isNoGravity(),
            button -> { if (menu.mannequin() != null) send(MannequinSettingsPayload.Action.BIOLOGICAL_BEHAVIOR, 0, menu.mannequin().isNoGravity()); }));
        settingsPanel.bindContents(immovable, biological);
        // 视角与朝向排在右侧标签最上方，注册顺序与面板自上而下一致。
        addLookPanel();
        int poseWidth = LOOK_PANEL_WIDTH; // 与视角与朝向同宽，右列面板宽度统一且不再随窗口变化。
        posePanel = rightPanel("pose", 34, poseWidth, 70,
            Component.translatable("gui.archweaver.mannequin.pose"), Items.DIAMOND_CHESTPLATE);
        List<AbstractWidget> poseControls = new ArrayList<>();
        presetButton = addRenderableWidget(new SolidDropdownButton<>(posePanel.getX() + 6, posePanel.getY() + 25,
            poseWidth - 30, 18,
            List.of(POSES), POSES[poseIndex()], pose -> Component.translatable(
                "gui.archweaver.mannequin.pose." + POSE_KEYS[List.of(POSES).indexOf(pose)]),
            pose -> send(MannequinSettingsPayload.Action.POSE, List.of(POSES).indexOf(pose), true)));
        poseControls.add(presetButton);
        resetAnglesButton = addRenderableWidget(new SolidButton(posePanel.getX() + poseWidth - 24, posePanel.getY() + 25,
            18, 18, PixelGlyph.RESET, Component.translatable("gui.archweaver.mannequin.reset_angles"), button -> {
                for (int limb = 0; limb < 4; limb++) {
                    java.util.Arrays.fill(angles[limb], 0);
                    for (IntegerSliderButton slider : angleSliders[limb]) slider.setRange(-180, 180, 0);
                    sendLimb(limb);
                }
            }));
        poseControls.add(resetAnglesButton);
        posePanel.setContentRenderer((graphics, x, y) -> presetButton.setSelected(POSES[poseIndex()]));
        posePanel.bindContents(poseControls.toArray(AbstractWidget[]::new));

        armsPanel = rightPanel("arms", 60, poseWidth, LIMB_PANEL_HEIGHT,
            Component.translatable("gui.archweaver.mannequin.arms"), Items.IRON_SWORD);
        legsPanel = rightPanel("legs", 86, poseWidth, LIMB_PANEL_HEIGHT,
            Component.translatable("gui.archweaver.mannequin.legs"), Items.LEATHER_BOOTS);
        List<AbstractWidget> armsControls = new ArrayList<>();
        List<AbstractWidget> legsControls = new ArrayList<>();
        String[] names = {"left_arm", "right_arm", "left_leg", "right_leg"};
        for (int limb = 0; limb < 4; limb++) {
            MannequinSavedData.Angles rotation = ClientMannequinAngles.get(menu.mannequinId(), limb);
            angles[limb] = new int[] {Math.round(rotation.x()), Math.round(rotation.y()), Math.round(rotation.z())};
            OverlayPanelManager.Panel targetPanel = limb < 2 ? armsPanel : legsPanel;
            int group = limb % 2; // 同一面板内该肢体所属的组，决定名称行与滑动条的位置。
            int limbIndex = limb; // 回调里需要 effectively final 的肢体序号。
            int[] limbAngles = angles[limb];
            IntegerSliderButton[] limbSliders = angleSliders[limb];
            List<AbstractWidget> groupControls = limb < 2 ? armsControls : legsControls;
            for (int axis = 0; axis < 3; axis++) {
                int a = axis;
                IntegerSliderButton slider = addRenderableWidget(new IntegerSliderButton(targetPanel.getX() + 6,
                    targetPanel.getY() + LIMB_SLIDER_TOP + group * LIMB_GROUP_STEP + axis * LIMB_SLIDER_STEP,
                    targetPanel.contentWidth() - 12, LIMB_SLIDER_HEIGHT, -180, 180, limbAngles[axis],
                    value -> Component.literal("XYZ".charAt(a) + ": " + value + "°"),
                    value -> { limbAngles[a] = value; sendLimb(limbIndex); }));
                limbSliders[axis] = slider;
                groupControls.add(slider);
            }
            // 名称行右端的重置按钮只清零当前肢体，其余肢体保持不动。
            groupControls.add(addRenderableWidget(new SolidButton(
                targetPanel.getX() + targetPanel.contentWidth() - 6 - LIMB_RESET_SIZE,
                targetPanel.getY() + LIMB_RESET_TOP + group * LIMB_GROUP_STEP,
                LIMB_RESET_SIZE, LIMB_RESET_SIZE, PixelGlyph.RESET,
                Component.translatable("gui.archweaver.mannequin.reset_limb",
                    Component.translatable("gui.archweaver.mannequin." + names[limbIndex])), button -> {
                    java.util.Arrays.fill(limbAngles, 0);
                    for (IntegerSliderButton slider : limbSliders) slider.setRange(-180, 180, 0);
                    sendLimb(limbIndex);
                })));
        }
        armsPanel.setContentRenderer((graphics, x, y) -> {
            graphics.text(font, Component.translatable("gui.archweaver.mannequin." + names[0]), x + 6, y + LIMB_LABEL_TOP, 0xFF404040, false);
            graphics.text(font, Component.translatable("gui.archweaver.mannequin." + names[1]), x + 6, y + LIMB_LABEL_TOP + LIMB_GROUP_STEP, 0xFF404040, false);
        });
        legsPanel.setContentRenderer((graphics, x, y) -> {
            graphics.text(font, Component.translatable("gui.archweaver.mannequin." + names[2]), x + 6, y + LIMB_LABEL_TOP, 0xFF404040, false);
            graphics.text(font, Component.translatable("gui.archweaver.mannequin." + names[3]), x + 6, y + LIMB_LABEL_TOP + LIMB_GROUP_STEP, 0xFF404040, false);
        });
        armsPanel.bindContents(armsControls.toArray(AbstractWidget[]::new));
        legsPanel.bindContents(legsControls.toArray(AbstractWidget[]::new));
        // 药水效果排在皮肤部件之前注册，标签顺序与面板自上而下一致。
        addEffectsPanel();
        skinPanel = panel("skin", 60, 100, SKIN_PANEL_HEIGHT,
            Component.translatable("gui.archweaver.fakeplayer.skin_parts.title"), Items.LEATHER_CHESTPLATE);
        List<AbstractWidget> skinControls = new ArrayList<>();
        PlayerModelPart[] parts = PlayerModelPart.values();
        for (int i = 0; i < parts.length; i++) {
            PlayerModelPart part = parts[i];
            skinControls.add(addRenderableWidget(new ToggleSwitchButton(skinPanel.getX() + 6,
                skinPanel.getY() + SKIN_TOGGLE_TOP + i * SKIN_TOGGLE_STEP,
                skinPanel.contentWidth() - 12, SKIN_TOGGLE_HEIGHT,
                Component.translatable("gui.archweaver.fakeplayer.skin_parts." + part.getId()),
                () -> menu.mannequin() != null && menu.mannequin().isModelPartShown(part),
                button -> { if (menu.mannequin() != null) PlatformNetworking.sendToServer(new AvatarSkinPartPayload(menu.containerId,
                    menu.mannequinId(), part, !menu.mannequin().isModelPartShown(part))); })));
        }
        skinPanel.bindContents(skinControls.toArray(AbstractWidget[]::new));
        addInfoPanel();
        panels.restoreOpenPanel(open);
        rightPanels.restoreOpenPanel(openRight);
    }

    /** 顶栏上的玩家/玩偶二态切换；右侧的玩偶是当前状态，切到左侧时提交类型转换。 */
    private void addTopBar() {
        TopBar bar = topBar();
        addRenderableWidget(new SegmentedSwitchButton(
            bar.contentX(), bar.contentY(), bar.contentWidth(), bar.contentHeight(),
            Component.translatable("gui.archweaver.fakeplayer.target_type.player"),
            Component.translatable("gui.archweaver.fakeplayer.target_type.mannequin"),
            () -> true,
            mannequin -> {
                if (!mannequin) {
                    PlatformNetworking.sendToServer(new TargetTypePayload(
                        menu.containerId, menu.mannequinId(), false));
                }
            }));
    }

    private TopBar topBar() {
        return TopBar.overRightHalf(leftPos, topPos, imageWidth);
    }

    private OverlayPanelManager.Panel panel(String id, int top, int width, int height, String title, net.minecraft.world.item.Item icon) {
        return panel(id, top, width, height, Component.literal(title), icon);
    }

    private OverlayPanelManager.Panel panel(String id, int top, int width, int height, Component title, net.minecraft.world.item.Item icon) {
        int anchorY = Math.max(4, Math.min(topPos, this.height - SIDE_BAR_HEIGHT));
        var panel = panels.addLeftPanel(id, leftPos - width, anchorY,
            new OverlayPanelManager.Layout(top, width, height, 21, 24), title);
        addRenderableWidget(panel);
        addRenderableWidget(panel.createTab(new ItemStack(icon)));
        return panel;
    }

    /** 右侧面板与左侧一样在窄窗口里整体上移，保证整个面板可见。 */
    private OverlayPanelManager.Panel rightPanel(String id, int top, int width, int height, String title, net.minecraft.world.item.Item icon) {
        return rightPanel(id, top, width, height, Component.literal(title), icon);
    }

    private OverlayPanelManager.Panel rightPanel(String id, int top, int width, int height, Component title, net.minecraft.world.item.Item icon) {
        int anchorY = Math.max(4, Math.min(topPos, this.height - (top + height) - 4));
        var panel = rightPanels.addRightPanel(id, leftPos + imageWidth, anchorY,
            new OverlayPanelManager.Layout(top, width, height, 21, 24), title);
        addRenderableWidget(panel);
        addRenderableWidget(panel.createTab(new ItemStack(icon)));
        return panel;
    }

    /** 信息面板复用原版描述作为别名，皮肤档案独立保留。 */
    private void addInfoPanel() {
        infoPanel = rightPanel("info", 112, INFO_PANEL_WIDTH, INFO_PANEL_HEIGHT,
            Component.translatable("gui.archweaver.mannequin.info"), Items.NAME_TAG);
        int x = infoPanel.getX(), y = infoPanel.getY();
        nameInput = addRenderableWidget(new EditBox(font, x + 6, y + INFO_NAME_ROW, 86, 16,
            Component.translatable("gui.archweaver.fakeplayer.info.name")));
        nameInput.setMaxLength(16);
        nameInput.setValue(menu.mannequinName());
        nameInput.setHint(Component.translatable("gui.archweaver.fakeplayer.info.name"));
        nameInput.setTooltip(Tooltip.create(Component.translatable("gui.archweaver.mannequin.info.name_hint")));
        Button rename = addRenderableWidget(new SolidButton(x + 96, y + INFO_NAME_ROW, 28, 16,
            Component.translatable("gui.archweaver.fakeplayer.info.rename"), button ->
                PlatformNetworking.sendToServer(new RenameFakePlayerPayload(menu.containerId, nameInput.getValue().trim()))));
        aliasInput = addRenderableWidget(new EditBox(font, x + 6, y + INFO_ALIAS_ROW, 86, 16,
            Component.translatable("gui.archweaver.fakeplayer.info.alias")));
        aliasInput.setMaxLength(FakePlayerAlias.MAX_LENGTH);
        aliasInput.setValue(menu.alias());
        aliasInput.setHint(Component.translatable("entity.minecraft.mannequin.label"));
        aliasInput.setTooltip(Tooltip.create(Component.translatable("gui.archweaver.mannequin.info.alias_hint")));
        Button alias = addRenderableWidget(new SolidButton(x + 96, y + INFO_ALIAS_ROW, 28, 16,
            Component.translatable("gui.archweaver.fakeplayer.info.rename"), button ->
                PlatformNetworking.sendToServer(new SetFakePlayerAliasPayload(menu.containerId, aliasInput.getValue()))));
        positionDisplay = addRenderableWidget(new TargetPositionDisplay(font, x + 7, y + INFO_STATUS_ROW, 85,
            INFO_POSITION_HEIGHT, menu::targetInfo));
        copyPositionButton = addRenderableWidget(new SolidButton(x + 96, y + INFO_STATUS_ROW + 1, 28, 14,
            Component.translatable("gui.archweaver.fakeplayer.info.copy"), button -> {
                var info = menu.targetInfo();
                if (info != null) minecraft.keyboardHandler.setClipboard(info.x() + " " + info.y() + " " + info.z());
            }));
        infoPanel.bindContents(nameInput, rename, aliasInput, alias, positionDisplay, copyPositionButton);
        infoPanel.setContentRenderer(this::drawInfoPanelContents);
        layoutInfoPanel();
        syncedName = menu.mannequinName();
        syncedAlias = menu.alias();
    }

    /** 坐标行跟在生命、护甲、氧气三行下方，与假人页面一致；未开生物行为时三行都不显示，面板随之收缩。 */
    private void layoutInfoPanel() {
        if (positionDisplay == null) return;
        var info = menu.targetInfo();
        int row = (info != null && info.biological()
            ? INFO_STATUS_ROW + INFO_STATUS_STEP * 3
            : INFO_STATUS_ROW) - INFO_POSITION_LIFT;
        positionDisplay.setY(infoPanel.getY() + row);
        copyPositionButton.setY(infoPanel.getY() + row + 1);
        infoPanel.setContentHeight(row + INFO_POSITION_HEIGHT + INFO_PANEL_BOTTOM_PADDING);
    }

    private void drawInfoPanelContents(GuiGraphicsExtractor graphics, int x, int y) {
        var info = menu.targetInfo();
        if (info == null || !info.biological()) return;
        int labelWidth = Math.max(font.width(Component.translatable("gui.archweaver.fakeplayer.info.health")),
            Math.max(font.width(Component.translatable("gui.archweaver.fakeplayer.info.armor")),
                font.width(Component.translatable("gui.archweaver.fakeplayer.info.air"))));
        int icons = x + 11 + labelWidth;
        int healthRow = y + INFO_STATUS_ROW;
        int armorRow = healthRow + INFO_STATUS_STEP;
        int airRow = armorRow + INFO_STATUS_STEP;
        drawInfoLabel(graphics, "health", x + 7, healthRow);
        drawInfoLabel(graphics, "armor", x + 7, armorRow);
        drawInfoLabel(graphics, "air", x + 7, airRow);
        int hearts = Math.clamp((int) Math.ceil(info.maxHealth() / 2), 0, 10);
        int health = (int) Math.ceil(info.health());
        int armor = Math.clamp(info.armor(), 0, 20);
        for (int i = 0; i < 10; i++) {
            if (i < hearts) {
                drawStatusIcon(graphics, "heart/container", icons + i * 8, healthRow);
                if (i * 2 < health) drawStatusIcon(graphics, i * 2 + 1 == health ? "heart/half" : "heart/full",
                    icons + i * 8, healthRow);
            }
            drawStatusIcon(graphics, i * 2 + 1 < armor ? "armor_full" : i * 2 + 1 == armor ? "armor_half" : "armor_empty",
                icons + i * 8, armorRow);
        }
        // 氧气条和假人页面一样常驻显示，满氧时显示空泡，方便一眼确认氧气上限。
        int bubbles = info.maxAir() <= 0 ? 0 : (int) Math.ceil((double) Math.clamp(info.air(), 0, info.maxAir()) * 10 / info.maxAir());
        for (int i = 0; i < 10; i++) drawStatusIcon(graphics, i < bubbles ? "air" : "air_empty",
            icons + (9 - i) * 8, airRow);
    }

    private void drawInfoLabel(GuiGraphicsExtractor graphics, String key, int x, int y) {
        graphics.text(font, Component.translatable("gui.archweaver.fakeplayer.info." + key), x, y, 0xFF404040, false);
    }

    private void drawStatusIcon(GuiGraphicsExtractor graphics, String sprite, int x, int y) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace("hud/" + sprite), x, y, 9, 9);
    }

    /** 服务端确认修改或其他查看者编辑后更新输入；普通状态同步不会覆盖正在输入的内容。 */
    private void syncIdentityInputs() {
        if (!menu.mannequinName().equals(syncedName)) {
            syncedName = menu.mannequinName();
            nameInput.setValue(syncedName);
        }
        if (!menu.alias().equals(syncedAlias)) {
            syncedAlias = menu.alias();
            aliasInput.setValue(syncedAlias);
        }
    }

    /** 视角与朝向面板：视角摇杆、方向摇杆、头身联动开关与角度输入框。 */
    private void addLookPanel() {
        lookPanel = rightPanel(LOOK_PANEL_ID, 8, LOOK_PANEL_WIDTH, LOOK_PANEL_HEIGHT,
            Component.translatable("gui.archweaver.mannequin.look"), Items.COMPASS);
        int x = lookPanel.getX();
        int y = lookPanel.getY();
        lookPanel.setContentRenderer(this::drawLookPanelContents);
        bodyFollowsHeadButton = addRenderableWidget(new ToggleSwitchButton(
            x + 6, y + 20, lookPanel.contentWidth() - 12, 16,
            Component.translatable("gui.archweaver.fakeplayer.look.body_follows_head"),
            menu::bodyFollowsHead,
            button -> send(MannequinSettingsPayload.Action.TOGGLE_BODY_FOLLOWS_HEAD, 0, false)));
        bodyFollowsHeadButton.setTooltip(Tooltip.create(
            Component.translatable("gui.archweaver.fakeplayer.look.body_follows_head_tooltip")));
        viewPad = addRenderableWidget(new RotationPad(
            x + LOOK_PAD_LEFT, y + LOOK_VIEW_PAD_TOP, LOOK_PAD_SIZE, RotationPad.Mode.VIEW,
            menu::pitch, menu::yaw, menu::bodyYaw, menu::bodyFollowsHead,
            selectedYaw -> send(MannequinSettingsPayload.Action.BODY_YAW, selectedYaw, false),
            this::sendViewRotation));
        directionPad = addRenderableWidget(new RotationPad(
            x + LOOK_PAD_LEFT, y + LOOK_BODY_PAD_TOP, LOOK_PAD_SIZE, RotationPad.Mode.BODY,
            menu::pitch, menu::yaw, menu::bodyYaw, menu::bodyFollowsHead,
            selectedYaw -> send(MannequinSettingsPayload.Action.BODY_YAW, selectedYaw, false),
            this::sendViewRotation));
        pitchInput = addRenderableWidget(new EditBox(font, x + LOOK_INPUT_LEFT, y + LOOK_PITCH_INPUT_TOP,
            LOOK_INPUT_WIDTH, 16, Component.translatable("gui.archweaver.fakeplayer.look_pitch")));
        yawInput = addRenderableWidget(new EditBox(font, x + LOOK_INPUT_LEFT, y + LOOK_YAW_INPUT_TOP,
            LOOK_INPUT_WIDTH, 16, Component.translatable("gui.archweaver.fakeplayer.look_yaw")));
        pitchInput.setValue(Integer.toString(menu.pitch()));
        yawInput.setValue(Integer.toString(menu.yaw()));
        pitchInput.setFilter(value -> value.matches("-?\\d{0,3}"));
        yawInput.setFilter(value -> value.matches("-?\\d{0,3}"));
        pitchInput.setResponder(value -> {
            if (syncingLookInputs) return;
            submitAngleInput(pitchInput, value, -90, 90, true);
        });
        yawInput.setResponder(value -> {
            if (syncingLookInputs) return;
            submitAngleInput(yawInput, value, -180, 179, false);
        });
        lookPanel.bindContents(bodyFollowsHeadButton, viewPad, directionPad, pitchInput, yawInput);
    }

    /** 修正超出范围的角度输入，并把最终值发送到服务端。 */
    private void submitAngleInput(EditBox input, String value, int minimum, int maximum, boolean pitch) {
        try {
            int angle = Integer.parseInt(value);
            int clamped = Math.clamp(angle, minimum, maximum);
            if (angle != clamped) {
                syncingLookInputs = true;
                input.setValue(Integer.toString(clamped));
                syncingLookInputs = false;
            }
            sendViewRotation(pitch ? clamped : menu.pitch(), pitch ? menu.yaw() : clamped);
        } catch (NumberFormatException ignored) {
            // 空输入和单独的负号是编辑过程中的合法中间状态。
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
        PlatformNetworking.sendToServer(new MannequinSettingsPayload(menu.mannequinId(),
            MannequinSettingsPayload.Action.VIEW_ROTATION, 0, false, clampedPitch, wrappedYaw));
    }

    private void drawLookPanelContents(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.text(font, Component.translatable("gui.archweaver.fakeplayer.look.view"), x + 16, y + 41,
            0xFF606060, false);
        graphics.text(font, Component.translatable("gui.archweaver.fakeplayer.look.direction"), x + 16, y + 115,
            0xFF606060, false);
        graphics.text(font, Component.translatable("gui.archweaver.fakeplayer.look_pitch"), x + 6, y + 197,
            0xFF404040, false);
        graphics.text(font, Component.translatable("gui.archweaver.fakeplayer.look_yaw"), x + 6, y + 217,
            0xFF404040, false);
    }

    private void syncLookInputs() {
        if (pitchInput == null || yawInput == null) return;
        syncingLookInputs = true;
        if (!pitchInput.isFocused()) {
            pitchInput.setValue(Integer.toString(menu.pitch()));
        }
        if (!yawInput.isFocused()) {
            yawInput.setValue(Integer.toString(menu.yaw()));
        }
        syncingLookInputs = false;
    }

    private void addEffectsPanel() {
        effectsPanel = panel(EFFECTS_PANEL_ID, EFFECTS_PANEL_TOP, EFFECTS_PANEL_WIDTH, EFFECTS_PANEL_HEIGHT,
            Component.translatable("gui.archweaver.mannequin.effects"), Items.POTION);
        effectsPanel.setContentRenderer(this::drawEffectsPanelContents);
    }

    private boolean isEffectsPanelOpen() {
        return effectsPanel != null && effectsPanel.isOpen() && effectsPanel.visible;
    }

    /** 玩偶当前可见的药水效果；观赏模式不推进效果计时，这里只负责显示。 */
    private List<MobEffectInstance> visibleEffects() {
        var mannequin = menu.mannequin();
        List<MobEffectInstance> effects = mannequin == null ? List.of() : mannequin.getActiveEffects().stream()
            .filter(effect -> effect.isInfiniteDuration() || effect.getDuration() > 0)
            .filter(MobEffectInstance::showIcon)
            .sorted(Comparator.naturalOrder())
            .toList();
        // 效果消失时修正首行，避免滚动位置停留在已经不存在的行。
        effectsScrollRow = Math.clamp(effectsScrollRow, 0, maxEffectsScrollRow(effects.size()));
        if (maxEffectsScrollRow(effects.size()) == 0) {
            draggingEffectsScrollbar = false;
        }
        return effects;
    }

    private void drawEffectsPanelContents(GuiGraphicsExtractor graphics, int left, int top) {
        List<MobEffectInstance> effects = visibleEffects();
        drawEffectsScrollbar(graphics, effects.size());
        if (effects.isEmpty()) {
            drawEmptyEffectsMessage(graphics, left, top);
            return;
        }
        int gridLeft = left + EFFECT_GRID_PADDING;
        int gridTop = top + EFFECT_GRID_TOP;
        int firstIndex = effectsScrollRow * EFFECTS_COLUMNS;
        int endIndex = Math.min(effects.size(), firstIndex + EFFECTS_VISIBLE_ROWS * EFFECTS_COLUMNS);
        // 按整行滚动，只绘制可见的三行，避免内容越过网格区域。
        for (int index = firstIndex; index < endIndex; index++) {
            MobEffectInstance effect = effects.get(index);
            int column = index % EFFECTS_COLUMNS;
            int row = index / EFFECTS_COLUMNS - effectsScrollRow;
            int cellLeft = gridLeft + column * EFFECT_CELL_WIDTH;
            int cellTop = gridTop + row * EFFECT_CELL_HEIGHT;
            drawEffectCellBackground(graphics, cellLeft, cellTop, effect.isAmbient());
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Gui.getMobEffectSprite(effect.getEffect()),
                cellLeft + 7, cellTop + 2, 18, 18);
            // 图标下方继续隐藏超长持续时间；真实值只在悬停提示中显示。
            Component duration = effectMaskedDurationComponent(effect);
            graphics.text(font, duration,
                cellLeft + (EFFECT_CELL_WIDTH - font.width(duration)) / 2, cellTop + 21, 0xFFFFFFFF, true);
        }
    }

    private void drawEffectsScrollbar(GuiGraphicsExtractor graphics, int effectCount) {
        int left = effectsScrollbarLeft();
        int top = effectsPanel.getY() + EFFECT_GRID_TOP;
        int thumbTop = effectsScrollbarThumbTop(effectCount);
        int thumbHeight = effectsScrollbarThumbHeight(effectCount);
        graphics.fill(left, top, left + EFFECT_SCROLLBAR_WIDTH, top + EFFECT_GRID_HEIGHT, 0xFF212121);
        graphics.fill(left, thumbTop, left + EFFECT_SCROLLBAR_WIDTH, thumbTop + thumbHeight, 0xFF555555);
        graphics.fill(left, thumbTop, left + EFFECT_SCROLLBAR_WIDTH - 1, thumbTop + thumbHeight - 1, 0xFFC6C6C6);
        graphics.fill(left, thumbTop, left + 1, thumbTop + thumbHeight - 1, 0xFFFFFFFF);
    }

    private int effectsScrollbarLeft() {
        return effectsPanel.getX() + EFFECT_GRID_PADDING + EFFECT_GRID_WIDTH + EFFECT_SCROLLBAR_GAP;
    }

    private int maxEffectsScrollRow(int effectCount) {
        return Math.max(0, (effectCount + EFFECTS_COLUMNS - 1) / EFFECTS_COLUMNS - EFFECTS_VISIBLE_ROWS);
    }

    private int effectsScrollbarThumbHeight(int effectCount) {
        if (maxEffectsScrollRow(effectCount) == 0) {
            return EFFECT_GRID_HEIGHT;
        }
        int rows = (effectCount + EFFECTS_COLUMNS - 1) / EFFECTS_COLUMNS;
        return Math.max(8, EFFECT_GRID_HEIGHT * EFFECTS_VISIBLE_ROWS / rows);
    }

    private int effectsScrollbarThumbTop(int effectCount) {
        if (maxEffectsScrollRow(effectCount) == 0) {
            return effectsPanel.getY() + EFFECT_GRID_TOP;
        }
        int travel = EFFECT_GRID_HEIGHT - effectsScrollbarThumbHeight(effectCount);
        return effectsPanel.getY() + EFFECT_GRID_TOP
            + (int) Math.round((double) effectsScrollRow * travel / maxEffectsScrollRow(effectCount));
    }

    private void dragEffectsScrollbar(double mouseY, int effectCount) {
        int maxRow = maxEffectsScrollRow(effectCount);
        if (maxRow == 0) {
            return;
        }
        int travel = EFFECT_GRID_HEIGHT - effectsScrollbarThumbHeight(effectCount);
        double thumbOffset = mouseY - effectsPanel.getY() - EFFECT_GRID_TOP - effectsScrollbarGrabOffset;
        effectsScrollRow = Math.clamp((int) Math.round(thumbOffset * maxRow / travel), 0, maxRow);
    }

    private void drawEffectCellBackground(
        GuiGraphicsExtractor graphics, int left, int top, boolean ambient
    ) {
        int borderColor = ambient ? 0xFF005454 : 0xFF000000;
        int fillColor = ambient ? 0xFF00A8A8 : 0xFF555555;
        graphics.fill(left, top, left + EFFECT_CELL_WIDTH, top + EFFECT_CELL_HEIGHT, borderColor);
        graphics.fill(left + 1, top + 1, left + EFFECT_CELL_WIDTH - 1, top + EFFECT_CELL_HEIGHT - 1, fillColor);
        graphics.fill(left + 3, top + 3, left + EFFECT_CELL_WIDTH - 3, top + EFFECT_CELL_HEIGHT - 3, 0xFF212121);
    }

    private void drawEmptyEffectsMessage(GuiGraphicsExtractor graphics, int left, int top) {
        Component message = Component.translatable("gui.archweaver.fakeplayer.effects.empty");
        int contentTop = top + 21;
        int contentHeight = EFFECTS_PANEL_HEIGHT - 21;
        int messageLeft = left + (EFFECTS_PANEL_WIDTH - font.width(message)) / 2;
        int messageTop = contentTop + (contentHeight - font.lineHeight) / 2;
        graphics.text(font, message, messageLeft, messageTop, 0xFF606060, false);
    }

    private void drawEffectsTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!isEffectsPanelOpen() || draggingEffectsScrollbar) {
            return;
        }
        List<MobEffectInstance> effects = visibleEffects();
        int gridLeft = effectsPanel.getX() + EFFECT_GRID_PADDING;
        int gridTop = effectsPanel.getY() + EFFECT_GRID_TOP;
        int relativeX = mouseX - gridLeft;
        int relativeY = mouseY - gridTop;
        if (relativeX < 0 || relativeY < 0 || relativeX >= EFFECT_GRID_WIDTH || relativeY >= EFFECT_GRID_HEIGHT) {
            return;
        }
        int index = (effectsScrollRow + relativeY / EFFECT_CELL_HEIGHT) * EFFECTS_COLUMNS
            + relativeX / EFFECT_CELL_WIDTH;
        if (index >= effects.size()) {
            return;
        }
        MobEffectInstance effect = effects.get(index);
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(effect.getEffect().value().getDisplayName());
        tooltip.add(Component.translatable("gui.archweaver.fakeplayer.effects.level", effect.getAmplifier() + 1));
        tooltip.add(Component.translatable("gui.archweaver.fakeplayer.effects.duration",
            effectRealDurationComponent(effect)));
        if (effect.isAmbient()) {
            tooltip.add(Component.translatable("gui.archweaver.fakeplayer.effects.ambient"));
        }
        graphics.setTooltipForNextFrame(font, tooltip, Optional.empty(), mouseX, mouseY);
    }

    private Component effectMaskedDurationComponent(MobEffectInstance effect) {
        return Component.literal(formatEffectDuration(effect))
            .withStyle(style -> style.withFont(EFFECT_DURATION_FONT));
    }

    private Component effectRealDurationComponent(MobEffectInstance effect) {
        return Component.literal(formatRealEffectDuration(effect));
    }

    private String formatEffectDuration(MobEffectInstance effect) {
        if (effect.isInfiniteDuration()) {
            return "∞";
        }
        int totalSeconds = Math.max(0, effect.getDuration()) / 20;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return minutes >= 100
            ? String.format(java.util.Locale.ROOT, "**:%02d", seconds)
            : String.format(java.util.Locale.ROOT, "%02d:%02d", minutes, seconds);
    }

    private String formatRealEffectDuration(MobEffectInstance effect) {
        if (effect.isInfiniteDuration()) {
            return "∞";
        }
        int totalSeconds = Math.max(0, effect.getDuration()) / 20;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format(java.util.Locale.ROOT, "%02d:%02d", minutes, seconds);
    }

    private int poseIndex() {
        if (menu.mannequin() != null) for (int i = 0; i < POSES.length; i++) if (menu.mannequin().getPose() == POSES[i]) return i;
        return 0;
    }

    private void sendLimb(int limb) {
        PlatformNetworking.sendToServer(new MannequinSettingsPayload(menu.mannequinId(), MannequinSettingsPayload.Action.LIMB_ANGLE,
            limb, true, 0, 0, angles[limb][0], angles[limb][1], angles[limb][2]));
    }

    private void send(MannequinSettingsPayload.Action action, int value, boolean enabled) {
        PlatformNetworking.sendToServer(new MannequinSettingsPayload(menu.mannequinId(), action, value, enabled));
    }

    @Override protected void containerTick() {
        super.containerTick();
        menu.updatePreviewSettings();
        syncLookInputs();
        syncIdentityInputs();
        layoutInfoPanel();
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (presetButton != null && posePanel.isOpen() && presetButton.popupMouseClicked(event)) return true;
        if (event.button() == 0 && isEffectsPanelOpen()) {
            int effectCount = visibleEffects().size();
            int scrollbarLeft = effectsScrollbarLeft();
            int scrollbarTop = effectsPanel.getY() + EFFECT_GRID_TOP;
            if (event.x() >= scrollbarLeft && event.x() < scrollbarLeft + EFFECT_SCROLLBAR_WIDTH
                && event.y() >= scrollbarTop && event.y() < scrollbarTop + EFFECT_GRID_HEIGHT) {
                // 内容未超出时仍显示完整滑块，点击只消耗事件，不开始拖动。
                if (maxEffectsScrollRow(effectCount) == 0) {
                    return true;
                }
                int thumbTop = effectsScrollbarThumbTop(effectCount);
                int thumbHeight = effectsScrollbarThumbHeight(effectCount);
                // 点击滑块保留抓取位置，点击轨道则将滑块中心移到鼠标位置。
                effectsScrollbarGrabOffset = event.y() >= thumbTop && event.y() < thumbTop + thumbHeight
                    ? event.y() - thumbTop : thumbHeight / 2.0;
                draggingEffectsScrollbar = true;
                dragEffectsScrollbar(event.y(), effectCount);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isEffectsPanelOpen()
            && mouseX >= effectsPanel.getX() && mouseX < effectsPanel.getRight()
            && mouseY >= effectsPanel.getY() && mouseY < effectsPanel.getBottom()) {
            int effectCount = visibleEffects().size();
            int rows = (int) Math.ceil(Math.abs(verticalAmount));
            int direction = verticalAmount > 0 ? -1 : 1;
            effectsScrollRow = Math.clamp(effectsScrollRow + direction * rows, 0, maxEffectsScrollRow(effectCount));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (event.button() == 0 && draggingEffectsScrollbar) {
            int effectCount = visibleEffects().size();
            if (isEffectsPanelOpen()) {
                dragEffectsScrollbar(event.y(), effectCount);
            } else {
                draggingEffectsScrollbar = false;
            }
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && draggingEffectsScrollbar) {
            draggingEffectsScrollbar = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (!ClientScreenNavigation.extractBackground(this, graphics, partialTick)) super.extractBackground(graphics, mouseX, mouseY, partialTick);
        topBar().draw(graphics);
        drawInventoryBackground(graphics);
        // 空槽里的“主”字由 MannequinInventoryMenu 的 getNoItemIcon 提供，原版会按空槽图标绘制。
        graphics.text(font, playerInventoryTitle, leftPos + 8,
            topPos + MannequinInventoryMenu.VIEWER_SECTION_TOP + 3, 0xFF404040, false);
        // 从下到上绘制：展开的面板要盖住其上方标签的下沿。
        skinPanel.drawBackground(graphics);
        effectsPanel.drawBackground(graphics);
        settingsPanel.drawBackground(graphics);
        infoPanel.drawBackground(graphics);
        legsPanel.drawBackground(graphics);
        armsPanel.drawBackground(graphics);
        posePanel.drawBackground(graphics);
        lookPanel.drawBackground(graphics);
        drawMannequinPreview(graphics);
    }

    /** 拼接原版容器边框与完整的玩家物品栏区域，上半部分只绘制预览和装备槽。 */
    private void drawInventoryBackground(GuiGraphicsExtractor graphics) {
        int sectionTop = MannequinInventoryMenu.VIEWER_SECTION_TOP;
        graphics.fill(leftPos + 7, topPos + 7, leftPos + imageWidth - 7, topPos + sectionTop, 0xFFC6C6C6);
        graphics.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_BACKGROUND,
            leftPos, topPos, 0.0F, 0.0F, imageWidth, 7, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_BACKGROUND,
            leftPos, topPos + 7, 0.0F, 7.0F, 7, sectionTop - 7, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_BACKGROUND,
            leftPos + imageWidth - 7, topPos + 7, 169.0F, 7.0F, 7, sectionTop - 7, 256, 256);
        // 使用与玩家背包相同的 96 像素区域，标题不再与脚部装备槽重叠。
        graphics.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_BACKGROUND,
            leftPos, topPos + sectionTop, 0.0F, 126.0F, imageWidth, 96, 256, 256);
        PixelGui.drawInventorySlotBackground(graphics, leftPos + PREVIEW_LEFT, topPos + PREVIEW_TOP,
            PREVIEW_RIGHT - PREVIEW_LEFT, PREVIEW_BOTTOM - PREVIEW_TOP, 0xFF000000);
        for (int index = 0; index < 6; index++) {
            var slot = menu.getSlot(index);
            graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND,
                leftPos + slot.x - 1, topPos + slot.y - 1, 7.0F, 7.0F, 18, 18, 256, 256);
        }
    }

    /**
     * 挂在预览区域正下方的细滑条，拖动它让画面里的模型绕竖轴转一整圈，方便查看模型的各个面。
     *
     * <p>只改预览这一处的渲染朝向，不写回实体，也不发任何包：世界里的玩偶朝向保持原样。
     */
    private void addPreviewRotationSlider() {
        // 预览下沿（PREVIEW_BOTTOM）与物品栏标题（VIEWER_SECTION_TOP）之间空出的那一条，滑条在里面垂直居中。
        int top = (PREVIEW_BOTTOM + MannequinInventoryMenu.VIEWER_SECTION_TOP - PREVIEW_ROTATION_SLIDER_HEIGHT) / 2;
        addRenderableWidget(new IntegerSliderButton(
            leftPos + PREVIEW_LEFT, topPos + top,
            PREVIEW_RIGHT - PREVIEW_LEFT, PREVIEW_ROTATION_SLIDER_HEIGHT,
            PREVIEW_ROTATION_MIN, PREVIEW_ROTATION_MAX, previewRotation,
            value -> Component.empty(),
            value -> previewRotation = value));
    }

    /** 直接提取实体渲染状态，保留姿势、四肢角度、头部俯仰和身体朝向。 */
    private void drawMannequinPreview(GuiGraphicsExtractor graphics) {
        var mannequin = menu.mannequin();
        if (mannequin == null) return;
        var renderer = minecraft.getEntityRenderDispatcher().getRenderer(mannequin);
        var state = renderer.createRenderState(mannequin, 1.0F);
        state.shadowPieces.clear();
        state.outlineColor = 0;
        state.nameTag = null;
        Vector3f translation = new Vector3f(0.0F, state.boundingBoxHeight / 2.0F + 0.0625F, 0.0F);
        // 先绕模型自身竖轴转 previewRotation，再用原有的 Z 翻转把模型摆正，得到纯画面上的转台效果。
        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI)
            .rotateY((float) Math.toRadians(previewRotation));
        graphics.entity(state, PREVIEW_SCALE, translation, rotation, null,
            leftPos + PREVIEW_LEFT + 1, topPos + PREVIEW_TOP + 1,
            leftPos + PREVIEW_RIGHT - 1, topPos + PREVIEW_BOTTOM - 1);
    }

    @Override protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (posePanel.isOpen()) presetButton.extractPopup(graphics, mouseX, mouseY, leftPos, topPos);
        drawEffectsTooltip(graphics, mouseX, mouseY);
    }
    @Override public void onClose() { ClientScreenNavigation.back(this); }
}
