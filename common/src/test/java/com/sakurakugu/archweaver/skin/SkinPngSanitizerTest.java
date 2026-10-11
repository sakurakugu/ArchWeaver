package com.sakurakugu.archweaver.skin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** 净化后的 PNG 只保留像素，不能携带输入文件的附加内容。 */
class SkinPngSanitizerTest {
    @Test
    void preservesAllPixelsAndDimensionsOfReportedSkins() throws IOException {
        for (String name : List.of("hd-rgba.png", "indexed.png")) {
            byte[] source = fixture(name);
            byte[] sanitized = SkinPngSanitizer.sanitize(source);
            assertPixelsEqual(source, sanitized);
            assertOnlyEssentialChunks(sanitized);
            assertArrayEquals(sanitized, SkinPngSanitizer.sanitize(sanitized));
        }
    }

    @Test
    void stripsTextCustomChunksAndTrailingBytes() throws IOException {
        byte[] source = fixture("hd-rgba.png");
        var dirty = new ByteArrayOutputStream();
        dirty.write(source, 0, 33);
        dirty.write(chunk("tEXt", "Comment\0private information".getBytes(StandardCharsets.UTF_8)));
        dirty.write(chunk("awMd", "custom payload".getBytes(StandardCharsets.UTF_8)));
        dirty.write(source, 33, source.length - 33);
        dirty.write("appended private data".getBytes(StandardCharsets.UTF_8));
        byte[] sanitized = SkinPngSanitizer.sanitize(dirty.toByteArray());
        assertOnlyEssentialChunks(sanitized);
        assertPixelsEqual(source, sanitized);
    }

    @Test
    void preservesLegacyDimensionsAndPartialTransparency() throws IOException {
        var image = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0x00123456);
        image.setRGB(1, 0, 0x80123456);
        image.setRGB(2, 0, 0xffabcdef);
        byte[] source = png(image);
        byte[] sanitized = SkinPngSanitizer.sanitize(source);
        assertPixelsEqual(source, sanitized);
        assertOnlyEssentialChunks(sanitized);
    }

    @Test
    void rejectsHeaderOnlyAndTruncatedPixelData() throws IOException {
        byte[] source = fixture("indexed.png");
        assertThrows(IllegalArgumentException.class, () -> SkinPngSanitizer.sanitize(Arrays.copyOf(source, 33)));
        assertThrows(IllegalArgumentException.class, () -> SkinPngSanitizer.sanitize(Arrays.copyOf(source, source.length / 2)));
    }

    @Test
    void rejectsOversizedInputAndForgedDimensionsBeforeDecoding() throws IOException {
        assertThrows(IllegalArgumentException.class,
            () -> SkinPngSanitizer.sanitize(new byte[SkinService.MAX_PNG_BYTES + 1]));
        byte[] source = fixture("indexed.png");
        ByteBuffer.wrap(source, 16, 8).putInt(65536).putInt(65536);
        assertThrows(IllegalArgumentException.class, () -> SkinPngSanitizer.sanitize(source));
    }

    @Test
    void enforcesSizeLimitAfterIndexedPixelsExpandToRgba() throws IOException {
        var random = new Random(42);
        byte[] red = new byte[256], green = new byte[256], blue = new byte[256];
        random.nextBytes(red);
        random.nextBytes(green);
        random.nextBytes(blue);
        var palette = new IndexColorModel(8, 256, red, green, blue);
        var image = new BufferedImage(512, 256, BufferedImage.TYPE_BYTE_INDEXED, palette);
        byte[] indices = new byte[512 * 256];
        random.nextBytes(indices);
        for (int i = 0; i < indices.length; i++) indices[i] &= 0x7f;
        image.getRaster().setDataElements(0, 0, 512, 256, indices);
        byte[] source = png(image);
        assertTrue(source.length <= SkinService.MAX_PNG_BYTES);
        var error = assertThrows(IllegalArgumentException.class, () -> SkinPngSanitizer.sanitize(source));
        assertTrue(error.getMessage().contains("净化后的"));
    }

    private static void assertPixelsEqual(byte[] source, byte[] sanitized) throws IOException {
        var expected = ImageIO.read(new ByteArrayInputStream(source));
        var actual = ImageIO.read(new ByteArrayInputStream(sanitized));
        int width = expected.getWidth(), height = expected.getHeight();
        assertEquals(width, actual.getWidth());
        assertEquals(height, actual.getHeight());
        assertArrayEquals(expected.getRGB(0, 0, width, height, null, 0, width),
            actual.getRGB(0, 0, width, height, null, 0, width));
    }

    private static void assertOnlyEssentialChunks(byte[] png) {
        int offset = 8;
        boolean ended = false;
        while (offset < png.length) {
            int length = ByteBuffer.wrap(png, offset, 4).getInt();
            String type = new String(png, offset + 4, 4, StandardCharsets.US_ASCII);
            assertTrue(List.of("IHDR", "IDAT", "IEND").contains(type), type);
            var crc = new CRC32();
            crc.update(png, offset + 4, length + 4);
            assertEquals((int) crc.getValue(), ByteBuffer.wrap(png, offset + 8 + length, 4).getInt());
            offset += length + 12;
            if (type.equals("IEND")) { ended = true; break; }
        }
        assertTrue(ended);
        assertEquals(png.length, offset, "不能保留 IEND 之后的附加数据");
    }

    private static byte[] chunk(String type, byte[] data) throws IOException {
        var buffer = new ByteArrayOutputStream();
        var output = new DataOutputStream(buffer);
        byte[] name = type.getBytes(StandardCharsets.US_ASCII);
        output.writeInt(data.length);
        output.write(name);
        output.write(data);
        var crc = new CRC32();
        crc.update(name);
        crc.update(data);
        output.writeInt((int) crc.getValue());
        return buffer.toByteArray();
    }

    private static byte[] fixture(String name) throws IOException {
        try (var input = SkinPngSanitizerTest.class.getResourceAsStream("/skins/" + name)) {
            return java.util.Objects.requireNonNull(input).readAllBytes();
        }
    }

    private static byte[] png(BufferedImage image) throws IOException {
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
