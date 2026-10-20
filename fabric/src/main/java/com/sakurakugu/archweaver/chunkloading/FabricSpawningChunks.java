package com.sakurakugu.archweaver.chunkloading;

import com.sakurakugu.archweaver.entity.FakeServerPlayer;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/** 补上绕过原版玩家票据的假人刷怪候选区块；普通区域加载票据不参与。 */
public final class FabricSpawningChunks {
    private FabricSpawningChunks() { }

    public static boolean canSpawnNear(FakeServerPlayer fake, ChunkPos chunk) {
        int distance = FakePlayerSimulationService.customSimulationDistance(fake);
        if (distance < 0 || fake.isSpectator()
            || Math.abs(fake.chunkPosition().x() - chunk.x()) > distance
            || Math.abs(fake.chunkPosition().z() - chunk.z()) > distance) return false;
        double dx = chunk.getMiddleBlockX() - fake.getX();
        double dz = chunk.getMiddleBlockZ() - fake.getZ();
        return dx * dx + dz * dz < 16384.0;
    }

    public static LongSet customChunks(ServerLevel level) {
        LongSet chunks = new LongOpenHashSet();
        for (var player : level.players()) {
            if (!(player instanceof FakeServerPlayer fake) || fake.isSpectator()) continue;
            int distance = Math.min(8, FakePlayerSimulationService.customSimulationDistance(fake));
            for (int x = -distance; x <= distance; x++) {
                for (int z = -distance; z <= distance; z++) {
                    ChunkPos chunk = new ChunkPos(fake.chunkPosition().x() + x, fake.chunkPosition().z() + z);
                    if (canSpawnNear(fake, chunk)) chunks.add(chunk.pack());
                }
            }
        }
        return chunks;
    }

    public static LongIterator includeCustom(ServerLevel level, LongIterator vanilla) {
        LongSet chunks = customChunks(level);
        if (chunks.isEmpty()) return vanilla;
        vanilla.forEachRemaining((long chunk) -> chunks.add(chunk));
        return chunks.iterator();
    }
}
