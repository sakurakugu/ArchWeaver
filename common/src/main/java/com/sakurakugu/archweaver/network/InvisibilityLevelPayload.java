package com.sakurakugu.archweaver.network;

import com.sakurakugu.archweaver.ArchWeaverMod;
import com.sakurakugu.archweaver.invisibility.InvisibilityLevels;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

/** 同步原版效果的等级；维度和 UUID 防止实体编号复用导致错误隐藏。 */
public record InvisibilityLevelPayload(Identifier dimension, int entityId, UUID uuid, int level)
    implements CustomPacketPayload {
    public static final Type<InvisibilityLevelPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(ArchWeaverMod.MOD_ID, "invisibility_level"));
    public static final StreamCodec<RegistryFriendlyByteBuf, InvisibilityLevelPayload> STREAM_CODEC =
        CustomPacketPayload.codec(InvisibilityLevelPayload::write, InvisibilityLevelPayload::new);

    public InvisibilityLevelPayload(LivingEntity entity) {
        this(entity.level().dimension().identifier(), entity.getId(), entity.getUUID(), InvisibilityLevels.effectLevel(entity));
    }

    private InvisibilityLevelPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readIdentifier(), buffer.readVarInt(), buffer.readUUID(), buffer.readUnsignedByte());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeIdentifier(dimension);
        buffer.writeVarInt(entityId);
        buffer.writeUUID(uuid);
        buffer.writeByte(level);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
