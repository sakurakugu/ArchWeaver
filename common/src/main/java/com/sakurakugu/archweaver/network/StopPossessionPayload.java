package com.sakurakugu.archweaver.network;

import com.sakurakugu.archweaver.ArchWeaverMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 客户端主动退出附身的一次性请求，不承载任何移动或行为输入。 */
public record StopPossessionPayload() implements CustomPacketPayload {
    public static final Type<StopPossessionPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(ArchWeaverMod.MOD_ID, "stop_possession")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, StopPossessionPayload> STREAM_CODEC =
        CustomPacketPayload.codec(StopPossessionPayload::write, StopPossessionPayload::new);

    private StopPossessionPayload(RegistryFriendlyByteBuf ignored) {
        this();
    }

    private void write(RegistryFriendlyByteBuf ignored) {
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
