package com.sakurakugu.fakeplayer.chunkloading;

import com.sakurakugu.fakeplayer.FakePlayerMod;
import com.sakurakugu.fakeplayer.config.FakePlayerConfig;
import com.sakurakugu.fakeplayer.entity.FakePlayerManager;
import com.sakurakugu.fakeplayer.entity.FakeServerPlayer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;

/** 维护逐假人模拟范围；玩家刷怪语义仍由在线 FakeServerPlayer 自身提供。 */
public final class FakePlayerSimulationService {
    private static final Identifier ID = Identifier.fromNamespaceAndPath(FakePlayerMod.MOD_ID, "fake_player_simulation");
    private static final TicketController CONTROLLER = new TicketController(ID, FakePlayerSimulationService::validate);
    private static final Map<UUID, ActiveRange> ACTIVE = new HashMap<>();

    private FakePlayerSimulationService() {
    }

    public static void register(RegisterTicketControllersEvent event) {
        event.register(CONTROLLER);
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
            .map(FakePlayerLoadPolicy::simulationDistance).orElse(-1);
    }

    public static ChunkLoaderManager.Result setPolicy(MinecraftServer server, UUID fakePlayerId,
                                                       FakePlayerLoadMode mode, int distance) {
        if (distance < 0 || distance > FakePlayerConfig.maxFakePlayerSimulationDistance()) {
            return ChunkLoaderManager.Result.failure("模拟距离必须在 0-" + FakePlayerConfig.maxFakePlayerSimulationDistance() + " 之间");
        }
        FakePlayerLoadPolicy policy = new FakePlayerLoadPolicy(fakePlayerId, mode, distance);
        var policies = new java.util.ArrayList<>(ChunkLoaderManager.data(server).policies());
        policies.removeIf(value -> value.fakePlayerId().equals(fakePlayerId));
        policies.add(policy);
        var usage = ChunkLoadPlanner.budget(ChunkLoaderManager.data(server).regions(), policies);
        if (usage.player() > FakePlayerConfig.maxPlayerLoadingChunks()) {
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
        if (policy == null || !policy.usesCustomSimulation()
            || policy.simulationDistance() > FakePlayerConfig.maxFakePlayerSimulationDistance()) {
            removeActive(fake.getUUID());
            return;
        }
        ActiveRange previous = ACTIVE.get(fake.getUUID());
        int chunkX = fake.chunkPosition().x();
        int chunkZ = fake.chunkPosition().z();
        if (previous != null && previous.sameLocation(fake.level(), chunkX, chunkZ, policy.simulationDistance())) return;
        ActiveRange next = new ActiveRange(fake.level(), chunkX, chunkZ, policy.simulationDistance(),
            ChunkLoadPlanner.square(chunkX, chunkZ, policy.simulationDistance()));
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
        CONTROLLER.forceChunk(level, id, ChunkPos.getX(chunk), ChunkPos.getZ(chunk), add, true);
    }

    private static void validate(ServerLevel level, TicketHelper helper) {
        var data = ChunkLoaderManager.data(level.getServer());
        for (var entry : helper.getEntityTickets().entrySet()) {
            FakePlayerLoadPolicy policy = data.policy(entry.getKey()).orElse(null);
            FakeServerPlayer fake = FakePlayerManager.all(level.getServer()).stream()
                .filter(value -> value.getUUID().equals(entry.getKey()) && value.level() == level).findFirst().orElse(null);
            if (policy == null || !policy.usesCustomSimulation() || fake == null) {
                helper.removeAllTickets(entry.getKey());
                continue;
            }
            Set<Long> expected = ChunkLoadPlanner.square(fake.chunkPosition().x(), fake.chunkPosition().z(),
                policy.simulationDistance());
            for (long chunk : entry.getValue().normal()) if (!expected.contains(chunk)) helper.removeTicket(entry.getKey(), chunk, false);
            for (long chunk : entry.getValue().naturalSpawning()) helper.removeTicket(entry.getKey(), chunk, true);
        }
    }

    private record ActiveRange(ServerLevel level, int chunkX, int chunkZ, int distance, Set<Long> chunks) {
        private boolean sameLocation(ServerLevel level, int chunkX, int chunkZ, int distance) {
            return this.level == level && this.chunkX == chunkX && this.chunkZ == chunkZ && this.distance == distance;
        }
    }

    public record ActiveRangeView(String dimension, int chunkX, int chunkZ, int distance) {
    }
}
