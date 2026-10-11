package com.sakurakugu.archweaver.client.camera;

import static org.junit.jupiter.api.Assertions.*;

import com.sakurakugu.archweaver.client.camera.CameraPreferences.Toggle;
import com.sakurakugu.archweaver.client.camera.CameraSelection.Category;
import java.util.EnumMap;
import java.util.EnumSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExternalCameraBodyControlsTest {
    private final ExternalCamera external = new ExternalCamera();
    private final ExternalCameraBodyControls controls = new ExternalCameraBodyControls();

    @BeforeEach
    void setup() {
        restorePreferences();
        CameraExclusivity.install(external);
    }

    @AfterEach
    void cleanup() {
        restorePreferences();
        CameraExclusivity.install(() -> false);
    }

    private static void restorePreferences() {
        if (CameraPreferences.get(Toggle.PAUSE_ARCHWEAVER)) CameraPreferences.flip(Toggle.PAUSE_ARCHWEAVER);
        if (!CameraPreferences.get(Toggle.TWEAKEROO_EXCLUSIVITY)) CameraPreferences.flip(Toggle.TWEAKEROO_EXCLUSIVITY);
    }

    private boolean selectFreeCamera() {
        return controls.selectFreeCamera(() -> CameraExclusivity.prepareSelection(Category.FREE, () -> { }));
    }

    @Test
    void bodyTogglesChangeIndependentlyAndLeaveInternalPreferencesUntouched() {
        boolean internalMovement = CameraPreferences.get(Toggle.BODY_MOVEMENT);
        boolean internalInteraction = CameraPreferences.get(Toggle.BODY_INTERACTION);
        assertTrue(controls.flip(Toggle.BODY_INTERACTION));
        assertEquals(Boolean.TRUE, CameraExclusivity.externalBodyToggle(Toggle.BODY_INTERACTION));
        assertEquals(Boolean.FALSE, CameraExclusivity.externalBodyToggle(Toggle.BODY_MOVEMENT));
        assertTrue(controls.flip(Toggle.BODY_MOVEMENT));
        assertEquals(Boolean.TRUE, CameraExclusivity.externalBodyToggle(Toggle.BODY_MOVEMENT));
        assertEquals(internalMovement, CameraPreferences.get(Toggle.BODY_MOVEMENT));
        assertEquals(internalInteraction, CameraPreferences.get(Toggle.BODY_INTERACTION));
        assertEquals(2, external.saves);
    }

    @Test
    void explicitMovementEditSurvivesCameraActivationReset() {
        assertTrue(controls.flip(Toggle.BODY_MOVEMENT));
        assertFalse(external.enabled);
        assertTrue(selectFreeCamera());
        assertTrue(external.enabled);
        assertEquals(Boolean.TRUE, CameraExclusivity.externalBodyToggle(Toggle.BODY_MOVEMENT));
        assertEquals(2, external.saves);
    }

    @Test
    void uneditedMovementKeepsNativeActivationReset() {
        external.values.put(Toggle.BODY_MOVEMENT, true);
        assertTrue(controls.flip(Toggle.BODY_INTERACTION));
        assertTrue(selectFreeCamera());
        assertEquals(Boolean.FALSE, CameraExclusivity.externalBodyToggle(Toggle.BODY_MOVEMENT));
        assertEquals(Boolean.TRUE, CameraExclusivity.externalBodyToggle(Toggle.BODY_INTERACTION));
    }

    @Test
    void activeCameraEditsApplyImmediatelyAndAreNotReapplied() {
        external.enabled = true;
        assertTrue(controls.flip(Toggle.BODY_MOVEMENT));
        assertEquals(Boolean.TRUE, CameraExclusivity.externalBodyToggle(Toggle.BODY_MOVEMENT));
        assertTrue(selectFreeCamera());
        assertEquals(Boolean.TRUE, CameraExclusivity.externalBodyToggle(Toggle.BODY_MOVEMENT));
        assertEquals(1, external.saves);
    }

    @Test
    void externalChangesRemainTheSourceOfTruth() {
        assertTrue(controls.flip(Toggle.BODY_MOVEMENT));
        external.values.put(Toggle.BODY_MOVEMENT, false);
        assertEquals(Boolean.FALSE, CameraExclusivity.externalBodyToggle(Toggle.BODY_MOVEMENT));
        assertTrue(selectFreeCamera());
        assertEquals(Boolean.FALSE, CameraExclusivity.externalBodyToggle(Toggle.BODY_MOVEMENT));
    }

    @Test
    void cancelledOrFailedSelectionDoesNotRestoreSettingsOrEnableCamera() {
        assertTrue(controls.flip(Toggle.BODY_MOVEMENT));
        assertFalse(controls.selectFreeCamera(() -> false));
        assertFalse(external.enabled);
        assertEquals(Boolean.TRUE, CameraExclusivity.externalBodyToggle(Toggle.BODY_MOVEMENT));
        assertEquals(1, external.saves);
    }

    @Test
    void missingOrUnreadableConfigDoesNotBreakOtherConfigOrCameraSelection() {
        external.available.remove(Toggle.BODY_MOVEMENT);
        assertNull(CameraExclusivity.externalBodyToggle(Toggle.BODY_MOVEMENT));
        assertFalse(controls.flip(Toggle.BODY_MOVEMENT));
        assertTrue(controls.flip(Toggle.BODY_INTERACTION));
        external.failRead = true;
        assertNull(CameraExclusivity.externalBodyToggle(Toggle.BODY_INTERACTION));
        assertFalse(controls.flip(Toggle.BODY_INTERACTION));
        assertTrue(selectFreeCamera());
        assertTrue(CameraExclusivity.shouldYield());
        external.failRead = false;
        assertEquals(Boolean.TRUE, CameraExclusivity.externalBodyToggle(Toggle.BODY_INTERACTION));
    }

    @Test
    void failedOrIgnoredWriteDisablesOnlyAffectedButton() {
        for (boolean ignore : new boolean[] {false, true}) {
            CameraExclusivity.install(external);
            external.failWrite = !ignore;
            external.ignoreWrite = ignore;
            assertFalse(controls.flip(Toggle.BODY_MOVEMENT));
            assertNull(CameraExclusivity.externalBodyToggle(Toggle.BODY_MOVEMENT));
            assertEquals(Boolean.FALSE, CameraExclusivity.externalBodyToggle(Toggle.BODY_INTERACTION));
            assertTrue(selectFreeCamera());
            assertTrue(CameraExclusivity.shouldYield());
        }
        assertEquals(0, external.saves);
    }

    @Test
    void saveFailureDoesNotBreakCameraSelection() {
        external.failSave = true;
        assertFalse(controls.flip(Toggle.BODY_INTERACTION));
        assertNull(CameraExclusivity.externalBodyToggle(Toggle.BODY_INTERACTION));
        assertTrue(selectFreeCamera());
        assertTrue(CameraExclusivity.shouldYield());
    }

    @Test
    void disabledCompatibilityPauseAndDefaultBackendPreventBodyWrites() {
        CameraPreferences.flip(Toggle.TWEAKEROO_EXCLUSIVITY);
        assertFalse(controls.flip(Toggle.BODY_MOVEMENT));
        assertNull(CameraExclusivity.externalBodyToggle(Toggle.BODY_MOVEMENT));
        CameraPreferences.flip(Toggle.TWEAKEROO_EXCLUSIVITY);
        CameraPreferences.flip(Toggle.PAUSE_ARCHWEAVER);
        assertFalse(controls.flip(Toggle.BODY_INTERACTION));
        CameraPreferences.flip(Toggle.PAUSE_ARCHWEAVER);
        assertNull(CameraExclusivity.externalBodyToggle(Toggle.AUTO_ORBIT));
        CameraExclusivity.install(() -> false);
        assertFalse(controls.flip(Toggle.BODY_MOVEMENT));
        assertEquals(0, external.saves);
    }

    private static final class ExternalCamera implements CameraExclusivity.Backend {
        private final EnumMap<Toggle, Boolean> values = new EnumMap<>(Toggle.class);
        private final EnumSet<Toggle> available = EnumSet.of(Toggle.BODY_INTERACTION, Toggle.BODY_MOVEMENT);
        private boolean enabled, failRead, failWrite, ignoreWrite, failSave;
        private int saves;

        public boolean externalAvailable() { return true; }
        public boolean externalControllable() { return true; }
        public boolean externalEnabled() { return enabled; }
        public boolean setExternalEnabled(boolean value) {
            // 模拟 Tweakeroo 在首次创建自由相机时关闭身体移动。
            if (value && !enabled) values.put(Toggle.BODY_MOVEMENT, false);
            enabled = value;
            return true;
        }
        public boolean externalBodyToggleAvailable(Toggle toggle) { return available.contains(toggle); }
        public boolean externalBodyToggle(Toggle toggle) {
            if (failRead) throw new IllegalStateException("unavailable");
            return values.getOrDefault(toggle, false);
        }
        public boolean setExternalBodyToggle(Toggle toggle, boolean value) {
            if (failWrite) throw new IllegalStateException("unavailable");
            if (!ignoreWrite) values.put(toggle, value);
            return true;
        }
        public boolean saveExternalBodyToggles() {
            if (failSave) return false;
            saves++;
            return true;
        }
    }
}
