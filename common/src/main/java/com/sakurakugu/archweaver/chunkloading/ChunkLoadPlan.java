package com.sakurakugu.archweaver.chunkloading;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 一次编辑在内存里的终态：只改声明，不碰存档、票据和磁盘。
 *
 * <p>整批编辑先全部折叠到这里，再对折叠结果校验一次、提交一次。校验的是真正要落地的状态，
 * 所以"中间态非法但终态合法"的批次不会被误拒——先删掉旧区域、再在同一个名字上建新区域，
 * 逐条应用会在中间那一步撞上重名。
 *
 * <p>折叠阶段没有任何副作用：任何一条编辑被拒绝时，世界、存档和票据都还没有动过。
 */
final class ChunkLoadPlan {
    static final String MISSING_REGION = "找不到加载区域";
    static final String DUPLICATE_NAME = "同名加载区域已存在";

    private final Map<UUID, ManualLoadRegion> regions = new LinkedHashMap<>();
    private final Map<UUID, FakePlayerLoadPolicy> policies = new LinkedHashMap<>();
    private boolean modified;

    private ChunkLoadPlan() {
    }

    static ChunkLoadPlan of(ChunkLoaderSavedData.State state) {
        ChunkLoadPlan plan = new ChunkLoadPlan();
        state.regions().forEach(region -> plan.regions.put(region.id(), region));
        state.policies().forEach(policy -> plan.policies.put(policy.fakePlayerId(), policy));
        return plan;
    }

    Collection<ManualLoadRegion> regions() {
        return List.copyOf(regions.values());
    }

    Collection<FakePlayerLoadPolicy> policies() {
        return List.copyOf(policies.values());
    }

    Optional<ManualLoadRegion> region(UUID id) {
        return Optional.ofNullable(regions.get(id));
    }

    /** 按名称查终态里的区域，命中的可能是本批次里刚建出来的那一个。 */
    Optional<ManualLoadRegion> region(String name) {
        return regions.values().stream()
            .filter(region -> region.name().equalsIgnoreCase(name)).findFirst();
    }

    /** 终态相比原状态是否有实际变更；全无变更的批次不动版本号也不写备份。 */
    boolean modified() {
        return modified;
    }

    /** 新增区域；id 或名称与终态里已有的区域冲突时返回拒绝原因。 */
    String create(ManualLoadRegion region) {
        if (regions.containsKey(region.id()) || region(region.name()).isPresent()) {
            return DUPLICATE_NAME;
        }
        regions.put(region.id(), region);
        modified = true;
        return null;
    }

    /** 覆盖已有区域；区域不存在、或改名撞上别的区域时返回拒绝原因。 */
    String replace(ManualLoadRegion region) {
        ManualLoadRegion existing = regions.get(region.id());
        if (existing == null) {
            return MISSING_REGION;
        }
        ManualLoadRegion named = region(region.name()).orElse(null);
        if (named != null && !named.id().equals(region.id())) {
            return DUPLICATE_NAME;
        }
        if (!region.equals(existing)) {
            regions.put(region.id(), region);
            modified = true;
        }
        return null;
    }

    /** 删除区域；区域不存在时返回拒绝原因。 */
    String remove(UUID id) {
        if (regions.remove(id) == null) {
            return MISSING_REGION;
        }
        modified = true;
        return null;
    }

    void setPolicy(FakePlayerLoadPolicy policy) {
        if (policy.equals(policies.put(policy.fakePlayerId(), policy))) {
            return;
        }
        modified = true;
    }
}
