package com.sakurakugu.fakeplayer.chunkloading;

import com.sakurakugu.fakeplayer.FakePlayerMod;
import com.sakurakugu.fakeplayer.entity.FakePlayerManager;
import com.sakurakugu.fakeplayer.entity.FakeServerPlayer;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;
import net.neoforged.neoforge.common.world.chunk.TicketSet;

/** 将通用区块加载声明映射为 NeoForge 票据。 */
public final class NeoForgeChunkTicketService implements ChunkTicketService {
    private static final TicketController MANUAL_CONTROLLER = new TicketController(
        Identifier.fromNamespaceAndPath(FakePlayerMod.MOD_ID, "chunk_load_regions"),
        NeoForgeChunkTicketService::validateManual);
    private static final TicketController SIMULATION_CONTROLLER = new TicketController(
        Identifier.fromNamespaceAndPath(FakePlayerMod.MOD_ID, "fake_player_simulation"),
        NeoForgeChunkTicketService::validateSimulation);

    public static void register(RegisterTicketControllersEvent event) {
        event.register(MANUAL_CONTROLLER);
        event.register(SIMULATION_CONTROLLER);
    }

    @Override
    public void add(ServerLevel level, ChunkLoadClaim claim) {
        set(level, claim, true);
    }

    @Override
    public void remove(ServerLevel level, ChunkLoadClaim claim) {
        set(level, claim, false);
    }

    private static void set(ServerLevel level, ChunkLoadClaim claim, boolean add) {
        int x = ChunkPos.getX(claim.chunk());
        int z = ChunkPos.getZ(claim.chunk());
        if (claim.owner().type() == LoadOwner.Type.FAKE_PLAYER) {
            SIMULATION_CONTROLLER.forceChunk(level, claim.owner().id(), x, z, add, true);
        } else {
            MANUAL_CONTROLLER.forceChunk(level, claim.owner().id(), x, z, add, false);
        }
    }

    private static void validateManual(ServerLevel level, TicketHelper helper) {
        ChunkLoaderSavedData data = ChunkLoaderManager.data(level.getServer());
        Map<UUID, ManualLoadRegion> regions = new HashMap<>();
        data.regions().forEach(region -> regions.put(region.id(), region));
        for (Map.Entry<UUID, TicketSet> entry : helper.getEntityTickets().entrySet()) {
            ManualLoadRegion region = regions.get(entry.getKey());
            if (region == null || !valid(level, region)) {
                helper.removeAllTickets(entry.getKey());
                continue;
            }
            removeUnexpectedManual(helper, entry.getKey(), entry.getValue(), region);
        }
        for (var owner : java.util.List.copyOf(helper.getBlockTickets().keySet())) {
            helper.removeAllTickets(owner);
        }
    }

    private static void validateSimulation(ServerLevel level, TicketHelper helper) {
        ChunkLoaderSavedData data = ChunkLoaderManager.data(level.getServer());
        for (Map.Entry<UUID, TicketSet> entry : helper.getEntityTickets().entrySet()) {
            FakePlayerLoadPolicy policy = data.policy(entry.getKey()).orElse(null);
            FakeServerPlayer fake = FakePlayerManager.all(level.getServer()).stream()
                .filter(value -> value.getUUID().equals(entry.getKey()) && value.level() == level)
                .findFirst().orElse(null);
            if (policy == null || !policy.usesCustomSimulation() || fake == null) {
                helper.removeAllTickets(entry.getKey());
                continue;
            }
            int distance = Math.min(policy.simulationDistance(),
                FakePlayerSimulationService.maxSimulationDistance(level.getServer()));
            removeUnexpectedSimulation(helper, entry.getKey(), entry.getValue(), fake, distance);
        }
        for (var owner : java.util.List.copyOf(helper.getBlockTickets().keySet())) {
            helper.removeAllTickets(owner);
        }
    }

    private static boolean valid(ServerLevel level, ManualLoadRegion region) {
        return region.enabled() && region.dimension().equals(level.dimension().identifier());
    }

    private static void removeUnexpectedManual(TicketHelper helper, UUID owner, TicketSet tickets,
                                               ManualLoadRegion region) {
        for (long chunk : tickets.normal()) {
            if (!region.chunks().contains(chunk)) {
                helper.removeTicket(owner, chunk, false);
            }
        }
        for (long chunk : tickets.naturalSpawning()) {
            helper.removeTicket(owner, chunk, true);
        }
    }

    private static void removeUnexpectedSimulation(TicketHelper helper, UUID owner, TicketSet tickets,
                                                   FakeServerPlayer fake, int distance) {
        Set<Long> expected = ChunkLoadPlanner.square(fake.chunkPosition().x(), fake.chunkPosition().z(), distance);
        for (long chunk : tickets.normal()) {
            if (!expected.contains(chunk)) {
                helper.removeTicket(owner, chunk, false);
            }
        }
        for (long chunk : tickets.naturalSpawning()) {
            if (!expected.contains(chunk)) {
                helper.removeTicket(owner, chunk, true);
            }
        }
    }
}
