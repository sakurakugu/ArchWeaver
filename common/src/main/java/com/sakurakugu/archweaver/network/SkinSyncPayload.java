package com.sakurakugu.archweaver.network;

import com.sakurakugu.archweaver.ArchWeaverMod;
import com.sakurakugu.archweaver.skin.SkinSavedData;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 服务端向客户端同步皮肤覆盖。 */
public record SkinSyncPayload(UUID target, SkinSavedData.TargetType targetType, SkinSavedData.Kind kind,
                              List<SkinSavedData.PropertyValue> properties, byte[] png, String model, boolean clear,
                              UUID sourceUuid, String sourceName, String fileName)
    implements CustomPacketPayload {
    public static final Type<SkinSyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ArchWeaverMod.MOD_ID, "skin_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SkinSyncPayload> STREAM_CODEC = CustomPacketPayload.codec(SkinSyncPayload::write, SkinSyncPayload::new);
    private SkinSyncPayload(RegistryFriendlyByteBuf b) {
        this(b.readUUID(), b.readEnum(SkinSavedData.TargetType.class), b.readEnum(SkinSavedData.Kind.class), readProperties(b), b.readByteArray(128 * 1024), b.readUtf(16), b.readBoolean(), b.readUUID(), b.readUtf(16), b.readUtf(128));
    }
    private static List<SkinSavedData.PropertyValue> readProperties(RegistryFriendlyByteBuf b) {
        int count = b.readVarInt(); java.util.ArrayList<SkinSavedData.PropertyValue> values = new java.util.ArrayList<>();
        for (int i = 0; i < Math.min(count, 16); i++) values.add(new SkinSavedData.PropertyValue(b.readUtf(32), b.readUtf(8192), b.readUtf(8192)));
        return values;
    }
    private void write(RegistryFriendlyByteBuf b) { b.writeUUID(target); b.writeEnum(targetType); b.writeEnum(kind); b.writeVarInt(properties.size()); properties.forEach(v -> { b.writeUtf(v.name(), 32); b.writeUtf(v.value(), 8192); b.writeUtf(v.signature(), 8192); }); b.writeByteArray(png); b.writeUtf(model, 16); b.writeBoolean(clear); b.writeUUID(sourceUuid); b.writeUtf(sourceName, 16); b.writeUtf(fileName, 128); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
