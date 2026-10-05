package com.sakurakugu.archweaver.chunkloading;

import com.sakurakugu.archweaver.network.ApplyChunkLoadEditsPayload;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 地图前端提交的批量编辑入口。
 *
 * <p>网络这一层只负责认人、认维度和并发版本，业务规则全部交给
 * {@link ChunkLoaderManager#submit}，与命令、界面走同一条折叠 → 校验 → 提交路径。
 */
public final class ChunkLoadApplicationService {
    private ChunkLoadApplicationService() {
    }

    public static ApplyResult apply(ServerPlayer viewer, ApplyChunkLoadEditsPayload payload) {
        MinecraftServer server = viewer.level().getServer();
        ChunkLoaderSavedData data = ChunkLoaderManager.data(server);
        if (payload.expectedRevision() != data.revision()) return ApplyResult.conflict(data.revision());
        Identifier dimension;
        try {
            dimension = Identifier.parse(payload.dimension());
        } catch (RuntimeException exception) {
            return ApplyResult.failure("维度标识非法", data.revision());
        }
        if (!dimension.equals(viewer.level().dimension().identifier())) {
            return ApplyResult.failure("只能编辑当前所在维度", data.revision());
        }

        List<ChunkLoaderManager.Change> changes = new ArrayList<>(payload.edits().size());
        for (ApplyChunkLoadEditsPayload.Edit edit : payload.edits()) {
            changes.add(plan -> fold(plan, server, dimension, edit));
        }
        return applied(ChunkLoaderManager.submit(server, changes), data.revision());
    }

    /** 把一条网络编辑折叠进终态；整批都在同一维度，越界编辑在这里被拒。 */
    private static String fold(ChunkLoadPlan plan, MinecraftServer server, Identifier dimension,
                               ApplyChunkLoadEditsPayload.Edit edit) {
        return switch (edit.action()) {
            case CREATE_REGION -> {
                if (edit.chunks().isEmpty()) yield "新区域不能为空";
                yield plan.create(new ManualLoadRegion(edit.targetId(), edit.name(), dimension,
                    Set.copyOf(edit.chunks()), edit.enabled()));
            }
            case ADD_CHUNKS -> changeChunks(plan, dimension, edit.targetId(), edit.chunks(), true);
            case REMOVE_CHUNKS -> changeChunks(plan, dimension, edit.targetId(), edit.chunks(), false);
            case SET_ENABLED -> replace(plan, dimension, edit.targetId(),
                region -> region.withEnabled(edit.enabled()));
            case DELETE_REGION -> remove(plan, dimension, edit.targetId());
            case SET_FAKE_POLICY -> setPolicy(plan, server, edit);
        };
    }

    private static String changeChunks(ChunkLoadPlan plan, Identifier dimension, UUID id,
                                       List<Long> chunks, boolean add) {
        return replace(plan, dimension, id, region -> {
            Set<Long> changed = new HashSet<>(region.chunks());
            if (add) changed.addAll(chunks); else changed.removeAll(chunks);
            return region.withChunks(changed);
        });
    }

    /** 编辑之前先核对该区域属于 payload 声明的维度。 */
    private static String replace(ChunkLoadPlan plan, Identifier dimension, UUID id,
                                  UnaryOperator<ManualLoadRegion> operation) {
        String rejected = checkDimension(plan, dimension, id);
        if (rejected != null) return rejected;
        return plan.replace(operation.apply(plan.region(id).orElseThrow()));
    }

    private static String remove(ChunkLoadPlan plan, Identifier dimension, UUID id) {
        String rejected = checkDimension(plan, dimension, id);
        return rejected != null ? rejected : plan.remove(id);
    }

    /** payload 只声明了目标维度，被编辑的区域自身可能属于其它维度。 */
    private static String checkDimension(ChunkLoadPlan plan, Identifier dimension, UUID id) {
        ManualLoadRegion region = plan.region(id).orElse(null);
        if (region == null) return ChunkLoadPlan.MISSING_REGION;
        return region.dimension().equals(dimension) ? null : "只能编辑当前所在维度的区域";
    }

    /**
     * 模拟距离是这一条请求自身的属性，逐条校验即可；
     * 玩家加载预算依赖最终策略集合，交给终态校验统一算。
     */
    private static String setPolicy(ChunkLoadPlan plan, MinecraftServer server,
                                    ApplyChunkLoadEditsPayload.Edit edit) {
        FakePlayerLoadMode mode = edit.enabled() ? FakePlayerLoadMode.DOLL : FakePlayerLoadMode.PLAYER;
        String rejected = FakePlayerSimulationService.validateDistance(server, mode, edit.simulationDistance());
        if (rejected != null) return rejected;
        plan.setPolicy(new FakePlayerLoadPolicy(edit.targetId(), mode, edit.simulationDistance()));
        return null;
    }

    /** 命令和界面只需要"成没成、为什么"，版本号留给批量编辑的并发控制。 */
    private static ApplyResult applied(ChunkLoaderManager.Result result, long revision) {
        return result.successful()
            ? ApplyResult.success(revision)
            : ApplyResult.failure(result.reason(), revision);
    }

    public record ApplyResult(boolean successful, boolean conflict, long revision, String reason) {
        public static ApplyResult success(long revision) { return new ApplyResult(true, false, revision, ""); }
        public static ApplyResult conflict(long revision) { return new ApplyResult(false, true, revision, "快照版本冲突"); }
        public static ApplyResult failure(String reason, long revision) { return new ApplyResult(false, false, revision, reason); }
    }
}
