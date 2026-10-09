package com.sakurakugu.archweaver.client.camera;

import static org.junit.jupiter.api.Assertions.*;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class OrthographicPreviewTest {
    @Test
    void freeRotationKeepsCapturedCenterAndIgnoresPlayerMovement() {
        var center = new Vector3d(10, 20, 30);
        var preview = new OrthographicPreview(center, 45, 35);
        preview.initialMode(1);
        center.zero();
        for (var angle : CameraMath.Angle.values()) {
            preview.rotation(angle.yaw, angle.pitch);
            var pos = preview.position(new Vector3d(100, 200, 300), 32);
            var pivot = pos.add(CameraMath.forward(preview.yaw(), preview.pitch()).mul(48));
            assertEquals(10, pivot.x, 1e-10);
            assertEquals(20, pivot.y, 1e-10);
            assertEquals(30, pivot.z, 1e-10);
        }
    }

    @Test
    void followModeTracksPlayerAndSwitchingToFreeDoesNotJump() {
        var preview = new OrthographicPreview(new Vector3d(), 75, 40);
        var player = new Vector3d(10, 20, 30);
        var following = preview.position(player, 24);
        preview.mode(1, player);
        assertEquals(following, preview.position(player, 24));
        player.add(1, 2, 3);
        assertEquals(following, preview.position(player, 24));
        preview.mode(0, player);
        assertEquals(new Vector3d(following).add(1, 2, 3), preview.position(player, 24));
    }

    @Test
    void changingPreviewDoesNotWritePreferencesOrSelectionHistory() {
        double yaw = CameraPreferences.get(CameraPreferences.NumberSetting.YAW);
        double pitch = CameraPreferences.get(CameraPreferences.NumberSetting.PITCH);
        var selection = new CameraSelection();
        selection.select(CameraSelection.Category.FREE, 0);
        selection.select(CameraSelection.Category.ORTHOGRAPHIC, 1);
        var preview = new OrthographicPreview(new Vector3d(), yaw, pitch);
        preview.rotation(541, -120);
        assertEquals(-179, preview.yaw());
        assertEquals(-90, preview.pitch());
        assertEquals(yaw, CameraPreferences.get(CameraPreferences.NumberSetting.YAW));
        assertEquals(pitch, CameraPreferences.get(CameraPreferences.NumberSetting.PITCH));
        assertEquals(CameraSelection.Category.ORTHOGRAPHIC, selection.category());
        assertEquals(CameraSelection.Category.FREE, selection.selectorSnapshot(true).category());
    }
}
