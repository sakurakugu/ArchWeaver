package com.sakurakugu.archweaver.network;

import com.sakurakugu.archweaver.ArchWeaverMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 保存回执独立于普通刷新，其他玩家的更新不能误确认本地草稿。 */
public record ChunkMapApplyResultPayload(long submissionId, String dimension, boolean successful,
                                         boolean conflict, String reason) implements CustomPacketPayload {
    public static final Type<ChunkMapApplyResultPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(ArchWeaverMod.MOD_ID, "chunk_map_apply_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChunkMapApplyResultPayload> STREAM_CODEC =
        CustomPacketPayload.codec(ChunkMapApplyResultPayload::write, ChunkMapApplyResultPayload::new);

    private ChunkMapApplyResultPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readVarLong(), buffer.readUtf(256), buffer.readBoolean(), buffer.readBoolean(), buffer.readUtf(2048));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarLong(submissionId);
        buffer.writeUtf(dimension, 256);
        buffer.writeBoolean(successful);
        buffer.writeBoolean(conflict);
        buffer.writeUtf(reason, 2048);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
