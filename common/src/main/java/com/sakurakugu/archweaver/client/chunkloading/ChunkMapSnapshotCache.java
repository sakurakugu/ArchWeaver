package com.sakurakugu.archweaver.client.chunkloading;

import com.sakurakugu.archweaver.network.ChunkMapSnapshotPayload;
import java.util.HashMap;
import java.util.Map;

/** 按维度保留完整快照；请求编号和版本共同阻止旧响应覆盖新状态。 */
public final class ChunkMapSnapshotCache {
    private final Map<String, ChunkMapSnapshotPayload> values = new HashMap<>();
    private final Map<String, Long> requests = new HashMap<>();

    public ChunkMapSnapshotPayload get(String dimension) { return values.get(dimension); }

    public ChunkMapSnapshotPayload accept(ChunkMapSnapshotPayload value) {
        var previous = values.get(value.dimension());
        long lastRequest = requests.getOrDefault(value.dimension(), 0L);
        if (value.requestId() != 0 && value.requestId() <= lastRequest) return null;
        if (previous != null && value.revision() < previous.revision()) return null;
        if (value.requestId() != 0) requests.put(value.dimension(), value.requestId());
        if (value.regionsUnchanged()) {
            if (previous == null || previous.revision() != value.revision()) {
                values.remove(value.dimension());
                return null;
            }
            value = value.withPreviousRegions(previous);
        }
        values.put(value.dimension(), value);
        return value;
    }

    public void clear() { values.clear(); requests.clear(); }
}
