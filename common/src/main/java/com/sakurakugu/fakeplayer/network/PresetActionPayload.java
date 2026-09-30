package com.sakurakugu.fakeplayer.network;

import com.sakurakugu.fakeplayer.FakePlayerMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 提交预设管理界面的字符串参数操作。 */
public record PresetActionPayload(int containerId, Action action, String first, String second, String third)
    implements CustomPacketPayload {
    public static final Type<PresetActionPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(FakePlayerMod.MOD_ID, "preset_action")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, PresetActionPayload> STREAM_CODEC =
        CustomPacketPayload.codec(PresetActionPayload::write, PresetActionPayload::new);

    private PresetActionPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readEnum(Action.class), buffer.readUtf(),
            buffer.readUtf(), buffer.readUtf());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeEnum(action);
        buffer.writeUtf(first);
        buffer.writeUtf(second);
        buffer.writeUtf(third);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Action {
        SAVE_PRESET,
        LOAD_PRESET,
        REMOVE_PRESET,
        CREATE_GROUP,
        ADD_TO_GROUP,
        REMOVE_FROM_GROUP,
        LOAD_GROUP,
        UNLOAD_GROUP,
        REMOVE_GROUP
    }
}
