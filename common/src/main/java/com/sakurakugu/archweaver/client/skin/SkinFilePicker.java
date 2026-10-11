package com.sakurakugu.archweaver.client.skin;

import com.sakurakugu.archweaver.skin.SkinService;
import com.sakurakugu.archweaver.skin.SkinPngSanitizer;
import com.sakurakugu.archweaver.skin.SkinSavedData;
import java.nio.file.Files;
import java.nio.file.Path;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/** 客户端选择并读取 PNG 皮肤文件。 */
public final class SkinFilePicker {
    private SkinFilePicker() { }

    public record SelectedPng(byte[] bytes, String fileName) { }

    public static SelectedPng choosePng() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filter = stack.mallocPointer(1);
            filter.put(stack.UTF8("*.png")).flip();
            String path = TinyFileDialogs.tinyfd_openFileDialog("选择皮肤 PNG", picturesDirectory(), filter, "PNG 文件", false);
            if (path == null || path.isBlank()) return null;
            Path file = Path.of(path).toAbsolutePath().normalize();
            if (!Files.isRegularFile(file) || Files.size(file) > SkinService.MAX_PNG_BYTES) return null;
            byte[] bytes = Files.readAllBytes(file);
            return new SelectedPng(SkinPngSanitizer.sanitize(bytes), SkinSavedData.sanitizeFileName(file.getFileName().toString()));
        } catch (RuntimeException | java.io.IOException | UnsatisfiedLinkError ignored) {
            return null;
        }
    }

    private static String picturesDirectory() {
        String home = System.getProperty("user.home", ".");
        Path pictures = Path.of(home, "Pictures");
        return Files.isDirectory(pictures) ? pictures.toString() : home;
    }
}
