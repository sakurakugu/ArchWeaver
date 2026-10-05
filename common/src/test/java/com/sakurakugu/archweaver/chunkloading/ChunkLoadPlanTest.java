package com.sakurakugu.archweaver.chunkloading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sakurakugu.archweaver.config.ArchWeaverConfig;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/** 折叠只做声明变更、且以终态为准，这两点是"先校验终态再提交"的前提。 */
class ChunkLoadPlanTest {
    private static final Identifier OVERWORLD = Identifier.withDefaultNamespace("overworld");

    @Test
    void laterEditsSeeRegionsCreatedEarlierInTheSameBatch() {
        UUID id = UUID.randomUUID();
        ChunkLoadPlan plan = ChunkLoadPlan.of(state(List.of(), List.of()));

        assertNull(plan.create(new ManualLoadRegion(id, "基地", OVERWORLD, Set.of(1L), true)));
        ManualLoadRegion created = plan.region(id).orElseThrow();
        assertNull(plan.replace(created.withChunks(Set.of(1L, 2L))));

        assertEquals(Set.of(1L, 2L), plan.region(id).orElseThrow().chunks());
        assertTrue(plan.modified());
    }

    @Test
    void removingThenRecreatingUnderTheSameNameLeavesOneRegion() {
        ManualLoadRegion existing = ChunkLoaderSavedDataTest.region("基地");
        UUID replacementId = UUID.randomUUID();
        ChunkLoadPlan plan = ChunkLoadPlan.of(state(List.of(existing), List.of()));

        // 逐条落地时这两步各自都合法，但只有折叠之后才能确认终态里名字没撞车
        assertNull(plan.remove(existing.id()));
        assertNull(plan.create(new ManualLoadRegion(replacementId, "基地", OVERWORLD, Set.of(7L), true)));

        assertEquals(List.of(replacementId), plan.regions().stream().map(ManualLoadRegion::id).toList());
    }

    @Test
    void rejectsDuplicateNamesAndMissingRegions() {
        ManualLoadRegion existing = ChunkLoaderSavedDataTest.region("基地");
        ChunkLoadPlan plan = ChunkLoadPlan.of(state(List.of(existing), List.of()));

        assertEquals(ChunkLoadPlan.DUPLICATE_NAME, plan.create(
            new ManualLoadRegion(UUID.randomUUID(), "基地", OVERWORLD, Set.of(1L), true)));
        assertEquals(ChunkLoadPlan.MISSING_REGION, plan.remove(UUID.randomUUID()));
        assertEquals(ChunkLoadPlan.MISSING_REGION, plan.replace(
            new ManualLoadRegion(UUID.randomUUID(), "分矿", OVERWORLD, Set.of(1L), true)));

        assertNull(plan.create(new ManualLoadRegion(UUID.randomUUID(), "分矿", OVERWORLD, Set.of(9L), true)));
        assertEquals(ChunkLoadPlan.DUPLICATE_NAME, plan.replace(
            new ManualLoadRegion(existing.id(), "分矿", OVERWORLD, existing.chunks(), true)));
    }

    @Test
    void identicalEditsLeaveThePlanUntouched() {
        ManualLoadRegion existing = ChunkLoaderSavedDataTest.region("基地");
        FakePlayerLoadPolicy policy = new FakePlayerLoadPolicy(UUID.randomUUID(), FakePlayerLoadMode.DOLL, 4);
        ChunkLoadPlan plan = ChunkLoadPlan.of(state(List.of(existing), List.of(policy)));

        assertNull(plan.replace(existing));
        plan.setPolicy(policy);

        assertFalse(plan.modified());
    }

    @Test
    void policyChangesMarkThePlanAsModified() {
        UUID fakePlayerId = UUID.randomUUID();
        FakePlayerLoadPolicy policy = new FakePlayerLoadPolicy(fakePlayerId, FakePlayerLoadMode.DOLL, 4);
        ChunkLoadPlan plan = ChunkLoadPlan.of(state(List.of(), List.of(policy)));

        plan.setPolicy(new FakePlayerLoadPolicy(fakePlayerId, FakePlayerLoadMode.DOLL, 6));

        assertTrue(plan.modified());
        assertEquals(6, plan.policies().iterator().next().simulationDistance());
    }

    @Test
    void manualBudgetIsCheckedAgainstTheWholeFinalState() {
        assertNull(ChunkLoadStateValidator.validateManualBudget(
            List.of(ChunkLoaderSavedDataTest.region("基地")), List.of()));
        assertEquals("模拟区块预算超限", ChunkLoadStateValidator.validateManualBudget(
            List.of(regionWithChunks(ArchWeaverConfig.maxTickingChunks() + 1L)), List.of()));
        assertEquals("手动加载总预算超限", ChunkLoadStateValidator.validateManualBudget(
            List.of(regionWithChunks(ArchWeaverConfig.maxForcedChunks() + 1L)), List.of()));
    }

    private static ManualLoadRegion regionWithChunks(long count) {
        Set<Long> chunks = new HashSet<>();
        for (long chunk = 0; chunk < count; chunk++) {
            chunks.add(chunk);
        }
        return new ManualLoadRegion(UUID.randomUUID(), "大区", OVERWORLD, chunks, true);
    }

    private static ChunkLoaderSavedData.State state(List<ManualLoadRegion> regions,
                                                    List<FakePlayerLoadPolicy> policies) {
        return new ChunkLoaderSavedData.State(0L, regions, policies);
    }
}
