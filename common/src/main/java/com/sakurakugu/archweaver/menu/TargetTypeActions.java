package com.sakurakugu.archweaver.menu;

import com.mojang.authlib.GameProfile;
import com.sakurakugu.archweaver.entity.AvatarModelParts;
import com.sakurakugu.archweaver.entity.FakePlayerAlias;
import com.sakurakugu.archweaver.entity.MannequinIdentity;
import com.sakurakugu.archweaver.entity.TargetListSync;
import com.sakurakugu.archweaver.entity.FakePlayerManager;
import com.sakurakugu.archweaver.entity.FakePlayerPossession;
import com.sakurakugu.archweaver.entity.FakeServerPlayer;
import com.sakurakugu.archweaver.entity.MannequinLook;
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
        try {
            var profile = ResolvableProfile.createResolved(fake.getGameProfile());
            ((MannequinInvoker) mannequin).archweaver$setProfile(profile);
            MannequinManager.setAlias(server, mannequin.getUUID(), fake.alias());
            int mask = ((AvatarModelParts) fake).archweaver$modelParts();
            ((AvatarModelParts) mannequin).archweaver$setModelParts(mask);
            // 视角、身体朝向和头身联动跟着迁移，避免切回玩偶模式后朝向重置。
            ((MannequinLook) mannequin).archweaver$setBodyFollowsHead(fake.actions().bodyFollowsHead());
            MannequinManager.setBodyRotation(server, mannequin.getUUID(), Math.round(fake.yBodyRot));
            MannequinManager.setViewRotation(server, mannequin.getUUID(), Math.round(fake.getXRot()), Math.round(fake.getYRot()));
            for (EquipmentSlot slot : EQUIPMENT) mannequin.setItemSlot(slot, fake.getItemBySlot(slot).copy());
            var old = MannequinManager.data(server).find(mannequin.getUUID()).orElseThrow();
            MannequinManager.data(server).put(new MannequinSavedData.Record(old.uuid(), old.name(), old.dimension(), old.x(), old.y(), old.z(),
                old.look(), old.pose(), old.immovable(), old.biologicalBehavior(), profile, mask,
                old.leftArm(), old.rightArm(), old.leftLeg(), old.rightLeg()));
            MannequinManager.data(server).putPlayerSnapshot(mannequin.getUUID(), snapshot);
            MannequinManager.capture(server, mannequin);
        } catch (RuntimeException exception) {
            // 提交前出错时撤销新玩偶，原玩家的背包和身份仍完整保留。
            MannequinManager.remove(server, mannequin.getUUID());
            throw exception;
        }
        // 背包已移交到玩偶快照，退出保存的 playerdata 不再保留第二份物品。
        fake.getInventory().clearContent();
        fake.getEnderChestInventory().clearContent();
        FakePlayerManager.remove(fake);
        TargetListSync.refresh(server);
        FakePlayerMenuOpener.openMannequinInventory(viewer, mannequin.getUUID());
    }

    private static void toPlayer(ServerPlayer viewer, Mannequin mannequin) {
        var server = viewer.level().getServer();
        var record = MannequinManager.data(server).find(mannequin.getUUID()).orElseThrow();
        GameProfile profile = MannequinIdentity.playerProfile(
            mannequin.getProfile().partialProfile(), record.name());
        var snapshot = MannequinManager.data(server).playerSnapshot(mannequin.getUUID());
        // 原版描述可能由命令写入，先校验，避免生成玩家后才因别名无效而中断。
        String alias = FakePlayerAlias.normalize(MannequinManager.alias(mannequin));
        if (FakePlayerPersistence.data(server).residents().stream().anyMatch(resident ->
                resident.uuid().equals(profile.id()) || resident.name().equalsIgnoreCase(profile.name()))) {
            throw new IllegalArgumentException("duplicate");
        }
        // 没有原玩家快照的玩偶不能接管同 UUID 的离线玩家数据。
        if (snapshot.isEmpty() && FakePlayerPersistence.hasStoredPlayerData(server, profile.id())) {
            throw new IllegalArgumentException("duplicate");
        }
        Vec2 rotation = new Vec2(mannequin.getXRot(), mannequin.getYRot());
        FakeServerPlayer fake = snapshot.isPresent()
            ? FakePlayerManager.spawnFromPlayerData(server, (ServerLevel) mannequin.level(), profile, mannequin.position(), rotation,
                FakePlayerPersistence.readSavedGameType(server, snapshot.get()), false, snapshot.get())
            : FakePlayerManager.spawn(server, (ServerLevel) mannequin.level(), profile, mannequin.position(), rotation, GameType.CREATIVE, false);
        try {
            // 从保存的玩家快照恢复背包后，再应用玩偶期间修改过的六个装备槽。
            for (EquipmentSlot slot : EQUIPMENT) fake.setItemSlot(slot, mannequin.getItemBySlot(slot).copy());
            ((AvatarModelParts) fake).archweaver$setModelParts(((AvatarModelParts) mannequin).archweaver$modelParts());
            fake.setAlias(alias);
            fake.actions().stop();
            // 玩偶期间编写的朝向带回假人：先定身体再定视角，保持两者之间的偏移。
            fake.actions().setBodyRotation(mannequin.yBodyRot);
            fake.actions().setViewRotation(mannequin.getXRot(), mannequin.getYRot());
            if (fake.actions().bodyFollowsHead() != ((MannequinLook) mannequin).archweaver$bodyFollowsHead()) {
                fake.actions().toggleBodyFollowsHead();
            }
            FakePlayerPersistence.data(server).migratePlayer(profile.id(), profile);
            FakePlayerPersistence.track(fake);
        } catch (RuntimeException exception) {
            // 玩偶与其快照仍持有原物品；清空临时玩家，退出保存也不能留下第二份。
            fake.getInventory().clearContent();
            fake.getEnderChestInventory().clearContent();
            FakePlayerManager.remove(fake);
            throw exception;
        }
        MannequinManager.remove(server, mannequin.getUUID());
        TargetListSync.refresh(server);
        FakePlayerMenuOpener.openInventory(viewer, fake);
    }
}
