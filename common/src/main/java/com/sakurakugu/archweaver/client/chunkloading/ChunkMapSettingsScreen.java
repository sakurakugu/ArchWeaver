package com.sakurakugu.archweaver.client.chunkloading;

import com.sakurakugu.archweaver.client.ClientScreenNavigation;
import com.sakurakugu.archweaver.client.ui.PixelGlyph;
import com.sakurakugu.archweaver.client.ui.SolidButton;
import com.sakurakugu.archweaver.client.ui.SolidSliderButton;
import com.sakurakugu.archweaver.client.ui.TitlePanel;
import com.sakurakugu.archweaver.network.ChunkMapSnapshotPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** 区块地图的显示设置页。地图作为背景页面保留，设置本身拥有独立的页面节点。 */
public final class ChunkMapSettingsScreen extends Screen {
    private static final int PANEL_WIDTH = 360; // 设置面板的理想宽度，窗口过窄时会被压缩。
    private static final int PANEL_HEIGHT = 82; // 设置面板的高度。
    private ChunkMapSnapshotPayload snapshot; // 最近一次从服务端同步来的状态快照。
    private float layoutScale = 1.0F; // 当前面板相对设计尺寸的缩放比例。
    private int layoutWidth = PANEL_WIDTH; // 当前面板实际宽度。
    private int layoutHeight = PANEL_HEIGHT; // 当前面板实际高度。

    public ChunkMapSettingsScreen(ChunkMapSnapshotPayload snapshot) {
        super(Component.translatable("gui.archweaver.chunkloader.map_settings_title"));
        this.snapshot = snapshot;
    }

    public void update(ChunkMapSnapshotPayload value) {
        snapshot = value;
    }

    @Override
    protected void init() {
        updateLayout();
        int panelWidth = layoutWidth;
        int left = (width - panelWidth) / 2;
        int top = panelTop();
        int halfWidth = panelWidth / 2;
        addRenderableWidget(new MarkerNameScaleSlider(left + halfWidth + s(8), top + s(44),
            size(halfWidth - s(24)), size(20)));
        TitlePanel titlePanel = new TitlePanel(left, top, panelWidth, layoutHeight,
            Component.translatable("gui.archweaver.chunkloader.map_settings_title"));
        addRenderableWidget(new SolidButton(titlePanel.leftButtonX(), titlePanel.buttonY(size(18)), size(18), size(18),
            PixelGlyph.BACK, Component.translatable("gui.back"), button -> onClose()));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (!ClientScreenNavigation.extractBackground(this, graphics, partialTick)) {
            graphics.fill(0, 0, width, height, 0xFF22282C);
        }
        updateLayout();
        int panelWidth = layoutWidth;
        int left = (width - panelWidth) / 2;
        int top = panelTop();
        int halfWidth = panelWidth / 2;
        new TitlePanel(left, top, panelWidth, layoutHeight,
            Component.translatable("gui.archweaver.chunkloader.map_settings_title")).draw(graphics, font);
        graphics.centeredText(font, Component.translatable("gui.archweaver.chunkloader.marker_name_preview"),
            left + halfWidth / 2, top + s(34), 0xFFB8C1BD);
        String name = minecraft.player == null ? "Player" : minecraft.player.getGameProfile().name();
        drawScaledPreview(graphics, Component.literal(name), left + halfWidth / 2, top + s(52));
    }

    private void updateLayout() {
        float availableWidth = Math.max(1.0F, width - 24.0F);
        float availableHeight = Math.max(1.0F, height - 12.0F);
        layoutScale = Math.min(1.0F, Math.min(
            availableWidth / PANEL_WIDTH, availableHeight / PANEL_HEIGHT));
        layoutWidth = Math.max(1, Math.round(PANEL_WIDTH * layoutScale));
        layoutHeight = Math.max(1, Math.round(PANEL_HEIGHT * layoutScale));
    }

    private int panelTop() {
        return Math.max(0, (height - layoutHeight) / 2);
    }

    private int s(int value) {
        return Math.round(value * layoutScale);
    }

    private int size(int value) {
        return Math.max(1, value);
    }

    private void drawScaledPreview(GuiGraphicsExtractor graphics, Component text, int centerX, int y) {
        float scale = (float) ChunkMapClientConfig.markerNameScale();
        int scaledWidth = Mth.ceil(font.width(text) * scale);
        int x = centerX - scaledWidth / 2;
        graphics.fill(x - 2, y - 1, x + scaledWidth + 2, y + Mth.ceil(9.0F * scale), 0x99000000);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        graphics.text(font, text, 0, 0, 0xFFFFFFFF, false);
        graphics.pose().popMatrix();
    }

    @Override
    public void onClose() {
        ClientScreenNavigation.back(this);
    }

    @Override
    public void removed() {
        ChunkMapClientConfig.save();
        super.removed();
    }

    private final class MarkerNameScaleSlider extends SolidSliderButton {
        private MarkerNameScaleSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(),
                (ChunkMapClientConfig.markerNameScale() - 0.5D) / 1.5D);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("gui.archweaver.chunkloader.marker_name_scale",
                Math.round((0.5D + value * 1.5D) * 100.0D)));
        }

        @Override
        protected void applyValue() {
            ChunkMapClientConfig.setMarkerNameScale(0.5D + value * 1.5D);
        }
    }
}
