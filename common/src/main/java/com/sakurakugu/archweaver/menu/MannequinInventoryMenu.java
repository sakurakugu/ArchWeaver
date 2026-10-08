package com.sakurakugu.archweaver.menu;

import com.sakurakugu.archweaver.network.TargetInfoPayload;
import com.sakurakugu.archweaver.platform.PlatformNetworking;
import com.sakurakugu.archweaver.config.ArchWeaverConfig;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.component.ResolvableProfile;
import com.sakurakugu.archweaver.ArchWeaverMod;
import com.sakurakugu.archweaver.entity.AvatarModelParts;
import com.sakurakugu.archweaver.entity.MannequinLook;
import com.sakurakugu.archweaver.entity.MannequinManager;
import com.sakurakugu.archweaver.mixin.MannequinInvoker;

/** 玩偶专用装备栏，只暴露六个装备槽和查看者背包。 */
public final class MannequinInventoryMenu extends AbstractContainerMenu implements TargetInfoMenu {
    // 菜单槽位和客户端背景共用布局，物品栏标题独占玩家背包上方的完整一行。
    public static final int IMAGE_WIDTH = 176;
    // 预览下沿与物品栏之间空出 11 像素的一条，给预览底部的朝向滑条留位置。
    public static final int VIEWER_SECTION_TOP = 129;
    public static final int IMAGE_HEIGHT = VIEWER_SECTION_TOP + 96;
    public static final int ARMOR_SLOT_LEFT = 8;
    public static final int ARMOR_SLOT_TOP = 26;
    public static final int HAND_SLOT_LEFT = 153;
    public static final int MAIN_HAND_SLOT_TOP = 62;
    public static final int OFF_HAND_SLOT_TOP = 80;
    // 玩家物品栏的 36 格相对物品栏背景上移 4 像素，正好嵌进背景贴图画好的凹槽；窗口高度和标题不动。
    private static final int VIEWER_SLOT_LIFT = 4;
    private static final int VIEWER_ROWS_TOP = VIEWER_SECTION_TOP + 18 - VIEWER_SLOT_LIFT;
    private static final int VIEWER_HOTBAR_TOP = VIEWER_SECTION_TOP + 76 - VIEWER_SLOT_LIFT;

    private TargetInfoPayload targetInfo;
    // 空的主手槽显示“主”字，做法和原版空盔甲槽一样，交给 getNoItemIcon 返回精灵。
    private static final Identifier MAIN_HAND_ICON =
        Identifier.fromNamespaceAndPath(ArchWeaverMod.MOD_ID, "container/slot/main_hand");
    private final Mannequin mannequin;
    private final Inventory viewerInventory;
    private final UUID mannequinId;
    private final String mannequinName;
    private final SimpleContainerData settings = new SimpleContainerData(8);

    public MannequinInventoryMenu(int id, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(id, inventory, data.readVarInt(), data.readUUID(), data.readUtf(64));
        // 菜单可能先于实体追踪包到达，远处的目标也可能根本不在客户端追踪范围内。
        ((MannequinInvoker) mannequin).archweaver$setProfile(ResolvableProfile.STREAM_CODEC.decode(data));
        mannequin.setPose(data.readEnum(Pose.class));
        ((MannequinInvoker) mannequin).archweaver$setImmovable(data.readBoolean());
        mannequin.setNoGravity(data.readBoolean());
        ((AvatarModelParts) mannequin).archweaver$setModelParts(data.readUnsignedByte());
        captureSettings();
        acceptTargetInfo(TargetInfoPayload.STREAM_CODEC.decode(data).forContainer(id));
    }

    private MannequinInventoryMenu(int id, Inventory inventory, int entityId, UUID uuid, String name) {
        this(id, inventory, resolve(inventory, entityId, uuid), uuid, name);
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
        // 盔甲在预览左侧，双手在右侧，中央留给完整的大玩偶。
        for (int i = 0; i < 4; i++) addSlot(new EquipmentSlotSlot(this::mannequin, slots[i], i,
            ARMOR_SLOT_LEFT, ARMOR_SLOT_TOP + i * 18));
        addSlot(new EquipmentSlotSlot(this::mannequin, EquipmentSlot.MAINHAND, 4, HAND_SLOT_LEFT, MAIN_HAND_SLOT_TOP));
        addSlot(new EquipmentSlotSlot(this::mannequin, EquipmentSlot.OFFHAND, 5, HAND_SLOT_LEFT, OFF_HAND_SLOT_TOP));
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, VIEWER_ROWS_TOP + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, VIEWER_HOTBAR_TOP));
        captureSettings();
        addDataSlots(settings);
    }

    private void captureSettings() {
        if (mannequin == null) return;
        settings.set(0, mannequin.getPose().ordinal());
        settings.set(1, ((MannequinInvoker) mannequin).archweaver$getImmovable() ? 1 : 0);
        settings.set(2, mannequin.isNoGravity() ? 1 : 0);
        settings.set(3, ((AvatarModelParts) mannequin).archweaver$modelParts());
        // 视角面板要编辑的三个角度和头身联动开关，远处未被追踪的玩偶也靠这些值显示。
        settings.set(4, Math.round(mannequin.getXRot()));
        settings.set(5, Math.round(mannequin.getYRot()));
        settings.set(6, Math.round(mannequin.yBodyRot));
        settings.set(7, ((MannequinLook) mannequin).archweaver$bodyFollowsHead() ? 1 : 0);
    }

    @Override public void broadcastChanges() {
        if (viewerInventory.player instanceof ServerPlayer viewer && mannequin != null) {
            captureSettings();
            var next = TargetInfoPayload.capture(containerId, mannequin,
                mannequin.getName().getString(), MannequinManager.alias(mannequin), !mannequin.isNoGravity());
            if (!next.equals(targetInfo)) {
                targetInfo = next;
                PlatformNetworking.sendToPlayer(viewer, next);
            }
        }
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
        // 视角与身体朝向同样按同步值刷新，未加载的玩偶靠预览实体渲染这些角度。
        float viewYaw = settings.get(5);
        float bodyYaw = settings.get(6);
        target.setXRot(settings.get(4));
        target.setYRot(viewYaw);
        target.setYHeadRot(viewYaw);
        target.setYBodyRot(bodyYaw);
        target.yHeadRotO = viewYaw;
        target.yBodyRotO = bodyYaw;
    }

    public int pitch() { return settings.get(4); }
    public int yaw() { return settings.get(5); }
    public int bodyYaw() { return settings.get(6); }
    public boolean bodyFollowsHead() { return settings.get(7) != 0; }

    private static Mannequin resolve(Inventory inventory, int entityId, UUID uuid) {
        // 跨维度的实体编号可能碰巧对应本地另一只玩偶，必须同时比较 UUID。
        if (inventory.player.level().getEntity(entityId) instanceof Mannequin m && m.getUUID().equals(uuid)) return m;
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
    public String mannequinName() { return targetInfo == null ? mannequinName : targetInfo.name(); }
    public String alias() { return targetInfo == null ? "" : targetInfo.alias(); }
    @Override public TargetInfoPayload targetInfo() { return targetInfo; }
    @Override public void acceptTargetInfo(TargetInfoPayload info) {
        if (info.containerId() == containerId && info.id().equals(mannequinId)) targetInfo = info;
    }

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

    @Override public boolean stillValid(Player player) {
        if (player.level().isClientSide()) return true;
        return player instanceof ServerPlayer viewer && mannequin != null && mannequin.isAlive()
            && MannequinManager.data(viewer.level().getServer()).find(mannequinId).isPresent()
            && ArchWeaverConfig.canUseCommands(viewer.createCommandSourceStack());
    }

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
        // 四个盔甲槽和副手槽沿用原版的空槽图标，主手槽用模组自己的“主”字。
        @Override public Identifier getNoItemIcon() {
            return switch (equipmentSlot) {
                case HEAD -> InventoryMenu.EMPTY_ARMOR_SLOT_HELMET;
                case CHEST -> InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE;
                case LEGS -> InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS;
                case FEET -> InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS;
                case OFFHAND -> InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD;
                case MAINHAND -> MAIN_HAND_ICON;
                default -> null;
            };
        }
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
