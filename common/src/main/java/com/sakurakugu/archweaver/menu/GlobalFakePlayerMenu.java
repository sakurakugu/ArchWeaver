package com.sakurakugu.archweaver.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** 为假人生成页面提供容器通道。 */
public final class GlobalFakePlayerMenu extends AbstractContainerMenu {
    public GlobalFakePlayerMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory);
    }

    public GlobalFakePlayerMenu(int containerId, Inventory inventory) {
        super(ModMenus.GLOBAL_FAKE_PLAYER.get(), containerId);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

}
