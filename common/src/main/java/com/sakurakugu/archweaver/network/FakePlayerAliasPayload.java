package com.sakurakugu.archweaver.network;

import com.sakurakugu.archweaver.ArchWeaverMod;
import com.sakurakugu.archweaver.entity.FakePlayerAlias;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 独立同步头顶名牌所需的信息，无需先打开管理界面。
 * 别名为空时仍会下发，由 {@code emptyAliasMarker} 决定客户端显示「假人」占位还是清除该假人的名牌。
 */
public record FakePlayerAliasPayload(UUID playerId, String alias, boolean aliasFirst, boolean emptyAliasMarker)
    implements CustomPacketPayload {
    public static final Type<FakePlayerAliasPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(ArchWeaverMod.MOD_ID, "fake_player_alias"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FakePlayerAliasPayload> STREAM_CODEC =
        CustomPacketPayload.codec(FakePlayerAliasPayload::write, FakePlayerAliasPayload::new);

    private FakePlayerAliasPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readUtf(FakePlayerAlias.MAX_LENGTH), buffer.readBoolean(), buffer.readBoolean());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(playerId);
        buffer.writeUtf(alias, FakePlayerAlias.MAX_LENGTH);
        buffer.writeBoolean(aliasFirst);
        buffer.writeBoolean(emptyAliasMarker);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
