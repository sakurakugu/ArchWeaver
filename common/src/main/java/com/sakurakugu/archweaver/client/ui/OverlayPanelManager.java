package com.sakurakugu.archweaver.client.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** 按注册顺序管理可展开的重叠面板；面板本身负责外框、标签和内容显隐。 */
public final class OverlayPanelManager {
    private final Font font; // 绘制面板标题所用的字体。
    private final List<Panel> panels = new ArrayList<>(); // 按注册顺序保存的面板列表。

    public OverlayPanelManager(Font font) {
        this.font = font;
    }

    /** 相对于界面锚点的面板外框布局。 */
    public record Layout(int top, int width, int height, int tabWidth, int tabHeight) {
    }

    public Panel addRightPanel(String id, int anchorX, int anchorY, Layout layout, Component title) {
        return addPanel(id, anchorX, anchorY + layout.top(), layout, title, Side.RIGHT);
    }

    public Panel addLeftPanel(String id, int anchorX, int anchorY, Layout layout, Component title) {
        return addPanel(id, anchorX, anchorY + layout.top(), layout, title, Side.LEFT);
    }

    private Panel addPanel(String id, int x, int y, Layout layout, Component title, Side side) {
        Panel panel = new Panel(id, x, y, layout, title, side);
        panels.add(panel);
        return panel;
    }

    /** 返回当前展开面板的稳定 ID，未展开时返回 null。 */
    public String openPanelId() {
        for (Panel panel : panels) {
            if (panel.open) {
                return panel.id;
            }
        }
        return null;
    }

    /** 根据稳定 ID 恢复重新初始化前展开的面板。 */
    public void restoreOpenPanel(String id) {
        if (id == null) {
            return;
        }
        for (Panel panel : panels) {
            if (panel.id.equals(id)) {
                panel.setOpen(true);
                return;
            }
        }
    }

    private void refresh() {
        boolean blocked = false;
        for (Panel panel : panels) {
            panel.applyState(blocked);
            if (panel.open) {
                blocked = true;
            }
        }
    }

    private enum Side {
        LEFT, // 面板从锚点左侧展开。
        RIGHT // 面板从锚点右侧展开。
    }

    @FunctionalInterface
    public interface ContentRenderer {
        void render(GuiGraphicsExtractor graphics, int x, int y);
    }

    /** 可加入 Screen 的面板覆盖层；基础背景仍可通过 drawBackground 按所需层级绘制。 */
    public final class Panel extends Button {
        private final List<AbstractWidget> contents = new ArrayList<>(); // 面板展开时显示并跟随状态启停的内部控件。
        private final String id; // 面板的稳定标识，用于重新初始化后恢复展开状态。
        private Layout layout; // 面板外框布局，含相对锚点的偏移与尺寸；高度可随内容增减调整。
        private final Component title; // 面板展开时显示的标题文本。
        private final Side side; // 面板相对锚点的展开方向，决定标签位置与背景画法。
        private AbstractWidget tab; // 控制面板展开与收起的标签按钮。
        private ContentRenderer contentRenderer = (graphics, x, y) -> { }; // 面板内容的自定义绘制回调，默认为空实现。
        private boolean open; // 面板当前是否展开。

        private Panel(
            String id, int x, int y, Layout layout, Component title, Side side
        ) {
            super(x, y, layout.width(), layout.height(), Component.empty(), ignored -> { }, DEFAULT_NARRATION);
            this.id = id;
            this.layout = layout;
            this.title = title;
            this.side = side;
            active = false;
            visible = false;
        }

        public IconTabButton createTab(ItemStack icon) {
            return createTab(icon, side == Side.LEFT ? 2 : 0);
        }

        public IconTabButton createTab(ItemStack icon, int iconOffsetX) {
            return createTab(icon, iconOffsetX, 0);
        }

        public IconTabButton createTab(ItemStack icon, int iconOffsetX, int iconOffsetY) {
            IconTabButton button = new IconTabButton(
                tabX(), getY(), layout.tabWidth(), layout.tabHeight(),
                icon, iconOffsetX, iconOffsetY, title, ignored -> toggle());
            bindTab(button);
            return button;
        }

        public IconTabButton createTab(Identifier icon) {
            return createTab(icon, title);
        }

        public IconTabButton createTab(Identifier icon, Component tooltip) {
            IconTabButton button = new IconTabButton(
                tabX(), getY(), layout.tabWidth(), layout.tabHeight(), icon, tooltip, ignored -> toggle());
            bindTab(button, tooltip);
            return button;
        }

        private int tabX() {
            return side == Side.RIGHT ? getX() : getX() + layout.width() - layout.tabWidth();
        }

        public int contentWidth() {
            return layout.width();
        }

        public int contentHeight() {
            return layout.height();
        }

        /** 运行时调整面板高度，供内容行数随状态增减的面板贴合实际内容；顶边保持不变。 */
        public void setContentHeight(int height) {
            layout = new Layout(layout.top(), layout.width(), height, layout.tabWidth(), layout.tabHeight());
            setHeight(height);
        }

        private void bindTab(AbstractWidget tab) {
            bindTab(tab, title);
        }

        private void bindTab(AbstractWidget tab, Component tooltip) {
            this.tab = tab;
            tab.setTooltip(Tooltip.create(tooltip));
            refresh();
        }

        public void bindContents(AbstractWidget... contents) {
            this.contents.clear();
            this.contents.addAll(Arrays.asList(contents));
            refresh();
        }

        public void setContentRenderer(ContentRenderer contentRenderer) {
            this.contentRenderer = contentRenderer;
        }

        public boolean isOpen() {
            return open;
        }

        public void toggle() {
            setOpen(!open);
        }

        public void setOpen(boolean open) {
            if (open) {
                // 面板互斥：打开当前面板时立即收起其他面板。
                for (Panel panel : panels) {
                    if (panel != this) {
                        panel.open = false;
                    }
                }
            }
            this.open = open;
            refresh();
        }

        /** 绘制当前展开或收起状态的外框，供 Screen 控制基础层的绘制顺序。 */
        public void drawBackground(GuiGraphicsExtractor graphics) {
            int width = open ? layout.width() : layout.tabWidth();
            int height = open ? layout.height() : layout.tabHeight();
            int x = side == Side.RIGHT || open ? getX() : tabX();
            if (side == Side.RIGHT) {
                PixelGui.drawRightTabBackground(graphics, x, getY(), width, height);
            } else {
                PixelGui.drawLeftTabBackground(graphics, x, getY(), width, height);
            }
            if (open) {
                // 右侧面板的标签在左边，文字从标签右侧起；左侧面板的标签在右边，文字右侧要避开标签。
                int titleX = side == Side.RIGHT ? getX() + 22 : getX() + 7;
                int titleTop = side == Side.RIGHT ? 8 : 7;
                int titleRight = side == Side.RIGHT
                    ? getX() + layout.width() - 4
                    : getX() + layout.width() - layout.tabWidth() - 2;
                // 标题放不下时在可用宽度内滚动，避免文字溢出面板边框或压到标签上。
                if (font.width(title) <= titleRight - titleX) {
                    graphics.text(font, title, titleX, getY() + titleTop, 0xFF404040, false);
                } else {
                    PixelGui.drawScrollingText(graphics, font, title, titleX, titleRight,
                        getY() + titleTop - 4, 16, 0xFF404040);
                }
                contentRenderer.render(graphics, getX(), getY());
            }
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            // 面板背景由所属 Screen 按遮挡顺序统一绘制。
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            // 覆盖层只负责绘制，不能拦截内部控件。
            return false;
        }

        private void applyState(boolean blocked) {
            if (tab != null) {
                // 展开的上方面板会遮住后续标签，隐藏的同时必须禁用点击。
                tab.visible = !blocked;
                tab.active = !blocked;
            }
            boolean contentEnabled = open && !blocked;
            visible = contentEnabled;
            for (AbstractWidget content : contents) {
                // 不可见控件不会采集 tooltip，因此遮挡时必须同时修改 visible。
                content.visible = contentEnabled;
                content.active = contentEnabled;
            }
        }
    }
}
