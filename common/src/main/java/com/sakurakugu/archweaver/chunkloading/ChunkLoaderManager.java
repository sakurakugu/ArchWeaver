package com.sakurakugu.archweaver.chunkloading;

import com.sakurakugu.archweaver.ArchWeaverMod;
import com.sakurakugu.archweaver.config.ArchWeaverConfig;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.ApiStatus;

/**
 * 区块加载配置的唯一提交入口：把编辑折叠成内存终态、校验一次、一次性落地。
 *
 * <p>不逐条应用是有原因的。逐条应用时每条编辑都要对着"当前状态"校验，而当前状态里
 * 还带着这一批前面的编辑留下的中间态，于是"中间态非法但终态合法"的批次会被误拒；
 * 逐条落地还会让一次批量编辑写出上百份备份、把票据下发拆成几百次。
 */
public final class ChunkLoaderManager {
    public static final int ABSOLUTE_MAX_RADIUS = 32;
    private static final ChunkLoadRepository REPOSITORY = new ChunkLoadRepository();
    private static ChunkTicketService tickets = new UnavailableChunkTicketService();

    private ChunkLoaderManager() {
    }

    @ApiStatus.Internal
    public static void installTicketService(ChunkTicketService service) {
        tickets = java.util.Objects.requireNonNull(service);
    }

    public static ChunkTicketService ticketService() {
        return tickets;
    }

    public static ChunkLoaderSavedData data(MinecraftServer server) {
        return REPOSITORY.get(server);
    }

    /**
     * 一条纯内存编辑：把变更写进终态，或返回拒绝原因。
     *
     * <p>折叠阶段不产生副作用，所以任何一条被拒绝时，世界、存档和票据都还没有动过。
     */
    @FunctionalInterface
    interface Change {
        String applyTo(ChunkLoadPlan plan);
    }

    /**
     * 折叠整批编辑、校验终态、一次性提交。
     *
     * <p>终态与原状态完全一致时直接返回：不动版本号，也不写备份。
     */
    static Result submit(MinecraftServer server, List<Change> changes) {
        ChunkLoaderSavedData data = data(server);
        ChunkLoaderSavedData.State before = data.snapshot();
        ChunkLoadPlan plan = ChunkLoadPlan.of(before);
        for (Change change : changes) {
            String rejection = change.applyTo(plan);
            if (rejection != null) {
                return Result.failure(rejection);
            }
        }
        if (!plan.modified()) {
            return Result.success();
        }
        String invalid = ChunkLoadStateValidator.validate(server, plan, before.policies());
        if (invalid != null) {
            return Result.failure(invalid);
        }
        return commit(server, data, before, plan);
    }

    /**
     * 一次性落地：按票据差集下发、替换配置、重建假人票据，最后只落盘一次。
     *
     * <p>失败时按账本撤销本批次下发过的票据并恢复配置，不把全服票据推倒重建。
     */
    private static Result commit(MinecraftServer server, ChunkLoaderSavedData data,
                                 ChunkLoaderSavedData.State before, ChunkLoadPlan plan) {
        ChunkLoadPlanner.ClaimDiff diff = ChunkLoadPlanner.diff(
            ChunkLoadPlanner.manualClaims(before.regions()),
            ChunkLoadPlanner.manualClaims(plan.regions()));
        ChunkLoadTransaction transaction = ChunkLoadTransaction.create();
        int mark = transaction.mark();
        try {
            apply(server, diff.removed(), false, transaction);
            apply(server, diff.added(), true, transaction);
            data.replaceAll(plan.regions(), plan.policies());
            if (!FakePlayerSimulationService.reconcile(server)) {
                throw new IllegalStateException("更新假玩家区块票据失败，请查看服务端日志");
            }
            ChunkLoaderBackupStore.save(server, data);
            return Result.success();
        } catch (RuntimeException exception) {
            rollback(server, data, before, transaction, mark);
            return Result.failure(exception.getMessage());
        }
    }

    /** 票据自带维度，按维度分组后分别下发。 */
    private static void apply(MinecraftServer server, Collection<ChunkLoadClaim> claims, boolean add,
                              ChunkLoadTransaction transaction) {
        Map<ResourceKey<Level>, List<ChunkLoadClaim>> byDimension = new LinkedHashMap<>();
        for (ChunkLoadClaim claim : claims) {
            byDimension.computeIfAbsent(claim.dimension(), ignored -> new ArrayList<>()).add(claim);
        }
        for (Map.Entry<ResourceKey<Level>, List<ChunkLoadClaim>> entry : byDimension.entrySet()) {
            ServerLevel level = server.getLevel(entry.getKey());
            if (level == null) throw new IllegalStateException("目标维度不存在");
            transaction.apply(level, entry.getValue(), add);
        }
    }

    /** 失败回滚：撤销本批次下发过的票据、恢复配置、按恢复后的配置重建假人票据，最后落盘一次。 */
    private static void rollback(MinecraftServer server, ChunkLoaderSavedData data,
                                 ChunkLoaderSavedData.State before, ChunkLoadTransaction transaction, int mark) {
        transaction.rollbackTo(mark);
        data.restore(before);
        try {
            FakePlayerSimulationService.reconcile(server);
        } catch (RuntimeException exception) {
            ArchWeaverMod.LOGGER.error("回滚后重建假玩家票据失败", exception);
        }
        ChunkLoaderBackupStore.save(server, data);
    }

    /** 启动和恢复备份后按配置重建票据，逐个禁用已经落不了地的区域。 */
    public static void reconcile(MinecraftServer server) {
        ChunkLoaderSavedData data = data(server);
        ChunkLoadTransaction transaction = ChunkLoadTransaction.create();
        for (ManualLoadRegion region : data.regions()) {
            if (!region.enabled()) {
                continue;
            }
            String invalid = ChunkLoadStateValidator.validateRegion(server, data.regions(), data.policies(), region);
            if (invalid != null) {
                data.putRegion(region.withEnabled(false));
                ArchWeaverMod.LOGGER.warn("区块加载区域 {} 已禁用：{}", region.name(), invalid);
                continue;
            }
            ServerLevel level = ChunkLoadStateValidator.level(server, region.dimension());
            if (level != null) {
                transaction.apply(level, ChunkLoadPlanner.manualClaims(List.of(region)), true);
            }
        }
        ChunkLoaderBackupStore.save(server, data);
    }

    /** 以指定位置为中心创建方形加载区域，供 {@code /chunkloader add} 使用。 */
    public static Result add(MinecraftServer server, String name, ServerLevel level, BlockPos position,
                             int radius) {
        if (radius < 0 || radius > ArchWeaverConfig.maxChunkLoadingRadius()) {
            return Result.failure("半径必须在 0-" + ArchWeaverConfig.maxChunkLoadingRadius() + " 之间");
        }
        ManualLoadRegion region = new ManualLoadRegion(UUID.randomUUID(), name, level.dimension().identifier(),
            ChunkLoadPlanner.square(position.getX() >> 4, position.getZ() >> 4, radius), true);
        return submit(server, List.of(plan -> plan.create(region)));
    }

    /** 重命名加载区域：形状、维度、启停状态和已提交的票据都不变。 */
    public static Result rename(MinecraftServer server, String name, String newName) {
        return submit(server, List.of(plan -> {
            ManualLoadRegion region = plan.region(name).orElse(null);
            if (region == null) return ChunkLoadPlan.MISSING_REGION;
            return plan.replace(new ManualLoadRegion(
                region.id(), newName, region.dimension(), region.chunks(), region.enabled()));
        }));
    }

    public static Result setEnabled(MinecraftServer server, String name, boolean enabled) {
        return submit(server, List.of(plan -> {
            ManualLoadRegion region = plan.region(name).orElse(null);
            return region == null ? ChunkLoadPlan.MISSING_REGION : plan.replace(region.withEnabled(enabled));
        }));
    }

    public static Result remove(MinecraftServer server, String name) {
        return submit(server, List.of(plan -> {
            ManualLoadRegion region = plan.region(name).orElse(null);
            return region == null ? ChunkLoadPlan.MISSING_REGION : plan.remove(region.id());
        }));
    }

    /**
     * 按调用方自带的 UUID 创建加载区域，区块集合任意，不限于方形。
     *
     * <p>供 API 层使用：方块一类的持有者有自己稳定的身份，按名称索引不够用。
     * 校验、预算、备份和回滚全部沿用 {@link #submit} 的既有管线，不另开旁路。
     */
    public static Result createRegion(MinecraftServer server, UUID id, String name, Identifier dimension,
                                      Set<Long> chunks, boolean enabled) {
        ManualLoadRegion region = new ManualLoadRegion(id, name, dimension, chunks, enabled);
        return submit(server, List.of(plan -> plan.create(region)));
    }

    /** 替换区域的区块集合；名称、维度和启停状态不变。 */
    public static Result updateRegionChunks(MinecraftServer server, UUID id, Set<Long> chunks) {
        return submit(server, List.of(plan -> {
            ManualLoadRegion region = plan.region(id).orElse(null);
            return region == null ? ChunkLoadPlan.MISSING_REGION : plan.replace(region.withChunks(chunks));
        }));
    }

    public static Result setEnabled(MinecraftServer server, UUID id, boolean enabled) {
        return submit(server, List.of(plan -> {
            ManualLoadRegion region = plan.region(id).orElse(null);
            return region == null ? ChunkLoadPlan.MISSING_REGION : plan.replace(region.withEnabled(enabled));
        }));
    }

    public static Result remove(MinecraftServer server, UUID id) {
        return submit(server, List.of(plan -> plan.remove(id)));
    }

    /** 按 UUID 查当前已落地的区域。 */
    public static Optional<ManualLoadRegion> region(MinecraftServer server, UUID id) {
        return data(server).regions().stream().filter(region -> region.id().equals(id)).findFirst();
    }

    public static boolean backup(MinecraftServer server) { return ChunkLoaderBackupStore.save(server, data(server)); }

    public static Result restoreLatestBackup(MinecraftServer server) {
        ChunkLoaderSavedData restored = ChunkLoaderBackupStore.loadLatest(server).orElse(null);
        if (restored == null) return Result.failure("没有可用的备份");
        ChunkLoaderSavedData data = data(server);
        ChunkLoadTransaction transaction = ChunkLoadTransaction.create();
        for (ManualLoadRegion region : data.regions()) {
            ServerLevel level = ChunkLoadStateValidator.level(server, region.dimension());
            if (region.enabled() && level != null) {
                transaction.applyLenient(level, ChunkLoadPlanner.manualClaims(List.of(region)), false);
            }
        }
        data.replaceAll(restored);
        reconcile(server);
        return Result.success();
    }

    public record Result(boolean successful, String reason) {
        public static Result success() { return new Result(true, ""); }
        public static Result failure(String reason) { return new Result(false, reason == null ? "未知错误" : reason); }
    }

    private static final class UnavailableChunkTicketService implements ChunkTicketService {
        public void add(ServerLevel level, ChunkLoadClaim claim) {
            throw new IllegalStateException("区块票据服务尚未由平台注册");
        }
        public void remove(ServerLevel level, ChunkLoadClaim claim) {
            throw new IllegalStateException("区块票据服务尚未由平台注册");
        }
    }
}
