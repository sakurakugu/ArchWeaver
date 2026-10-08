package com.sakurakugu.archweaver.persistence;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sakurakugu.archweaver.ArchWeaverMod;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.nbt.CompoundTag;

/** 保存玩偶登记信息和 ArchWeaver 专属姿势设置。 */
public final class MannequinSavedData extends SavedData {
    public static final Codec<Angles> ANGLES_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.FLOAT.fieldOf("x").forGetter(Angles::x),
        Codec.FLOAT.fieldOf("y").forGetter(Angles::y),
        Codec.FLOAT.fieldOf("z").forGetter(Angles::z)
    ).apply(instance, Angles::new));
    public static final Codec<Look> LOOK_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.FLOAT.fieldOf("yaw").forGetter(Look::viewYaw),
        Codec.FLOAT.fieldOf("pitch").forGetter(Look::pitch),
        Codec.FLOAT.fieldOf("body_yaw").forGetter(Look::bodyYaw),
        Codec.BOOL.fieldOf("body_follows_head").forGetter(Look::bodyFollowsHead)
    ).apply(instance, Look::new));
    public static final Codec<Record> RECORD_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.CODEC.fieldOf("uuid").forGetter(Record::uuid),
        Codec.STRING.fieldOf("name").forGetter(Record::name),
        Codec.STRING.fieldOf("dimension").forGetter(Record::dimension),
        Codec.DOUBLE.fieldOf("x").forGetter(Record::x),
        Codec.DOUBLE.fieldOf("y").forGetter(Record::y),
        Codec.DOUBLE.fieldOf("z").forGetter(Record::z),
        LOOK_CODEC.fieldOf("look").forGetter(Record::look),
        Codec.STRING.fieldOf("pose").forGetter(Record::pose),
        Codec.BOOL.fieldOf("immovable").forGetter(Record::immovable),
        Codec.BOOL.fieldOf("biological_behavior").forGetter(Record::biologicalBehavior),
        ResolvableProfile.CODEC.fieldOf("profile").forGetter(Record::profile),
        Codec.INT.fieldOf("model_customisation").forGetter(Record::modelCustomisation),
        ANGLES_CODEC.fieldOf("left_arm").forGetter(Record::leftArm),
        ANGLES_CODEC.fieldOf("right_arm").forGetter(Record::rightArm),
        ANGLES_CODEC.fieldOf("left_leg").forGetter(Record::leftLeg),
        ANGLES_CODEC.fieldOf("right_leg").forGetter(Record::rightLeg)
    ).apply(instance, Record::new));
    public static final Codec<MannequinSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        RECORD_CODEC.listOf().fieldOf("mannequins").forGetter(data -> data.records()),
        Codec.unboundedMap(UUIDUtil.STRING_CODEC, CompoundTag.CODEC).fieldOf("entities").forGetter(data -> data.entitySnapshots),
        Codec.unboundedMap(UUIDUtil.STRING_CODEC, CompoundTag.CODEC).fieldOf("players").forGetter(data -> data.playerSnapshots)
    ).apply(instance, MannequinSavedData::new));
    public static final SavedDataType<MannequinSavedData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(ArchWeaverMod.MOD_ID, "mannequins"),
        MannequinSavedData::new, CODEC, null);

    private final Map<UUID, Record> records = new LinkedHashMap<>();
    private final Map<UUID, CompoundTag> entitySnapshots = new LinkedHashMap<>();
    private final Map<UUID, CompoundTag> playerSnapshots = new LinkedHashMap<>();

    public MannequinSavedData() { }

    private MannequinSavedData(List<Record> records, Map<UUID, CompoundTag> entities, Map<UUID, CompoundTag> players) {
        records.forEach(record -> this.records.put(record.uuid(), record));
        entitySnapshots.putAll(entities);
        playerSnapshots.putAll(players);
    }

    public List<Record> records() { return List.copyOf(records.values()); }
    public Optional<Record> find(UUID id) { return Optional.ofNullable(records.get(id)); }
    public Optional<CompoundTag> entitySnapshot(UUID id) { return Optional.ofNullable(entitySnapshots.get(id)).map(CompoundTag::copy); }
    public Optional<CompoundTag> playerSnapshot(UUID id) { return Optional.ofNullable(playerSnapshots.get(id)).map(CompoundTag::copy); }
    public void putEntitySnapshot(UUID id, CompoundTag snapshot) { entitySnapshots.put(id, snapshot.copy()); setDirty(); }
    public void putPlayerSnapshot(UUID id, CompoundTag snapshot) { playerSnapshots.put(id, snapshot.copy()); setDirty(); }
    public void put(Record record) {
        if (!record.equals(records.put(record.uuid(), record))) setDirty();
    }
    public boolean remove(UUID id) {
        if (records.remove(id) == null) return false;
        entitySnapshots.remove(id);
        playerSnapshots.remove(id);
        setDirty();
        return true;
    }

    public record Angles(float x, float y, float z) {
        public static final Angles ZERO = new Angles(0, 0, 0);
        public Angles {
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
                || Math.abs(x) > 360 || Math.abs(y) > 360 || Math.abs(z) > 360) {
                throw new IllegalArgumentException("玩偶肢体角度非法");
            }
        }
    }

    /** 视角与朝向：{@code viewYaw} 是视角偏航角，{@code bodyYaw} 是身体偏航角。 */
    public record Look(float viewYaw, float pitch, float bodyYaw, boolean bodyFollowsHead) {
        public Look {
            if (!Float.isFinite(viewYaw) || !Float.isFinite(pitch) || !Float.isFinite(bodyYaw)) {
                throw new IllegalArgumentException("玩偶视角非法");
            }
        }

        /** 生成的玩偶默认面朝登记朝向，俯仰角和头身联动都保持默认值。 */
        public static Look of(float yaw) {
            return new Look(yaw, 0.0F, yaw, false);
        }
    }

    public record Record(UUID uuid, String name, String dimension, double x, double y, double z, Look look,
                         String pose, boolean immovable, boolean biologicalBehavior, ResolvableProfile profile, int modelCustomisation, Angles leftArm,
                         Angles rightArm, Angles leftLeg, Angles rightLeg) {
        public Record {
            if (name.isBlank() || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("玩偶登记信息非法");
            }
        }
        public static Record create(UUID id, String name, String dimension, double x, double y, double z, float yaw) {
            return new Record(id, name, dimension, x, y, z, Look.of(yaw), "STANDING", true, false,
                ResolvableProfile.createUnresolved(name),
                127,
                Angles.ZERO, Angles.ZERO, Angles.ZERO, Angles.ZERO);
        }
    }
}
