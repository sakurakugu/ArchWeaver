package com.sakurakugu.archweaver.skin;

import com.google.common.collect.ArrayListMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sakurakugu.archweaver.ArchWeaverMod;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** 保存目标的皮肤覆盖；覆盖记录只保存纹理，不改变目标身份。 */
public final class SkinSavedData extends SavedData {
    public enum TargetType { PLAYER, MANNEQUIN }
    public enum Kind { PROFILE, PNG }
    private static final Codec<PropertyValue> PROPERTY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("name").forGetter(PropertyValue::name),
        Codec.STRING.fieldOf("value").forGetter(PropertyValue::value),
        Codec.STRING.optionalFieldOf("signature", "").forGetter(PropertyValue::signature)
    ).apply(instance, PropertyValue::new));
    private static final Codec<Record> RECORD_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.CODEC.fieldOf("target").forGetter(Record::target),
        Codec.STRING.fieldOf("type").forGetter(value -> value.type().name()),
        Codec.STRING.fieldOf("kind").forGetter(value -> value.kind().name()),
        PROPERTY_CODEC.listOf().optionalFieldOf("properties", List.of()).forGetter(Record::properties),
        Codec.STRING.optionalFieldOf("png", "").forGetter(Record::png),
        Codec.STRING.optionalFieldOf("model", "").forGetter(Record::model),
        UUIDUtil.CODEC.fieldOf("source_uuid").forGetter(Record::sourceUuid),
        Codec.STRING.fieldOf("source_name").forGetter(Record::sourceName),
        Codec.STRING.fieldOf("file_name").forGetter(Record::fileName)
    ).apply(instance, (target, type, kind, properties, png, model, sourceUuid, sourceName, fileName) -> new Record(target,
        TargetType.valueOf(type), Kind.valueOf(kind), properties, png, model, sourceUuid, sourceName, fileName)));
    static final Codec<SkinSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        RECORD_CODEC.listOf().fieldOf("skins").forGetter(data -> List.copyOf(data.skins.values()))
    ).apply(instance, SkinSavedData::new));
    public static final SavedDataType<SkinSavedData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(ArchWeaverMod.MOD_ID, "skins"), SkinSavedData::new, CODEC, null);

    private final Map<String, Record> skins = new LinkedHashMap<>();
    public SkinSavedData() { }
    private SkinSavedData(List<Record> records) { records.forEach(record -> skins.put(key(record.target(), record.type()), record)); }
    public Optional<Record> find(UUID target, TargetType type) { return Optional.ofNullable(skins.get(key(target, type))); }
    public void put(Record record) { skins.put(key(record.target(), record.type()), record); setDirty(); }
    public boolean remove(UUID target, TargetType type) { if (skins.remove(key(target, type)) == null) return false; setDirty(); return true; }
    public List<Record> records() { return List.copyOf(skins.values()); }
    private static String key(UUID target, TargetType type) { return type.name() + ":" + target; }

    public record PropertyValue(String name, String value, String signature) {
        static PropertyValue from(Property value) { return new PropertyValue(value.name(), value.value(), value.signature() == null ? "" : value.signature()); }
        Property toProperty() { return signature.isEmpty() ? new Property(name, value) : new Property(name, value, signature); }
    }
    /** 文件名仅用于展示，绝不保存本地目录，并限制长度和控制字符。 */
    public static String sanitizeFileName(String value) {
        if (value == null) return "";
        String name = value.substring(Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\')) + 1);
        name = name.replaceAll("[\\p{Cntrl}§]", "").strip();
        return name.substring(0, Math.min(name.length(), 128));
    }

    public record Record(UUID target, TargetType type, Kind kind, List<PropertyValue> properties, String png, String model,
                         UUID sourceUuid, String sourceName, String fileName) {
        public Record {
            properties = List.copyOf(properties);
            png = png == null ? "" : png;
            model = model == null ? "" : model;
            sourceName = sourceName == null ? "" : sourceName;
            fileName = sanitizeFileName(fileName);
        }
        public static Record profile(UUID target, TargetType type, GameProfile profile) {
            return new Record(target, type, Kind.PROFILE, profile.properties().values().stream().map(PropertyValue::from).toList(), "", "",
                profile.id(), profile.name(), "");
        }
        public static Record png(UUID target, TargetType type, byte[] bytes, String model) {
            return png(target, type, bytes, model, "");
        }
        public static Record png(UUID target, TargetType type, byte[] bytes, String model, String fileName) {
            return new Record(target, type, Kind.PNG, List.of(), Base64.getEncoder().encodeToString(bytes), model, target, "", fileName);
        }
        public byte[] pngBytes() { return png.isEmpty() ? new byte[0] : Base64.getDecoder().decode(png); }
        public GameProfile profile(UUID id, String name) {
            ArrayListMultimap<String, Property> map = ArrayListMultimap.create();
            properties.forEach(value -> map.put(value.name(), value.toProperty()));
            return new GameProfile(id, name, new PropertyMap(map));
        }
    }
}
