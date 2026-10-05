package com.sakurakugu.archweaver.api.chunk;

import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;

/**
 * 一组强加载区块的声明。
 *
 * <p>{@code id} 由调用方提供并自行持久化——方块一类的持有者有自己稳定的身份，
 * 之后要按它查询和撤销。{@code chunks} 是 {@link ChunkLoadingApi#packChunk} 打包出来的
 * 区块坐标集合，形状任意，不必是方形。
 */
public record RegionSpec(
    UUID id,
    String name,
    Identifier dimension,
    Set<Long> chunks,
    boolean enabled
) {
    public RegionSpec {
        chunks = Set.copyOf(chunks);
    }
}
