package com.sakurakugu.archweaver.client.camera;

import static org.junit.jupiter.api.Assertions.*;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class CameraMathTest {
    @Test
    void orthographicSizeIsIndependentOfDepthAndHonorsAspectRatio() {
        for (boolean zeroToOne : new boolean[] {false, true}) {
            var matrix = CameraMath.orthographic(20, 2, 1000, zeroToOne);
            var near = matrix.transformProject(new Vector3f(10, 5, -10));
            var far = matrix.transformProject(new Vector3f(10, 5, -500));
            assertEquals(0.5, near.x, 1e-6);
            assertEquals(0.5, near.y, 1e-6);
            assertEquals(near.x, far.x, 1e-6);
            assertEquals(near.y, far.y, 1e-6);
            assertEquals(zeroToOne ? 0 : -1, matrix.transformProject(new Vector3f(0, 0, -0.05F)).z, 1e-6);
            assertEquals(1, matrix.transformProject(new Vector3f(0, 0, -1000)).z, 1e-6);
        }
    }

    @Test
    void orthographicScreenRaysStayParallelAndProjectBackToScreenCoordinates() {
        var center = CameraMath.orthographicRay(new Vector3d(10, 20, 30), 0, 0, 20, 2, 0, 0);
        var corner = CameraMath.orthographicRay(new Vector3d(10, 20, 30), 0, 0, 20, 2, 1, 1);
        assertEquals(center.direction(), corner.direction());
        assertEquals(new Vector3d(-10, 30, 30), corner.origin());
        assertEquals(1, corner.direction().length(), 1e-12);
        for (var angle : CameraMath.Angle.values()) {
            var ray = CameraMath.orthographicRay(new Vector3d(), angle.yaw, angle.pitch, 32, 1.5, 0.6, -0.4);
            assertEquals(0, ray.origin().dot(ray.direction()), 1e-10);
            assertEquals(1, ray.direction().length(), 1e-10);
        }
    }

    @Test
    void anglePresetsLookFromTheirNamedDirections() {
        assertEquals(-1, CameraMath.forward(CameraMath.Angle.TOP.yaw, CameraMath.Angle.TOP.pitch).y, 1e-10);
        assertEquals(1, CameraMath.forward(CameraMath.Angle.BOTTOM.yaw, CameraMath.Angle.BOTTOM.pitch).y, 1e-10);
        assertTrue(CameraMath.forward(CameraMath.Angle.NORTH.yaw, 0).z > 0);
        assertTrue(CameraMath.forward(CameraMath.Angle.EAST.yaw, 0).x < 0);
        var nw = CameraMath.forward(CameraMath.Angle.ISO_NW.yaw, CameraMath.Angle.ISO_NW.pitch).negate();
        assertTrue(nw.x < 0 && nw.z < 0 && nw.y > 0);
        assertEquals(Math.abs(nw.x), Math.abs(nw.y), 1e-8);
        assertEquals(Math.abs(nw.y), Math.abs(nw.z), 1e-8);
    }

    @Test
    void numericSettingsRejectNonFiniteValuesAndClampRanges() {
        var scale = CameraPreferences.NumberSetting.SCALE;
        assertEquals(scale.initial, scale.clamp(Double.NaN));
        assertEquals(scale.initial, scale.clamp(Double.POSITIVE_INFINITY));
        assertEquals(scale.min, scale.clamp(-10));
        assertEquals(scale.max, scale.clamp(10000));
    }
}
