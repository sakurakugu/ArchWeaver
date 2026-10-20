package com.sakurakugu.archweaver.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.sakurakugu.archweaver.chunkloading.FabricSpawningChunks;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** 全局刷怪上限按原版与手动模拟的并集计算，重叠范围只计一次。 */
@Mixin(ServerChunkCache.class)
public abstract class SpawningChunkCacheMixin {
    @Shadow @Final private ServerLevel level;

    @WrapOperation(method = "tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;J)V", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/server/level/DistanceManager;getNaturalSpawnChunkCount()I"))
    private int archweaver$includeCustomSpawnCap(DistanceManager manager, Operation<Integer> original) {
        int vanilla = original.call(manager);
        var chunks = FabricSpawningChunks.customChunks(level);
        if (chunks.isEmpty()) return vanilla;
        manager.getSpawnCandidateChunks().forEachRemaining((long chunk) -> chunks.add(chunk));
        return chunks.size();
    }
}
