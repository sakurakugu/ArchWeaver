package com.sakurakugu.archweaver.network;

import com.sakurakugu.archweaver.ArchWeaverMod;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

/** 菜单目标的实时信息，不依赖客户端实体追踪，也不截断远处的坐标。 */
public record TargetInfoPayload(int containerId, UUID id, String name, String alias, String dimension,
                                int x, int y, int z, float health, float maxHealth, int armor,
                                int air, int maxAir, boolean biological) implements CustomPacketPayload {
    public static final Type<TargetInfoPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(ArchWeaverMod.MOD_ID, "target_info"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TargetInfoPayload> STREAM_CODEC =
        CustomPacketPayload.codec(TargetInfoPayload::write, TargetInfoPayload::new);

    public static TargetInfoPayload capture(int containerId, LivingEntity target, String name, String alias,
                                             boolean biological) {
        return new TargetInfoPayload(containerId, target.getUUID(), name, alias,
            target.level().dimension().identifier().toString(), target.getBlockX(), target.getBlockY(), target.getBlockZ(),
            target.getHealth(), target.getMaxHealth(), target.getArmorValue(), target.getAirSupply(),
            target.getMaxAirSupply(), biological);
    }

    /** 打开菜单的附加数据写入时，服务端尚未绑定新菜单，客户端使用构造参数绑定编号。 */
    public TargetInfoPayload forContainer(int containerId) {
        return new TargetInfoPayload(containerId, id, name, alias, dimension, x, y, z,
            health, maxHealth, armor, air, maxAir, biological);
    }

    private TargetInfoPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readUUID(), buffer.readUtf(64), buffer.readUtf(), buffer.readUtf(),
            buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readFloat(), buffer.readFloat(),
            buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeUUID(id);
        buffer.writeUtf(name, 64);
        buffer.writeUtf(alias);
        buffer.writeUtf(dimension);
        buffer.writeVarInt(x);
        buffer.writeVarInt(y);
        buffer.writeVarInt(z);
        buffer.writeFloat(health);
        buffer.writeFloat(maxHealth);
        buffer.writeVarInt(armor);
        buffer.writeVarInt(air);
        buffer.writeVarInt(maxAir);
        buffer.writeBoolean(biological);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
