package com.sakurakugu.fakeplayer.chunkloading;

import com.sakurakugu.fakeplayer.FakePlayerMod;
import com.sakurakugu.fakeplayer.config.FakePlayerConfig;
import com.sakurakugu.fakeplayer.entity.FakePlayerManager;
import com.sakurakugu.fakeplayer.entity.FakeServerPlayer;
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
    private static final Map<UUID, ActiveRange> ACTIVE = new HashMap<>();

    private FakePlayerSimulationService() {
    }

    public static void reconcile(MinecraftServer server) {
        tick(server);
        for (FakeServerPlayer fake : FakePlayerManager.all(server)) {
            fake.level().getChunkSource().move(fake);
        }
    }

    public static void tick(MinecraftServer server) {
        var online = FakePlayerManager.all(server);
        for (FakeServerPlayer fake : online) update(fake);
        Set<UUID> onlineIds = online.stream().map(FakeServerPlayer::getUUID).collect(java.util.stream.Collectors.toSet());
        ACTIVE.keySet().stream().filter(id -> !onlineIds.contains(id)).toList().forEach(FakePlayerSimulationService::removeActive);
    }

    /** 返回已实际提交区块票的假人加载范围。 */
    public static Optional<ActiveRangeView> activeRange(UUID fakePlayerId) {
        ActiveRange range = ACTIVE.get(fakePlayerId);
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
            .filter(FakePlayerLoadPolicy::usesCustomSimulation)
            .map(policy -> effectiveSimulationDistance(fake.server(), policy)).orElse(-1);
    }

    public static ChunkLoaderManager.Result setPolicy(MinecraftServer server, UUID fakePlayerId,
                                                       FakePlayerLoadMode mode, int distance) {
        int maxDistance = maxSimulationDistance(server);
        if (distance < 0 || (mode == FakePlayerLoadMode.DOLL && distance > maxDistance)) {
            return ChunkLoaderManager.Result.failure("模拟距离必须在 0-" + maxDistance + " 之间");
        }
        FakePlayerLoadPolicy policy = new FakePlayerLoadPolicy(fakePlayerId, mode, distance);
        int budget = FakePlayerConfig.maxPlayerLoadingChunks();
        if (mode == FakePlayerLoadMode.DOLL && budget >= 0
            && uniquePlayerChunks(server, fakePlayerId, policy) > budget) {
            return ChunkLoaderManager.Result.failure("玩家加载预算超限");
        }
        ChunkLoaderManager.data(server).putPolicy(policy);
        FakeServerPlayer fake = FakePlayerManager.all(server).stream()
            .filter(value -> value.getUUID().equals(fakePlayerId)).findFirst().orElse(null);
        if (fake != null) {
            update(fake);
            fake.level().getChunkSource().move(fake);
        } else {
            removeActive(fakePlayerId);
        }
        ChunkLoaderBackupStore.save(server, ChunkLoaderManager.data(server));
        return ChunkLoaderManager.Result.success();
    }

    public static void removePolicy(MinecraftServer server, UUID fakePlayerId) {
        removeActive(fakePlayerId);
        ChunkLoaderManager.data(server).removePolicy(fakePlayerId);
        ChunkLoaderBackupStore.save(server, ChunkLoaderManager.data(server));
    }

    private static void update(FakeServerPlayer fake) {
        FakePlayerLoadPolicy policy = ChunkLoaderManager.data(fake.server()).policy(fake.getUUID()).orElse(null);
        if (policy == null || !policy.usesCustomSimulation()) {
            removeActive(fake.getUUID());
            return;
        }
        int distance = effectiveSimulationDistance(fake.server(), policy);
        ActiveRange previous = ACTIVE.get(fake.getUUID());
        int chunkX = fake.chunkPosition().x();
        int chunkZ = fake.chunkPosition().z();
        if (previous != null && previous.sameLocation(fake.level(), chunkX, chunkZ, distance)) return;
        ActiveRange next = new ActiveRange(fake.level(), chunkX, chunkZ, distance,
            ChunkLoadPlanner.square(chunkX, chunkZ, distance));
        if (!withinBudget(fake, next)) {
            removeActive(fake.getUUID());
            return;
        }
        try {
            if (previous != null) setDifference(fake.getUUID(), previous, next, false);
            setDifference(fake.getUUID(), next, previous, true);
            ACTIVE.put(fake.getUUID(), next);
        } catch (RuntimeException exception) {
            FakePlayerMod.LOGGER.error("更新假玩家 {} 的模拟范围失败", fake.getGameProfile().name(), exception);
        }
    }

    private static void removeActive(UUID id) {
        ActiveRange previous = ACTIVE.remove(id);
        if (previous != null) previous.chunks().forEach(chunk -> set(previous.level(), id, chunk, false));
    }

    private static void setDifference(UUID id, ActiveRange source, ActiveRange other, boolean add) {
        for (long chunk : source.chunks()) {
            if (other == null || other.level() != source.level() || !other.chunks().contains(chunk)) {
                set(source.level(), id, chunk, add);
            }
        }
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

    /** 用候选策略和在线假人位置计算实际覆盖区块并集。 */
    private static long uniquePlayerChunks(MinecraftServer server, UUID replacementId,
                                           FakePlayerLoadPolicy replacement) {
        Map<UUID, FakePlayerLoadPolicy> policies = new HashMap<>();
        ChunkLoaderManager.data(server).policies().forEach(policy -> policies.put(policy.fakePlayerId(), policy));
        policies.put(replacementId, replacement);
        List<ChunkLoadPlanner.SimulationRange> ranges = new ArrayList<>();
        for (FakeServerPlayer fake : FakePlayerManager.all(server)) {
            FakePlayerLoadPolicy policy = policies.get(fake.getUUID());
            if (policy == null || !policy.usesCustomSimulation()) continue;
            int distance = effectiveSimulationDistance(server, policy);
            ranges.add(new ChunkLoadPlanner.SimulationRange(fake.getUUID(),
                fake.level().dimension().identifier(),
                ChunkLoadPlanner.square(fake.chunkPosition().x(), fake.chunkPosition().z(),
                    distance)));
        }
        return ChunkLoadPlanner.uniquePlayerChunks(ranges);
    }

    /** 检查移动后的范围是否仍在总预算内。 */
    private static boolean withinBudget(FakeServerPlayer fake, ActiveRange next) {
        int budget = FakePlayerConfig.maxPlayerLoadingChunks();
        if (budget < 0) return true;
        List<ChunkLoadPlanner.SimulationRange> ranges = new ArrayList<>();
        for (Map.Entry<UUID, ActiveRange> entry : ACTIVE.entrySet()) {
            if (entry.getKey().equals(fake.getUUID())) continue;
            ActiveRange range = entry.getValue();
            ranges.add(new ChunkLoadPlanner.SimulationRange(entry.getKey(),
                range.level().dimension().identifier(), range.chunks()));
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
