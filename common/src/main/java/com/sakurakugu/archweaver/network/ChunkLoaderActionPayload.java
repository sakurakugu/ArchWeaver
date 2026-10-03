package com.sakurakugu.archweaver.network;

import com.sakurakugu.archweaver.ArchWeaverMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 提交区块加载点管理界面的操作。 */
public record ChunkLoaderActionPayload(Action action, String name, String newName)
    implements CustomPacketPayload {
    public static final Type<ChunkLoaderActionPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(ArchWeaverMod.MOD_ID, "chunk_loader_action")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ChunkLoaderActionPayload> STREAM_CODEC =
        CustomPacketPayload.codec(ChunkLoaderActionPayload::write, ChunkLoaderActionPayload::new);

    /** 不需要附加名称的操作（启停、删除、备份、恢复）。 */
    public ChunkLoaderActionPayload(Action action, String name) {
        this(action, name, "");
    }

    private ChunkLoaderActionPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readEnum(Action.class), buffer.readUtf(32), buffer.readUtf(32));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeEnum(action);
        buffer.writeUtf(name, 32);
        buffer.writeUtf(newName, 32);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Action {
        RENAME, ENABLE, DISABLE, REMOVE, BACKUP, RESTORE
    }
}
