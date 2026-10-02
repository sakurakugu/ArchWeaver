package com.sakurakugu.archweaver.network;

import com.sakurakugu.archweaver.ArchWeaverMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 请求重新打开指定假人的物品栏，用于末影箱页面返回上一级。 */
public record OpenFakePlayerInventoryPayload(String targetName) implements CustomPacketPayload {
    public static final Type<OpenFakePlayerInventoryPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(ArchWeaverMod.MOD_ID, "open_fake_player_inventory")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenFakePlayerInventoryPayload> STREAM_CODEC =
        CustomPacketPayload.codec(OpenFakePlayerInventoryPayload::write, OpenFakePlayerInventoryPayload::new);

    private OpenFakePlayerInventoryPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUtf(64));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(targetName, 64);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
