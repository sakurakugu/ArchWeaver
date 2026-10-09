package com.sakurakugu.archweaver.client.camera;

import static org.junit.jupiter.api.Assertions.*;

import com.sakurakugu.archweaver.client.camera.CameraPreferences.NumberSetting;
import com.sakurakugu.archweaver.client.camera.CameraPreferences.Toggle;
import java.util.EnumMap;
import org.junit.jupiter.api.Test;

class CameraPreferencesTest {
    private static final class Backend implements CameraPreferences.Backend {
        private final EnumMap<NumberSetting, Double> numbers = new EnumMap<>(NumberSetting.class);
        private final EnumMap<Toggle, Boolean> toggles = new EnumMap<>(Toggle.class);
        private int saves;
        public double number(NumberSetting setting) { return numbers.getOrDefault(setting, setting.initial); }
        public void number(NumberSetting setting, double value) { numbers.put(setting, value); }
        public boolean toggle(Toggle setting) { return toggles.getOrDefault(setting, false); }
        public void toggle(Toggle setting, boolean value) { toggles.put(setting, value); }
        public void save() { saves++; }
    }

    @Test
    void previousViewPreferenceDefaultsOffAndPersistsAcrossInstallations() {
        Backend backend = new Backend();
        CameraPreferences.install(backend);
        assertFalse(CameraPreferences.get(Toggle.SELECT_PREVIOUS));
        CameraPreferences.flip(Toggle.SELECT_PREVIOUS);
        assertTrue(backend.toggle(Toggle.SELECT_PREVIOUS));
        assertEquals(1, backend.saves);
        CameraPreferences.install(backend);
        assertTrue(CameraPreferences.get(Toggle.SELECT_PREVIOUS));
        CameraPreferences.flip(Toggle.SELECT_PREVIOUS);
        assertFalse(backend.toggle(Toggle.SELECT_PREVIOUS));
        assertEquals(2, backend.saves);
    }

    @Test
    void unsavedEditsSurviveBackendReloadAndArePersistedTogether() {
        Backend backend = new Backend();
        CameraPreferences.install(backend);
        CameraPreferences.set(NumberSetting.SCALE, 2);
        CameraPreferences.set(NumberSetting.YAW, -45);
        // 模拟先前保存引发的异步配置重载。
        backend.number(NumberSetting.SCALE, 32);
        assertEquals(2, CameraPreferences.get(NumberSetting.SCALE));
        CameraPreferences.save();
        assertEquals(2, backend.number(NumberSetting.SCALE));
        assertEquals(-45, backend.number(NumberSetting.YAW));
        assertEquals(1, backend.saves);
        backend.number(NumberSetting.SCALE, 64);
        assertEquals(64, CameraPreferences.get(NumberSetting.SCALE));
    }

    @Test
    void togglesSavePendingEditsAndNewBackendClearsOldSessionEdits() {
        Backend backend = new Backend();
        CameraPreferences.install(backend);
        CameraPreferences.set(NumberSetting.SCALE, 8);
        CameraPreferences.flip(Toggle.BODY_MOVEMENT);
        assertTrue(CameraPreferences.get(Toggle.BODY_MOVEMENT));
        assertFalse(CameraPreferences.get(Toggle.BODY_INTERACTION));
        assertEquals(8, backend.number(NumberSetting.SCALE));
        CameraPreferences.set(NumberSetting.SCALE, 2);
        CameraPreferences.install(new Backend());
        assertEquals(NumberSetting.SCALE.initial, CameraPreferences.get(NumberSetting.SCALE));
        assertFalse(CameraPreferences.get(Toggle.BODY_MOVEMENT));
    }
}
