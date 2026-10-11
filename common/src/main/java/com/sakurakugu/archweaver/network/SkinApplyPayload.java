package com.sakurakugu.archweaver.network;

import com.sakurakugu.archweaver.ArchWeaverMod;
import com.sakurakugu.archweaver.skin.SkinSavedData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 客户端请求给假人或玩偶更换皮肤。 */
public record SkinApplyPayload(SkinSavedData.TargetType targetType, String targetName, String sourceName,
                               byte[] png, String model, boolean clear, String fileName) implements CustomPacketPayload {
    public SkinApplyPayload(SkinSavedData.TargetType targetType, String targetName, String sourceName,
                            byte[] png, String model, boolean clear) {
        this(targetType, targetName, sourceName, png, model, clear, "");
    }
    public SkinApplyPayload { fileName = SkinSavedData.sanitizeFileName(fileName); }
    public static final Type<SkinApplyPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ArchWeaverMod.MOD_ID, "skin_apply"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SkinApplyPayload> STREAM_CODEC = StreamCodec.of(
        (b, value) -> { b.writeEnum(value.targetType()); b.writeUtf(value.targetName(), 64); b.writeUtf(value.sourceName(), 16); b.writeByteArray(value.png().length > 128 * 1024 ? new byte[0] : value.png()); b.writeUtf(value.model(), 16); b.writeBoolean(value.clear()); b.writeUtf(value.fileName(), 128); },
        b -> new SkinApplyPayload(b.readEnum(SkinSavedData.TargetType.class), b.readUtf(64), b.readUtf(16), b.readByteArray(128 * 1024), b.readUtf(16), b.readBoolean(), b.readUtf(128)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
