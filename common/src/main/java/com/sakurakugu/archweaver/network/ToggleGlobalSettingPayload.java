package com.sakurakugu.archweaver.network;

import com.sakurakugu.archweaver.ArchWeaverMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 从地图设置页切换一项假人全局设置。 */
public record ToggleGlobalSettingPayload(int settingIndex) implements CustomPacketPayload {
    public static final Type<ToggleGlobalSettingPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(ArchWeaverMod.MOD_ID, "toggle_global_setting")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleGlobalSettingPayload> STREAM_CODEC =
        CustomPacketPayload.codec(ToggleGlobalSettingPayload::write, ToggleGlobalSettingPayload::new);

    private ToggleGlobalSettingPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(settingIndex);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
