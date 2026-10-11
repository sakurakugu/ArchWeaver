package com.sakurakugu.archweaver.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sakurakugu.archweaver.skin.SkinSavedData;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

/** 来源字段与原有纹理字段必须一起完整传输。 */
class SkinPayloadTest {
    @Test
    void uploadRoundTripPreservesPngAndOnlyTheFileName() {
        for (var type : SkinSavedData.TargetType.values()) {
            var original = new SkinApplyPayload(type, "Bot", "", new byte[] {1, 2, 3}, "SLIM", false,
                "C:\\Users\\Player\\皮肤.png");
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            try {
                SkinApplyPayload.STREAM_CODEC.encode(buffer, original);
                var decoded = SkinApplyPayload.STREAM_CODEC.decode(buffer);
                assertEquals(type, decoded.targetType());
                assertEquals("Bot", decoded.targetName());
                assertEquals("", decoded.sourceName());
                assertEquals("皮肤.png", decoded.fileName());
                assertEquals("SLIM", decoded.model());
                assertEquals(false, decoded.clear());
                assertArrayEquals(original.png(), decoded.png());
                assertEquals(0, buffer.readableBytes());
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void syncRoundTripPreservesSourcesTexturesAndRemoval() {
        UUID target = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        for (var type : SkinSavedData.TargetType.values()) {
            for (var original : List.of(
                new SkinSyncPayload(target, type, SkinSavedData.Kind.PROFILE,
                    List.of(new SkinSavedData.PropertyValue("textures", "value", "signature")), new byte[0], "", false,
                    source, "Source", ""),
                new SkinSyncPayload(target, type, SkinSavedData.Kind.PNG, List.of(), new byte[] {4, 5}, "SLIM", false,
                    target, "", "皮肤.png"),
                new SkinSyncPayload(target, type, SkinSavedData.Kind.PROFILE, List.of(), new byte[0], "", true,
                    target, "", ""))) {
                var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
                try {
                    SkinSyncPayload.STREAM_CODEC.encode(buffer, original);
                    var decoded = SkinSyncPayload.STREAM_CODEC.decode(buffer);
                    assertEquals(original.target(), decoded.target());
                    assertEquals(original.targetType(), decoded.targetType());
                    assertEquals(original.kind(), decoded.kind());
                    assertEquals(original.properties(), decoded.properties());
                    assertArrayEquals(original.png(), decoded.png());
                    assertEquals(original.model(), decoded.model());
                    assertEquals(original.clear(), decoded.clear());
                    assertEquals(original.sourceUuid(), decoded.sourceUuid());
                    assertEquals(original.sourceName(), decoded.sourceName());
                    assertEquals(original.fileName(), decoded.fileName());
                    assertEquals(0, buffer.readableBytes());
                } finally {
                    buffer.release();
                }
            }
        }
    }
}
