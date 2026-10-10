package com.sakurakugu.archweaver.client.chunkloading;

import com.sakurakugu.archweaver.chunkloading.ChunkKey;
import java.util.*;

/** 先合并同行同等级的区块，再合并相邻行中跨度相同的条带，避免每区块一个多边形。 */
public final class ChunkMapRectangles {
    public record Rectangle(int minX, int minZ, int maxX, int maxZ, ChunkMapLoadLevel level) { }
    private record Span(int minX, int maxX, ChunkMapLoadLevel level) { }

    private ChunkMapRectangles() { }

    public static List<Rectangle> merge(Map<Long, ChunkMapLoadLevel> cells) {
        var rows = new TreeMap<Integer, TreeMap<Integer, ChunkMapLoadLevel>>();
        cells.forEach((key, level) -> rows.computeIfAbsent(ChunkKey.z(key), ignored -> new TreeMap<>())
            .put(ChunkKey.x(key), level));
        var result = new ArrayList<Rectangle>();
        Map<Span, Rectangle> active = Map.of();
        for (var row : rows.entrySet()) {
            var spans = new ArrayList<Span>();
            Span span = null;
            for (var cell : row.getValue().entrySet()) {
                if (span != null && (long) span.maxX() + 1 == cell.getKey() && span.level() == cell.getValue()) {
                    span = new Span(span.minX(), cell.getKey(), span.level());
                } else {
                    if (span != null) spans.add(span);
                    span = new Span(cell.getKey(), cell.getKey(), cell.getValue());
                }
            }
            if (span != null) spans.add(span);
            Map<Span, Rectangle> next = new LinkedHashMap<>();
            for (Span current : spans) {
                Rectangle prior = active.get(current);
                if (prior != null && (long) prior.maxZ() + 1 == row.getKey()) {
                    next.put(current, new Rectangle(prior.minX(), prior.minZ(), prior.maxX(), row.getKey(), prior.level()));
                } else {
                    next.put(current, new Rectangle(current.minX(), row.getKey(), current.maxX(), row.getKey(), current.level()));
                }
            }
            for (var entry : active.entrySet()) {
                var replacement = next.get(entry.getKey());
                if (replacement == null || replacement.minZ() != entry.getValue().minZ()) result.add(entry.getValue());
            }
            active = next;
        }
        result.addAll(active.values());
        result.sort(Comparator.comparingInt(Rectangle::minZ).thenComparingInt(Rectangle::minX));
        return List.copyOf(result);
    }
}
