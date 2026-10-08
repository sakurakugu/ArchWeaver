package com.sakurakugu.archweaver.client;

import com.sakurakugu.archweaver.client.ui.IntegerSliderButton;
import com.sakurakugu.archweaver.client.ui.OverlayPanelManager;
import com.sakurakugu.archweaver.client.ui.SolidButton;
import com.sakurakugu.archweaver.client.ui.SolidDropdownButton;
import com.sakurakugu.archweaver.client.ui.ToggleSwitchButton;
import com.sakurakugu.archweaver.menu.MannequinInventoryMenu;
import com.sakurakugu.archweaver.mixin.MannequinInvoker;
import com.sakurakugu.archweaver.network.AvatarSkinPartPayload;
import com.sakurakugu.archweaver.network.MannequinSettingsPayload;
import com.sakurakugu.archweaver.network.TargetTypePayload;
import com.sakurakugu.archweaver.persistence.MannequinSavedData;
import com.sakurakugu.archweaver.platform.PlatformNetworking;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** 玩偶采用原版玩家背包外观，左侧仅提供玩偶设置、姿势和皮肤部件。 */
public final class MannequinInventoryScreen extends AbstractContainerScreen<MannequinInventoryMenu> {
    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/inventory.png");
    private static final Pose[] POSES = {Pose.STANDING, Pose.CROUCHING, Pose.SWIMMING, Pose.FALL_FLYING, Pose.SLEEPING};
    private static final String[] POSE_NAMES = {"站立", "潜行", "游泳", "鞘翅飞行", "睡眠"};
    private OverlayPanelManager panels;
    private OverlayPanelManager.Panel settingsPanel, posePanel, skinPanel;
    private SolidDropdownButton<Pose> presetButton;
    private final int[][] angles = new int[4][3];
    private final IntegerSliderButton[][] angleSliders = new IntegerSliderButton[4][3];

    public MannequinInventoryScreen(MannequinInventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
    }

    @Override protected void init() {
        String open = panels == null ? null : panels.openPanelId();
        super.init();
        // 窄窗口至少为左侧设置预留空间，常规窗口保持原版背包的居中位置。
        leftPos = Math.max(leftPos, Math.min(136, width - imageWidth - 4));
        panels = new OverlayPanelManager(font);
        addRenderableWidget(new ToggleSwitchButton(leftPos + 8, topPos - 18, 160, 18,
            Component.literal("玩偶模式"), () -> true,
            button -> PlatformNetworking.sendToServer(new TargetTypePayload(menu.containerId, menu.mannequinId(), false))));
        settingsPanel = panel("settings", 8, 100, 70, "玩偶设置", Items.ARMOR_STAND);
        var immovable = addRenderableWidget(new ToggleSwitchButton(settingsPanel.getX() + 6, settingsPanel.getY() + 25,
            settingsPanel.contentWidth() - 12, 18,
            Component.literal("不可移动"), () -> menu.mannequin() != null && ((MannequinInvoker) menu.mannequin()).archweaver$getImmovable(),
            button -> { if (menu.mannequin() != null) send(MannequinSettingsPayload.Action.IMMOVABLE, 0,
                !((MannequinInvoker) menu.mannequin()).archweaver$getImmovable()); }));
        var biological = addRenderableWidget(new ToggleSwitchButton(settingsPanel.getX() + 6, settingsPanel.getY() + 45,
            settingsPanel.contentWidth() - 12, 18,
            Component.literal("生物行为"), () -> menu.mannequin() != null && !menu.mannequin().isNoGravity(),
            button -> { if (menu.mannequin() != null) send(MannequinSettingsPayload.Action.BIOLOGICAL_BEHAVIOR, 0, menu.mannequin().isNoGravity()); }));
        settingsPanel.bindContents(immovable, biological);
        int poseWidth = Math.min(196, Math.max(132, leftPos - 4));
        int sliderWidth = (poseWidth - 16) / 3;
        posePanel = panel("pose", 34, poseWidth, 200, "姿势与四肢角度", Items.STICK);
        List<AbstractWidget> poseControls = new ArrayList<>();
        presetButton = addRenderableWidget(new SolidDropdownButton<>(posePanel.getX() + 6, posePanel.getY() + 25, poseWidth - 12, 18,
            List.of(POSES), POSES[poseIndex()], pose -> Component.literal("姿势：" + POSE_NAMES[List.of(POSES).indexOf(pose)]),
            pose -> send(MannequinSettingsPayload.Action.POSE, List.of(POSES).indexOf(pose), true)));
        poseControls.add(presetButton);
        poseControls.add(addRenderableWidget(new SolidButton(posePanel.getX() + 6, posePanel.getY() + 177, poseWidth - 12, 18,
            Component.literal("重置角度"), button -> {
                for (int limb = 0; limb < 4; limb++) {
                    java.util.Arrays.fill(angles[limb], 0);
                    for (IntegerSliderButton slider : angleSliders[limb]) slider.setRange(-180, 180, 0);
                    sendLimb(limb);
                }
            })));
        String[] names = {"左臂", "右臂", "左腿", "右腿"};
        for (int limb = 0; limb < 4; limb++) {
            MannequinSavedData.Angles rotation = ClientMannequinAngles.get(menu.mannequinId(), limb);
            angles[limb] = new int[] {Math.round(rotation.x()), Math.round(rotation.y()), Math.round(rotation.z())};
            for (int axis = 0; axis < 3; axis++) {
                int l = limb, a = axis;
                angleSliders[limb][axis] = addRenderableWidget(new IntegerSliderButton(posePanel.getX() + 6 + axis * (sliderWidth + 2),
                    posePanel.getY() + 60 + limb * 30, sliderWidth, 18, -180, 180, angles[limb][axis],
                    value -> Component.literal("XYZ".charAt(a) + ": " + value + "°"), value -> { angles[l][a] = value; sendLimb(l); }));
                poseControls.add(angleSliders[limb][axis]);
            }
        }
        posePanel.setContentRenderer((graphics, x, y) -> {
            presetButton.setSelected(POSES[poseIndex()]);
            for (int i = 0; i < 4; i++) graphics.text(font, Component.literal(names[i]), x + 6, y + 49 + i * 30, 0xFF404040, false);
        });
        posePanel.bindContents(poseControls.toArray(AbstractWidget[]::new));
        skinPanel = panel("skin", 60, 100, 174, "皮肤部件", Items.LEATHER_CHESTPLATE);
        List<AbstractWidget> skinControls = new ArrayList<>();
        PlayerModelPart[] parts = PlayerModelPart.values();
        for (int i = 0; i < parts.length; i++) {
            PlayerModelPart part = parts[i];
            skinControls.add(addRenderableWidget(new ToggleSwitchButton(skinPanel.getX() + 6, skinPanel.getY() + 25 + i * 20,
                skinPanel.contentWidth() - 12, 18,
                part.getName(), () -> menu.mannequin() != null && menu.mannequin().isModelPartShown(part),
                button -> { if (menu.mannequin() != null) PlatformNetworking.sendToServer(new AvatarSkinPartPayload(menu.containerId,
                    menu.mannequinId(), part, !menu.mannequin().isModelPartShown(part))); })));
        }
        skinPanel.bindContents(skinControls.toArray(AbstractWidget[]::new));
        panels.restoreOpenPanel(open);
    }

    private OverlayPanelManager.Panel panel(String id, int top, int width, int height, String title, net.minecraft.world.item.Item icon) {
        int anchorY = Math.max(4, Math.min(topPos, this.height - 238));
        var panel = panels.addLeftPanel(id, leftPos - width, anchorY,
            new OverlayPanelManager.Layout(top, width, height, 21, 24), Component.literal(title));
        addRenderableWidget(panel);
        addRenderableWidget(panel.createTab(new ItemStack(icon)));
        return panel;
    }

    private int poseIndex() {
        if (menu.mannequin() != null) for (int i = 0; i < POSES.length; i++) if (menu.mannequin().getPose() == POSES[i]) return i;
        return 0;
    }

    private void sendLimb(int limb) {
        PlatformNetworking.sendToServer(new MannequinSettingsPayload(menu.mannequinId(), MannequinSettingsPayload.Action.LIMB_ANGLE,
            limb, true, angles[limb][0], angles[limb][1], angles[limb][2]));
    }

    private void send(MannequinSettingsPayload.Action action, int value, boolean enabled) {
        PlatformNetworking.sendToServer(new MannequinSettingsPayload(menu.mannequinId(), action, value, enabled));
    }

    @Override protected void containerTick() {
        super.containerTick();
        menu.updatePreviewSettings();
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (presetButton != null && posePanel.isOpen() && presetButton.popupMouseClicked(event)) return true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (!ClientScreenNavigation.extractBackground(this, graphics, partialTick)) super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 0.0F, 0.0F, 176, 166, 256, 256);
        // 玩偶没有合成能力，清除原版合成区并在其中显示名称和主手装备槽。
        graphics.fill(leftPos + 96, topPos + 7, leftPos + 172, topPos + 78, 0xFFC6C6C6);
        graphics.text(font, Component.literal(menu.mannequinName()), leftPos + 97, topPos + 8, 0xFF404040, false);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos + 97, topPos + 28, 7.0F, 7.0F, 18, 18, 256, 256);
        graphics.text(font, Component.literal("主手"), leftPos + 120, topPos + 33, 0xFF404040, false);
        graphics.text(font, playerInventoryTitle, leftPos + 8, topPos + 74, 0xFF404040, false);
        skinPanel.drawBackground(graphics);
        posePanel.drawBackground(graphics);
        settingsPanel.drawBackground(graphics);
        if (menu.mannequin() != null) InventoryScreen.extractEntityInInventoryFollowsMouse(graphics,
            leftPos + 26, topPos + 8, leftPos + 75, topPos + 78, 30, 0.0625F, mouseX, mouseY, menu.mannequin());
    }

    @Override protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (posePanel.isOpen()) presetButton.extractPopup(graphics, mouseX, mouseY, leftPos, topPos);
    }
    @Override public void onClose() { ClientScreenNavigation.back(this); }
}
