package com.sakurakugu.archweaver.network;

import com.sakurakugu.archweaver.ArchWeaverMod;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 玩偶背包边栏的属性、姿势和皮肤部件设置。 */
public record MannequinSettingsPayload(UUID id, Action action, int value, boolean enabled, float x, float y, float z) implements CustomPacketPayload {
    public MannequinSettingsPayload(UUID id, Action action, int value, boolean enabled) {
        this(id, action, value, enabled, 0, 0, 0);
    }
    public enum Action { IMMOVABLE, BIOLOGICAL_BEHAVIOR, POSE, SKIN_PART, LIMB_ANGLE }
    public static final Type<MannequinSettingsPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(ArchWeaverMod.MOD_ID, "mannequin_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MannequinSettingsPayload> STREAM_CODEC =
        CustomPacketPayload.codec(MannequinSettingsPayload::write, MannequinSettingsPayload::new);
    private MannequinSettingsPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readEnum(Action.class), buffer.readVarInt(), buffer.readBoolean(),
            buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
    }
    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(id); buffer.writeEnum(action); buffer.writeVarInt(value); buffer.writeBoolean(enabled);
        buffer.writeFloat(x); buffer.writeFloat(y); buffer.writeFloat(z);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
