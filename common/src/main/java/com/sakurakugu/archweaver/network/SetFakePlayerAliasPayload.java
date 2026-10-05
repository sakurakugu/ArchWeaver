package com.sakurakugu.archweaver.network;

import com.sakurakugu.archweaver.ArchWeaverMod;
import com.sakurakugu.archweaver.entity.FakePlayerAlias;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 从假人信息面板提交别名修改，空字符串清除别名。 */
public record SetFakePlayerAliasPayload(int containerId, String alias) implements CustomPacketPayload {
    public static final Type<SetFakePlayerAliasPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(ArchWeaverMod.MOD_ID, "set_fake_player_alias"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetFakePlayerAliasPayload> STREAM_CODEC =
        CustomPacketPayload.codec(SetFakePlayerAliasPayload::write, SetFakePlayerAliasPayload::new);

    private SetFakePlayerAliasPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readUtf(FakePlayerAlias.MAX_LENGTH));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeUtf(alias, FakePlayerAlias.MAX_LENGTH);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
