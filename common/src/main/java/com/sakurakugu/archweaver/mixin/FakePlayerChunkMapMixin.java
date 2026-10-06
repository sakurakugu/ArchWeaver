package com.sakurakugu.archweaver.mixin;

import com.sakurakugu.archweaver.chunkloading.FakePlayerSimulationService;
import com.sakurakugu.archweaver.entity.FakeServerPlayer;
import java.util.ArrayList;
import java.util.List;
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

/** 关闭和手动策略绕过原版玩家加载票据；手动范围由模组独立维护。 */
@Mixin(ChunkMap.class)
public abstract class FakePlayerChunkMapMixin {
    @Shadow @Final private ServerLevel level;

    @Inject(method = "skipPlayer(Lnet/minecraft/server/level/ServerPlayer;)Z", at = @At("HEAD"), cancellable = true)
    private void archweaver$skipVanillaPlayerTickets(ServerPlayer player, CallbackInfoReturnable<Boolean> callback) {
        if (player instanceof FakeServerPlayer fake && FakePlayerSimulationService.skipsVanillaLoading(fake)) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "getPlayerViewDistance(Lnet/minecraft/server/level/ServerPlayer;)I", at = @At("HEAD"), cancellable = true)
    private void archweaver$limitFakeChunkTracking(ServerPlayer player, CallbackInfoReturnable<Integer> callback) {
        if (player instanceof FakeServerPlayer fake && FakePlayerSimulationService.skipsVanillaLoading(fake)) {
            callback.setReturnValue(0);
        }
    }

    @Inject(method = "anyPlayerCloseEnoughForSpawning(Lnet/minecraft/world/level/ChunkPos;)Z",
        at = @At("HEAD"), cancellable = true)
    private void archweaver$allowCustomSpawning(ChunkPos chunk, CallbackInfoReturnable<Boolean> callback) {
        if (level.players().stream().filter(FakeServerPlayer.class::isInstance).map(FakeServerPlayer.class::cast)
            .anyMatch(fake -> archweaver$canSpawnNear(fake, chunk))) callback.setReturnValue(true);
    }

    @Inject(method = "getPlayersCloseForSpawning(Lnet/minecraft/world/level/ChunkPos;)Ljava/util/List;",
        at = @At("RETURN"), cancellable = true)
    private void archweaver$includeCustomSpawningPlayers(ChunkPos chunk,
        CallbackInfoReturnable<List<ServerPlayer>> callback) {
        List<ServerPlayer> players = new ArrayList<>(callback.getReturnValue());
        players.removeIf(player -> player instanceof FakeServerPlayer fake
            && FakePlayerSimulationService.usesCustomSimulation(fake) && !archweaver$canSpawnNear(fake, chunk));
        for (ServerPlayer player : level.players()) {
            if (player instanceof FakeServerPlayer fake && archweaver$canSpawnNear(fake, chunk)
                && !players.contains(fake)) players.add(fake);
        }
        callback.setReturnValue(List.copyOf(players));
    }

    private boolean archweaver$canSpawnNear(FakeServerPlayer fake, ChunkPos chunk) {
        int distance = FakePlayerSimulationService.customSimulationDistance(fake);
        if (distance < 0 || fake.level() != level || fake.isSpectator()
            || Math.abs(fake.chunkPosition().x() - chunk.x()) > distance
            || Math.abs(fake.chunkPosition().z() - chunk.z()) > distance) return false;
        double dx = chunk.getMiddleBlockX() - fake.getX();
        double dz = chunk.getMiddleBlockZ() - fake.getZ();
        return dx * dx + dz * dz < 16384.0;
    }
}
