package com.sakurakugu.archweaver.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.sakurakugu.archweaver.chunkloading.ChunkKey;
import com.sakurakugu.archweaver.chunkloading.FakePlayerLoadMode;
import org.junit.jupiter.api.Test;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;

class ChunkMapSnapshotPayloadTest {
    @Test
    void nameTagSyncTransmitsIdentityChineseAliasClearAndBothOrders() {
        UUID playerId = UUID.randomUUID();
        for (String alias : List.of("矿场 一号", "矿".repeat(32), "")) {
            for (boolean aliasFirst : List.of(false, true)) {
                var original = new FakePlayerAliasPayload(playerId, alias, aliasFirst);
                var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
                try {
                    FakePlayerAliasPayload.STREAM_CODEC.encode(buffer, original);
                    assertEquals(original, FakePlayerAliasPayload.STREAM_CODEC.decode(buffer));
                } finally {
                    buffer.release();
                }
            }
        }
    }

    @Test
    void snapshotTransmitsChineseAliasSeparatelyFromRealName() {
        var fake = new ChunkMapSnapshotPayload.FakePlayerView(UUID.randomUUID(), "robot-1", "矿场 一号",
            "minecraft:overworld", 0, 64, 0, 0.0F, true, false, FakePlayerLoadMode.PLAYER, 0,
            false, "", 0, 0, 0);
        var original = new ChunkMapSnapshotPayload(ChunkMapOpenTarget.NONE, 0, 32, 1L, false,
            "minecraft:overworld", 0, 0, List.of(), List.of(), List.of(fake));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            ChunkMapSnapshotPayload.STREAM_CODEC.encode(buffer, original);
            var decoded = ChunkMapSnapshotPayload.STREAM_CODEC.decode(buffer);
            assertEquals(original, decoded);
            assertEquals("robot-1", decoded.fakePlayers().getFirst().name());
            assertEquals("矿场 一号", decoded.fakePlayers().getFirst().alias());
        } finally {
            buffer.release();
        }
    }

    @Test
    void aliasRequestTransmitsChineseAndEmptyAlias() {
        for (String alias : List.of("矿场一号", "")) {
            var original = new SetFakePlayerAliasPayload(7, alias);
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            try {
                SetFakePlayerAliasPayload.STREAM_CODEC.encode(buffer, original);
                assertEquals(original, SetFakePlayerAliasPayload.STREAM_CODEC.decode(buffer));
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void fakePlayerViewOnlyLoadsChunksInsideItsActiveRange() {
        var active = view(true, "minecraft:overworld", 10, -5, 2);

        assertTrue(active.loadsChunk("minecraft:overworld", 8, -7));
        assertTrue(active.loadsChunk("minecraft:overworld", 12, -3));
        assertFalse(active.loadsChunk("minecraft:overworld", 13, -5));
        assertFalse(active.loadsChunk("minecraft:the_nether", 10, -5));
        assertFalse(view(false, "", 0, 0, 0).loadsChunk("minecraft:overworld", 0, 0));
    }

    @Test
    void automaticFakePlayerViewUsesItsSynchronizedPlayerSimulationDistance() {
        var automatic = new ChunkMapSnapshotPayload.FakePlayerView(UUID.randomUUID(), "Loader", "",
            "minecraft:overworld", -1, 64, -17, 0.0F, true, false, FakePlayerLoadMode.PLAYER, 3,
            false, "", 0, 0, 0);

        assertTrue(automatic.loadsChunk("minecraft:overworld", -4, -4));
        assertTrue(automatic.loadsChunk("minecraft:overworld", -1, -2));
        assertFalse(automatic.loadsChunk("minecraft:overworld", 3, -2));
        assertFalse(automatic.loadsChunk("minecraft:the_nether", -1, -2));
    }

    @Test
    void regionsAreSkippedOnlyWhenRevisionAndDimensionBothMatch() {
        assertTrue(ChunkMapSnapshotPayload.canSkipRegions(7L, "minecraft:overworld", 7L, "minecraft:overworld"));
        assertFalse(ChunkMapSnapshotPayload.canSkipRegions(7L, "minecraft:overworld", 8L, "minecraft:overworld"));
        // 同一份 revision 下换维度，区域列表是按维度过滤的，不能沿用
        assertFalse(ChunkMapSnapshotPayload.canSkipRegions(7L, "minecraft:overworld", 7L, "minecraft:the_nether"));
        assertFalse(ChunkMapSnapshotPayload.canSkipRegions(RequestChunkMapPayload.NO_REVISION, "",
            7L, "minecraft:overworld"));
    }

    @Test
    void skippingRegionsKeepsThePreviousChunkLists() {
        var chunk = ChunkKey.pack(4, -9);
        var region = new ChunkMapSnapshotPayload.RegionView(UUID.randomUUID(), "main",
            "minecraft:overworld", true, Set.of(chunk));
        var previous = payload(3L, List.of(region));
        // 服务端说区域没变时列表是空的，客户端得把上一份拼回去
        var incremental = payload(4L, List.of());

        var merged = incremental.withPreviousRegions(previous);

        assertEquals(previous.regions(), merged.regions());
        assertEquals(4L, merged.revision());
        assertFalse(merged.regionsUnchanged());
        assertEquals(previous.playerChunkX(), merged.playerChunkX());
    }

    private static ChunkMapSnapshotPayload payload(long revision,
                                                   List<ChunkMapSnapshotPayload.RegionView> regions) {
        return new ChunkMapSnapshotPayload(ChunkMapOpenTarget.NONE, 0, 32, revision, true,
            "minecraft:overworld", 12, -3, regions, List.of(), List.of());
    }

    private static ChunkMapSnapshotPayload.FakePlayerView view(boolean active, String dimension,
                                                                int chunkX, int chunkZ, int distance) {
        return new ChunkMapSnapshotPayload.FakePlayerView(UUID.randomUUID(), "Loader", "", "minecraft:overworld",
            0, 64, 0, 0.0F, true, false, FakePlayerLoadMode.DOLL, distance,
            active, dimension, chunkX, chunkZ, distance);
    }
}
