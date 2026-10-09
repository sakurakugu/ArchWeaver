package com.sakurakugu.archweaver.client.camera;

import static org.junit.jupiter.api.Assertions.*;
import com.sakurakugu.archweaver.client.camera.CameraSelection.Category;
import org.junit.jupiter.api.Test;

class CameraSelectionTest {
    @Test
    void scrollingTraversesEverySubmodeAndWrapsInBothDirections() {
        var selection = new CameraSelection();
        // 预先记住末项，验证跨类别仍从首项开始。
        selection.select(Category.SHOULDER, 1);
        selection.select(Category.VANILLA, 0);
        for (Category category : Category.values()) {
            for (int mode = 0; mode < category.modes(); mode++) {
                assertEquals(category, selection.category());
                assertEquals(mode, selection.mode());
                selection.cycleView(true);
            }
        }
        assertEquals(Category.VANILLA, selection.category());
        assertEquals(0, selection.mode());
        Category[] categories = Category.values();
        for (int i = categories.length - 1; i >= 0; i--) {
            for (int mode = categories[i].modes() - 1; mode >= 0; mode--) {
                selection.cycleView(false);
                assertEquals(categories[i], selection.category());
                assertEquals(mode, selection.mode());
            }
        }
    }

    @Test
    void selectorUsesCurrentViewWhenDisabledOrHistoryIsAbsent() {
        var selection = new CameraSelection();
        assertEquals(Category.VANILLA, selection.selectorSnapshot(true).category());
        assertEquals(0, selection.selectorSnapshot(true).mode());
        selection.select(Category.FREE, 0);
        assertEquals(Category.FREE, selection.selectorSnapshot(false).category());
        assertEquals(Category.FREE, selection.category());
    }

    @Test
    void committingPreviousViewAlternatesBetweenExactSubmodes() {
        var selection = new CameraSelection();
        selection.select(Category.ORTHOGRAPHIC, 1);
        selection.select(Category.SHOULDER, 1);
        var preview = selection.selectorSnapshot(true);
        assertEquals(Category.ORTHOGRAPHIC, preview.category());
        assertEquals(1, preview.mode());
        selection.select(preview.category(), preview.mode());
        preview = selection.selectorSnapshot(true);
        assertEquals(Category.SHOULDER, preview.category());
        assertEquals(1, preview.mode());
        selection.select(preview.category(), preview.mode());
        assertEquals(Category.ORTHOGRAPHIC, selection.selectorSnapshot(true).category());
    }

    @Test
    void modeChangesWithinSameGroupRememberPreviousMode() {
        var selection = new CameraSelection();
        selection.select(Category.VANILLA, 2);
        selection.cycleMode();
        assertEquals(0, selection.mode());
        assertEquals(2, selection.selectorSnapshot(true).mode());
        var preview = selection.selectorSnapshot(true);
        selection.select(preview.category(), preview.mode());
        assertEquals(0, selection.selectorSnapshot(true).mode());
        selection.select(Category.ORTHOGRAPHIC, 0);
        selection.cycleMode();
        assertEquals(Category.ORTHOGRAPHIC, selection.selectorSnapshot(true).category());
        assertEquals(0, selection.selectorSnapshot(true).mode());
    }

    @Test
    void unchangedSelectionsAndSingletonF5PreserveHistory() {
        var selection = new CameraSelection();
        selection.select(Category.VANILLA, 2);
        selection.select(Category.FREE, 0);
        selection.cycleMode();
        selection.select(Category.FREE, 0);
        assertEquals(Category.VANILLA, selection.selectorSnapshot(true).category());
        assertEquals(2, selection.selectorSnapshot(true).mode());
    }

    @Test
    void cancellingHistoryPreviewPreservesHistoryAndGroupMemory() {
        var selection = new CameraSelection();
        selection.select(Category.ORTHOGRAPHIC, 1);
        selection.select(Category.FREE, 0);
        var preview = selection.selectorSnapshot(true);
        preview.select(Category.ORTHOGRAPHIC, 0);
        preview.select(Category.SHOULDER, 1);
        assertEquals(Category.FREE, selection.category());
        assertEquals(1, selection.mode(Category.ORTHOGRAPHIC));
        assertEquals(Category.ORTHOGRAPHIC, selection.selectorSnapshot(true).category());
        assertEquals(1, selection.selectorSnapshot(true).mode());
    }

    @Test
    void cancelledPreviewKeepsActivePerspectiveAndRememberedModes() {
        var active = new CameraSelection();
        active.select(Category.ORTHOGRAPHIC, 1);
        active.select(Category.VANILLA, 2);
        var preview = active.copy();
        preview.select(Category.ORTHOGRAPHIC, 0);
        preview.select(Category.FREE, 0);
        assertEquals(Category.VANILLA, active.category());
        assertEquals(2, active.mode());
        assertEquals(1, active.mode(Category.ORTHOGRAPHIC));
        active.select(preview.category(), preview.mode());
        assertEquals(Category.FREE, active.category());
    }

    @Test
    void f5CyclesWithinGroupAndLeavesSingletonGroupsSelected() {
        var selection = new CameraSelection();
        for (Category category : Category.values()) {
            selection.select(category, category.modes() - 1);
            selection.cycleMode();
            assertEquals(category, selection.category());
            assertEquals(0, selection.mode());
        }
        assertEquals(Category.VANILLA, Category.FOLLOW.next());
    }

    @Test
    void independentInputTogglesRouteAllFourCombinations() {
        for (Category category : Category.values()) {
            for (int mode = 0; mode < category.modes(); mode++) {
                boolean detached = CameraSelection.detachedControls(category, mode);
                for (boolean movement : new boolean[] {false, true}) {
                    for (boolean interaction : new boolean[] {false, true}) {
                        assertEquals(detached && !movement, CameraSelection.blockMovement(category, mode, movement, false));
                        assertEquals(detached && !interaction, CameraSelection.blockInteraction(category, mode, interaction, false));
                        assertTrue(CameraSelection.blockMovement(category, mode, movement, true));
                        assertTrue(CameraSelection.blockInteraction(category, mode, interaction, true));
                    }
                }
            }
        }
        assertFalse(CameraSelection.detachedControls(Category.FIXED, 0));
        assertFalse(CameraSelection.detachedControls(Category.ORTHOGRAPHIC, 0));
        assertTrue(CameraSelection.detachedControls(Category.ORTHOGRAPHIC, 1));
    }

    @Test
    void worldResetDiscardsModeMemory() {
        var selection = new CameraSelection();
        selection.select(Category.ORTHOGRAPHIC, 1);
        selection.clear();
        assertEquals(Category.VANILLA, selection.category());
        assertEquals(0, selection.mode(Category.ORTHOGRAPHIC));
        assertEquals(Category.VANILLA, selection.selectorSnapshot(true).category());
        assertEquals(0, selection.selectorSnapshot(true).mode());
        selection.select(Category.FREE, 0);
        selection.clear(2);
        assertEquals(Category.VANILLA, selection.selectorSnapshot(true).category());
        assertEquals(2, selection.selectorSnapshot(true).mode());
        // 重置后反复同步原版视角不应产生虚假的历史。
        selection.select(Category.VANILLA, 2);
        assertEquals(2, selection.selectorSnapshot(true).mode());
    }
}
