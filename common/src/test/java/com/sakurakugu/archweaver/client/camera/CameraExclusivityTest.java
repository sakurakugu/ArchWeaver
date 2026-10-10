package com.sakurakugu.archweaver.client.camera;

import static org.junit.jupiter.api.Assertions.*;

import com.sakurakugu.archweaver.client.camera.CameraPreferences.Toggle;
import com.sakurakugu.archweaver.client.camera.CameraSelection.Category;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CameraExclusivityTest {
    private final ExternalCamera external = new ExternalCamera();

    @BeforeEach
    void setup() {
        restorePreferences();
        CameraExclusivity.install(external);
    }

    @AfterEach
    void restoreDefaultBackend() {
        restorePreferences();
        CameraExclusivity.install(() -> false);
    }

    private static void restorePreferences() {
        if (CameraPreferences.get(Toggle.PAUSE_ARCHWEAVER)) CameraPreferences.flip(Toggle.PAUSE_ARCHWEAVER);
        if (!CameraPreferences.get(Toggle.TWEAKEROO_EXCLUSIVITY)) CameraPreferences.flip(Toggle.TWEAKEROO_EXCLUSIVITY);
    }

    @Test
    void defaultBackendDoesNotYield() {
        CameraExclusivity.install(() -> false);
        assertFalse(CameraExclusivity.externalInstalled());
        assertFalse(CameraExclusivity.shouldYield());
        assertFalse(CameraExclusivity.externalControlAvailable());
        assertTrue(CameraExclusivity.prepareSelection(Category.FREE, () -> fail("Must not release")));
    }

    @Test
    void installedStateIsIndependentOfCompatibilityAndCameraReadFailures() {
        assertTrue(CameraExclusivity.externalInstalled());
        CameraPreferences.flip(Toggle.TWEAKEROO_EXCLUSIVITY);
        assertTrue(CameraExclusivity.externalInstalled());
        assertFalse(CameraExclusivity.externalAvailable());
        CameraPreferences.flip(Toggle.TWEAKEROO_EXCLUSIVITY);
        external.failRead = true;
        assertTrue(CameraExclusivity.shouldYield());
        assertTrue(CameraExclusivity.externalInstalled());
    }

    @Test
    void backendStateIsRead() {
        external.enabled = true;
        assertTrue(CameraExclusivity.shouldYield());
    }

    @Test
    void backendFailureDoesNotBreakCamera() {
        external.enabled = true;
        assertTrue(CameraExclusivity.shouldYield());
        external.failRead = true;
        assertTrue(CameraExclusivity.shouldYield());
        assertTrue(CameraExclusivity.selectionBlocked());
        assertFalse(CameraExclusivity.prepareSelection(Category.VANILLA, () -> fail("Must not release")));
        external.failRead = false;
        external.enabled = false;
        assertFalse(CameraExclusivity.shouldYield());
        assertFalse(CameraExclusivity.selectionBlocked());
    }

    @Test
    void firstReadFailureYieldsUntilStateCanBeConfirmed() {
        external.failRead = true;
        assertTrue(CameraExclusivity.shouldYield());
        assertTrue(CameraExclusivity.selectionBlocked());
        assertFalse(CameraExclusivity.prepareSelection(Category.FREE, () -> fail("Must not release")));
        assertTrue(external.events.isEmpty());
        external.failRead = false;
        assertFalse(CameraExclusivity.shouldYield());
        assertFalse(CameraExclusivity.selectionBlocked());
    }

    @Test
    void disabledCompatibilityLeavesExternalCameraUntouched() {
        external.enabled = true;
        CameraPreferences.flip(Toggle.TWEAKEROO_EXCLUSIVITY);
        assertFalse(CameraExclusivity.shouldYield());
        assertFalse(CameraExclusivity.externalControlAvailable());
        assertTrue(CameraExclusivity.prepareSelection(Category.SHOULDER, () -> fail("Must not release")));
        assertTrue(external.enabled);
        assertTrue(external.events.isEmpty());
    }

    @Test
    void selectingFreeCameraReleasesFirstAndRepeatedConfirmationKeepsItEnabled() {
        assertTrue(CameraExclusivity.prepareSelection(Category.FREE, () -> external.events.add("release")));
        assertEquals(List.of("release", "enable"), external.events);
        assertTrue(CameraExclusivity.shouldYield());
        assertTrue(CameraExclusivity.prepareSelection(Category.FREE, () -> external.events.add("release")));
        assertTrue(external.enabled);
        assertEquals(List.of("release", "enable", "release", "enable"), external.events);
        assertTrue(CameraExclusivity.prepareSelection(Category.ORTHOGRAPHIC, () -> fail("Must not release")));
        assertFalse(external.enabled);
        assertFalse(CameraExclusivity.shouldYield());
    }

    @Test
    void externalHotkeyParticipatesInHistoryAndPreviewDoesNotChangeIt() {
        CameraSelection selection = new CameraSelection();
        selection.select(Category.SHOULDER, 1);
        selection.select(Category.VANILLA, 2);
        external.enabled = true;
        CameraExclusivity.synchronizeSelection(selection, Category.VANILLA, 2);
        assertEquals(Category.FREE, selection.selectorSnapshot(false).category());
        var preview = selection.selectorSnapshot(true);
        assertEquals(Category.VANILLA, preview.category());
        assertEquals(2, preview.mode());
        preview.select(Category.ORTHOGRAPHIC, 1);
        assertEquals(Category.FREE, selection.category());
        assertEquals(Category.VANILLA, selection.selectorSnapshot(true).category());
        // 重复同步外部相机不能覆盖原来的子视角历史。
        CameraExclusivity.synchronizeSelection(selection, Category.VANILLA, 2);
        assertEquals(2, selection.selectorSnapshot(true).mode());
        external.enabled = false;
        CameraExclusivity.synchronizeSelection(selection, Category.VANILLA, 2);
        assertEquals(Category.FREE, selection.selectorSnapshot(true).category());
        assertTrue(external.events.isEmpty());
    }

    @Test
    void selectingExternalFreeCameraAndReturningRemembersItAsPrevious() {
        CameraSelection controls = new CameraSelection();
        CameraSelection history = new CameraSelection();
        controls.select(Category.SHOULDER, 1);
        CameraExclusivity.synchronizeSelection(history, controls.category(), controls.mode());
        assertTrue(CameraExclusivity.prepareSelection(Category.FREE, () -> controls.clear(2)));
        CameraExclusivity.synchronizeSelection(history, controls.category(), controls.mode());
        assertEquals(Category.VANILLA, controls.category());
        assertEquals(Category.FREE, history.category());
        assertEquals(Category.SHOULDER, history.selectorSnapshot(true).category());
        assertEquals(1, history.selectorSnapshot(true).mode());

        assertTrue(CameraExclusivity.prepareSelection(Category.VANILLA, () -> fail("Must not release")));
        controls.select(Category.VANILLA, 2);
        CameraExclusivity.synchronizeSelection(history, controls.category(), controls.mode());
        var preview = history.selectorSnapshot(true);
        assertEquals(Category.FREE, preview.category());
        assertEquals(Category.VANILLA, history.selectorSnapshot(false).category());
        assertEquals(2, history.selectorSnapshot(false).mode());

        assertTrue(CameraExclusivity.prepareSelection(preview.category(), () -> controls.clear(2)));
        CameraExclusivity.synchronizeSelection(history, controls.category(), controls.mode());
        assertEquals(Category.VANILLA, history.selectorSnapshot(true).category());
        assertEquals(2, history.selectorSnapshot(true).mode());
        assertEquals(List.of("enable", "disable", "enable"), external.events);
    }

    @Test
    void externalFreeCameraWithoutHistoryPreselectsStartingView() {
        CameraSelection history = new CameraSelection();
        history.clear(2);
        external.enabled = true;
        CameraExclusivity.synchronizeSelection(history, Category.VANILLA, 2);
        assertEquals(Category.VANILLA, history.selectorSnapshot(true).category());
        assertEquals(2, history.selectorSnapshot(true).mode());
        history.clear(2);
        assertEquals(Category.VANILLA, history.selectorSnapshot(true).category());
        assertEquals(2, history.selectorSnapshot(true).mode());
    }

    @Test
    void pauseBlocksEveryCategoryAndNeverChangesExternalState() {
        external.enabled = true;
        CameraPreferences.flip(Toggle.PAUSE_ARCHWEAVER);
        assertTrue(CameraExclusivity.shouldYield());
        assertTrue(CameraExclusivity.selectionBlocked());
        for (Category category : Category.values()) {
            assertFalse(CameraExclusivity.prepareSelection(category, () -> fail("Must not release")));
        }
        assertFalse(CameraExclusivity.setExternalEnabled(false));
        assertTrue(external.enabled);
        assertTrue(external.events.isEmpty());
        CameraPreferences.flip(Toggle.PAUSE_ARCHWEAVER);
        assertFalse(CameraExclusivity.selectionBlocked());
        assertTrue(CameraExclusivity.shouldYield());
    }

    @Test
    void readOnlyCompatibilityBlocksSwitchesOnlyWhileExternalCameraIsEnabled() {
        external.controllable = false;
        assertFalse(CameraExclusivity.selectionBlocked());
        assertTrue(CameraExclusivity.prepareSelection(Category.FREE, () -> fail("Must not release")));
        external.enabled = true;
        assertTrue(CameraExclusivity.selectionBlocked());
        assertFalse(CameraExclusivity.prepareSelection(Category.VANILLA, () -> fail("Must not release")));
        assertTrue(external.events.isEmpty());
    }

    @Test
    void failedWriteAbortsSwitch() {
        external.enabled = true;
        external.failWrite = true;
        assertFalse(CameraExclusivity.prepareSelection(Category.SHOULDER, () -> fail("Must not release")));
        assertTrue(CameraExclusivity.shouldYield());
        assertTrue(external.enabled);
    }

    @Test
    void reportedWriteSuccessRequiresMatchingState() {
        external.enabled = true;
        external.ignoreWrite = true;
        assertFalse(CameraExclusivity.prepareSelection(Category.SHOULDER, () -> fail("Must not release")));
        assertTrue(CameraExclusivity.shouldYield());
        assertEquals(List.of("disable"), external.events);
    }

    private static final class ExternalCamera implements CameraExclusivity.Backend {
        private final List<String> events = new ArrayList<>();
        private boolean enabled, failRead, failWrite, ignoreWrite;
        private boolean controllable = true;
        public boolean externalInstalled() { return true; }
        public boolean externalAvailable() { return true; }
        public boolean externalControllable() { return controllable; }
        public boolean externalEnabled() {
            if (failRead) throw new IllegalStateException("unavailable");
            return enabled;
        }
        public boolean setExternalEnabled(boolean value) {
            if (failWrite) return false;
            events.add(value ? "enable" : "disable");
            if (!ignoreWrite) enabled = value;
            return true;
        }
    }
}
