package com.sakurakugu.archweaver.network;

import com.sakurakugu.archweaver.ArchWeaverMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 主页面请求加载或卸载已登记假玩家。 */
public record FakePlayerLifecyclePayload(String name, Action action) implements CustomPacketPayload {
    public enum Action { LOAD, UNLOAD }
    public static final Type<FakePlayerLifecyclePayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(ArchWeaverMod.MOD_ID, "fake_player_lifecycle"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FakePlayerLifecyclePayload> STREAM_CODEC =
        CustomPacketPayload.codec(FakePlayerLifecyclePayload::write, FakePlayerLifecyclePayload::new);
    private FakePlayerLifecyclePayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUtf(64), buffer.readEnum(Action.class));
    }
    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(name, 64);
        buffer.writeEnum(action);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
