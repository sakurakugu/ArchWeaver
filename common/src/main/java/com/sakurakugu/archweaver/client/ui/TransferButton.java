package com.sakurakugu.archweaver.client.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** 根据 Shift 和 Ctrl 状态切换转移模式的 12 像素箭头按钮。 */
public final class TransferButton extends Button {
    public static final int SIZE = 12; // 按钮的边长（像素），宽度与高度相同。
    private static final int ARROW_COLOR = 0xFFE0E0E0; // 箭头主色，偏灰的浅白，两个方向不再靠红绿区分。
    private static final int ARROW_SHADOW = 0xFFC0C0C0; // 箭头暗部，只比主色深一档，做很轻的立体感。
    private static final int ARROW_GAP = 0xFF8B8B8B; // 箭头缺口，与按钮内层底色一致，看上去像被切断。

    private final Direction direction; // 转移方向，决定箭头朝向与提示文案。
    private final OnTransfer onTransfer; // 按下按钮时触发的转移回调。
    private boolean showingAll; // 当前提示是否处于「全部转移」模式，用于避免重复刷新提示。
    private boolean showingHotbar; // 当前提示是否处于「包含快捷栏」模式，用于避免重复刷新提示。

    public TransferButton(int x, int y, Direction direction, OnTransfer onTransfer) {
        super(x, y, SIZE, SIZE, Component.empty(), button -> {}, DEFAULT_NARRATION);
        this.direction = direction;
        this.onTransfer = onTransfer;
        updateTooltip(false, false);
    }

    /** 支持快速转移的原版容器界面：物品栏标题位置与箱子一致，按钮才能用同一套偏移。 */
    public static boolean supports(AbstractContainerScreen<?> screen) {
        AbstractContainerMenu menu = screen.getMenu();
        return menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu;
    }

    /** 在任意容器界面添加通用快速转移按钮。 */
    public static List<TransferButton> forContainer(AbstractContainerScreen<?> screen) {
        Player player = Minecraft.getInstance().player;
        if (player == null || !supports(screen)
            || screen.getMenu().slots.stream().noneMatch(slot -> slot.container == player.getInventory())
            || screen.getMenu().slots.stream().noneMatch(slot -> slot.container != player.getInventory())) {
            return List.of();
        }
        // 原版容器的物品栏标题位于底部区域，按钮与假人界面一样放在标题右侧。
        int x = screen.getLeftPos() + 144;
        int y = screen.getTopPos() + screen.getImageHeight() - 96;
        return List.of(
            new TransferButton(x, y, Direction.TO_CONTAINER,
                (all, hotbar) -> transfer(screen, true, all, hotbar)),
            new TransferButton(x + SIZE, y, Direction.TO_INVENTORY,
                (all, hotbar) -> transfer(screen, false, all, hotbar))
        );
    }

    private static void transfer(AbstractContainerScreen<?> screen, boolean toContainer,
        boolean all, boolean includeHotbar) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode == null || minecraft.player == null) {
            return;
        }
        AbstractContainerMenu menu = screen.getMenu();
        Player player = minecraft.player;
        List<Slot> sources = new ArrayList<>();
        List<Slot> destinations = new ArrayList<>();
        for (Slot slot : menu.slots) {
            boolean playerSlot = slot.container == player.getInventory();
            if (playerSlot && !includeHotbar && slot.getContainerSlot() < 9) {
                continue;
            }
            (toContainer == playerSlot ? sources : destinations).add(slot);
        }
        for (Slot source : sources) {
            if (source.getItem().isEmpty()) {
                continue;
            }
            if (!all && destinations.stream().noneMatch(slot ->
                !slot.getItem().isEmpty() && ItemStack.isSameItemSameComponents(source.getItem(), slot.getItem()))) {
                continue;
            }
            minecraft.gameMode.handleContainerInput(menu.containerId, source.index, 0, ContainerInput.QUICK_MOVE, player);
        }
    }

    @Override
    public void onPress(InputWithModifiers input) {
        onTransfer.run(input.hasShiftDown(), input.hasControlDown());
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean transferAll = minecraft.hasShiftDown();
        boolean includeHotbar = minecraft.hasControlDown();
        if (showingAll != transferAll || showingHotbar != includeHotbar) {
            updateTooltip(transferAll, includeHotbar);
        }
        drawBackground(graphics, isMouseOver(mouseX, mouseY));
        drawArrow(graphics, transferAll);
    }

    private void updateTooltip(boolean transferAll, boolean includeHotbar) {
        showingAll = transferAll;
        showingHotbar = includeHotbar;
        Component message = Component.translatable("gui.archweaver.fakeplayer.transfer_" + direction.translationPart
            + (transferAll ? "_all" : "_matching"));
        if (includeHotbar) {
            message = message.copy().append(Component.translatable("gui.archweaver.fakeplayer.transfer_hotbar_suffix"));
        }
        setMessage(message);
        Component tooltip = getMessage().copy();
        if (!includeHotbar) {
            tooltip = tooltip.copy().append(Component.literal("\n"))
                .append(Component.translatable("gui.archweaver.fakeplayer.transfer_hotbar_hint").withColor(0x555555));
        }
        if (!transferAll) {
            tooltip = tooltip.copy().append(Component.literal("\n"))
                .append(Component.translatable("gui.archweaver.fakeplayer.transfer_all_hint").withColor(0x555555));
        }
        setTooltip(Tooltip.create(tooltip));
    }

    private void drawBackground(GuiGraphicsExtractor graphics, boolean hovered) {
        int x = getX();
        int y = getY();
        int right = x + getWidth();
        int bottom = y + getHeight();
        if (hovered) {
            graphics.fill(x, y, right, bottom, 0xFFFFFFFF);
        }
        graphics.fill(x + 1, y + 1, right - 1, bottom - 1, 0xFFAAAAAA);
        graphics.fill(x + 2, y + 2, right - 1, bottom - 1, 0xFF8B8B8B);
        graphics.fill(x + 1, bottom - 2, right - 1, bottom - 1, 0xFF555555);
        graphics.fill(right - 2, y + 1, right - 1, bottom - 1, 0xFF555555);
    }

    private void drawArrow(GuiGraphicsExtractor graphics, boolean showGap) {
        int centerX = getX() + 6;
        if (direction == Direction.TO_CONTAINER) {
            drawUpArrow(graphics, centerX, getY() + 1, showGap);
        } else {
            drawDownArrow(graphics, centerX - 1, getY() + 1, showGap);
        }
    }

    private static void drawUpArrow(GuiGraphicsExtractor graphics, int x, int y, boolean showGap) {
        graphics.fill(x - 1, y + 4, x, y + 9, ARROW_COLOR);
        graphics.fill(x - 1, y + 1, x + 1, y + 2, ARROW_COLOR);
        graphics.fill(x - 2, y + 2, x + 2, y + 3, ARROW_COLOR);
        graphics.fill(x - 3, y + 3, x + 3, y + 4, ARROW_COLOR);
        graphics.fill(x, y + 4, x + 1, y + 9, ARROW_SHADOW);
        if (showGap) {
            graphics.fill(x - 1, y + 6, x + 1, y + 7, ARROW_GAP);
        }
    }

    private static void drawDownArrow(GuiGraphicsExtractor graphics, int x, int y, boolean showGap) {
        graphics.fill(x, y + 1, x + 1, y + 6, ARROW_COLOR);
        graphics.fill(x - 2, y + 6, x + 4, y + 7, ARROW_COLOR);
        graphics.fill(x - 1, y + 7, x + 3, y + 8, ARROW_COLOR);
        graphics.fill(x, y + 8, x + 2, y + 9, ARROW_COLOR);
        graphics.fill(x + 1, y + 1, x + 2, y + 6, ARROW_SHADOW);
        if (showGap) {
            graphics.fill(x, y + 3, x + 2, y + 4, ARROW_GAP);
        }
    }

    public enum Direction {
        TO_CONTAINER("to_container"), // 向容器转移物品，箭头朝上。
        TO_INVENTORY("to_inventory"); // 向玩家物品栏转移物品，箭头朝下。

        private final String translationPart; // 翻译键后缀，用于拼接当前方向的提示文本。

        Direction(String translationPart) {
            this.translationPart = translationPart;
        }
    }

    @FunctionalInterface
    public interface OnTransfer {
        void run(boolean transferAll, boolean includeHotbar);
    }
}
