package com.sakurakugu.archweaver.client.camera;

import static org.junit.jupiter.api.Assertions.*;

import com.sakurakugu.archweaver.client.camera.CameraSelection.Category;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class BodyAimIndicatorsTest {
    @Test
    void crosshairRequiresAnEnabledCustomViewWithInteraction() {
        for (Category category : Category.values()) {
            assertFalse(BodyAimIndicators.showCrosshair(category, false, false));
            assertFalse(BodyAimIndicators.showCrosshair(category, true, true));
            assertEquals(category != Category.VANILLA, BodyAimIndicators.showCrosshair(category, false, true));
        }
    }

    @Test
    void raySwitchAndBlockedInteractionPreferenceApplyIndependentlyOfCrosshair() {
        for (Category category : Category.values()) {
            boolean eligible = category != Category.VANILLA && category != Category.SHOULDER;
            for (boolean enabled : new boolean[] {false, true}) {
                for (boolean whenBlocked : new boolean[] {false, true}) {
                    for (boolean interaction : new boolean[] {false, true}) {
                        for (int mode = 0; mode < category.modes(); mode++) {
                            boolean blocked = CameraSelection.blockInteraction(category, mode, interaction, false);
                            assertEquals(eligible && enabled && (!blocked || whenBlocked),
                                BodyAimIndicators.showRay(category, blocked, enabled, whenBlocked));
                        }
                    }
                }
            }
        }
        assertTrue(BodyAimIndicators.showRay(Category.FIXED, false, true, false));
        assertFalse(BodyAimIndicators.showRay(Category.FREE, true, true, false));
        assertTrue(BodyAimIndicators.showRay(Category.FREE, true, true, true));
    }

    @Test
    void rayEndsAtExactHitOrGreaterInteractionReachWithoutChangingInputs() {
        Vector3d eye = new Vector3d(10, 64, -5);
        Vector3d view = new Vector3d(0, 0, 1);
        Vector3d hit = new Vector3d(10, 64, -3);
        Vector3d end = BodyAimIndicators.rayEnd(eye, view, hit, 4.5, 3);
        assertEquals(hit, end);
        assertNotSame(hit, end);
        assertEquals(new Vector3d(10, 64, -0.5), BodyAimIndicators.rayEnd(eye, view, null, 4.5, 3));
        assertEquals(new Vector3d(10, 64, 1), BodyAimIndicators.rayEnd(eye, view, null, 3, 6));
        assertEquals(new Vector3d(10, 64, -5), eye);
        assertEquals(new Vector3d(0, 0, 1), view);
    }
}
