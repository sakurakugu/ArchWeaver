package com.sakurakugu.archweaver.skin;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/** 客户端和服务端共用的皮肤净化：只从解码后的像素生成全新 PNG。 */
public final class SkinPngSanitizer {
    private SkinPngSanitizer() { }

    public static byte[] sanitize(byte[] bytes) {
        // 先限制输入大小和尺寸，避免解码超大图片后才发现不符合皮肤要求。
        SkinService.validatePng(bytes);
        BufferedImage pixels;
        var reader = ImageIO.getImageReadersByFormatName("png").next();
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            // 不加载 EXIF、文本、色彩配置等附加信息，也不使用磁盘缓存。
            reader.setInput(input, true, true);
            SkinService.validateDimensions(reader.getWidth(0), reader.getHeight(0));
            BufferedImage decoded = reader.read(0);
            int width = decoded.getWidth(), height = decoded.getHeight();
            pixels = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            // 直接复制 RGBA，保留透明像素中的 RGB；不经 Graphics 绘制与混色。
            pixels.setRGB(0, 0, width, height, decoded.getRGB(0, 0, width, height, null, 0, width), 0, width);
        } catch (IOException exception) {
            throw new IllegalArgumentException("皮肤 PNG 无法完整解码", exception);
        } finally {
            reader.dispose();
        }

        var buffer = new ByteArrayOutputStream();
        var writer = ImageIO.getImageWritersByFormatName("png").next();
        try (var output = new MemoryCacheImageOutputStream(buffer)) {
            writer.setOutput(output);
            // 不传递原图元数据，输出仅包含尺寸、RGBA 像素和 PNG 必要结构。
            writer.write(null, new IIOImage(pixels, null, null), writer.getDefaultWriteParam());
        } catch (IOException exception) {
            throw new IllegalArgumentException("皮肤 PNG 重新编码失败", exception);
        } finally {
            writer.dispose();
        }
        byte[] sanitized = buffer.toByteArray();
        if (sanitized.length > SkinService.MAX_PNG_BYTES)
            throw new IllegalArgumentException("净化后的皮肤 PNG 超过 128 KiB，请使用更低分辨率的皮肤");
        return sanitized;
    }
}
