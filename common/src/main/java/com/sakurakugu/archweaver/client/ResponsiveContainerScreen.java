package com.sakurakugu.archweaver.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** 为固定设计尺寸的容器页面提供按逻辑屏幕缩小的布局基准。 */
abstract class ResponsiveContainerScreen<T extends AbstractContainerMenu>
    extends AbstractContainerScreen<T> {
    private final int designWidth;
    private final int designHeight;
    private float layoutScale = 1.0F;
    private int responsiveWidth;
    private int responsiveHeight;

    protected ResponsiveContainerScreen(
        T menu, Inventory inventory, Component title, int designWidth, int designHeight
    ) {
        super(menu, inventory, title, designWidth, designHeight);
        this.designWidth = designWidth;
        this.designHeight = designHeight;
    }

    /** 在原版计算 leftPos/topPos 前把容器尺寸压缩到当前逻辑屏幕能容纳的范围。 */
    @Override
    protected void init() {
        float availableWidth = Math.max(1.0F, width - 12.0F);
        float availableHeight = Math.max(1.0F, height - 12.0F);
        layoutScale = Math.min(1.0F, Math.min(
            availableWidth / designWidth, availableHeight / designHeight));
        responsiveWidth = Math.max(1, Math.round(designWidth * layoutScale));
        responsiveHeight = Math.max(1, Math.round(designHeight * layoutScale));
        super.init();
        // AbstractContainerScreen 的 imageWidth/imageHeight 是 final，不能直接改写。
        // 这些页面没有原版槽位背景，使用自己的面板尺寸和位置即可。
        leftPos = (width - responsiveWidth) / 2;
        topPos = (height - responsiveHeight) / 2;
    }

    /** 将设计稿中的坐标按当前布局比例转换为屏幕逻辑像素。 */
    protected final int s(int value) {
        return Math.round(value * layoutScale);
    }

    /** 将设计稿中的尺寸缩放，并保证控件至少有一个逻辑像素。 */
    protected final int size(int value) {
        return Math.max(1, s(value));
    }

    protected final int responsiveWidth() {
        return responsiveWidth;
    }

    protected final int responsiveHeight() {
        return responsiveHeight;
    }

    protected final int designWidth() {
        return designWidth;
    }

    protected final int designHeight() {
        return designHeight;
    }
}
