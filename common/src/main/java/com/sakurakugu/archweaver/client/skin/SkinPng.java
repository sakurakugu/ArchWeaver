package com.sakurakugu.archweaver.client.skin;

import com.mojang.blaze3d.platform.NativeImage;
import com.sakurakugu.archweaver.skin.SkinService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.PlayerModelType;

/** 解码皮肤 PNG，保留高清像素并按原版规则补全旧版布局。 */
final class SkinPng {
    private SkinPng() { }

    static Decoded read(byte[] bytes, String requestedModel) throws IOException {
        SkinService.validatePng(bytes);
        // 显式解码为 RGBA，索引色和 RGB 图片也能使用透明度与像素接口。
        NativeImage image = NativeImage.read(NativeImage.Format.RGBA, new ByteArrayInputStream(bytes));
        try {
            SkinService.validateDimensions(image.getWidth(), image.getHeight());
            int scale = image.getWidth() / 64;
            boolean legacy = image.getHeight() == image.getWidth() / 2;
            PlayerModelType model = switch (requestedModel == null ? "" : requestedModel) {
                case "SLIM", "slim" -> PlayerModelType.SLIM;
                case "WIDE", "wide" -> PlayerModelType.WIDE;
                default -> !legacy && transparent(image, 54 * scale, 20 * scale, 56 * scale, 32 * scale)
                    ? PlayerModelType.SLIM : PlayerModelType.WIDE;
            };
            if (legacy) {
                NativeImage expanded = new NativeImage(image.getWidth(), image.getWidth(), true);
                expanded.copyFrom(image);
                image.close();
                image = expanded;
                // 原版左右肢体的镜像规则；坐标随高清倍率一起放大。
                copy(image, scale, 4, 16, 16, 32, 4, 4);
                copy(image, scale, 8, 16, 16, 32, 4, 4);
                copy(image, scale, 0, 20, 24, 32, 4, 12);
                copy(image, scale, 4, 20, 16, 32, 4, 12);
                copy(image, scale, 8, 20, 8, 32, 4, 12);
                copy(image, scale, 12, 20, 16, 32, 4, 12);
                copy(image, scale, 44, 16, -8, 32, 4, 4);
                copy(image, scale, 48, 16, -8, 32, 4, 4);
                copy(image, scale, 40, 20, 0, 32, 4, 12);
                copy(image, scale, 44, 20, -8, 32, 4, 12);
                copy(image, scale, 48, 20, -16, 32, 4, 12);
                copy(image, scale, 52, 20, -8, 32, 4, 12);
            }
            opaque(image, 0, 0, 32 * scale, 16 * scale);
            if (legacy) clearOpaqueOverlay(image, scale);
            opaque(image, 0, 16 * scale, 64 * scale, 32 * scale);
            opaque(image, 16 * scale, 48 * scale, 48 * scale, 64 * scale);
            return new Decoded(image, model);
        } catch (RuntimeException exception) {
            image.close();
            throw exception;
        }
    }

    private static void copy(NativeImage image, int scale, int x, int y, int dx, int dy, int width, int height) {
        image.copyRect(x * scale, y * scale, dx * scale, dy * scale, width * scale, height * scale, true, false);
    }

    private static boolean transparent(NativeImage image, int x0, int y0, int x1, int y1) {
        for (int y = y0; y < y1; y++)
            for (int x = x0; x < x1; x++)
                if (ARGB.alpha(image.getPixel(x, y)) != 0) return false;
        return true;
    }

    private static void opaque(NativeImage image, int x0, int y0, int x1, int y1) {
        for (int y = y0; y < y1; y++)
            for (int x = x0; x < x1; x++)
                image.setPixel(x, y, ARGB.opaque(image.getPixel(x, y)));
    }

    private static void clearOpaqueOverlay(NativeImage image, int scale) {
        for (int y = 0; y < 32 * scale; y++)
            for (int x = 32 * scale; x < 64 * scale; x++)
                if (ARGB.alpha(image.getPixel(x, y)) < 128) return;
        for (int y = 0; y < 32 * scale; y++)
            for (int x = 32 * scale; x < 64 * scale; x++)
                image.setPixel(x, y, image.getPixel(x, y) & 0x00ffffff);
    }

    record Decoded(NativeImage image, PlayerModelType model) { }
}
