package com.sakurakugu.archweaver.client.camera;

import static org.junit.jupiter.api.Assertions.*;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class ShoulderAimTest {
    @Test
    void bodyAndBothShouldersAimAtTheSameCentralPoint() {
        Vector3d eye = new Vector3d(10, 20, 30);
        for (double yaw : new double[] {-181, -179, -90, 0, 90, 179, 181}) {
            for (double pitch : new double[] {-90, -60, 0, 60, 90}) {
                for (double offset : new double[] {-3, -0.7, 0, 0.7, 3}) {
                    for (double distance : new double[] {1, 4, 12}) {
                        Vector3d camera = CameraMath.shoulderPosition(eye, yaw, pitch, distance, offset);
                        Vector3d target = new Vector3d(camera).add(CameraMath.forward(yaw, pitch).mul(distance + 2));
                        CameraMath.Rotation body = CameraMath.aimRotation(eye, target, yaw, pitch);
                        Vector3d expected = new Vector3d(target).sub(eye).normalize();
                        assertTrue(expected.distance(CameraMath.forward(body.yaw(), body.pitch())) < 1.0E-10);
                        assertTrue(body.pitch() >= -90 && body.pitch() <= 90);
                    }
                }
            }
        }
        assertEquals(new Vector3d(10, 20, 30), eye);
    }

    @Test
    void shouldersMirrorTheirPositionAndBodyYawCorrection() {
        Vector3d eye = new Vector3d();
        Vector3d left = CameraMath.shoulderPosition(eye, 0, 0, 4, -0.7);
        Vector3d right = CameraMath.shoulderPosition(eye, 0, 0, 4, 0.7);
        assertEquals(new Vector3d(0.7, 0, -4), left);
        assertEquals(new Vector3d(-0.7, 0, -4), right);
        CameraMath.Rotation leftBody = CameraMath.aimRotation(eye, left.add(0, 0, 6), 0, 0);
        CameraMath.Rotation rightBody = CameraMath.aimRotation(eye, right.add(0, 0, 6), 0, 0);
        assertTrue(leftBody.yaw() < 0);
        assertEquals(-leftBody.yaw(), rightBody.yaw(), 1.0E-10);
    }

    @Test
    void wallShortenedCameraStillConvergesOnTheCentralTarget() {
        Vector3d eye = new Vector3d(30, 70, -15);
        Vector3d fullCamera = CameraMath.shoulderPosition(eye, 135, 35, 4, -0.7);
        Vector3d camera = new Vector3d(eye).lerp(fullCamera, 0.2);
        Vector3d target = new Vector3d(camera).add(CameraMath.forward(135, 35).mul(2));
        CameraMath.Rotation body = CameraMath.aimRotation(eye, target, 135, 35);
        assertTrue(new Vector3d(target).sub(eye).normalize()
            .distance(CameraMath.forward(body.yaw(), body.pitch())) < 1.0E-10);
    }

    @Test
    void coincidentOrRearTargetsPreserveTheCameraDirection() {
        Vector3d eye = new Vector3d(10, 20, 30);
        CameraMath.Rotation camera = new CameraMath.Rotation(179, -35);
        assertEquals(camera, CameraMath.aimRotation(eye, eye, camera.yaw(), camera.pitch()));
        Vector3d rear = new Vector3d(eye).sub(CameraMath.forward(camera.yaw(), camera.pitch()));
        assertEquals(camera, CameraMath.aimRotation(eye, rear, camera.yaw(), camera.pitch()));
    }

    @Test
    void verticalTargetsKeepYawAndZeroOffsetKeepsTheCameraDirection() {
        Vector3d eye = new Vector3d();
        assertEquals(new CameraMath.Rotation(135, -90), CameraMath.aimRotation(eye, new Vector3d(0, 5, 0), 135, -90));
        assertEquals(new CameraMath.Rotation(-45, 90), CameraMath.aimRotation(eye, new Vector3d(0, -5, 0), -45, 90));
        Vector3d camera = CameraMath.shoulderPosition(eye, 179, 35, 4, 0);
        Vector3d target = camera.add(CameraMath.forward(179, 35).mul(8));
        CameraMath.Rotation body = CameraMath.aimRotation(eye, target, 179, 35);
        assertEquals(179, body.yaw(), 1.0E-10);
        assertEquals(35, body.pitch(), 1.0E-10);
    }

    @Test
    void missingAtATrunkEdgeDoesNotSnapTheBodyBackToTheCameraDirection() {
        for (double shoulder : new double[] {-0.7, 0.7}) {
            ShoulderAim aim = new ShoulderAim();
            Vector3d eye = new Vector3d();
            Vector3d camera = CameraMath.shoulderPosition(eye, 0, 0, 4, shoulder);
            Vector3d trunk = new Vector3d(camera).add(0, 0, 6);
            CameraMath.Rotation hit = aim.rotation(eye, trunk, 0, 0);
            assertTrue(Math.abs(hit.yaw()) > 15);
            for (int i = 0; i < 10; i++) {
                CameraMath.Rotation miss = aim.rotation(eye, null, 0, 0);
                assertEquals(hit.yaw(), miss.yaw(), 1.0E-10);
                assertEquals(hit.pitch(), miss.pitch(), 1.0E-10);
                assertEquals(hit, aim.rotation(eye, trunk, 0, 0));
            }
        }
    }

    @Test
    void bodyKeepsFollowingTheCameraWhileMissingAndWrapsYaw() {
        ShoulderAim aim = new ShoulderAim();
        Vector3d eye = new Vector3d();
        Vector3d camera = CameraMath.shoulderPosition(eye, 179, 30, 4, -0.7);
        Vector3d target = new Vector3d(camera).add(CameraMath.forward(179, 30).mul(6));
        CameraMath.Rotation hit = aim.rotation(eye, target, 179, 30);
        CameraMath.Rotation miss = aim.rotation(eye, null, -179, 35);
        assertTrue(CameraMath.forward(hit.yaw() + 2, hit.pitch() + 5)
            .distance(CameraMath.forward(miss.yaw(), miss.pitch())) < 1.0E-10);
        assertTrue(miss.yaw() >= -180 && miss.yaw() < 180);
        assertEquals(-90, aim.rotation(eye, null, 0, -90).pitch(), 1.0E-10);
    }

    @Test
    void resetClearsThePreviousCompensationAndNewHitsRealignExactly() {
        ShoulderAim aim = new ShoulderAim();
        Vector3d eye = new Vector3d();
        assertEquals(new CameraMath.Rotation(20, 35), aim.rotation(eye, null, 20, 35));
        aim.rotation(eye, new Vector3d(0.7, 0, 2), 0, 0);
        Vector3d nextTarget = new Vector3d(-0.7, -1, 3);
        CameraMath.Rotation next = aim.rotation(eye, nextTarget, 10, 5);
        assertTrue(new Vector3d(nextTarget).normalize()
            .distance(CameraMath.forward(next.yaw(), next.pitch())) < 1.0E-10);
        aim.reset();
        assertEquals(new CameraMath.Rotation(20, 35), aim.rotation(eye, null, 20, 35));
    }

    @Test
    void nearTargetsCrossingTheEyePlaneDoNotEraseTheCompensation() {
        Vector3d eye = new Vector3d(10, 20, 30);
        for (double shoulder : new double[] {-0.7, 0.7}) {
            for (double yaw : new double[] {-179, 0, 90, 179}) {
                for (double pitch : new double[] {-60, 0, 60}) {
                    ShoulderAim aim = new ShoulderAim();
                    Vector3d camera = CameraMath.shoulderPosition(eye, yaw, pitch, 4, shoulder);
                    Vector3d forward = CameraMath.forward(yaw, pitch);
                    Vector3d front = new Vector3d(camera).add(new Vector3d(forward).mul(4.01));
                    CameraMath.Rotation hit = aim.rotation(eye, front, yaw, pitch);
                    assertTrue(new Vector3d(front).sub(eye).normalize()
                        .distance(CameraMath.forward(hit.yaw(), hit.pitch())) < 1.0E-10);
                    Vector3d rear = new Vector3d(camera).add(new Vector3d(forward).mul(3.99));
                    for (int i = 0; i < 10; i++) {
                        assertRotationEquals(hit, aim.rotation(eye, rear, yaw, pitch));
                        assertRotationEquals(hit, aim.rotation(eye, eye, yaw, pitch));
                        assertRotationEquals(hit, aim.rotation(eye, null, yaw, pitch));
                        assertRotationEquals(hit, aim.rotation(eye, front, yaw, pitch));
                    }
                }
            }
        }
    }

    private static void assertRotationEquals(CameraMath.Rotation expected, CameraMath.Rotation actual) {
        assertEquals(expected.yaw(), actual.yaw(), 1.0E-10);
        assertEquals(expected.pitch(), actual.pitch(), 1.0E-10);
    }
}
