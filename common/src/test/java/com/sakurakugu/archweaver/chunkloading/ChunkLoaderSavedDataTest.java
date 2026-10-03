package com.sakurakugu.archweaver.chunkloading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class ChunkLoaderSavedDataTest {
    private static final Identifier OVERWORLD = Identifier.withDefaultNamespace("overworld");

    @Test
    void regionsUseUuidAndCaseInsensitiveUniqueNames() {
        ChunkLoaderSavedData data = new ChunkLoaderSavedData();
        ManualLoadRegion region = region("Spawn");

        assertTrue(data.addRegion(region));
        assertFalse(data.addRegion(region("sPaWn")));
        assertEquals(region, data.region(region.id()).orElseThrow());
        assertEquals(region, data.region("SPAWN").orElseThrow());
        assertEquals(1, data.revision());
    }

    @Test
    void policiesAndRegionsRoundTrip() {
        ChunkLoaderSavedData original = new ChunkLoaderSavedData();
        ManualLoadRegion region = region("Spawn").withEnabled(false);
        original.addRegion(region);
        original.putPolicy(new FakePlayerLoadPolicy(UUID.randomUUID(), FakePlayerLoadMode.DOLL, 7));

        var json = ChunkLoaderSavedData.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        ChunkLoaderSavedData decoded = ChunkLoaderSavedData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

        assertEquals(original.revision(), decoded.revision());
        assertEquals(original.regions(), decoded.regions());
        assertEquals(original.policies(), decoded.policies());
    }

    @Test
    void codecRejectsDuplicateChunksAndDoesNotPersistRemovedMode() {
        ChunkLoaderSavedData original = new ChunkLoaderSavedData();
        original.addRegion(region("Spawn"));
        var json = ChunkLoaderSavedData.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        var region = json.getAsJsonObject().getAsJsonArray("manual_regions").get(0).getAsJsonObject();
        assertFalse(region.has("mode"));
        region.getAsJsonArray("chunks").add(region.getAsJsonArray("chunks").get(0));
        assertTrue(ChunkLoaderSavedData.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent());
    }

    @Test
    void codecRejectsDuplicateRegionUuid() {
        ChunkLoaderSavedData original = new ChunkLoaderSavedData();
        original.addRegion(region("Spawn"));
        var json = ChunkLoaderSavedData.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        json.getAsJsonObject().getAsJsonArray("manual_regions")
            .add(json.getAsJsonObject().getAsJsonArray("manual_regions").get(0).deepCopy());

        assertTrue(ChunkLoaderSavedData.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent());
    }

    @Test
    void snapshotsAreImmutableAndCanRestoreRevision() {
        ChunkLoaderSavedData data = new ChunkLoaderSavedData();
        data.addRegion(region("one"));
        ChunkLoaderSavedData.State state = data.snapshot();
        data.addRegion(region("two"));

        data.restore(state);

        assertEquals(1, data.revision());
        assertEquals(1, data.regions().size());
        assertThrows(UnsupportedOperationException.class, () -> state.regions().clear());
    }

    static ManualLoadRegion region(String name) {
        return new ManualLoadRegion(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name, OVERWORLD,
            Set.of(ChunkKey.pack(1, 2), ChunkKey.pack(2, 2), ChunkKey.pack(2, 3)), true);
    }

    @Test
    void acceptsUnicodeNamesWithinNetworkLimits() {
        assertTrue(ChunkLoaderSavedData.isValidName("Spawn"));
        assertTrue(ChunkLoaderSavedData.isValidName("spawn-2_A"));
        assertTrue(ChunkLoaderSavedData.isValidName("主城"));
        assertTrue(ChunkLoaderSavedData.isValidName("刷铁机-1"));
        assertTrue(ChunkLoaderSavedData.isValidName("жStation"));
        assertTrue(ChunkLoaderSavedData.isValidName("あ".repeat(ChunkLoaderSavedData.MAX_NAME_LENGTH)));

        assertFalse(ChunkLoaderSavedData.isValidName(""));
        assertFalse(ChunkLoaderSavedData.isValidName("has space"));
        assertFalse(ChunkLoaderSavedData.isValidName("emoji😀"));
        assertFalse(ChunkLoaderSavedData.isValidName("slash/name"));
        assertFalse(ChunkLoaderSavedData.isValidName("称".repeat(ChunkLoaderSavedData.MAX_NAME_LENGTH + 1)));
        // 4 字节字符按 32 个字符编码后超过网络写入的 96 字节上限，必须提前拒绝而不是编码时抛异常。
        assertFalse(ChunkLoaderSavedData.isValidName("𠀋".repeat(ChunkLoaderSavedData.MAX_NAME_LENGTH)));
    }

    @Test
    void codecRoundTripsUnicodeRegionNames() {
        ChunkLoaderSavedData original = new ChunkLoaderSavedData();
        original.addRegion(region("主城-东区"));
        var json = ChunkLoaderSavedData.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        ChunkLoaderSavedData decoded = ChunkLoaderSavedData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals("主城-东区", decoded.regions().iterator().next().name());
    }

    @Test
    void codecRejectsUnknownArchWeaverMode() {
        ChunkLoaderSavedData original = new ChunkLoaderSavedData();
        original.putPolicy(new FakePlayerLoadPolicy(UUID.randomUUID(), FakePlayerLoadMode.DOLL, 4));
        var json = ChunkLoaderSavedData.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        json.getAsJsonObject().getAsJsonArray("fake_player_policies").get(0).getAsJsonObject()
            .addProperty("mode", "UNKNOWN");

        assertTrue(ChunkLoaderSavedData.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent());
    }
}
