package com.sakurakugu.archweaver.menu;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.component.ResolvableProfile;
import com.sakurakugu.archweaver.entity.AvatarModelParts;
import com.sakurakugu.archweaver.entity.MannequinManager;
import com.sakurakugu.archweaver.mixin.MannequinInvoker;

/** 玩偶专用装备栏，只暴露六个装备槽和查看者背包。 */
public final class MannequinInventoryMenu extends AbstractContainerMenu {
    private final Mannequin mannequin;
    private final Inventory viewerInventory;
    private final UUID mannequinId;
    private final String mannequinName;
    private final SimpleContainerData settings = new SimpleContainerData(4);

    public MannequinInventoryMenu(int id, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(id, inventory, resolve(inventory, data.readVarInt()), data.readUUID(), data.readUtf(64));
        // 菜单可能先于实体追踪包到达，远处的目标也可能根本不在客户端追踪范围内。
        ((MannequinInvoker) mannequin).archweaver$setProfile(ResolvableProfile.STREAM_CODEC.decode(data));
        mannequin.setPose(data.readEnum(Pose.class));
        ((MannequinInvoker) mannequin).archweaver$setImmovable(data.readBoolean());
        mannequin.setNoGravity(data.readBoolean());
        ((AvatarModelParts) mannequin).archweaver$setModelParts(data.readUnsignedByte());
        captureSettings();
    }

    public MannequinInventoryMenu(int id, Inventory inventory, Mannequin mannequin) {
        this(id, inventory, mannequin, mannequin == null ? new UUID(0, 0) : mannequin.getUUID(),
            mannequin == null ? "玩偶" : mannequin.getName().getString());
    }

    private MannequinInventoryMenu(int id, Inventory inventory, Mannequin mannequin, UUID uuid, String name) {
        super(ModMenus.MANNEQUIN_INVENTORY.get(), id);
        this.mannequin = mannequin;
        this.viewerInventory = inventory;
        this.mannequinId = uuid;
        this.mannequinName = name;
        if (mannequin != null) mannequin.setUUID(uuid);
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
        // 盔甲槽沿用原版玩家背包左列，主手和副手放在右侧，保持六个装备槽可见。
        for (int i = 0; i < 4; i++) addSlot(new EquipmentSlotSlot(this::mannequin, slots[i], i, 8, 8 + i * 18));
        addSlot(new EquipmentSlotSlot(this::mannequin, EquipmentSlot.MAINHAND, 4, 98, 29));
        addSlot(new EquipmentSlotSlot(this::mannequin, EquipmentSlot.OFFHAND, 5, 77, 62));
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        captureSettings();
        addDataSlots(settings);
    }

    private void captureSettings() {
        if (mannequin == null) return;
        settings.set(0, mannequin.getPose().ordinal());
        settings.set(1, ((MannequinInvoker) mannequin).archweaver$getImmovable() ? 1 : 0);
        settings.set(2, mannequin.isNoGravity() ? 1 : 0);
        settings.set(3, ((AvatarModelParts) mannequin).archweaver$modelParts());
    }

    @Override public void broadcastChanges() {
        if (!viewerInventory.player.level().isClientSide()) captureSettings();
        super.broadcastChanges();
    }

    /** 使用菜单同步数据更新独立预览，使远处或其他维度的玩偶也能编辑设置。 */
    public void updatePreviewSettings() {
        if (!viewerInventory.player.level().isClientSide() || mannequin == null) return;
        Mannequin target = mannequin();
        target.setPose(Pose.values()[settings.get(0)]);
        ((MannequinInvoker) target).archweaver$setImmovable(settings.get(1) != 0);
        target.setNoGravity(settings.get(2) != 0);
        ((AvatarModelParts) target).archweaver$setModelParts(settings.get(3));
    }

    private static Mannequin resolve(Inventory inventory, int entityId) {
        if (inventory.player.level().getEntity(entityId) instanceof Mannequin m) return m;
        Mannequin preview = EntityType.MANNEQUIN.create(inventory.player.level(), EntitySpawnReason.COMMAND);
        if (preview == null) throw new IllegalStateException("无法创建玩偶背包预览");
        preview.setId(entityId);
        return preview;
    }

    public Mannequin mannequin() {
        if (mannequin != null && viewerInventory.player.level().getEntity(mannequin.getId()) instanceof Mannequin current
            && current.getUUID().equals(mannequinId)) return current;
        return mannequin;
    }
    public UUID mannequinId() { return mannequinId; }
    public String mannequinName() { return mannequinName; }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || !getSlot(index).hasItem()) return ItemStack.EMPTY;
        Slot source = getSlot(index);
        ItemStack remaining = source.getItem();
        ItemStack original = remaining.copy();
        if (index < 6) {
            if (!moveItemStackTo(remaining, 6, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (!moveItemStackTo(remaining, 0, 6, false)) {
                if (index < 33) {
                    if (!moveItemStackTo(remaining, 33, 42, false)) return ItemStack.EMPTY;
                } else if (!moveItemStackTo(remaining, 6, 33, false)) return ItemStack.EMPTY;
            }
        }
        source.setByPlayer(remaining.isEmpty() ? ItemStack.EMPTY : remaining, original);
        source.onTake(player, remaining);
        return original;
    }

    @Override public boolean stillValid(Player player) { return mannequin == null || mannequin.isAlive(); }

    @Override public void removed(Player player) {
        super.removed(player);
        if (player instanceof ServerPlayer viewer && mannequin != null
            && MannequinManager.data(viewer.level().getServer()).find(mannequinId).isPresent()) {
            MannequinManager.capture(viewer.level().getServer(), mannequin);
        }
    }

    private static final class EquipmentSlotSlot extends Slot {
        private final Supplier<Mannequin> target;
        private final EquipmentSlot equipmentSlot;
        EquipmentSlotSlot(Supplier<Mannequin> target, EquipmentSlot equipmentSlot, int index, int x, int y) {
            super(new EmptyContainer(), index, x, y); this.target = target; this.equipmentSlot = equipmentSlot;
        }
        @Override public ItemStack getItem() { return target.get() == null ? ItemStack.EMPTY : target.get().getItemBySlot(equipmentSlot); }
        @Override public void set(ItemStack stack) { if (target.get() != null) target.get().setItemSlot(equipmentSlot, stack); }
        @Override public void setByPlayer(ItemStack stack, ItemStack old) { set(stack); }
        @Override public ItemStack remove(int amount) {
            Mannequin mannequin = target.get();
            if (mannequin == null) return ItemStack.EMPTY;
            ItemStack current = mannequin.getItemBySlot(equipmentSlot);
            if (current.isEmpty()) return ItemStack.EMPTY;
            ItemStack result = current.split(Math.min(amount, current.getCount()));
            mannequin.setItemSlot(equipmentSlot, current);
            return result;
        }
        @Override public boolean mayPickup(Player player) { return target.get() != null; }
        @Override public boolean mayPlace(ItemStack stack) {
            Mannequin mannequin = target.get();
            return mannequin != null && (equipmentSlot == EquipmentSlot.MAINHAND || equipmentSlot == EquipmentSlot.OFFHAND
                || stack.canEquip(equipmentSlot, mannequin));
        }
        @Override public int getMaxStackSize() { return equipmentSlot == EquipmentSlot.MAINHAND || equipmentSlot == EquipmentSlot.OFFHAND ? 64 : 1; }
    }

    private static final class EmptyContainer implements Container {
        public int getContainerSize() { return 0; }
        public boolean isEmpty() { return true; }
        public ItemStack getItem(int i) { return ItemStack.EMPTY; }
        public ItemStack removeItem(int i, int n) { return ItemStack.EMPTY; }
        public ItemStack removeItemNoUpdate(int i) { return ItemStack.EMPTY; }
        public void setItem(int i, ItemStack s) { }
        public void setChanged() { }
        public boolean stillValid(Player p) { return true; }
        public void clearContent() { }
    }
}
