package com.sakurakugu.archweaver.menu;

import com.mojang.authlib.GameProfile;
import com.sakurakugu.archweaver.entity.AvatarModelParts;
import com.sakurakugu.archweaver.entity.FakePlayerManager;
import com.sakurakugu.archweaver.entity.FakePlayerPossession;
import com.sakurakugu.archweaver.entity.FakeServerPlayer;
import com.sakurakugu.archweaver.entity.MannequinManager;
import com.sakurakugu.archweaver.mixin.MannequinInvoker;
import com.sakurakugu.archweaver.network.AvatarSkinPartPayload;
import com.sakurakugu.archweaver.network.TargetTypePayload;
import com.sakurakugu.archweaver.persistence.FakePlayerPersistence;
import com.sakurakugu.archweaver.persistence.MannequinSavedData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec2;

/** 转换实体类型时保留原玩家状态与装备，并通过新菜单更新客户端。 */
public final class TargetTypeActions {
    private static final EquipmentSlot[] EQUIPMENT = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
        EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
    private TargetTypeActions() { }

    public static void setSkinPart(ServerPlayer viewer, AvatarSkinPartPayload payload) {
        if (viewer.containerMenu.containerId != payload.containerId()) return;
        if (viewer.containerMenu instanceof FakePlayerInventoryMenu menu && menu.targetUuid().equals(payload.id())
            && menu.view() == FakePlayerInventoryMenu.View.INVENTORY && menu.canManageTarget(viewer)) {
            var parts = (AvatarModelParts) menu.target();
            int mask = parts.archweaver$modelParts();
            parts.archweaver$setModelParts(payload.shown() ? mask | payload.part().getMask() : mask & ~payload.part().getMask());
            FakePlayerPersistence.track(menu.target());
        } else if (viewer.containerMenu instanceof MannequinInventoryMenu menu && menu.mannequinId().equals(payload.id())
            && menu.stillValid(viewer)) {
            MannequinManager.setModelPart(viewer.level().getServer(), payload.id(), payload.part(), payload.shown());
        }
    }

    public static void convert(ServerPlayer viewer, TargetTypePayload payload) {
        if (viewer.containerMenu.containerId != payload.containerId()) return;
        if (payload.mannequin() && viewer.containerMenu instanceof FakePlayerInventoryMenu menu
            && menu.view() == FakePlayerInventoryMenu.View.INVENTORY && menu.targetUuid().equals(payload.id())
            && menu.canManageTarget(viewer) && !FakePlayerPossession.isPossessed(menu.target())) {
            toMannequin(viewer, menu.target());
        } else if (!payload.mannequin() && viewer.containerMenu instanceof MannequinInventoryMenu menu
            && menu.mannequinId().equals(payload.id()) && menu.mannequin() != null && menu.stillValid(viewer)) {
            toPlayer(viewer, menu.mannequin());
        }
    }

    private static void toMannequin(ServerPlayer viewer, FakeServerPlayer fake) {
        var server = viewer.level().getServer();
        var snapshot = FakePlayerPersistence.snapshot(fake);
        // 载具与已投出的珍珠仍属于世界，切回时不能从快照再次生成。
        snapshot.remove("RootVehicle");
        snapshot.remove("ender_pearls");
        var mannequin = MannequinManager.spawn(server, fake.level(), fake.getGameProfile().name(), fake.position(), fake.getYRot());
        var profile = ResolvableProfile.createResolved(fake.getGameProfile());
        ((MannequinInvoker) mannequin).archweaver$setProfile(profile);
        int mask = ((AvatarModelParts) fake).archweaver$modelParts();
        ((AvatarModelParts) mannequin).archweaver$setModelParts(mask);
        for (EquipmentSlot slot : EQUIPMENT) mannequin.setItemSlot(slot, fake.getItemBySlot(slot).copy());
        var old = MannequinManager.data(server).find(mannequin.getUUID()).orElseThrow();
        MannequinManager.data(server).put(new MannequinSavedData.Record(old.uuid(), old.name(), old.dimension(), old.x(), old.y(), old.z(),
            old.yaw(), old.pose(), old.immovable(), old.biologicalBehavior(), profile, mask,
            old.leftArm(), old.rightArm(), old.leftLeg(), old.rightLeg()));
        MannequinManager.data(server).putPlayerSnapshot(mannequin.getUUID(), snapshot);
        MannequinManager.capture(server, mannequin);
        // 背包已移交到玩偶快照，退出保存的 playerdata 不再保留第二份物品。
        fake.getInventory().clearContent();
        fake.getEnderChestInventory().clearContent();
        FakePlayerManager.remove(fake);
        com.sakurakugu.archweaver.entity.TargetListSync.refresh(server);
        FakePlayerMenuOpener.openMannequinInventory(viewer, mannequin.getUUID());
    }

    private static void toPlayer(ServerPlayer viewer, Mannequin mannequin) {
        var server = viewer.level().getServer();
        GameProfile profile = mannequin.getProfile().partialProfile();
        var snapshot = MannequinManager.data(server).playerSnapshot(mannequin.getUUID());
        Vec2 rotation = new Vec2(mannequin.getXRot(), mannequin.getYRot());
        FakeServerPlayer fake = snapshot.isPresent()
            ? FakePlayerManager.spawnFromPlayerData(server, (ServerLevel) mannequin.level(), profile, mannequin.position(), rotation,
                FakePlayerPersistence.readSavedGameType(server, snapshot.get()), false, snapshot.get())
            : FakePlayerManager.spawn(server, (ServerLevel) mannequin.level(), profile, mannequin.position(), rotation, GameType.CREATIVE, false);
        // 从保存的玩家快照恢复背包后，再应用玩偶期间修改过的六个装备槽。
        for (EquipmentSlot slot : EQUIPMENT) fake.setItemSlot(slot, mannequin.getItemBySlot(slot).copy());
        ((AvatarModelParts) fake).archweaver$setModelParts(((AvatarModelParts) mannequin).archweaver$modelParts());
        fake.actions().stop();
        FakePlayerPersistence.track(fake);
        MannequinManager.remove(server, mannequin.getUUID());
        com.sakurakugu.archweaver.entity.TargetListSync.refresh(server);
        FakePlayerMenuOpener.openInventory(viewer, fake);
    }
}
