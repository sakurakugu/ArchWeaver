package com.sakurakugu.archweaver.fabric.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.sakurakugu.archweaver.chunkloading.FabricSpawningChunks;
import com.sakurakugu.archweaver.chunkloading.FakePlayerSimulationService;
import com.sakurakugu.archweaver.entity.FakeServerPlayer;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkMap.class)
public abstract class SpawningChunkMapMixin {
    @Shadow @Final private ServerLevel level;

    @ModifyExpressionValue(method = "collectSpawningChunks", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/server/level/ChunkMap$DistanceManager;getSpawnCandidateChunks()Lit/unimi/dsi/fastutil/longs/LongIterator;"))
    private LongIterator archweaver$includeCustom(LongIterator vanilla) {
        return FabricSpawningChunks.includeCustom(level, vanilla);
    }

    @Inject(method = "playerIsCloseEnoughForSpawning", at = @At("HEAD"), cancellable = true)
    private void archweaver$respectSimulationPolicy(ServerPlayer player, ChunkPos chunk,
        CallbackInfoReturnable<Boolean> callback) {
        if (player instanceof FakeServerPlayer fake && FakePlayerSimulationService.skipsVanillaLoading(fake))
            callback.setReturnValue(FabricSpawningChunks.canSpawnNear(fake, chunk));
    }
}
