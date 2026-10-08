package com.sakurakugu.archweaver.network;

import com.sakurakugu.archweaver.ArchWeaverMod;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 背包上方二态开关提交当前目标的类型转换。 */
public record TargetTypePayload(int containerId, UUID id, boolean mannequin) implements CustomPacketPayload {
    public static final Type<TargetTypePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ArchWeaverMod.MOD_ID, "target_type"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TargetTypePayload> STREAM_CODEC = CustomPacketPayload.codec(TargetTypePayload::write, TargetTypePayload::new);
    private TargetTypePayload(RegistryFriendlyByteBuf buffer) { this(buffer.readVarInt(), buffer.readUUID(), buffer.readBoolean()); }
    private void write(RegistryFriendlyByteBuf buffer) { buffer.writeVarInt(containerId); buffer.writeUUID(id); buffer.writeBoolean(mannequin); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
