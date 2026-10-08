package com.sakurakugu.archweaver.network;

import com.sakurakugu.archweaver.ArchWeaverMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 请求加载或卸载已登记目标。 */
public record MannequinLifecyclePayload(String name, Action action) implements CustomPacketPayload {
    public enum Action { LOAD, UNLOAD }

    public static final Type<MannequinLifecyclePayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(ArchWeaverMod.MOD_ID, "mannequin_lifecycle"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MannequinLifecyclePayload> STREAM_CODEC =
        CustomPacketPayload.codec(MannequinLifecyclePayload::write, MannequinLifecyclePayload::new);

    private MannequinLifecyclePayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUtf(64), buffer.readEnum(Action.class));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(name, 64);
        buffer.writeEnum(action);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
