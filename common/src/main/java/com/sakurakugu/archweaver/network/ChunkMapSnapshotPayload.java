package com.sakurakugu.archweaver.network;

import com.sakurakugu.archweaver.ArchWeaverMod;
import com.sakurakugu.archweaver.chunkloading.ChunkLoaderSavedData;
import com.sakurakugu.archweaver.chunkloading.FakePlayerLoadPolicy;
import com.sakurakugu.archweaver.chunkloading.FakePlayerLoadMode;
import com.sakurakugu.archweaver.chunkloading.FakePlayerSimulationService;
import com.sakurakugu.archweaver.chunkloading.ManualLoadRegion;
import com.sakurakugu.archweaver.entity.FakePlayerManager;
import com.sakurakugu.archweaver.entity.FakePlayerPossession;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

/**
 * 当前维度的权威加载快照，同时供内置地图和第三方地图前端使用。
 *
 * <p>{@code regionsUnchanged} 为 true 时 {@code regions} 是空的：客户端已知的区域数据仍然有效，
 * 应当保留原样（见 {@code ClientChunkLoadingState}）。假人位置与区域摘要始终是最新的。
 */
public record ChunkMapSnapshotPayload(
    ChunkMapOpenTarget openTarget,
    int globalSettingsMask,
    int maximumRadius,
    long revision,
    boolean regionsUnchanged,
    String dimension,
    int playerChunkX,
    int playerChunkZ,
    List<RegionView> regions,
    List<RegionSummary> managementRegions,
    List<FakePlayerView> fakePlayers
) implements CustomPacketPayload {
    public static final int MAX_REGIONS = 1024;
    public static final int MAX_SNAPSHOT_CHUNKS = 65536;
    public static final int MAX_FAKE_PLAYERS = 1024;
    public static final Type<ChunkMapSnapshotPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(ArchWeaverMod.MOD_ID, "chunk_map_snapshot")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ChunkMapSnapshotPayload> STREAM_CODEC =
        CustomPacketPayload.codec(ChunkMapSnapshotPayload::write, ChunkMapSnapshotPayload::new);

    public ChunkMapSnapshotPayload {
        if (maximumRadius < 0 || maximumRadius > 32) throw new IllegalArgumentException("最大半径非法");
        regions = List.copyOf(regions);
        managementRegions = List.copyOf(managementRegions);
        fakePlayers = List.copyOf(fakePlayers);
    }

    private ChunkMapSnapshotPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readEnum(ChunkMapOpenTarget.class), buffer.readVarInt(),
            buffer.readVarInt(), buffer.readVarLong(), buffer.readBoolean(),
            buffer.readUtf(256), buffer.readInt(), buffer.readInt(),
            readRegions(buffer), readRegionSummaries(buffer), readFakePlayers(buffer));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeEnum(openTarget);
        buffer.writeVarInt(globalSettingsMask);
        buffer.writeVarInt(maximumRadius);
        buffer.writeVarLong(revision);
        buffer.writeBoolean(regionsUnchanged);
        buffer.writeUtf(dimension, 256);
        buffer.writeInt(playerChunkX);
        buffer.writeInt(playerChunkZ);
        buffer.writeVarInt(regions.size());
        regions.forEach(region -> region.write(buffer));
        buffer.writeVarInt(managementRegions.size());
        managementRegions.forEach(region -> region.write(buffer));
        buffer.writeVarInt(fakePlayers.size());
        fakePlayers.forEach(fake -> fake.write(buffer));
    }

    public static ChunkMapSnapshotPayload create(ServerPlayer player, ChunkLoaderSavedData data,
                                                 ChunkMapOpenTarget openTarget) {
        return create(player, data, openTarget, RequestChunkMapPayload.NO_REVISION, null);
    }

    /**
     * @param knownRevision  客户端手上区域数据的 revision，见 {@link RequestChunkMapPayload}
     * @param knownDimension 该数据对应的维度，为 null 表示按"客户端可能有别的维度"处理
     */
    public static ChunkMapSnapshotPayload create(ServerPlayer player, ChunkLoaderSavedData data,
                                                 ChunkMapOpenTarget openTarget,
                                                 long knownRevision, String knownDimension) {
        String dimension = player.level().dimension().identifier().toString();
        boolean regionsUnchanged = canSkipRegions(knownRevision, knownDimension, data.revision(), dimension);
        List<RegionView> regionViews = regionsUnchanged ? List.of() : data.regions().stream()
            .filter(region -> region.dimension().toString().equals(dimension))
            .limit(MAX_REGIONS)
            .map(RegionView::from)
            .toList();
        List<RegionSummary> summaries = data.regions().stream()
            .limit(MAX_REGIONS)
            .map(RegionSummary::from)
            .toList();
        List<FakePlayerView> fakeViews = FakePlayerManager.all(player.level().getServer()).stream()
            .limit(MAX_FAKE_PLAYERS)
            .map(fake -> {
                FakePlayerLoadPolicy policy = data.policy(fake.getUUID())
                    .orElse(new FakePlayerLoadPolicy(fake.getUUID(), FakePlayerLoadMode.PLAYER, 0));
                var activeRange = FakePlayerSimulationService.activeRange(fake.getUUID()).orElse(null);
                int simulationDistance = policy.usesCustomSimulation()
                    ? FakePlayerSimulationService.dollSimulationDistance(fake)
                    : policy.simulationDistance();
                return new FakePlayerView(fake.getUUID(), fake.getGameProfile().name(),
                    fake.level().dimension().identifier().toString(), fake.getBlockX(), fake.getBlockY(),
                    fake.getBlockZ(), fake.getYRot(), true, FakePlayerPossession.isPossessed(fake),
                    policy.mode(), simulationDistance,
                    activeRange != null, activeRange == null ? "" : activeRange.dimension(),
                    activeRange == null ? 0 : activeRange.chunkX(), activeRange == null ? 0 : activeRange.chunkZ(),
                    activeRange == null ? 0 : activeRange.distance());
            }).toList();
        return new ChunkMapSnapshotPayload(openTarget,
            com.sakurakugu.archweaver.config.ArchWeaverConfig.globalSettingsMask(),
            com.sakurakugu.archweaver.config.ArchWeaverConfig.maxChunkLoadingRadius(), data.revision(),
            regionsUnchanged, dimension,
            player.chunkPosition().x(), player.chunkPosition().z(), regionViews, summaries, fakeViews);
    }

    /**
     * 客户端手上的区域数据能否原样复用。
     * 维度必须一起判断：同一份 revision 下换维度，区域列表是按维度过滤的，不能沿用。
     */
    public static boolean canSkipRegions(long knownRevision, String knownDimension,
                                         long revision, String dimension) {
        return knownRevision == revision && dimension.equals(knownDimension);
    }

    /** 把上一份快照的区域列表套到这份增量快照上，其余字段保持最新。 */
    public ChunkMapSnapshotPayload withPreviousRegions(ChunkMapSnapshotPayload previous) {
        return new ChunkMapSnapshotPayload(openTarget, globalSettingsMask,
            maximumRadius, revision, false, dimension, playerChunkX, playerChunkZ, previous.regions,
            managementRegions, fakePlayers);
    }

    private static List<RegionView> readRegions(RegistryFriendlyByteBuf buffer) {
        int size = checkedSize(buffer.readVarInt(), MAX_REGIONS, "区域");
        List<RegionView> values = new ArrayList<>(size);
        int chunks = 0;
        for (int index = 0; index < size; index++) {
            RegionView value = new RegionView(buffer);
            chunks = Math.addExact(chunks, value.chunks().size());
            if (chunks > MAX_SNAPSHOT_CHUNKS) throw new IllegalArgumentException("快照区块数量超过上限");
            values.add(value);
        }
        return values;
    }

    private static List<FakePlayerView> readFakePlayers(RegistryFriendlyByteBuf buffer) {
        int size = checkedSize(buffer.readVarInt(), MAX_FAKE_PLAYERS, "假玩家");
        List<FakePlayerView> values = new ArrayList<>(size);
        for (int index = 0; index < size; index++) values.add(new FakePlayerView(buffer));
        return values;
    }

    private static List<RegionSummary> readRegionSummaries(RegistryFriendlyByteBuf buffer) {
        int size = checkedSize(buffer.readVarInt(), MAX_REGIONS, "区域摘要");
        List<RegionSummary> values = new ArrayList<>(size);
        for (int index = 0; index < size; index++) values.add(new RegionSummary(buffer));
        return values;
    }

    private static int checkedSize(int size, int maximum, String type) {
        if (size < 0 || size > maximum) throw new IllegalArgumentException(type + "数量超过上限");
        return size;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public record RegionView(UUID id, String name, String dimension, boolean enabled, Set<Long> chunks) {
        private RegionView(RegistryFriendlyByteBuf buffer) {
            this(buffer.readUUID(), buffer.readUtf(32), buffer.readUtf(256),
                buffer.readBoolean(), readChunks(buffer));
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeUUID(id);
            buffer.writeUtf(name, 32);
            buffer.writeUtf(dimension, 256);
            buffer.writeBoolean(enabled);
            buffer.writeVarInt(chunks.size());
            chunks.forEach(buffer::writeLong);
        }

        private static RegionView from(ManualLoadRegion region) {
            return new RegionView(region.id(), region.name(), region.dimension().toString(),
                region.enabled(), region.chunks());
        }

        private static Set<Long> readChunks(RegistryFriendlyByteBuf buffer) {
            int size = checkedSize(buffer.readVarInt(), ChunkLoaderSavedData.MAX_REGION_CHUNKS, "区域区块");
            java.util.HashSet<Long> values = new java.util.HashSet<>();
            for (int index = 0; index < size; index++) {
                if (!values.add(buffer.readLong())) throw new IllegalArgumentException("区域包含重复区块");
            }
            return Set.copyOf(values);
        }

        public boolean contains(int chunkX, int chunkZ) {
            return enabled && chunks.contains(ChunkPos.pack(chunkX, chunkZ));
        }

        public int chunkX() { return chunks.stream().mapToInt(ChunkPos::getX).min().orElse(0); }
        public int chunkZ() { return chunks.stream().mapToInt(ChunkPos::getZ).min().orElse(0); }
    }

    public record RegionSummary(String name, String dimension, int chunkX, int chunkZ,
                                int chunkCount, boolean enabled) {
        private RegionSummary(RegistryFriendlyByteBuf buffer) {
            this(buffer.readUtf(32), buffer.readUtf(256), buffer.readInt(), buffer.readInt(),
                buffer.readVarInt(), buffer.readBoolean());
            if (chunkCount < 1 || chunkCount > ChunkLoaderSavedData.MAX_REGION_CHUNKS) {
                throw new IllegalArgumentException("区域摘要非法");
            }
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(name, 32);
            buffer.writeUtf(dimension, 256);
            buffer.writeInt(chunkX);
            buffer.writeInt(chunkZ);
            buffer.writeVarInt(chunkCount);
            buffer.writeBoolean(enabled);
        }

        private static RegionSummary from(ManualLoadRegion region) {
            int minX = region.chunks().stream().mapToInt(ChunkPos::getX).min().orElse(0);
            int minZ = region.chunks().stream().mapToInt(ChunkPos::getZ).min().orElse(0);
            return new RegionSummary(region.name(), region.dimension().toString(), minX, minZ,
                region.chunks().size(), region.enabled());
        }
    }

    public record FakePlayerView(UUID id, String name, String dimension, int x, int y, int z, float yaw,
                                 boolean online, boolean possessed, FakePlayerLoadMode mode, int simulationDistance,
                                 boolean loadingActive, String loadingDimension, int loadingChunkX,
                                 int loadingChunkZ, int loadingDistance) {
        private FakePlayerView(RegistryFriendlyByteBuf buffer) {
            this(buffer.readUUID(), buffer.readUtf(32), buffer.readUtf(256), buffer.readInt(), buffer.readInt(),
                buffer.readInt(), buffer.readFloat(), buffer.readBoolean(), buffer.readBoolean(),
                buffer.readEnum(FakePlayerLoadMode.class), buffer.readVarInt(),
                buffer.readBoolean(), buffer.readUtf(256), buffer.readInt(), buffer.readInt(), buffer.readVarInt());
            if (!Float.isFinite(yaw)) throw new IllegalArgumentException("假玩家朝向非法");
            if (simulationDistance < 0 || simulationDistance > ChunkLoaderSavedData.MAX_SIMULATION_DISTANCE) {
                throw new IllegalArgumentException("假玩家模拟距离非法");
            }
            if (loadingDistance < 0 || loadingDistance > ChunkLoaderSavedData.MAX_SIMULATION_DISTANCE) {
                throw new IllegalArgumentException("假玩家活动加载距离非法");
            }
            if (mode == null) throw new IllegalArgumentException("假玩家加载模式为空");
            if (loadingActive && loadingDimension.isEmpty()) throw new IllegalArgumentException("假玩家活动加载维度为空");
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeUUID(id); buffer.writeUtf(name, 32); buffer.writeUtf(dimension, 256);
            buffer.writeInt(x); buffer.writeInt(y); buffer.writeInt(z);
            buffer.writeFloat(yaw);
            buffer.writeBoolean(online); buffer.writeBoolean(possessed);
            buffer.writeEnum(mode); buffer.writeVarInt(simulationDistance);
            buffer.writeBoolean(loadingActive); buffer.writeUtf(loadingDimension, 256);
            buffer.writeInt(loadingChunkX); buffer.writeInt(loadingChunkZ); buffer.writeVarInt(loadingDistance);
        }

        public boolean loadsChunk(String dimension, int chunkX, int chunkZ) {
            return loadingActive && loadingDimension.equals(dimension)
                && Math.abs(chunkX - loadingChunkX) <= loadingDistance
                && Math.abs(chunkZ - loadingChunkZ) <= loadingDistance;
        }
    }
}
