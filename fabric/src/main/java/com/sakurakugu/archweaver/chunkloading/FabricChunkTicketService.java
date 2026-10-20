package com.sakurakugu.archweaver.chunkloading;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

/** 临时票据只由存档中的区域和假人策略重建，不向原版注册表添加内容。 */
public final class FabricChunkTicketService implements ChunkTicketService {
    private static final TicketType TYPE = new TicketType(TicketType.NO_TIMEOUT,
        TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE);
    private final Map<ServerLevel, Map<Long, Set<ChunkLoadClaim>>> claims = new HashMap<>();
    public void add(ServerLevel level, ChunkLoadClaim claim) {
        var owners = claims.computeIfAbsent(level, ignored -> new HashMap<>())
            .computeIfAbsent(claim.chunk(), ignored -> new HashSet<>());
        if (owners.add(claim) && owners.size() == 1)
            level.getChunkSource().addTicketWithRadius(TYPE, ChunkPos.unpack(claim.chunk()), 2);
    }
    public void remove(ServerLevel level, ChunkLoadClaim claim) {
        var chunks = claims.get(level);
        if (chunks == null) return;
        var owners = chunks.get(claim.chunk());
        if (owners == null || !owners.remove(claim) || !owners.isEmpty()) return;
        level.getChunkSource().removeTicketWithRadius(TYPE, ChunkPos.unpack(claim.chunk()), 2);
        chunks.remove(claim.chunk());
        if (chunks.isEmpty()) claims.remove(level);
    }
    public void clear() { claims.clear(); }
}
