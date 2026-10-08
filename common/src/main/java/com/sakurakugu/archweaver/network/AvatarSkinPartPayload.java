package com.sakurakugu.archweaver.network;

import com.sakurakugu.archweaver.ArchWeaverMod;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelPart;

/** 两种背包共用的皮肤模型部件开关。 */
public record AvatarSkinPartPayload(int containerId, UUID id, PlayerModelPart part, boolean shown) implements CustomPacketPayload {
    public static final Type<AvatarSkinPartPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ArchWeaverMod.MOD_ID, "avatar_skin_part"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AvatarSkinPartPayload> STREAM_CODEC = CustomPacketPayload.codec(AvatarSkinPartPayload::write, AvatarSkinPartPayload::new);
    private AvatarSkinPartPayload(RegistryFriendlyByteBuf buffer) { this(buffer.readVarInt(), buffer.readUUID(), buffer.readEnum(PlayerModelPart.class), buffer.readBoolean()); }
    private void write(RegistryFriendlyByteBuf buffer) { buffer.writeVarInt(containerId); buffer.writeUUID(id); buffer.writeEnum(part); buffer.writeBoolean(shown); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
