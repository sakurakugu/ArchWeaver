package com.sakurakugu.archweaver.skin;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.google.common.collect.HashMultimap;
import java.util.UUID;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.Test;

/** 皮肤上传边界校验。 */
public class SkinServiceTest {
    @Test
    void rejectsInvalidPng() {
        assertThrows(IllegalArgumentException.class, () -> SkinService.validatePng(new byte[24]));
    }

    @Test
    void rejectsOversizedPayload() {
        assertThrows(IllegalArgumentException.class, () -> SkinService.validatePng(new byte[SkinService.MAX_PNG_BYTES + 1]));
    }

    @Test
    void acceptsPngWith64By64Dimensions() throws java.io.IOException {
        try (var input = SkinServiceTest.class.getResourceAsStream("/skins/indexed.png")) {
            byte[] png = java.util.Objects.requireNonNull(input).readAllBytes();
            assertDoesNotThrow(() -> SkinService.validatePng(png));
        }
    }

    @Test
    void savedDataRoundTripPreservesProfileAndPngRecords() {
        UUID profileId = UUID.randomUUID();
        UUID pngId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        var properties = HashMultimap.<String, Property>create();
        properties.put("textures", new Property("textures", "encoded", "signature"));
        var data = new SkinSavedData();
        data.put(SkinSavedData.Record.profile(profileId, SkinSavedData.TargetType.PLAYER,
            new GameProfile(sourceId, "Source", new PropertyMap(properties))));
        byte[] png = new byte[] {1, 2, 3, 4};
        data.put(SkinSavedData.Record.png(pngId, SkinSavedData.TargetType.MANNEQUIN, png, "SLIM", "C:\\Users\\Player\\skin.png"));
        var decoded = SkinSavedData.CODEC.parse(NbtOps.INSTANCE,
            SkinSavedData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow()).getOrThrow();
        var profile = decoded.find(profileId, SkinSavedData.TargetType.PLAYER).orElseThrow();
        assertEquals("encoded", profile.properties().getFirst().value());
        assertEquals("signature", profile.properties().getFirst().signature());
        assertEquals(sourceId, profile.sourceUuid());
        assertEquals("Source", profile.sourceName());
        var restoredPng = decoded.find(pngId, SkinSavedData.TargetType.MANNEQUIN).orElseThrow();
        assertEquals(java.util.Arrays.toString(png), java.util.Arrays.toString(restoredPng.pngBytes()));
        assertEquals("SLIM", restoredPng.model());
        assertEquals("skin.png", restoredPng.fileName());
    }

    @Test
    void pngDisplayNameNeverRetainsDirectoriesOrControlCharacters() {
        assertEquals("皮肤.png", SkinSavedData.sanitizeFileName("C:\\Users\\Player\\皮肤.png"));
        assertEquals("skin.png", SkinSavedData.sanitizeFileName("/home/player/skin.png"));
        assertEquals("skin.png", SkinSavedData.sanitizeFileName("\nskin\r.png\t"));
        assertEquals("", SkinSavedData.sanitizeFileName(null));
        assertEquals("a".repeat(128), SkinSavedData.sanitizeFileName("a".repeat(200)));
        assertEquals("", SkinSavedData.Record.png(UUID.randomUUID(), SkinSavedData.TargetType.PLAYER,
            new byte[] {1}, "SLIM").fileName());
    }
}
