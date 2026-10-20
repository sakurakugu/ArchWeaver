package com.sakurakugu.archweaver.config;

import com.sakurakugu.archweaver.ArchWeaverMod;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** 使用 UTF-8 属性文件，不引入额外配置模组。 */
public class FabricConfigFile {
    protected final Properties values = new Properties();
    private Path path;
    protected void load(Path file) {
        path = file;
        values.clear();
        if (Files.exists(file)) {
            try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { values.load(reader); }
            catch (IOException | IllegalArgumentException exception) {
                ArchWeaverMod.LOGGER.error("读取配置失败：{}", file, exception);
            }
        }
    }
    protected String text(String key, String fallback) {
        String value = values.getProperty(key, fallback);
        values.setProperty(key, value);
        return value;
    }
    protected boolean bool(String key, boolean fallback) {
        String value = text(key, Boolean.toString(fallback));
        boolean result = value.equalsIgnoreCase("true") || (!value.equalsIgnoreCase("false") && fallback);
        put(key, result);
        return result;
    }
    protected double number(String key, double fallback, double min, double max) {
        double value;
        try { value = Double.parseDouble(text(key, Double.toString(fallback))); }
        catch (NumberFormatException ignored) { value = fallback; }
        if (!Double.isFinite(value)) value = fallback;
        value = Math.clamp(value, min, max);
        put(key, value);
        return value;
    }
    protected int integer(String key, int fallback, int min, int max) {
        int value;
        try { value = Integer.parseInt(text(key, Integer.toString(fallback))); }
        catch (NumberFormatException ignored) { value = fallback; }
        value = Math.clamp(value, min, max);
        put(key, value);
        return value;
    }
    protected <E extends Enum<E>> E choice(String key, E fallback) {
        E value;
        try { value = Enum.valueOf(fallback.getDeclaringClass(), text(key, fallback.name())); }
        catch (IllegalArgumentException ignored) { value = fallback; }
        put(key, value.name());
        return value;
    }
    protected void put(String key, Object value) { values.setProperty(key, value.toString()); }
    public void save() {
        if (path == null) return;
        try {
            Files.createDirectories(path.getParent());
            try (var writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                values.store(writer, "ArchWeaver 配置；修改后重启生效，界面中的设置会自动保存。");
            }
        } catch (IOException exception) { ArchWeaverMod.LOGGER.error("保存配置失败：{}", path, exception); }
    }
}
