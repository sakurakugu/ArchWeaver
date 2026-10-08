package com.sakurakugu.archweaver.network;

import com.sakurakugu.archweaver.ArchWeaverMod;
import java.util.UUID;
import com.sakurakugu.archweaver.persistence.MannequinSavedData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 向客户端同步玩偶四肢角度。 */
public record MannequinAnglesPayload(UUID id, MannequinSavedData.Angles leftArm, MannequinSavedData.Angles rightArm,
                                     MannequinSavedData.Angles leftLeg, MannequinSavedData.Angles rightLeg) implements CustomPacketPayload {
    public static final Type<MannequinAnglesPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ArchWeaverMod.MOD_ID, "mannequin_angles"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MannequinAnglesPayload> STREAM_CODEC = CustomPacketPayload.codec(MannequinAnglesPayload::write, MannequinAnglesPayload::new);
    private MannequinAnglesPayload(RegistryFriendlyByteBuf b) {
        this(b.readUUID(), read(b), read(b), read(b), read(b));
    }
    private static MannequinSavedData.Angles read(RegistryFriendlyByteBuf b) { return new MannequinSavedData.Angles(b.readFloat(), b.readFloat(), b.readFloat()); }
    private void write(RegistryFriendlyByteBuf b) {
        b.writeUUID(id); write(b, leftArm); write(b, rightArm); write(b, leftLeg); write(b, rightLeg);
    }
    private static void write(RegistryFriendlyByteBuf b, MannequinSavedData.Angles a) { b.writeFloat(a.x()); b.writeFloat(a.y()); b.writeFloat(a.z()); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
