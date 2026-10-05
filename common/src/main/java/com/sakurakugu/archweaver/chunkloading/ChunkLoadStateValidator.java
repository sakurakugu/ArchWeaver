package com.sakurakugu.archweaver.chunkloading;

import com.sakurakugu.archweaver.config.ArchWeaverConfig;
import com.sakurakugu.archweaver.entity.FakePlayerManager;
import com.sakurakugu.archweaver.entity.FakeServerPlayer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** 校验一份候选配置能否落地：区域自身、名称唯一性，以及手动与玩家加载预算。 */
final class ChunkLoadStateValidator {
    /** 名称非法时的统一提示，命令和界面共用。 */
    static final String INVALID_NAME_MESSAGE =
        "名称只能包含 1-32 个字母、数字、下划线或连字符（支持中文）";

    private ChunkLoadStateValidator() {
    }

    /**
     * 校验整批编辑的终态；返回第一个拒绝原因，合法时返回 {@code null}。
     *
     * @param previous 编辑前的假人策略，用来判断这次改动是不是把玩家预算撑得更紧了
     */
    static String validate(MinecraftServer server, ChunkLoadPlan plan,
                           Collection<FakePlayerLoadPolicy> previous) {
        Collection<ManualLoadRegion> regions = plan.regions();
        Collection<FakePlayerLoadPolicy> policies = plan.policies();
        String invalid = validateRegions(server, regions);
        if (invalid != null) return invalid;
        if (regions.size() > ChunkLoaderSavedData.MAX_REGIONS) return "加载区域数量超限";
        if (policies.size() > ChunkLoaderSavedData.MAX_POLICIES) return "假玩家策略数量超限";
        invalid = validateManualBudget(regions, policies);
        return invalid != null ? invalid : validatePlayerBudget(server, policies, previous);
    }

    /** 逐个区域的容错扫描：把候选区域放回整份配置后，是否仍然合法。 */
    static String validateRegion(MinecraftServer server, Collection<ManualLoadRegion> regions,
                                 Collection<FakePlayerLoadPolicy> policies, ManualLoadRegion candidate) {
        List<ManualLoadRegion> candidates = new ArrayList<>(regions);
        candidates.removeIf(region -> region.id().equals(candidate.id()));
        candidates.add(candidate);
        String invalid = validateRegions(server, candidates);
        return invalid != null ? invalid : validateManualBudget(candidates, policies);
    }

    static ServerLevel level(MinecraftServer server, Identifier dimension) {
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
    }

    private static String validateRegions(MinecraftServer server, Collection<ManualLoadRegion> regions) {
        Set<String> names = new HashSet<>();
        for (ManualLoadRegion region : regions) {
            if (region.chunks().isEmpty() || region.chunks().size() > ChunkLoaderSavedData.MAX_REGION_CHUNKS) {
                return "区域区块数量非法";
            }
            if (!ChunkLoaderSavedData.isValidName(region.name())) return INVALID_NAME_MESSAGE;
            if (!names.add(region.name().toLowerCase(Locale.ROOT))) return ChunkLoadPlan.DUPLICATE_NAME;
            if (level(server, region.dimension()) == null) return "目标维度不存在";
        }
        return null;
    }

    static String validateManualBudget(Collection<ManualLoadRegion> regions,
                                       Collection<FakePlayerLoadPolicy> policies) {
        var usage = ChunkLoadPlanner.budget(regions, policies);
        if (usage.manualTotal() > ArchWeaverConfig.maxForcedChunks()) return "手动加载总预算超限";
        if (usage.manualTotal() > ArchWeaverConfig.maxTickingChunks()) return "模拟区块预算超限";
        return null;
    }

    /**
     * 玩家加载预算按最终策略集合和在线假人当前位置算，所以与批次内的先后顺序无关。
     *
     * <p>只在这次改动把用量推得更高时才拒绝：预算被下调过的服务器本来就已经超支，
     * 那时连"关掉几个假人"都被拦下来，管理员就再也改不回去了。
     */
    private static String validatePlayerBudget(MinecraftServer server,
                                               Collection<FakePlayerLoadPolicy> policies,
                                               Collection<FakePlayerLoadPolicy> previous) {
        int budget = ArchWeaverConfig.maxPlayerLoadingChunks();
        if (budget < 0) return null;
        long next = playerChunks(server, policies);
        if (next <= budget) return null;
        return next > playerChunks(server, previous) ? "玩家加载预算超限" : null;
    }

    /** 按给定策略集合与在线假人当前位置，算实际覆盖的区块并集。 */
    private static long playerChunks(MinecraftServer server, Collection<FakePlayerLoadPolicy> policies) {
        Map<UUID, FakePlayerLoadPolicy> byId = new HashMap<>();
        policies.forEach(policy -> byId.put(policy.fakePlayerId(), policy));
        List<ChunkLoadPlanner.SimulationRange> ranges = new ArrayList<>();
        for (FakeServerPlayer fake : FakePlayerManager.all(server)) {
            int distance = FakePlayerSimulationService.customDistance(server, byId.get(fake.getUUID()));
            if (distance < 0) continue;
            ranges.add(new ChunkLoadPlanner.SimulationRange(fake.getUUID(),
                fake.level().dimension().identifier(),
                ChunkLoadPlanner.square(fake.chunkPosition().x(), fake.chunkPosition().z(), distance)));
        }
        return ChunkLoadPlanner.uniquePlayerChunks(ranges);
    }
}
