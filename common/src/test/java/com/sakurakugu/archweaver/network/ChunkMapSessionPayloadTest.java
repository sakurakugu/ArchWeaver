package com.sakurakugu.archweaver.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ChunkMapSessionPayloadTest {
    private static <T> void roundTrip(StreamCodec<RegistryFriendlyByteBuf, T> codec, T payload) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            codec.encode(buffer, payload);
            assertEquals(payload, codec.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }

    @Test void dimensionAndCorrelationsSurviveSerialization() {
        roundTrip(RequestChunkMapPayload.STREAM_CODEC,
            new RequestChunkMapPayload(ChunkMapOpenTarget.MAP, 17, "minecraft:overworld", "minecraft:the_nether", 91));
        roundTrip(ChunkMapSnapshotPayload.STREAM_CODEC,
            new ChunkMapSnapshotPayload(ChunkMapOpenTarget.MAP, 7, 32, 17, false, "minecraft:the_nether",
                -3, -5, List.of(), List.of(), List.of(), 91));
        roundTrip(ApplyChunkLoadEditsPayload.STREAM_CODEC,
            new ApplyChunkLoadEditsPayload(17, "minecraft:the_nether", List.of(
                new ApplyChunkLoadEditsPayload.Edit(ApplyChunkLoadEditsPayload.Action.CREATE_REGION,
                    new java.util.UUID(0, 1), "base", true, 0, List.of(-1L))), 92));
        roundTrip(ChunkLoaderActionPayload.STREAM_CODEC,
            new ChunkLoaderActionPayload(ChunkLoaderActionPayload.Action.RENAME, "base", "new_base", "minecraft:the_nether"));
        roundTrip(ChunkMapApplyResultPayload.STREAM_CODEC,
            new ChunkMapApplyResultPayload(92, "minecraft:the_nether", false, true, "快照版本冲突"));
        roundTrip(ChunkMapRequestFailedPayload.STREAM_CODEC, new ChunkMapRequestFailedPayload(91, "目标维度不存在"));
    }
}
