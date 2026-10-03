package com.sakurakugu.archweaver.menu;

import com.sakurakugu.archweaver.chunkloading.ChunkLoaderManager;
import com.sakurakugu.archweaver.network.ChunkLoaderActionPayload;
import net.minecraft.network.chat.Component;
import com.sakurakugu.archweaver.platform.PlatformNetworking;
import com.sakurakugu.archweaver.network.ChunkMapSnapshotPayload;
import net.minecraft.server.level.ServerPlayer;

/** 在服务端执行区块加载点界面操作并返回最新快照。 */
public final class ChunkLoaderActions {
    private ChunkLoaderActions() {
    }

    public static void handle(ServerPlayer viewer, ChunkLoaderActionPayload payload) {
        ChunkLoaderManager.Result result = switch (payload.action()) {
            case RENAME -> ChunkLoaderManager.rename(server(viewer), payload.name(), payload.newName());
            case ENABLE -> ChunkLoaderManager.setEnabled(server(viewer), payload.name(), true);
            case DISABLE -> ChunkLoaderManager.setEnabled(server(viewer), payload.name(), false);
            case REMOVE -> ChunkLoaderManager.remove(server(viewer), payload.name());
            case BACKUP -> ChunkLoaderManager.backup(server(viewer))
                ? ChunkLoaderManager.Result.success()
                : ChunkLoaderManager.Result.failure("创建备份失败，请查看服务端日志");
            case RESTORE -> ChunkLoaderManager.restoreLatestBackup(server(viewer));
        };
        if (!result.successful()) {
            viewer.sendSystemMessage(Component.translatable(
                "commands.fakeplayer.chunkloader.failed", result.reason()).withColor(0xFF5555));
        } else {
            viewer.sendSystemMessage(Component.translatable(
                "gui.fakeplayer.chunkloader.action_success." + payload.action().name().toLowerCase(java.util.Locale.ROOT)));
        }
        PlatformNetworking.sendToPlayer(viewer, ChunkMapSnapshotPayload.create(viewer,
            ChunkLoaderManager.data(server(viewer)), false, false));
    }

    private static net.minecraft.server.MinecraftServer server(ServerPlayer viewer) {
        return viewer.level().getServer();
    }
}
