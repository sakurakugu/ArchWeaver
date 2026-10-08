package com.sakurakugu.archweaver.entity;

import com.sakurakugu.archweaver.chunkloading.ChunkLoaderManager;
import com.sakurakugu.archweaver.config.ArchWeaverConfig;
import com.sakurakugu.archweaver.network.ChunkMapOpenTarget;
import com.sakurakugu.archweaver.network.ChunkMapSnapshotPayload;
import com.sakurakugu.archweaver.platform.PlatformNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** 命令、生命周期操作和类型转换共用列表更新，包含正在作为背包背景显示的列表。 */
public final class TargetListSync {
    private TargetListSync() { }

    public static void refresh(MinecraftServer server) {
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            if (viewer instanceof FakeServerPlayer || !ArchWeaverConfig.canUseCommands(viewer.createCommandSourceStack())) continue;
            MannequinManager.syncAnglesTo(viewer);
            PlatformNetworking.sendToPlayer(viewer, ChunkMapSnapshotPayload.create(viewer,
                ChunkLoaderManager.data(server), ChunkMapOpenTarget.NONE));
        }
    }
}
