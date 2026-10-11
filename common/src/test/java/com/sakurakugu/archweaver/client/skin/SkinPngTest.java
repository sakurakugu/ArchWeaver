package com.sakurakugu.archweaver.client.skin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mojang.blaze3d.platform.NativeImage;
import com.sakurakugu.archweaver.skin.SkinService;
import com.sakurakugu.archweaver.skin.SkinPngSanitizer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.junit.jupiter.api.Test;

/** 用实际出错图片回归解码、高清布局和动态纹理路径。 */
class SkinPngTest {
    @Test
    void sanitizedSkinsRetainNativeDecoderPixels() throws IOException {
        for (String name : java.util.List.of("hd-rgba.png", "indexed.png")) {
            byte[] png = fixture(name);
            try (NativeImage original = NativeImage.read(png);
                 NativeImage sanitized = NativeImage.read(SkinPngSanitizer.sanitize(png))) {
                assertEquals(original.getWidth(), sanitized.getWidth());
                assertEquals(original.getHeight(), sanitized.getHeight());
                assertArrayEquals(original.getPixels(), sanitized.getPixels());
            }
        }
    }

    @Test
    void loadsReportedHdRgbaSkinWithoutLosingPixels() throws IOException {
        byte[] png = fixture("hd-rgba.png");
        var decoded = SkinPng.read(png, "");
        try (NativeImage image = decoded.image()) {
            assertEquals(128, image.getWidth());
            assertEquals(128, image.getHeight());
            var expected = ImageIO.read(new java.io.ByteArrayInputStream(png));
            for (int y = 0; y < 128; y++) {
                for (int x = 0; x < 128; x++) {
                    int pixel = expected.getRGB(x, y);
                    if ((x < 64 && y < 32) || (y >= 32 && y < 64)
                        || (x >= 32 && x < 96 && y >= 96)) pixel |= 0xff000000;
                    assertEquals(pixel, image.getPixel(x, y), "像素 " + x + "," + y);
                }
            }
        }
    }

    @Test
    void loadsReportedIndexedSkinAsRgba() throws IOException {
        byte[] png = fixture("indexed.png");
        var decoded = SkinPng.read(png, "");
        try (NativeImage image = decoded.image()) {
            assertEquals(NativeImage.Format.RGBA, image.format());
            assertEquals(64, image.getWidth());
            assertEquals(64, image.getHeight());
            var expected = ImageIO.read(new java.io.ByteArrayInputStream(png));
            assertEquals(expected.getRGB(8, 8) | 0xff000000, image.getPixel(8, 8));
            assertEquals(expected.getRGB(40, 8), image.getPixel(40, 8));
        }
    }

    @Test
    void expandsLegacySkinAndDefaultsToWideArms() throws IOException {
        BufferedImage source = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(4, 20, 0xff123456);
        var decoded = SkinPng.read(png(source), "");
        try (NativeImage image = decoded.image()) {
            assertEquals(64, image.getHeight());
            assertEquals(PlayerModelType.WIDE, decoded.model());
            assertEquals(0xff123456, image.getPixel(23, 52));
            assertEquals(0, image.getPixel(0, 40));
        }
    }

    @Test
    void explicitModelOverridesAutomaticDetection() throws IOException {
        var decoded = SkinPng.read(fixture("indexed.png"), "WIDE");
        try (NativeImage image = decoded.image()) {
            assertEquals(PlayerModelType.WIDE, decoded.model());
        }
    }

    @Test
    void dynamicSkinUsesRegisteredTexturePathAndPreservesCape() {
        Identifier texture = Identifier.fromNamespaceAndPath("archweaver", "skin/test");
        var cape = new ClientAsset.ResourceTexture(Identifier.withDefaultNamespace("test/cape"));
        var original = PlayerSkin.insecure(cape, cape, cape, PlayerModelType.WIDE);
        var result = ClientSkinCache.pngSkin(original, texture, PlayerModelType.SLIM);
        assertEquals(texture, result.body().texturePath());
        assertEquals(PlayerModelType.SLIM, result.model());
        assertEquals(cape, result.cape());
        assertEquals(cape, result.elytra());
    }

    @Test
    void rejectsInvalidDimensionsAndTruncatedImage() {
        assertThrows(IllegalArgumentException.class, () -> SkinService.validateDimensions(128, 32));
        assertThrows(IllegalArgumentException.class, () -> SkinService.validateDimensions(96, 96));
        assertThrows(IllegalArgumentException.class, () -> SkinService.validateDimensions(2048, 2048));
        assertThrows(IOException.class, () -> SkinPng.read(java.util.Arrays.copyOf(fixture("indexed.png"), 33), ""));
    }

    private static byte[] fixture(String name) throws IOException {
        try (var input = SkinPngTest.class.getResourceAsStream("/skins/" + name)) {
            return java.util.Objects.requireNonNull(input).readAllBytes();
        }
    }

    private static byte[] png(BufferedImage image) throws IOException {
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
