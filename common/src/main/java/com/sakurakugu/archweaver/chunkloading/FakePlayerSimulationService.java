package com.sakurakugu.archweaver.chunkloading;

import com.sakurakugu.archweaver.ArchWeaverMod;
import com.sakurakugu.archweaver.config.ArchWeaverConfig;
import com.sakurakugu.archweaver.entity.FakePlayerManager;
import com.sakurakugu.archweaver.entity.FakeServerPlayer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/** 维护逐假人模拟范围；玩家刷怪语义仍由在线 FakeServerPlayer 自身提供。 */
public final class FakePlayerSimulationService {
    /** 运行时票据状态按服务端实例隔离，避免切换世界后仍持有旧实例的 ServerLevel。 */
    private static final Map<MinecraftServer, Map<UUID, ActiveRange>> ACTIVE = new HashMap<>();

    private FakePlayerSimulationService() {
    }

    public static boolean reconcile(MinecraftServer server) {
        boolean successful = tick(server);
        for (FakeServerPlayer fake : FakePlayerManager.all(server)) {
            fake.level().getChunkSource().move(fake);
        }
        return successful;
    }

    public static boolean tick(MinecraftServer server) {
        var online = FakePlayerManager.all(server);
        boolean successful = true;
        for (FakeServerPlayer fake : online) {
            if (!update(fake)) successful = false;
        }
        Map<UUID, ActiveRange> active = ACTIVE.get(server);
        if (active == null) return successful;
        Set<UUID> onlineIds = online.stream().map(FakeServerPlayer::getUUID).collect(java.util.stream.Collectors.toSet());
        for (UUID id : active.keySet().stream().filter(id -> !onlineIds.contains(id)).toList()) {
            if (!removeActive(server, id)) successful = false;
        }
        return successful;
    }

    /** 服务端停止时撤销该实例已提交的全部模拟票据，并释放对应的运行时状态。 */
    public static void revokeAll(MinecraftServer server) {
        Map<UUID, ActiveRange> active = ACTIVE.remove(server);
        if (active == null) return;
        for (Map.Entry<UUID, ActiveRange> entry : active.entrySet()) {
            for (long chunk : entry.getValue().chunks()) {
                try {
                    set(entry.getValue().level(), entry.getKey(), chunk, false);
                } catch (RuntimeException exception) {
                    // 服务器正在关闭，单个票据撤销失败不能阻断其余清理。
                    ArchWeaverMod.LOGGER.error("撤销假玩家 {} 的模拟票据失败", entry.getKey(), exception);
                }
            }
        }
    }

    /** 丢弃其它服务端实例残留的状态；同一 JVM 同时只会存在一个服务端。 */
    public static void discardStale(MinecraftServer server) {
        ACTIVE.keySet().removeIf(key -> key != server);
    }

    /** 返回已实际提交区块票的假人加载范围。 */
    public static Optional<ActiveRangeView> activeRange(FakeServerPlayer fake) {
        Map<UUID, ActiveRange> active = ACTIVE.get(fake.server());
        ActiveRange range = active == null ? null : active.get(fake.getUUID());
        if (range == null) return Optional.empty();
        return Optional.of(new ActiveRangeView(range.level().dimension().identifier().toString(),
            range.chunkX(), range.chunkZ(), range.distance()));
    }

    public static boolean usesDollMode(FakeServerPlayer fake) {
        return ChunkLoaderManager.data(fake.server()).policy(fake.getUUID())
            .map(FakePlayerLoadPolicy::usesCustomSimulation).orElse(false);
    }

    public static int dollSimulationDistance(FakeServerPlayer fake) {
        return ChunkLoaderManager.data(fake.server()).policy(fake.getUUID())
            .map(policy -> customDistance(fake.server(), policy)).orElse(-1);
    }

    /** 策略实际生效的模拟距离；没有策略或不是自定义模拟时返回 -1。 */
    static int customDistance(MinecraftServer server, FakePlayerLoadPolicy policy) {
        return policy == null || !policy.usesCustomSimulation() ? -1
            : effectiveSimulationDistance(server, policy);
    }

    /**
     * 模拟距离是请求自身的属性，与终态无关，所以逐条校验；玩家加载预算依赖终态，由终态校验统一算。
     */
    static String validateDistance(MinecraftServer server, FakePlayerLoadMode mode, int distance) {
        int maxDistance = maxSimulationDistance(server);
        return distance < 0 || (mode == FakePlayerLoadMode.DOLL && distance > maxDistance)
            ? "模拟距离必须在 0-" + maxDistance + " 之间"
            : null;
    }

    public static ChunkLoaderManager.Result setPolicy(MinecraftServer server, UUID fakePlayerId,
                                                      FakePlayerLoadMode mode, int distance) {
        String rejected = validateDistance(server, mode, distance);
        if (rejected != null) return ChunkLoaderManager.Result.failure(rejected);
        return ChunkLoaderManager.submit(server, List.of(plan -> {
            plan.setPolicy(new FakePlayerLoadPolicy(fakePlayerId, mode, distance));
            return null;
        }));
    }

    public static void removePolicy(MinecraftServer server, UUID fakePlayerId) {
        removeActive(server, fakePlayerId);
        ChunkLoaderManager.data(server).removePolicy(fakePlayerId);
        ChunkLoaderBackupStore.save(server, ChunkLoaderManager.data(server));
    }

    private static boolean update(FakeServerPlayer fake) {
        MinecraftServer server = fake.server();
        FakePlayerLoadPolicy policy = ChunkLoaderManager.data(server).policy(fake.getUUID()).orElse(null);
        if (policy == null || !policy.usesCustomSimulation()) {
            return removeActive(server, fake.getUUID());
        }
        int distance = effectiveSimulationDistance(server, policy);
        Map<UUID, ActiveRange> active = ACTIVE.get(server);
        ActiveRange previous = active == null ? null : active.get(fake.getUUID());
        int chunkX = fake.chunkPosition().x();
        int chunkZ = fake.chunkPosition().z();
        if (previous != null && previous.sameLocation(fake.level(), chunkX, chunkZ, distance)) return true;
        ActiveRange next = new ActiveRange(fake.level(), chunkX, chunkZ, distance,
            ChunkLoadPlanner.square(chunkX, chunkZ, distance));
        if (!withinBudget(fake, next)) {
            return removeActive(server, fake.getUUID());
        }
        try {
            List<TicketChange> changes = new ArrayList<>();
            appendDifference(changes, previous, next, false);
            appendDifference(changes, next, previous, true);
            applyChanges(fake.getUUID(), changes, FakePlayerSimulationService::set);
            ACTIVE.computeIfAbsent(server, key -> new HashMap<>()).put(fake.getUUID(), next);
            return true;
        } catch (RuntimeException exception) {
            ArchWeaverMod.LOGGER.error("更新假玩家 {} 的模拟范围失败", fake.getGameProfile().name(), exception);
            return false;
        }
    }

    private static boolean removeActive(MinecraftServer server, UUID id) {
        Map<UUID, ActiveRange> active = ACTIVE.get(server);
        ActiveRange previous = active == null ? null : active.get(id);
        if (previous == null) return true;
        try {
            List<TicketChange> changes = new ArrayList<>();
            appendDifference(changes, previous, null, false);
            applyChanges(id, changes, FakePlayerSimulationService::set);
            active.remove(id);
            return true;
        } catch (RuntimeException exception) {
            ArchWeaverMod.LOGGER.error("移除假玩家 {} 的模拟范围失败", id, exception);
            return false;
        }
    }

    private static void appendDifference(List<TicketChange> changes, ActiveRange source,
                                         ActiveRange other, boolean add) {
        if (source == null) return;
        for (long chunk : source.chunks()) {
            if (other == null || other.level() != source.level() || !other.chunks().contains(chunk)) {
                changes.add(new TicketChange(source.level(), chunk, add));
            }
        }
    }

    /** 失败的操作也可能已经生效，回滚时一并执行其反向操作。 */
    static void applyChanges(UUID id, List<TicketChange> changes, TicketWriter writer) {
        int attempted = -1;
        try {
            for (int index = 0; index < changes.size(); index++) {
                TicketChange change = changes.get(index);
                attempted = index;
                writer.set(change.level(), id, change.chunk(), change.add());
            }
        } catch (RuntimeException exception) {
            for (int index = attempted; index >= 0; index--) {
                TicketChange change = changes.get(index);
                try {
                    writer.set(change.level(), id, change.chunk(), !change.add());
                } catch (RuntimeException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
            }
            throw exception;
        }
    }

    record TicketChange(ServerLevel level, long chunk, boolean add) { }

    @FunctionalInterface
    interface TicketWriter {
        void set(ServerLevel level, UUID id, long chunk, boolean add);
    }

    private static void set(ServerLevel level, UUID id, long chunk, boolean add) {
        ChunkLoadClaim claim = new ChunkLoadClaim(LoadOwner.fakePlayer(id), level.dimension(), chunk);
        if (add) {
            ChunkLoaderManager.ticketService().add(level, claim);
        } else {
            ChunkLoaderManager.ticketService().remove(level, claim);
        }
    }

    /** 读取原版服务端模拟距离，假人自定义范围不能超过该值。 */
    public static int maxSimulationDistance(MinecraftServer server) {
        return Math.min(ChunkLoaderSavedData.MAX_SIMULATION_DISTANCE,
            Math.max(0, server.getPlayerList().getSimulationDistance()));
    }

    /** 检查移动后的范围是否仍在同一服务端实例的总预算内。 */
    private static boolean withinBudget(FakeServerPlayer fake, ActiveRange next) {
        int budget = ArchWeaverConfig.maxPlayerLoadingChunks();
        if (budget < 0) return true;
        Map<UUID, ActiveRange> active = ACTIVE.get(fake.server());
        List<ChunkLoadPlanner.SimulationRange> ranges = new ArrayList<>();
        if (active != null) {
            for (Map.Entry<UUID, ActiveRange> entry : active.entrySet()) {
                if (entry.getKey().equals(fake.getUUID())) continue;
                ActiveRange range = entry.getValue();
                ranges.add(new ChunkLoadPlanner.SimulationRange(entry.getKey(),
                    range.level().dimension().identifier(), range.chunks()));
            }
        }
        ranges.add(new ChunkLoadPlanner.SimulationRange(fake.getUUID(),
            next.level().dimension().identifier(), next.chunks()));
        return ChunkLoadPlanner.uniquePlayerChunks(ranges) <= budget;
    }

    private static int effectiveSimulationDistance(MinecraftServer server, FakePlayerLoadPolicy policy) {
        return Math.min(policy.simulationDistance(), maxSimulationDistance(server));
    }

    private record ActiveRange(ServerLevel level, int chunkX, int chunkZ, int distance, Set<Long> chunks) {
        private boolean sameLocation(ServerLevel level, int chunkX, int chunkZ, int distance) {
            return this.level == level && this.chunkX == chunkX && this.chunkZ == chunkZ && this.distance == distance;
        }
    }

    public record ActiveRangeView(String dimension, int chunkX, int chunkZ, int distance) {
    }
}
