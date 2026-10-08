package com.sakurakugu.archweaver.entity;

import com.mojang.authlib.GameProfile;
import com.sakurakugu.archweaver.mixin.MannequinInvoker;
import com.sakurakugu.archweaver.persistence.MannequinSavedData;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.phys.Vec3;
import com.sakurakugu.archweaver.network.MannequinAnglesPayload;
import com.sakurakugu.archweaver.platform.PlatformNetworking;
import net.minecraft.server.level.ServerPlayer;
import com.sakurakugu.archweaver.ArchWeaverMod;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.core.BlockPos;

/** 管理原版 mannequin 实体及其登记生命周期。 */
public final class MannequinManager {
    public static final String MANAGED_TAG = "archweaver.mannequin";
    private MannequinManager() { }

    public static MannequinSavedData data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(MannequinSavedData.TYPE);
    }

    public static Mannequin spawn(MinecraftServer server, ServerLevel level, String name, Vec3 position, float yaw) {
        if (!name.matches("[A-Za-z0-9_-]{1,16}")) throw new IllegalArgumentException("invalid_name");
        if (find(server, name).isPresent()) throw new IllegalArgumentException("duplicate");
        Mannequin entity = EntityType.MANNEQUIN.create(level, EntitySpawnReason.COMMAND);
        if (entity == null) throw new IllegalStateException("无法创建原版 mannequin");
        UUID uuid = UUID.randomUUID();
        entity.setUUID(uuid);
        entity.addTag(MANAGED_TAG);
        entity.setCustomName(net.minecraft.network.chat.Component.literal(name));
        entity.setCustomNameVisible(true);
        entity.snapTo(position.x, position.y, position.z, yaw, 0);
        ((MannequinInvoker) entity).archweaver$setProfile(ResolvableProfile.createResolved(new GameProfile(net.minecraft.core.UUIDUtil.createOfflinePlayerUUID(name), name)));
        ((MannequinInvoker) entity).archweaver$setImmovable(true);
        entity.setNoGravity(true);
        if (!level.addFreshEntity(entity)) throw new IllegalStateException("玩偶未能加入世界");
        var initial = MannequinSavedData.Record.create(uuid, name,
            level.dimension().identifier().toString(), position.x, position.y, position.z, yaw);
        data(server).put(new MannequinSavedData.Record(initial.uuid(), initial.name(), initial.dimension(), initial.x(),
            initial.y(), initial.z(), initial.yaw(), initial.pose(), initial.immovable(), initial.biologicalBehavior(),
            entity.getProfile(), initial.modelCustomisation(), initial.leftArm(), initial.rightArm(), initial.leftLeg(), initial.rightLeg()));
        return entity;
    }

    public static List<MannequinSavedData.Record> registered(MinecraftServer server) {
        return data(server).records().stream().sorted(Comparator.comparing(
            MannequinSavedData.Record::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    public static Optional<Mannequin> loaded(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof Mannequin mannequin) return Optional.of(mannequin);
        }
        return Optional.empty();
    }

    public static Optional<MannequinSavedData.Record> find(MinecraftServer server, String name) {
        return data(server).records().stream().filter(record -> record.name().equalsIgnoreCase(name)).findFirst();
    }

    public static Mannequin load(MinecraftServer server, UUID id) {
        Mannequin current = loaded(server, id).orElse(null);
        if (current != null) return current;
        MannequinSavedData.Record record = data(server).find(id).orElseThrow(() -> new IllegalArgumentException("not_found"));
        ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(
            net.minecraft.core.registries.Registries.DIMENSION,
            net.minecraft.resources.Identifier.parse(record.dimension())));
        if (level == null) throw new IllegalArgumentException("dimension");
        // 先加载登记位置的区块，避免复制仍保存在该区块中的同 UUID 实体。
        level.getChunk(BlockPos.containing(record.x(), record.y(), record.z()));
        current = loaded(server, id).orElse(null);
        if (current != null) return current;
        Mannequin entity = EntityType.MANNEQUIN.create(level, EntitySpawnReason.COMMAND);
        if (entity == null) throw new IllegalStateException("无法创建原版 mannequin");
        data(server).entitySnapshot(id).ifPresent(snapshot -> {
            try (ProblemReporter.ScopedCollector collector = new ProblemReporter.ScopedCollector(ArchWeaverMod.LOGGER)) {
                entity.load(TagValueInput.create(collector, server.registryAccess(), snapshot));
            }
        });
        entity.setUUID(record.uuid());
        entity.addTag(MANAGED_TAG);
        entity.setCustomName(net.minecraft.network.chat.Component.literal(record.name()));
        entity.setCustomNameVisible(true);
        entity.snapTo(record.x(), record.y(), record.z(), record.yaw(), 0);
        entity.setPose(Pose.valueOf(record.pose()));
        ((MannequinInvoker) entity).archweaver$setProfile(record.profile());
        ((AvatarModelParts) entity).archweaver$setModelParts(record.modelCustomisation());
        ((MannequinInvoker) entity).archweaver$setImmovable(record.immovable());
        entity.setNoGravity(!record.biologicalBehavior());
        if (!level.addFreshEntity(entity)) throw new IllegalStateException("玩偶未能加入世界");
        return entity;
    }

    public static boolean unload(MinecraftServer server, UUID id) {
        Mannequin entity = loaded(server, id).orElse(null);
        if (entity == null) return false;
        capture(server, entity);
        MannequinSavedData.Record old = data(server).find(id).orElse(null);
        if (old != null) data(server).put(new MannequinSavedData.Record(old.uuid(), old.name(),
            entity.level().dimension().identifier().toString(), entity.getX(), entity.getY(), entity.getZ(),
            entity.getYRot(), entity.getPose().name(), ((MannequinInvoker) entity).archweaver$getImmovable(),
            !entity.isNoGravity(), old.profile(), ((AvatarModelParts) entity).archweaver$modelParts(),
            old.leftArm(), old.rightArm(), old.leftLeg(), old.rightLeg()));
        entity.discard();
        return true;
    }

    /** 记录原版实体数据，使手动卸载后装备和生命状态能够完整恢复。 */
    public static void capture(MinecraftServer server, Mannequin entity) {
        try (ProblemReporter.ScopedCollector collector = new ProblemReporter.ScopedCollector(entity.problemPath(), ArchWeaverMod.LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(collector, server.registryAccess());
            entity.saveWithoutId(output);
            data(server).putEntitySnapshot(entity.getUUID(), output.buildResult());
        }
        data(server).find(entity.getUUID()).ifPresent(old -> data(server).put(copy(old, entity,
            ((MannequinInvoker) entity).archweaver$getImmovable(), !entity.isNoGravity(), entity.getPose().name(),
            ((AvatarModelParts) entity).archweaver$modelParts(), old.leftArm(), old.rightArm(), old.leftLeg(), old.rightLeg())));
    }

    public static void captureAll(MinecraftServer server) {
        for (MannequinSavedData.Record record : data(server).records()) {
            loaded(server, record.uuid()).ifPresent(entity -> capture(server, entity));
        }
    }

    public static void setImmovable(MinecraftServer server, UUID id, boolean value) {
        Mannequin entity = loaded(server, id).orElseThrow(() -> new IllegalArgumentException("not_loaded"));
        ((MannequinInvoker) entity).archweaver$setImmovable(value);
        MannequinSavedData.Record old = data(server).find(id).orElseThrow();
        data(server).put(copy(old, entity, value, old.biologicalBehavior(), old.pose(), old.modelCustomisation(), old.leftArm(), old.rightArm(), old.leftLeg(), old.rightLeg()));
    }

    public static void setBiologicalBehavior(MinecraftServer server, UUID id, boolean value) {
        Mannequin entity = loaded(server, id).orElseThrow(() -> new IllegalArgumentException("not_loaded"));
        entity.setNoGravity(!value);
        MannequinSavedData.Record old = data(server).find(id).orElseThrow();
        data(server).put(copy(old, entity, old.immovable(), value, old.pose(), old.modelCustomisation(), old.leftArm(), old.rightArm(), old.leftLeg(), old.rightLeg()));
    }

    public static void setPose(MinecraftServer server, UUID id, Pose pose) {
        Mannequin entity = loaded(server, id).orElseThrow(() -> new IllegalArgumentException("not_loaded"));
        entity.setPose(pose);
        MannequinSavedData.Record old = data(server).find(id).orElseThrow();
        data(server).put(copy(old, entity, old.immovable(), old.biologicalBehavior(), pose.name(), old.modelCustomisation(), old.leftArm(), old.rightArm(), old.leftLeg(), old.rightLeg()));
    }

    public static void setModelPart(MinecraftServer server, UUID id, PlayerModelPart part, boolean shown) {
        Mannequin entity = loaded(server, id).orElseThrow(() -> new IllegalArgumentException("not_loaded"));
        int mask = ((AvatarModelParts) entity).archweaver$modelParts();
        mask = shown ? mask | part.getMask() : mask & ~part.getMask();
        ((AvatarModelParts) entity).archweaver$setModelParts(mask);
        MannequinSavedData.Record old = data(server).find(id).orElseThrow();
        data(server).put(copy(old, entity, old.immovable(), old.biologicalBehavior(), old.pose(), mask, old.leftArm(), old.rightArm(), old.leftLeg(), old.rightLeg()));
    }

    public static void setLimbAngles(MinecraftServer server, UUID id, int limb, float x, float y, float z) {
        Mannequin entity = loaded(server, id).orElseThrow(() -> new IllegalArgumentException("not_loaded"));
        MannequinSavedData.Record old = data(server).find(id).orElseThrow();
        MannequinSavedData.Angles angles = new MannequinSavedData.Angles(x, y, z);
        if (limb < 0 || limb > 3) throw new IllegalArgumentException("limb");
        data(server).put(copy(old, entity, old.immovable(), old.biologicalBehavior(), old.pose(), old.modelCustomisation(),
            limb == 0 ? angles : old.leftArm(), limb == 1 ? angles : old.rightArm(),
            limb == 2 ? angles : old.leftLeg(), limb == 3 ? angles : old.rightLeg()));
    }

    public static MannequinAnglesPayload anglesPayload(MinecraftServer server, UUID id) {
        MannequinSavedData.Record record = data(server).find(id).orElseThrow();
        return new MannequinAnglesPayload(id, record.leftArm(), record.rightArm(), record.leftLeg(), record.rightLeg());
    }

    /** 把所有已登记玩偶的当前姿势发送给一个观察者。 */
    public static void syncAnglesTo(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        for (MannequinSavedData.Record record : data(server).records()) {
            PlatformNetworking.sendToPlayer(player, new MannequinAnglesPayload(record.uuid(), record.leftArm(),
                record.rightArm(), record.leftLeg(), record.rightLeg()));
        }
    }

    private static MannequinSavedData.Record copy(MannequinSavedData.Record old, Mannequin entity,
                                                   boolean immovable, boolean biological, String pose, int mask,
                                                   MannequinSavedData.Angles leftArm, MannequinSavedData.Angles rightArm,
                                                   MannequinSavedData.Angles leftLeg, MannequinSavedData.Angles rightLeg) {
        return new MannequinSavedData.Record(old.uuid(), old.name(), entity.level().dimension().identifier().toString(),
            entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), pose, immovable, biological,
            old.profile(), mask, leftArm, rightArm, leftLeg, rightLeg);
    }

    public static boolean remove(MinecraftServer server, UUID id) {
        return remove(server, id, false);
    }

    /** 删除登记；永久删除时可选择先把玩偶装备栏中的物品掉落到实体位置。 */
    public static boolean remove(MinecraftServer server, UUID id, boolean dropItems) {
        if (dropItems) {
            Mannequin entity = loaded(server, id).orElse(null);
            if (entity == null) {
                entity = load(server, id);
            }
            dropEquipment(entity);
        }
        unload(server, id);
        return data(server).remove(id);
    }

    private static void dropEquipment(Mannequin entity) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HAND && slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
                continue;
            }
            var stack = entity.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            entity.setItemSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
            entity.spawnAtLocation((ServerLevel) entity.level(), stack.copy());
        }
    }
}
