package com.sakurakugu.archweaver.client.camera;

import java.util.EnumMap;

/** 相机偏好使用加载器配置保存；坐标与观察目标不属于偏好。 */
public final class CameraPreferences {
    public enum NumberSetting {
        SPEED(0.5, 0.02, 8), SCALE(32, 2, 256), YAW(45, -180, 180), PITCH(35.2643897, -90, 90),
        DISTANCE(8, 1, 128), SHOULDER_DISTANCE(4, 1, 12), SHOULDER_OFFSET(0.7, 0, 3), ORBIT_SPEED(12, -180, 180);

        public final double initial, min, max;
        NumberSetting(double initial, double min, double max) { this.initial = initial; this.min = min; this.max = max; }
        public String key() { return name().toLowerCase(java.util.Locale.ROOT); }
        public double clamp(double value) { return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : initial; }
    }

    public enum Toggle {
        BODY_INTERACTION(false), BODY_MOVEMENT(false), AUTO_ORBIT(false), SELECT_PREVIOUS(true), HIDE_HUD_TEXT(false);

        public final boolean initial;
        Toggle(boolean initial) { this.initial = initial; }
        public String key() { return name().toLowerCase(java.util.Locale.ROOT); }
    }

    public interface Backend {
        double number(NumberSetting setting);
        void number(NumberSetting setting, double value);
        boolean toggle(Toggle setting);
        void toggle(Toggle setting, boolean value);
        void save();
    }

    private static Backend backend = new Backend() {
        private final EnumMap<NumberSetting, Double> numbers = new EnumMap<>(NumberSetting.class);
        private final EnumMap<Toggle, Boolean> toggles = new EnumMap<>(Toggle.class);
        public double number(NumberSetting setting) { return numbers.getOrDefault(setting, setting.initial); }
        public void number(NumberSetting setting, double value) { numbers.put(setting, value); }
        public boolean toggle(Toggle setting) { return toggles.getOrDefault(setting, setting.initial); }
        public void toggle(Toggle setting, boolean value) { toggles.put(setting, value); }
        public void save() { }
    };

    // 未保存的连续鼠标或文本编辑留在内存，避免配置重载覆盖这些修改。
    private static final EnumMap<NumberSetting, Double> pendingNumbers = new EnumMap<>(NumberSetting.class);

    private CameraPreferences() { }
    public static void install(Backend value) { backend = value; pendingNumbers.clear(); }
    public static double get(NumberSetting setting) {
        Double pending = pendingNumbers.get(setting);
        return pending != null ? pending : setting.clamp(backend.number(setting));
    }
    public static void set(NumberSetting setting, double value) { pendingNumbers.put(setting, setting.clamp(value)); }
    public static boolean get(Toggle setting) { return backend.toggle(setting); }
    public static void flip(Toggle setting) { backend.toggle(setting, !get(setting)); save(); }
    public static void save() {
        pendingNumbers.forEach(backend::number);
        backend.save();
        pendingNumbers.clear();
    }
}
