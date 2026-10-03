package com.sakurakugu.archweaver.client.chunkloading;

import com.sakurakugu.archweaver.ArchWeaverMod;
import com.sakurakugu.archweaver.network.ChunkMapSnapshotPayload.RegionView;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

/** 在 F3 调试信息中显示玩家当前所在的本模组加载范围。 */
public final class ChunkLoadingDebugEntry implements DebugScreenEntry {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(
        ArchWeaverMod.MOD_ID, "chunk_loading");

    @Override
    public void display(DebugScreenDisplayer displayer, @Nullable Level level,
                        @Nullable LevelChunk clientChunk, @Nullable LevelChunk serverChunk) {
        Minecraft minecraft = Minecraft.getInstance();
        var snapshot = ClientChunkLoadingState.snapshot();
        String entries = "";
        if (snapshot != null && minecraft.player != null) {
            int chunkX = minecraft.player.chunkPosition().x();
            int chunkZ = minecraft.player.chunkPosition().z();
            String dimension = minecraft.player.level().dimension().identifier().toString();
            entries = snapshot.regions().stream()
                .filter(region -> region.dimension().equals(dimension))
                .filter(region -> region.contains(chunkX, chunkZ))
                .map(ChunkLoadingDebugEntry::describe)
                .collect(Collectors.joining("; "));
            boolean loadedByFakePlayer = snapshot.fakePlayers().stream()
                .anyMatch(fake -> fake.loadsChunk(dimension, chunkX, chunkZ));
            if (loadedByFakePlayer) {
                entries = entries.isEmpty()
                    ? Component.translatable("fakeplayer.chunkloader.fake_label").getString()
                    : entries + " | " + Component.translatable("fakeplayer.chunkloader.fake_label").getString();
            }
        }
        String status = entries.isEmpty()
            ? Component.translatable("commands.fakeplayer.none").getString()
            : entries;
        displayer.addPriorityLine(Component.translatable(
            "f3.fakeplayer.chunkloader.line", status).getString());
    }

    private static String describe(RegionView region) {
        return Component.translatable("f3.fakeplayer.chunkloader.entry", region.name(),
            region.chunks().size(), Component.translatable(
                "gui.fakeplayer.chunkloader.level_strong")).getString();
    }
}
