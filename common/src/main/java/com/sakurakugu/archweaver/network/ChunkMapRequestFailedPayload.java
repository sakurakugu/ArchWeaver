package com.sakurakugu.archweaver.network;

import com.sakurakugu.archweaver.ArchWeaverMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 无权访问或维度不存在时终止等待，避免地图不断重试。 */
public record ChunkMapRequestFailedPayload(long requestId, String reason) implements CustomPacketPayload {
    public static final Type<ChunkMapRequestFailedPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(ArchWeaverMod.MOD_ID, "chunk_map_request_failed"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChunkMapRequestFailedPayload> STREAM_CODEC =
        CustomPacketPayload.codec(ChunkMapRequestFailedPayload::write, ChunkMapRequestFailedPayload::new);
    private ChunkMapRequestFailedPayload(RegistryFriendlyByteBuf buffer) { this(buffer.readVarLong(), buffer.readUtf(2048)); }
    private void write(RegistryFriendlyByteBuf buffer) { buffer.writeVarLong(requestId); buffer.writeUtf(reason, 2048); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
