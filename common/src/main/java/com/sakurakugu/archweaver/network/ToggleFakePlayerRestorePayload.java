package com.sakurakugu.archweaver.network;

import com.sakurakugu.archweaver.ArchWeaverMod;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 切换单个假人的服务器重启恢复设置。 */
public record ToggleFakePlayerRestorePayload(int containerId, UUID fakePlayerId) implements CustomPacketPayload {
    public static final Type<ToggleFakePlayerRestorePayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(ArchWeaverMod.MOD_ID, "toggle_fake_player_restore")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleFakePlayerRestorePayload> STREAM_CODEC =
        CustomPacketPayload.codec(ToggleFakePlayerRestorePayload::write, ToggleFakePlayerRestorePayload::new);

    private ToggleFakePlayerRestorePayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readUUID());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeUUID(fakePlayerId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
