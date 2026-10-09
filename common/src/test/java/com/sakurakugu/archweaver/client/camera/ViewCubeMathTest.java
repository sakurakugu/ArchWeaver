package com.sakurakugu.archweaver.client.camera;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ViewCubeMathTest {
    @Test
    void cardinalViewsExposeOnlyTheirNamedFaceAndCenterHitsIt() {
        for (var angle : CameraMath.Angle.values()) {
            if (angle.ordinal() >= 6) continue;
            var geometry = ViewCubeMath.project(angle.yaw, angle.pitch, 12);
            assertEquals(1, geometry.faces().size());
            assertEquals(angle, geometry.faces().getFirst().angle());
            assertEquals(angle, geometry.hit(0, 0));
            assertNull(geometry.hit(40, 40));
        }
    }

    @Test
    void isometricViewsExposeThreeFacesAndUpperCornersSelectTheirPresets() {
        for (var angle : CameraMath.Angle.values()) {
            if (angle.ordinal() < 6) continue;
            var geometry = ViewCubeMath.project(angle.yaw, angle.pitch, 12);
            assertEquals(3, geometry.faces().size());
            assertTrue(geometry.faces().stream().anyMatch(face -> face.angle() == CameraMath.Angle.TOP));
            assertNotNull(geometry.hit(0, 0));
            for (var face : geometry.faces()) {
                assertEquals(face.angle(), geometry.hit(face.label().x(), face.label().y()));
                double area = 0;
                for (int i = 0; i < 4; i++) {
                    var a = face.points().get(i);
                    var b = face.points().get((i + 1) % 4);
                    area += a.x() * b.y() - b.x() * a.y();
                }
                assertTrue(area < 0, "GUI 面片需要与原版矩形相同的绕序");
            }
            for (var corner : geometry.corners()) {
                assertEquals(corner.angle(), geometry.hit(corner.point().x(), corner.point().y()));
            }
        }
    }

    @Test
    void bottomViewCannotHitUpperCornersThroughCube() {
        var geometry = ViewCubeMath.project(0, -90, 12);
        assertTrue(geometry.corners().isEmpty());
        assertEquals(CameraMath.Angle.BOTTOM, geometry.hit(0, 0));
    }

    @Test
    void projectionStaysInsideWidgetAndHitTestingHandlesYawWrap() {
        for (int yaw = -180; yaw < 180; yaw += 15) {
            for (int pitch = -90; pitch <= 90; pitch += 15) {
                var geometry = ViewCubeMath.project(yaw, pitch, 44 / 3.5);
                assertNotNull(geometry.hit(0, 0));
                for (var face : geometry.faces()) for (var point : face.points()) {
                    assertTrue(Math.abs(point.x()) < 25);
                    assertTrue(Math.abs(point.y()) < 25);
                }
            }
        }
        assertEquals(ViewCubeMath.project(179, 30, 12), ViewCubeMath.project(ViewCubeMath.wrap(539), 30, 12));
    }

    @Test
    void draggingHasDeadZoneAndNormalizesAngles() {
        assertFalse(ViewCubeMath.dragged(0, 0));
        assertFalse(ViewCubeMath.dragged(3, 0));
        assertTrue(ViewCubeMath.dragged(3, 1));
        assertEquals(-179, ViewCubeMath.wrap(181));
        assertEquals(179, ViewCubeMath.wrap(-181));
        assertEquals(-180, ViewCubeMath.wrap(540));
        assertEquals(90, ViewCubeMath.clampPitch(150));
        assertEquals(-90, ViewCubeMath.clampPitch(-150));
    }
}
