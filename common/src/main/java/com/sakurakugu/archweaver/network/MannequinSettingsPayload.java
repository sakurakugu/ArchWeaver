package com.sakurakugu.archweaver.network;

import com.sakurakugu.archweaver.ArchWeaverMod;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 玩偶背包边栏的属性、姿势、皮肤部件和视角设置。 */
public record MannequinSettingsPayload(UUID id, Action action, int value, boolean enabled, int pitch, int yaw,
                                       float x, float y, float z) implements CustomPacketPayload {
    public MannequinSettingsPayload(UUID id, Action action, int value, boolean enabled) {
        this(id, action, value, enabled, 0, 0, 0, 0, 0);
    }
    public MannequinSettingsPayload(UUID id, Action action, int value, boolean enabled, int pitch, int yaw) {
        this(id, action, value, enabled, pitch, yaw, 0, 0, 0);
    }
    /** {@code value} 用于姿势、皮肤部件和身体偏航角，{@code pitch}/{@code yaw} 用于视角，{@code x}-{@code z} 用于四肢角度。 */
    public enum Action { IMMOVABLE, BIOLOGICAL_BEHAVIOR, POSE, SKIN_PART, LIMB_ANGLE, VIEW_ROTATION, BODY_YAW, TOGGLE_BODY_FOLLOWS_HEAD }
    public static final Type<MannequinSettingsPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(ArchWeaverMod.MOD_ID, "mannequin_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MannequinSettingsPayload> STREAM_CODEC =
        CustomPacketPayload.codec(MannequinSettingsPayload::write, MannequinSettingsPayload::new);
    private MannequinSettingsPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readEnum(Action.class), buffer.readVarInt(), buffer.readBoolean(),
            buffer.readVarInt(), buffer.readVarInt(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
    }
    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(id); buffer.writeEnum(action); buffer.writeVarInt(value); buffer.writeBoolean(enabled);
        buffer.writeVarInt(pitch); buffer.writeVarInt(yaw);
        buffer.writeFloat(x); buffer.writeFloat(y); buffer.writeFloat(z);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
