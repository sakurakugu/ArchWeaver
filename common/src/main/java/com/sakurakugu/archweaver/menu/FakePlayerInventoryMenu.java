package com.sakurakugu.archweaver.menu;

import com.sakurakugu.archweaver.config.ArchWeaverConfig;
import com.sakurakugu.archweaver.chunkloading.ChunkLoaderManager;
import com.sakurakugu.archweaver.chunkloading.FakePlayerLoadPolicy;
import com.sakurakugu.archweaver.chunkloading.FakePlayerLoadMode;
import com.sakurakugu.archweaver.chunkloading.FakePlayerSimulationService;
import com.sakurakugu.archweaver.entity.FakePlayerActions;
import com.sakurakugu.archweaver.entity.FakePlayerManager;
import com.sakurakugu.archweaver.entity.FakePlayerPossession;
import com.sakurakugu.archweaver.entity.FakeServerPlayer;
import com.sakurakugu.archweaver.menu.FakePlayerMenuAction.Automation;
import com.sakurakugu.archweaver.menu.FakePlayerMenuAction.ContinuousInterval;
import com.sakurakugu.archweaver.menu.FakePlayerMenuAction.Control;
import com.sakurakugu.archweaver.menu.FakePlayerMenuAction.Drop;
import com.sakurakugu.archweaver.menu.FakePlayerMenuAction.Held;
import com.sakurakugu.archweaver.menu.FakePlayerMenuAction.HotbarSelect;
import com.sakurakugu.archweaver.menu.FakePlayerMenuAction.SetBodyYaw;
import com.sakurakugu.archweaver.menu.FakePlayerMenuAction.SetGameMode;
import com.sakurakugu.archweaver.menu.FakePlayerMenuAction.Simple;
import com.sakurakugu.archweaver.menu.FakePlayerMenuAction.ToggleContinuous;
import com.sakurakugu.archweaver.menu.FakePlayerMenuAction.ToggleMove;
import com.sakurakugu.archweaver.menu.FakePlayerMenuAction.Transfer;
import com.sakurakugu.archweaver.persistence.FakePlayerPersistence;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** 编辑假人的完整物品栏或末影箱，并同时显示操作者背包。 */
public final class FakePlayerInventoryMenu extends AbstractContainerMenu {
    private static final int INVENTORY_TARGET_SLOTS = 41;
    private static final int CRAFTING_SLOT_COUNT = 5;
    private static final int ENDER_CHEST_TARGET_SLOTS = 27;
    private static final int CRAFTING_RESULT_SLOT = INVENTORY_TARGET_SLOTS;
    private static final int CRAFTING_INPUT_START = CRAFTING_RESULT_SLOT + 1;
    private static final int CRAFTING_INPUT_END = CRAFTING_INPUT_START + 4;
    /** 可设置间隔的持续动作，顺序与菜单外发的间隔索引一致。 */
    private static final FakePlayerActions.ScheduledAction[] CONTINUOUS_CONTROLS = {
        FakePlayerActions.ScheduledAction.ATTACK,
        FakePlayerActions.ScheduledAction.USE,
        FakePlayerActions.ScheduledAction.JUMP
    };
    private static final int CONTINUOUS_INTERVAL_ACTION_COUNT = CONTINUOUS_CONTROLS.length;
    private static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private final FakeServerPlayer target;
    private final String targetName;
    private final String targetAlias;
    private final java.util.UUID targetUuid;
    private final int targetEntityId;
    private final View view;
    private final int targetSlotCount;
    private final boolean possessedByViewer;
    private final boolean targetOccupied;
    private final boolean restoreOnRestart;
    private final Player viewer;
    private final Player craftingOwner;
    private final CraftingContainer craftSlots = new TransientCraftingContainer(this, 2, 2);
    private final ResultContainer resultSlots = new ResultContainer();
    private int selectedHotbarSlotSnapshot;
    private final DataSlot selectedHotbarSlot;
    private int automationMaskSnapshot;
    private final DataSlot automationMask;
    private int flyingSnapshot;
    private final DataSlot flying;
    private int continuousControlMaskSnapshot;
    private final DataSlot continuousControlMask;
    private final int[] continuousIntervals = new int[CONTINUOUS_INTERVAL_ACTION_COUNT];
    private final DataSlot[] continuousIntervalData = new DataSlot[CONTINUOUS_INTERVAL_ACTION_COUNT];
    /** 当前被长按的控制项，null 表示没有按下的控制。 */
    private Control heldControl;
    private int pitchSnapshot;
    private int yawSnapshot;
    private int bodyYawSnapshot;
    private DataSlot pitchData;
    private DataSlot yawData;
    private DataSlot bodyYawData;
    private int bodyFollowsHeadSnapshot;
    private final DataSlot bodyFollowsHead;
    private int healthSnapshot;
    private int maxHealthSnapshot;
    private int foodSnapshot;
    private int saturationSnapshot;
    private int armorSnapshot;
    private int airSupplySnapshot;
    private int maxAirSupplySnapshot;
    private int experienceLevelSnapshot;
    private int experiencePointsSnapshot;
    private int experienceNeededSnapshot;
    private int totalExperienceSnapshot;
    private int gameModeSnapshot;
    private int positionXSnapshot;
    private int positionYSnapshot;
    private int positionZSnapshot;
    private int simulationModeSnapshot;
    private int simulationDistanceSnapshot;
    private int simulationDistanceLimitSnapshot;
    private final DataSlot health;
    private final DataSlot maxHealth;
    private final DataSlot food;
    private final DataSlot saturation;
    private final DataSlot armor;
    private final DataSlot airSupply;
    private final DataSlot maxAirSupply;
    private final DataSlot experienceLevel;
    private final DataSlot experiencePoints;
    private final DataSlot experienceNeeded;
    private final DataSlot totalExperience;
    private final DataSlot gameMode;
    private final DataSlot positionX;
    private final DataSlot positionY;
    private final DataSlot positionZ;
    private final DataSlot simulationMode;
    private final DataSlot simulationDistance;
    private final DataSlot simulationDistanceLimit;

    public FakePlayerInventoryMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(
            containerId,
            inventory,
            null,
            data.readUtf(64),
            data.readUtf(com.sakurakugu.archweaver.entity.FakePlayerAlias.MAX_LENGTH),
            data.readUUID(),
            View.fromNetwork(data.readVarInt()),
            data.readVarInt(),
            data.readBoolean(),
            data.readBoolean(),
            data.readBoolean(),
            data.readVarInt(),
            data.readVarInt(),
            data.readVarInt(),
            data.readVarInt(),
            data.readVarInt(),
            data.readVarInt(),
            data.readVarInt(),
            data.readVarInt(),
            data.readBoolean(),
            data.readVarInt(),
            data.readVarInt(),
            data.readVarInt()
        );
    }

    public FakePlayerInventoryMenu(
        int containerId,
        Inventory inventory,
        FakeServerPlayer target,
        View view,
        boolean possessedByViewer,
        boolean targetOccupied
    ) {
        this(containerId, inventory, target, target.getGameProfile().name(), target.alias(), target.getUUID(), view, target.getId(),
            possessedByViewer, targetOccupied,
            com.sakurakugu.archweaver.persistence.FakePlayerPersistence.data(target.server()).resident(target.getUUID())
                .map(com.sakurakugu.archweaver.persistence.FakePlayerSavedData.Resident::restoreOnRestart)
                .orElse(ArchWeaverConfig.restoreFakePlayers()),
            automationMask(target), continuousControlMask(target),
            target.actions().repeatInterval(FakePlayerActions.ScheduledAction.ATTACK),
            target.actions().repeatInterval(FakePlayerActions.ScheduledAction.USE),
            target.actions().repeatInterval(FakePlayerActions.ScheduledAction.JUMP),
            Math.round(target.getXRot()), Math.round(target.getYRot()), Math.round(target.yBodyRot),
            target.actions().bodyFollowsHead(), simulationMode(target), simulationDistance(target),
            FakePlayerSimulationService.maxSimulationDistance(target.server()));
    }

    private static int simulationMode(FakeServerPlayer target) {
        return simulationPolicyFor(target).mode().ordinal();
    }

    private static int simulationDistance(FakeServerPlayer target) {
        return simulationPolicyFor(target).simulationDistance();
    }

    private static FakePlayerLoadPolicy simulationPolicyFor(FakeServerPlayer target) {
        return ChunkLoaderManager.data(target.server()).policy(target.getUUID())
            .orElse(new FakePlayerLoadPolicy(target.getUUID(), FakePlayerLoadMode.PLAYER, 0));
    }

    private FakePlayerInventoryMenu(
        int containerId,
        Inventory viewerInventory,
        FakeServerPlayer target,
        String targetName,
        String targetAlias,
        java.util.UUID targetUuid,
        View view,
        int targetEntityId,
        boolean possessedByViewer,
        boolean targetOccupied,
        boolean restoreOnRestart,
        int automationMask,
        int continuousControlMask,
        int attackInterval,
        int useInterval,
        int jumpInterval,
        int pitch,
        int yaw,
        int bodyYaw,
        boolean bodyFollowsHead,
        int simulationMode,
        int simulationDistance,
        int simulationDistanceLimit
    ) {
        super(ModMenus.FAKE_PLAYER_INVENTORY.get(), containerId);
        this.target = target;
        this.targetName = targetName;
        this.targetAlias = targetAlias;
        this.targetUuid = targetUuid;
        this.view = view;
        this.targetEntityId = targetEntityId;
        this.possessedByViewer = possessedByViewer;
        this.targetOccupied = targetOccupied;
        this.restoreOnRestart = restoreOnRestart;
        this.pitchSnapshot = pitch;
        this.yawSnapshot = yaw;
        this.bodyYawSnapshot = bodyYaw;
        this.bodyFollowsHeadSnapshot = bodyFollowsHead ? 1 : 0;
        this.simulationModeSnapshot = simulationMode;
        this.simulationDistanceSnapshot = simulationDistance;
        this.simulationDistanceLimitSnapshot = simulationDistanceLimit;
        this.pitchData = addDataSlot(new DataSlot() {
            public int get() { return target == null ? pitchSnapshot : Math.round(target.getXRot()); }
            public void set(int value) { pitchSnapshot = value; }
        });
        this.yawData = addDataSlot(new DataSlot() {
            public int get() { return target == null ? yawSnapshot : Math.round(target.getYRot()); }
            public void set(int value) { yawSnapshot = value; }
        });
        this.bodyYawData = addDataSlot(new DataSlot() {
            public int get() { return target == null ? bodyYawSnapshot : Math.round(target.yBodyRot); }
            public void set(int value) { bodyYawSnapshot = value; }
        });
        this.bodyFollowsHead = addDataSlot(new DataSlot() {
            public int get() {
                return target == null ? bodyFollowsHeadSnapshot : target.actions().bodyFollowsHead() ? 1 : 0;
            }
            public void set(int value) { bodyFollowsHeadSnapshot = value; }
        });
        this.health = syncedValue(
            () -> target == null ? healthSnapshot : Math.round(target.getHealth() * 100.0F),
            value -> healthSnapshot = value);
        this.maxHealth = syncedValue(
            () -> target == null ? maxHealthSnapshot : Math.round(target.getMaxHealth() * 100.0F),
            value -> maxHealthSnapshot = value);
        this.food = syncedValue(
            () -> target == null ? foodSnapshot : target.getFoodData().getFoodLevel(),
            value -> foodSnapshot = value);
        this.saturation = syncedValue(
            () -> target == null ? saturationSnapshot
                : Math.round(target.getFoodData().getSaturationLevel() * 100.0F),
            value -> saturationSnapshot = value);
        this.armor = syncedValue(
            () -> target == null ? armorSnapshot : target.getArmorValue(),
            value -> armorSnapshot = value);
        this.airSupply = syncedValue(
            () -> target == null ? airSupplySnapshot : target.getAirSupply(),
            value -> airSupplySnapshot = value);
        this.maxAirSupply = syncedValue(
            () -> target == null ? maxAirSupplySnapshot : target.getMaxAirSupply(),
            value -> maxAirSupplySnapshot = value);
        this.experienceLevel = syncedValue(
            () -> target == null ? experienceLevelSnapshot : target.experienceLevel,
            value -> experienceLevelSnapshot = value);
        this.experiencePoints = syncedValue(
            () -> target == null ? experiencePointsSnapshot
                : Math.round(target.experienceProgress * target.getXpNeededForNextLevel()),
            value -> experiencePointsSnapshot = value);
        this.experienceNeeded = syncedValue(
            () -> target == null ? experienceNeededSnapshot : target.getXpNeededForNextLevel(),
            value -> experienceNeededSnapshot = value);
        this.totalExperience = syncedValue(
            () -> target == null ? totalExperienceSnapshot : target.totalExperience,
            value -> totalExperienceSnapshot = value);
        this.gameMode = syncedValue(
            () -> target == null ? gameModeSnapshot : target.gameMode.getGameModeForPlayer().getId(),
            value -> gameModeSnapshot = value);
        this.positionX = syncedValue(
            () -> target == null ? positionXSnapshot : target.getBlockX(),
            value -> positionXSnapshot = value);
        this.positionY = syncedValue(
            () -> target == null ? positionYSnapshot : target.getBlockY(),
            value -> positionYSnapshot = value);
        this.positionZ = syncedValue(
            () -> target == null ? positionZSnapshot : target.getBlockZ(),
            value -> positionZSnapshot = value);
        this.simulationMode = syncedValue(
            () -> target == null ? simulationModeSnapshot
                : ChunkLoaderManager.data(target.server()).policy(target.getUUID())
                    .map(policy -> policy.mode().ordinal()).orElse(0),
            value -> simulationModeSnapshot = value);
        this.simulationDistance = syncedValue(
            () -> target == null ? simulationDistanceSnapshot
                : ChunkLoaderManager.data(target.server()).policy(target.getUUID())
                    .map(FakePlayerLoadPolicy::simulationDistance).orElse(0),
            value -> simulationDistanceSnapshot = value);
        this.simulationDistanceLimit = syncedValue(
            () -> target == null ? simulationDistanceLimitSnapshot
                : FakePlayerSimulationService.maxSimulationDistance(target.server()),
            value -> simulationDistanceLimitSnapshot = value);
        this.automationMaskSnapshot = automationMask;
        this.continuousControlMaskSnapshot = continuousControlMask;
        this.continuousIntervals[0] = attackInterval;
        this.continuousIntervals[1] = useInterval;
        this.continuousIntervals[2] = jumpInterval;
        this.viewer = viewerInventory.player;
        this.craftingOwner = target == null ? viewer : target;
        this.targetSlotCount = switch (view) {
            case INVENTORY -> INVENTORY_TARGET_SLOTS;
            case POSSESSED_INVENTORY -> INVENTORY_TARGET_SLOTS + CRAFTING_SLOT_COUNT;
            case ENDER_CHEST -> ENDER_CHEST_TARGET_SLOTS;
        };
        this.selectedHotbarSlot = addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return target == null ? selectedHotbarSlotSnapshot : target.getInventory().getSelectedSlot();
            }

            @Override
            public void set(int value) {
                selectedHotbarSlotSnapshot = value;
            }
        });
        this.automationMask = addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return target == null ? automationMaskSnapshot : automationMask(target);
            }

            @Override
            public void set(int value) {
                automationMaskSnapshot = value;
            }
        });
        this.flying = addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return target == null ? flyingSnapshot : target.getAbilities().flying ? 1 : 0;
            }

            @Override
            public void set(int value) {
                flyingSnapshot = value;
            }
        });
        this.continuousControlMask = addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return target == null ? continuousControlMaskSnapshot : continuousControlMask(target);
            }

            @Override
            public void set(int value) {
                continuousControlMaskSnapshot = value;
            }
        });
        for (int index = 0; index < continuousIntervalData.length; index++) {
            int intervalIndex = index;
            continuousIntervalData[index] = addDataSlot(new DataSlot() {
                @Override
                public int get() {
                    if (target != null) {
                        continuousIntervals[intervalIndex] = target.actions().repeatInterval(
                            CONTINUOUS_CONTROLS[intervalIndex]);
                    }
                    return continuousIntervals[intervalIndex];
                }

                @Override
                public void set(int value) {
                    continuousIntervals[intervalIndex] = value;
                }
            });
        }

        Container targetContainer = target == null
            ? new SimpleContainer(view == View.ENDER_CHEST ? ENDER_CHEST_TARGET_SLOTS : INVENTORY_TARGET_SLOTS)
            : view == View.ENDER_CHEST ? target.getEnderChestInventory() : target.getInventory();
        if (view != View.ENDER_CHEST) {
            addTargetInventorySlots(targetContainer, target == null ? viewerInventory.player : target);
            if (view == View.POSSESSED_INVENTORY) {
                addCraftingSlots();
            }
            if (view == View.INVENTORY) {
                addViewerSlots(viewerInventory, 8, 178);
            }
        } else {
            addGrid(targetContainer, 0, 3, 8, 18);
            addViewerSlots(viewerInventory, 8, 85);
        }
    }

    private DataSlot syncedValue(IntSupplier getter, IntConsumer setter) {
        return addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return getter.getAsInt();
            }

            @Override
            public void set(int value) {
                setter.accept(value);
            }
        });
    }

    private void addTargetInventorySlots(Container inventory, LivingEntity owner) {
        // 假人主背包使用原版 Inventory 索引：9-35 为主背包，0-8 为快捷栏。
        addGrid(inventory, 9, 3, 8, 84);
        addGrid(inventory, 0, 1, 8, 142);

        for (int index = 0; index < ARMOR_SLOTS.length; index++) {
            EquipmentSlot equipmentSlot = ARMOR_SLOTS[index];
            addSlot(new EquipmentSlotSlot(inventory, owner, equipmentSlot, 39 - index, 8, 8 + index * 18));
        }
        addSlot(new EquipmentSlotSlot(inventory, owner, EquipmentSlot.OFFHAND, 40, 77, 62));
    }

    private void addCraftingSlots() {
        addSlot(new ResultSlot(craftingOwner, craftSlots, resultSlots, 0, 154, 28));
        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 2; column++) {
                addSlot(new Slot(craftSlots, column + row * 2, 98 + column * 18, 18 + row * 18));
            }
        }
    }

    private void addViewerSlots(Inventory inventory, int left, int top) {
        addGrid(inventory, 9, 3, left, top);
        addGrid(inventory, 0, 1, left, top + 58);
    }

    private void addGrid(Container container, int startIndex, int rows, int left, int top) {
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(container, startIndex + row * 9 + column, left + column * 18, top + row * 18));
            }
        }
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput containerInput, Player player) {
        // 每个服务端槽位操作都重新校验，避免目标离线或权限变更后继续编辑。
        if (!canAccess(player)) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.closeContainer();
            }
            return;
        }
        super.clicked(slotId, button, containerInput, player);
    }

    @Override
    public boolean clickMenuButton(Player player, int actionId) {
        FakePlayerMenuAction action;
        try {
            action = FakePlayerMenuActionCodec.decode(actionId);
        } catch (IllegalArgumentException exception) {
            // 编号不在动作表内，直接忽略，不做任何状态变更。
            return false;
        }
        if (view == View.ENDER_CHEST || !canAccess(player)) {
            return false;
        }
        if (view == View.POSSESSED_INVENTORY && action != Simple.POSSESS) {
            return false;
        }
        if (action instanceof HotbarSelect(int hotbarSlot)) {
            target.getInventory().setSelectedSlot(hotbarSlot);
            broadcastChanges();
            return true;
        }
        if (!(player instanceof ServerPlayer viewer) || target == null) {
            return false;
        }
        switch (action) {
            case Simple simple -> handleSimple(simple, viewer, player);
            case HotbarSelect ignored -> {
                // 快捷栏选中已经在解码后的分支里处理掉。
            }
            case Drop(int value, boolean percentage, boolean continuous) -> {
                FakePlayerActions.RepeatMode mode = continuous
                    ? FakePlayerActions.RepeatMode.CONTINUOUS
                    : FakePlayerActions.RepeatMode.ONCE;
                if (percentage) {
                    target.actions().dropPercentage(target.getInventory().getSelectedSlot(), value, mode, 1);
                } else {
                    target.actions().dropAmount(target.getInventory().getSelectedSlot(), value, mode, 1);
                }
            }
            case Transfer(boolean toTarget, boolean all, boolean includeHotbar) ->
                transferItems(player, toTarget, !all, includeHotbar);
            case Automation(int index) -> {
                target.automation().toggleSetting(index);
                // 只同步数据槽，保留客户端展开状态。
                broadcastChanges();
            }
            case ToggleMove(FakePlayerActions.MoveDirection direction) -> {
                if (target.actions().isMoving(direction)) {
                    target.actions().stopMove();
                } else {
                    target.actions().startMove(direction);
                }
                broadcastChanges();
            }
            case ToggleContinuous(FakePlayerActions.ScheduledAction scheduled) -> {
                toggleContinuousAction(scheduled);
                broadcastChanges();
            }
            case Held(Control control, boolean pressed) -> applyHeldControl(control, pressed);
            case ContinuousInterval(FakePlayerActions.ScheduledAction scheduled, int interval) -> {
                target.actions().setRepeatInterval(scheduled, interval);
                continuousIntervals[continuousIntervalIndex(scheduled)] = interval;
                broadcastChanges();
            }
            case SetBodyYaw(int yaw) -> {
                target.actions().setBodyRotation(yaw);
                broadcastChanges();
            }
            case SetGameMode(GameType gameType) -> {
                target.gameMode.changeGameModeForPlayer(gameType);
                target.getAbilities().flying = target.getAbilities().flying && target.getAbilities().mayfly;
                FakePlayerPersistence.track(target);
                broadcastChanges();
            }
        }
        return true;
    }

    /** 处理不带参数的动作。 */
    private void handleSimple(Simple simple, ServerPlayer viewer, Player player) {
        switch (simple) {
            case ENDER_CHEST -> FakePlayerMenuOpener.openEnderChest(viewer, target);
            case REMOVE -> {
                player.closeContainer();
                FakePlayerManager.remove(target);
            }
            case POSSESS -> {
                if (FakePlayerPossession.isControlling(viewer, target)) {
                    FakePlayerPossession.stop(viewer);
                    viewer.closeContainer();
                } else {
                    FakePlayerPossession.start(viewer, target);
                }
            }
            case MOUNT -> target.actions().mountNearest(false);
            case MOUNT_NEARBY -> target.actions().mountNearest(true);
            case MOUNT_ANYTHING -> target.actions().mountNearest(true);
            case DISMOUNT -> target.actions().dismount();
            case MOVE_FORWARD -> target.actions().moveOnce(FakePlayerActions.MoveDirection.FORWARD);
            case MOVE_BACKWARD -> target.actions().moveOnce(FakePlayerActions.MoveDirection.BACKWARD);
            case MOVE_LEFT -> target.actions().moveOnce(FakePlayerActions.MoveDirection.LEFT);
            case MOVE_RIGHT -> target.actions().moveOnce(FakePlayerActions.MoveDirection.RIGHT);
            case JUMP -> target.actions().jump();
            case ATTACK_ONCE -> target.actions().attackOnce();
            case USE_ONCE -> target.actions().useOnce();
            case TURN_LEFT -> target.actions().turn(-1.0F);
            case TURN_RIGHT -> target.actions().turn(1.0F);
            case SNEAK -> target.actions().toggleSneak();
            case FLY_UP -> target.actions().flyVertical(true);
            case FLY_DOWN -> target.actions().flyVertical(false);
            case TOGGLE_FLIGHT -> target.actions().toggleFlight();
            case TOGGLE_BODY_FOLLOWS_HEAD -> {
                target.actions().toggleBodyFollowsHead();
                broadcastChanges();
            }
            case STOP_ALL -> {
                target.actions().stop();
                heldControl = null;
                broadcastChanges();
            }
        }
    }

    /** 按下或松开一个可长按的控制项。 */
    private void applyHeldControl(Control control, boolean pressed) {
        if (!pressed) {
            // 只停掉真正按下的那一项，避免误停其他正在进行的长按动作。
            if (control == heldControl) {
                stopControl(control);
                heldControl = null;
            }
            return;
        }
        switch (control) {
            case MOVE_FORWARD -> target.actions().startMove(FakePlayerActions.MoveDirection.FORWARD);
            case MOVE_BACKWARD -> target.actions().startMove(FakePlayerActions.MoveDirection.BACKWARD);
            case MOVE_LEFT -> target.actions().startMove(FakePlayerActions.MoveDirection.LEFT);
            case MOVE_RIGHT -> target.actions().startMove(FakePlayerActions.MoveDirection.RIGHT);
            case TURN_LEFT -> target.actions().startTurn(-1.0F);
            case TURN_RIGHT -> target.actions().startTurn(1.0F);
            case ATTACK -> target.actions().startAttack();
            case USE -> target.actions().startUse();
            case JUMP -> target.actions().startJump();
            case FLY_UP -> target.actions().startFlyVertical(true);
            case FLY_DOWN -> target.actions().startFlyVertical(false);
        }
        heldControl = control;
    }

    /** 停止一个可长按的控制项。 */
    private void stopControl(Control control) {
        switch (control) {
            case MOVE_FORWARD, MOVE_BACKWARD, MOVE_LEFT, MOVE_RIGHT -> target.actions().stopMove();
            case TURN_LEFT, TURN_RIGHT -> target.actions().stopTurn();
            case ATTACK -> target.actions().stopAttack();
            case USE -> target.actions().stopUse();
            case JUMP -> target.actions().stopJump();
            case FLY_UP, FLY_DOWN -> target.actions().stopFlyVertical();
        }
    }

    /** 停掉当前按下的长按控制项，用于“全部停止”和界面关闭。 */
    private void stopHeldControl() {
        if (heldControl == null) {
            return;
        }
        stopControl(heldControl);
        heldControl = null;
    }

    /** 切换攻击、使用或跳跃的持续执行。 */
    private void toggleContinuousAction(FakePlayerActions.ScheduledAction action) {
        boolean enabled = target.actions().isRepeating(action);
        int interval = target.actions().repeatInterval(action);
        switch (action) {
            case ATTACK -> {
                if (enabled) {
                    target.actions().stopAttack();
                } else {
                    target.actions().attack(FakePlayerActions.RepeatMode.INTERVAL, interval);
                }
            }
            case USE -> {
                if (enabled) {
                    target.actions().stopUse();
                } else {
                    target.actions().use(FakePlayerActions.RepeatMode.INTERVAL, interval);
                }
            }
            case JUMP -> {
                if (enabled) {
                    target.actions().stopJump();
                } else {
                    target.actions().jump(FakePlayerActions.RepeatMode.INTERVAL, interval);
                }
            }
            case DROP -> throw new IllegalArgumentException("不支持切换该持续动作: " + action);
        }
    }

    /** 持续动作在间隔数组中的下标，与 {@link #CONTINUOUS_CONTROLS} 的顺序一致。 */
    private static int continuousIntervalIndex(FakePlayerActions.ScheduledAction action) {
        for (int index = 0; index < CONTINUOUS_CONTROLS.length; index++) {
            if (CONTINUOUS_CONTROLS[index] == action) {
                return index;
            }
        }
        throw new IllegalArgumentException("不支持设置间隔的动作: " + action);
    }

    /** 默认只处理双方的 27 格主背包；按住 Ctrl 才包含快捷栏，装备槽始终保持原样。 */
    private void transferItems(Player player, boolean toTarget, boolean filterByContents, boolean includeHotbar) {
        int viewerStart = targetSlotCount;
        int sourceStart = toTarget ? viewerStart : 0;
        int sourceEnd = sourceStart + (includeHotbar ? 36 : 27);
        int destinationStart = toTarget ? 0 : viewerStart;
        int destinationEnd = destinationStart + (includeHotbar ? 36 : 27);
        List<ItemStack> destinationContents = filterByContents
            ? snapshotContents(destinationStart, destinationEnd)
            : List.of();

        for (int slotIndex = sourceStart; slotIndex < sourceEnd; slotIndex++) {
            Slot source = slots.get(slotIndex);
            if (!source.hasItem()) {
                continue;
            }
            ItemStack sourceStack = source.getItem();
            if (filterByContents && destinationContents.stream()
                .noneMatch(existing -> ItemStack.isSameItemSameComponents(existing, sourceStack))) {
                continue;
            }

            ItemStack original = sourceStack.copy();
            if (!moveItemStackTo(sourceStack, destinationStart, destinationEnd, false)
                || sourceStack.getCount() == original.getCount()) {
                continue;
            }
            if (sourceStack.isEmpty()) {
                source.setByPlayer(ItemStack.EMPTY, original);
            } else {
                source.setChanged();
            }
            source.onTake(player, sourceStack);
        }
        broadcastChanges();
    }

    private List<ItemStack> snapshotContents(int start, int end) {
        List<ItemStack> contents = new ArrayList<>();
        for (int slotIndex = start; slotIndex < end; slotIndex++) {
            ItemStack stack = slots.get(slotIndex).getItem();
            if (!stack.isEmpty() && contents.stream()
                .noneMatch(existing -> ItemStack.isSameItemSameComponents(existing, stack))) {
                contents.add(stack.copyWithCount(1));
            }
        }
        return contents;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (!canAccess(player) || slotIndex < 0 || slotIndex >= slots.size()) {
            return ItemStack.EMPTY;
        }

        Slot source = slots.get(slotIndex);
        if (!source.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack sourceStack = source.getItem();
        ItemStack original = sourceStack.copy();
        int viewerStart = targetSlotCount;

        if (view == View.POSSESSED_INVENTORY && slotIndex == CRAFTING_RESULT_SLOT) {
            if (!moveItemStackTo(sourceStack, 0, 36, true)) {
                return ItemStack.EMPTY;
            }
            source.onQuickCraft(sourceStack, original);
        } else if (view == View.POSSESSED_INVENTORY
            && slotIndex >= CRAFTING_INPUT_START && slotIndex < CRAFTING_INPUT_END) {
            if (!moveItemStackTo(sourceStack, 0, 36, false)) {
                return ItemStack.EMPTY;
            }
        } else if (slotIndex < viewerStart && view == View.POSSESSED_INVENTORY) {
            if (!moveWithinTargetInventory(sourceStack, slotIndex)) {
                return ItemStack.EMPTY;
            }
        } else if (slotIndex < viewerStart) {
            if (!moveItemStackTo(sourceStack, viewerStart, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveToTarget(sourceStack, player)) {
            return ItemStack.EMPTY;
        }

        if (sourceStack.isEmpty()) {
            source.setByPlayer(ItemStack.EMPTY, original);
        } else {
            source.setChanged();
        }
        if (sourceStack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        source.onTake(player, sourceStack);
        if (view == View.POSSESSED_INVENTORY && slotIndex == CRAFTING_RESULT_SLOT && !sourceStack.isEmpty()) {
            craftingOwner.drop(sourceStack, false);
        }
        return original;
    }

    private boolean moveWithinTargetInventory(ItemStack stack, int slotIndex) {
        if (slotIndex < 27) {
            return moveItemStackTo(stack, 27, 36, false);
        }
        return moveItemStackTo(stack, 0, 27, false);
    }

    @Override
    public void slotsChanged(Container container) {
        if (container != craftSlots || !(craftingOwner.level() instanceof ServerLevel level)
            || !(craftingOwner instanceof ServerPlayer serverCraftingOwner)
            || !(viewer instanceof ServerPlayer serverViewer)) {
            super.slotsChanged(container);
            return;
        }

        CraftingInput input = craftSlots.asCraftInput();
        Optional<RecipeHolder<CraftingRecipe>> recipe = level.getServer().getRecipeManager()
            .getRecipeFor(RecipeType.CRAFTING, input, level, (RecipeHolder<CraftingRecipe>) null);
        ItemStack result = recipe.filter(holder -> resultSlots.setRecipeUsed(serverCraftingOwner, holder))
            .map(RecipeHolder::value)
            .map(craftingRecipe -> craftingRecipe.assemble(input))
            .filter(stack -> stack.isItemEnabled(level.enabledFeatures()))
            .orElse(ItemStack.EMPTY);

        resultSlots.setItem(0, result);
        setRemoteSlot(CRAFTING_RESULT_SLOT, result);
        serverViewer.connection.send(new ClientboundContainerSetSlotPacket(
            containerId, incrementStateId(), CRAFTING_RESULT_SLOT, result));
    }

    @Override
    public void removed(Player player) {
        if (target != null) {
            stopHeldControl();
        }
        // 附身界面没有操作者背包，关闭时鼠标携带物也必须回到假人。
        if (view == View.POSSESSED_INVENTORY && !player.level().isClientSide() && !getCarried().isEmpty()) {
            ItemStack carried = getCarried();
            setCarried(ItemStack.EMPTY);
            craftingOwner.getInventory().placeItemBackInInventory(carried);
        }
        super.removed(player);
        resultSlots.clearContent();
        if (!craftingOwner.level().isClientSide()) {
            clearContainer(craftingOwner, craftSlots);
        }
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack carried, Slot targetSlot) {
        return targetSlot.container != resultSlots && super.canTakeItemForPickAll(carried, targetSlot);
    }

    private boolean moveToTarget(ItemStack stack, Player player) {
        if (view == View.ENDER_CHEST) {
            return moveItemStackTo(stack, 0, targetSlotCount, false);
        }

        // 客户端菜单没有假人实体，使用同为玩家的操作者完成装备槽判定、以及类似箱子的手势操作判定。
        EquipmentSlot equipmentSlot = player.getEquipmentSlotForItem(stack);
        int equipmentIndex = switch (equipmentSlot) {
            case HEAD -> 36;
            case CHEST -> 37;
            case LEGS -> 38;
            case FEET -> 39;
            case OFFHAND -> 40;
            default -> -1;
        };
        if (equipmentIndex >= 0 && !slots.get(equipmentIndex).hasItem()
            && moveItemStackTo(stack, equipmentIndex, equipmentIndex + 1, false)) {
            return true;
        }
        // 前 36 个菜单槽位对应假人的主背包和快捷栏。
        return moveItemStackTo(stack, 0, 36, false);
    }

    private boolean canAccess(Player player) {
        if (target == null) {
            return true;
        }
        return !target.hasDisconnected()
            && player instanceof ServerPlayer viewer
            && ArchWeaverConfig.canUseCommands(viewer.createCommandSourceStack())
            && FakePlayerPossession.canOpenMenu(viewer, target);
    }

    @Override
    public boolean stillValid(Player player) {
        return canAccess(player);
    }

    boolean canManageTarget(Player player) {
        return target != null && canAccess(player) && !FakePlayerPossession.isPossessed(target);
    }

    public FakeServerPlayer target() {
        return target;
    }

    /** 同时更新两个视角分量，只产生一次服务端状态广播。 */
    public boolean setViewRotation(Player player, int pitch, int yaw) {
        if (view != View.INVENTORY || !canAccess(player) || pitch < -90 || pitch > 90
            || yaw < -180 || yaw > 179) {
            return false;
        }
        int currentPitch = Math.round(target.getXRot());
        int currentYaw = Math.round(target.getYRot());
        if (currentPitch == pitch && currentYaw == yaw) {
            return true;
        }
        target.actions().setViewRotation(pitch, yaw);
        broadcastChanges();
        return true;
    }

    public String targetAlias() {
        return target == null ? targetAlias : target.alias();
    }

    public String targetName() {
        return targetName;
    }

    public java.util.UUID targetUuid() {
        return targetUuid;
    }

    public View view() {
        return view;
    }

    public int targetEntityId() {
        return targetEntityId;
    }

    public float health() { return health.get() / 100.0F; }
    public float maxHealth() { return maxHealth.get() / 100.0F; }
    public int food() { return food.get(); }
    public float saturation() { return saturation.get() / 100.0F; }
    public int armor() { return armor.get(); }
    public int airSupply() { return airSupply.get(); }
    public int maxAirSupply() { return maxAirSupply.get(); }
    public int experienceLevel() { return experienceLevel.get(); }
    public int experiencePoints() { return experiencePoints.get(); }
    public int experienceNeeded() { return experienceNeeded.get(); }
    public int totalExperience() { return totalExperience.get(); }
    public int gameMode() { return gameMode.get(); }
    public int positionX() { return positionX.get(); }
    public int positionY() { return positionY.get(); }
    public int positionZ() { return positionZ.get(); }
    public FakePlayerLoadMode simulationMode() {
        int ordinal = simulationMode.get();
        return ordinal >= 0 && ordinal < FakePlayerLoadMode.values().length
            ? FakePlayerLoadMode.values()[ordinal] : FakePlayerLoadMode.PLAYER;
    }
    public int simulationDistance() { return simulationDistance.get(); }
    public int simulationDistanceLimit() { return simulationDistanceLimit.get(); }

    public int selectedHotbarSlot() {
        return selectedHotbarSlot.get();
    }

    public boolean possessedByViewer() {
        return possessedByViewer;
    }

    public boolean targetOccupied() {
        return targetOccupied;
    }

    public boolean restoreOnRestart() {
        return restoreOnRestart;
    }

    public boolean isFlying() {
        return flying.get() != 0;
    }

    public boolean automationEnabled(int index) {
        return (automationMask.get() & (1 << index)) != 0;
    }

    public int pitch() { return pitchData.get(); }
    public int yaw() { return yawData.get(); }
    public int bodyYaw() { return bodyYawData.get(); }
    public boolean bodyFollowsHead() { return bodyFollowsHead.get() != 0; }

    public boolean continuousControlEnabled(int index) {
        return (continuousControlMask.get() & (1 << index)) != 0;
    }

    public int continuousInterval(int index) {
        return continuousIntervalData[index].get();
    }

    /** 间隔索引对应的持续动作，索引与客户端界面上的一行一一对应。 */
    public static FakePlayerActions.ScheduledAction continuousControl(int index) {
        if (index < 0 || index >= CONTINUOUS_INTERVAL_ACTION_COUNT) {
            throw new IllegalArgumentException("无效的持续动作索引: " + index);
        }
        return CONTINUOUS_CONTROLS[index];
    }

    public static int continuousControlMask(FakeServerPlayer fake) {
        FakePlayerActions actions = fake.actions();
        int mask = 0;
        for (FakePlayerActions.MoveDirection direction : FakePlayerActions.MoveDirection.values()) {
            if (actions.isMoving(direction)) {
                mask |= 1 << direction.ordinal();
            }
        }
        if (actions.isRepeating(FakePlayerActions.ScheduledAction.ATTACK)) {
            mask |= 1 << 4;
        }
        if (actions.isRepeating(FakePlayerActions.ScheduledAction.USE)) {
            mask |= 1 << 5;
        }
        if (actions.isRepeating(FakePlayerActions.ScheduledAction.JUMP)) {
            mask |= 1 << 6;
        }
        return mask;
    }

    public static int automationMask(FakeServerPlayer fake) {
        var settings = fake.automation().settings();
        int mask = 0;
        if (settings.autoReplenishment()) {
            mask |= 1;
        }
        if (settings.autoReplenishmentFromShulkerBoxes()) {
            mask |= 1 << 1;
        }
        if (settings.autoReplaceTools()) {
            mask |= 1 << 2;
        }
        if (settings.autoFishing()) {
            mask |= 1 << 3;
        }
        return mask;
    }

    public int screenWidth() {
        return 176;
    }

    public int screenHeight() {
        return switch (view) {
            case INVENTORY -> 261;
            case POSSESSED_INVENTORY -> 166;
            case ENDER_CHEST -> 168;
        };
    }

    public enum View {
        INVENTORY,
        POSSESSED_INVENTORY,
        ENDER_CHEST

        ;

        private static View fromNetwork(int ordinal) {
            return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : INVENTORY;
        }
    }

    /** 复现原版装备槽限制，并把装备变化通知给假人实体。 */
    private static final class EquipmentSlotSlot extends Slot {
        private final LivingEntity owner;
        private final EquipmentSlot equipmentSlot;

        private EquipmentSlotSlot(
            Container container,
            LivingEntity owner,
            EquipmentSlot equipmentSlot,
            int containerIndex,
            int x,
            int y
        ) {
            super(container, containerIndex, x, y);
            this.owner = owner;
            this.equipmentSlot = equipmentSlot;
        }

        @Override
        public void setByPlayer(ItemStack newStack, ItemStack oldStack) {
            owner.onEquipItem(equipmentSlot, oldStack, newStack);
            super.setByPlayer(newStack, oldStack);
        }

        @Override
        public int getMaxStackSize() {
            return equipmentSlot == EquipmentSlot.OFFHAND ? super.getMaxStackSize() : 1;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return equipmentSlot == EquipmentSlot.OFFHAND || stack.canEquip(equipmentSlot, owner);
        }

        @Override
        public boolean mayPickup(Player player) {
            if (equipmentSlot == EquipmentSlot.OFFHAND) {
                return super.mayPickup(player);
            }
            ItemStack stack = getItem();
            return stack.isEmpty() || player.isCreative()
                || !EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE);
        }

        @Override
        public Identifier getNoItemIcon() {
            return switch (equipmentSlot) {
                case HEAD -> InventoryMenu.EMPTY_ARMOR_SLOT_HELMET;
                case CHEST -> InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE;
                case LEGS -> InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS;
                case FEET -> InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS;
                case OFFHAND -> InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD;
                default -> null;
            };
        }
    }
}
