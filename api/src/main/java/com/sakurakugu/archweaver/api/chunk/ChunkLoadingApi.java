package com.sakurakugu.archweaver.api.chunk;

import com.sakurakugu.archweaver.api.ApiResult;
import com.sakurakugu.archweaver.chunkloading.ChunkKey;
import com.sakurakugu.archweaver.chunkloading.ChunkLoaderManager;
import com.sakurakugu.archweaver.chunkloading.ManualLoadRegion;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/**
 * 区块强加载 API。
 *
 * <p>所有方法都必须在服务端主线程调用。写入会经过核心模组的统一提交管线：
 * 整批折叠成终态、校验一次、一次性落地，失败自动回滚，不会留下半成品状态。
 *
 * <p>区域受服务端配置的预算限制（{@code maxForcedChunks} 等），超出预算的请求会
 * 失败而不是被截断，原因在 {@link ApiResult#reason()} 里。
 */
public final class ChunkLoadingApi {
    private ChunkLoadingApi() {
    }

    /** 创建加载区域。{@code spec.id()} 已存在或名称重复时失败。 */
    public static ApiResult createRegion(MinecraftServer server, RegionSpec spec) {
        return convert(ChunkLoaderManager.createRegion(server, spec.id(), spec.name(), spec.dimension(),
            spec.chunks(), spec.enabled()));
    }

    /** 替换区域的区块集合；名称、维度和启停状态不变。 */
    public static ApiResult updateChunks(MinecraftServer server, UUID regionId, Set<Long> chunks) {
        return convert(ChunkLoaderManager.updateRegionChunks(server, regionId, chunks));
    }

    /** 启用或停用区域。停用会撤销票据但保留声明，之后可原样恢复。 */
    public static ApiResult setEnabled(MinecraftServer server, UUID regionId, boolean enabled) {
        return convert(ChunkLoaderManager.setEnabled(server, regionId, enabled));
    }

    /** 删除区域并撤销其票据。持有者消失时（例如方块被破坏）必须调用。 */
    public static ApiResult removeRegion(MinecraftServer server, UUID regionId) {
        return convert(ChunkLoaderManager.remove(server, regionId));
    }

    public static Optional<RegionSpec> region(MinecraftServer server, UUID regionId) {
        return ChunkLoaderManager.region(server, regionId).map(ChunkLoadingApi::convert);
    }

    /** 当前所有手动加载区域，包含其他来源创建的（例如地图界面和命令）。 */
    public static List<RegionSpec> regions(MinecraftServer server) {
        return ChunkLoaderManager.data(server).regions().stream().map(ChunkLoadingApi::convert).toList();
    }

    /** 把区块坐标打包成 {@link RegionSpec#chunks()} 要的长整型，布局与原版 ChunkPos 一致。 */
    public static long packChunk(int chunkX, int chunkZ) {
        return ChunkKey.pack(chunkX, chunkZ);
    }

    public static int chunkX(long packed) {
        return ChunkKey.x(packed);
    }

    public static int chunkZ(long packed) {
        return ChunkKey.z(packed);
    }

    private static RegionSpec convert(ManualLoadRegion region) {
        return new RegionSpec(region.id(), region.name(), region.dimension(), region.chunks(), region.enabled());
    }

    private static ApiResult convert(ChunkLoaderManager.Result result) {
        return result.successful() ? ApiResult.success() : ApiResult.failure(result.reason());
    }
}
