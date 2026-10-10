package com.sakurakugu.archweaver.client.chunkloading;

import com.sakurakugu.archweaver.chunkloading.ChunkKey;
import com.sakurakugu.archweaver.network.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ChunkMapSessionTest {
    private static ChunkMapSnapshotPayload snapshot(String dimension, long revision, long request, boolean unchanged) {
        var regions = unchanged ? List.<ChunkMapSnapshotPayload.RegionView>of() : List.of(
            new ChunkMapSnapshotPayload.RegionView(new UUID(0, revision), "region", dimension, true, Set.of(ChunkKey.pack(-1, -2))));
        return new ChunkMapSnapshotPayload(ChunkMapOpenTarget.NONE, 0, 32, revision, unchanged,
            dimension, 0, 0, regions, List.of(), List.of(), request);
    }

    @Test void cacheIsolatesDimensionsRejectsOldResponsesAndMergesOnlySameRevision() {
        var cache = new ChunkMapSnapshotCache();
        var overworld = snapshot("minecraft:overworld", 4, 1, false);
        var nether = snapshot("minecraft:the_nether", 4, 2, false);
        cache.accept(overworld); cache.accept(nether);
        assertEquals(overworld, cache.get(overworld.dimension()));
        assertEquals(nether, cache.get(nether.dimension()));
        var merged = cache.accept(snapshot(overworld.dimension(), 4, 3, true));
        assertEquals(overworld.regions(), merged.regions());
        assertEquals(3, merged.requestId());
        assertNull(cache.accept(snapshot(overworld.dimension(), 4, 2, false)));
        assertNull(cache.accept(snapshot(overworld.dimension(), 3, 4, false)));
        assertEquals(merged, cache.get(overworld.dimension()));
        assertNull(cache.accept(snapshot(overworld.dimension(), 5, 5, true)));
        assertNull(cache.get(overworld.dimension()));
        assertEquals(nether, cache.get(nether.dimension()));
        cache.clear();
        assertNull(cache.get(nether.dimension()));
    }

    @Test void ordinaryRefreshAndWrongReceiptsNeverClearPendingDraft() {
        var sent = new ArrayList<ApplyChunkLoadEditsPayload>();
        var controller = new ChunkLoadMapController(snapshot("minecraft:the_nether", 4, 1, false), sent::add);
        controller.setMode(ChunkMapEditMode.EDIT);
        controller.edit(-5, -6, false);
        controller.apply();
        assertEquals(1, sent.size());
        assertEquals("minecraft:the_nether", sent.getFirst().dimension());
        assertFalse(controller.canUndo());
        controller.edit(10, 10, false);
        assertFalse(controller.painted().contains(ChunkKey.pack(10, 10)));
        controller.accept(snapshot("minecraft:the_nether", 5, 2, false));
        assertTrue(controller.awaitingApply());
        assertTrue(controller.dirty());
        assertFalse(controller.acceptResult(new ChunkMapApplyResultPayload(controller.submissionId() + 1,
            "minecraft:the_nether", true, false, "")));
        assertFalse(controller.acceptResult(new ChunkMapApplyResultPayload(controller.submissionId(),
            "minecraft:overworld", true, false, "")));
        assertTrue(controller.dirty());
        assertTrue(controller.acceptResult(new ChunkMapApplyResultPayload(controller.submissionId(),
            "minecraft:the_nether", true, false, "")));
        assertFalse(controller.awaitingApply());
        assertFalse(controller.dirty());
        assertFalse(controller.canUndo());
    }

    @Test void conflictRejectionAndTimeoutKeepDraftAndPermitRetry() {
        var sent = new ArrayList<ApplyChunkLoadEditsPayload>();
        var controller = new ChunkLoadMapController(snapshot("minecraft:overworld", 4, 1, false), sent::add);
        controller.setMode(ChunkMapEditMode.EDIT);
        controller.edit(1, 2, false);
        controller.apply();
        long first = controller.submissionId();
        controller.acceptResult(new ChunkMapApplyResultPayload(first, "minecraft:overworld", false, true, "快照版本冲突"));
        assertTrue(controller.dirty());
        assertTrue(controller.canUndo());
        assertEquals("快照版本冲突", controller.failure());
        controller.apply();
        long retry = controller.submissionId();
        assertTrue(retry > first);
        assertFalse(controller.acceptResult(new ChunkMapApplyResultPayload(first, "minecraft:overworld", true, false, "")));
        controller.acceptResult(new ChunkMapApplyResultPayload(retry, "minecraft:overworld", false, false, "超过预算"));
        assertTrue(controller.dirty());
        controller.apply();
        for (int i = 0; i < 600; i++) controller.tick();
        assertFalse(controller.awaitingApply());
        assertTrue(controller.dirty());
        assertTrue(controller.canUndo());
        controller.accept(snapshot("minecraft:the_nether", 6, 5, false));
        assertEquals("minecraft:overworld", controller.snapshot().dimension());
        controller.clearDraft();
        controller.accept(snapshot("minecraft:the_nether", 6, 5, false));
        // 清空草稿也不能把当前编辑会话切换到其他维度。
        assertEquals("minecraft:overworld", controller.snapshot().dimension());
    }

    @Test void rectanglesCoverEveryCellExactlyOnceIncludingHolesAndNegativeCoordinates() {
        Random random = new Random(20261010);
        for (int iteration = 0; iteration < 100; iteration++) {
            Map<Long, ChunkMapLoadLevel> cells = new HashMap<>();
            for (int z = -8; z <= 8; z++) for (int x = -8; x <= 8; x++) {
                int choice = random.nextInt(3);
                if (choice != 0) cells.put(ChunkKey.pack(x, z), choice == 1 ? ChunkMapLoadLevel.STRONG : ChunkMapLoadLevel.WEAK);
            }
            Map<Long, ChunkMapLoadLevel> recovered = new HashMap<>();
            for (var rectangle : ChunkMapRectangles.merge(cells)) {
                for (int z = rectangle.minZ(); z <= rectangle.maxZ(); z++)
                    for (int x = rectangle.minX(); x <= rectangle.maxX(); x++)
                        assertNull(recovered.put(ChunkKey.pack(x, z), rectangle.level()), "矩形不应重叠");
            }
            assertEquals(cells, recovered);
        }
        Map<Long, ChunkMapLoadLevel> solid = new HashMap<>();
        for (int z = -20; z < 20; z++) for (int x = -20; x < 20; x++) solid.put(ChunkKey.pack(x, z), ChunkMapLoadLevel.STRONG);
        assertEquals(List.of(new ChunkMapRectangles.Rectangle(-20, -20, 19, 19, ChunkMapLoadLevel.STRONG)), ChunkMapRectangles.merge(solid));
    }
}
