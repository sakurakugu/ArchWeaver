package com.sakurakugu.archweaver.client.camera;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CameraSettingsLayoutTest {
    @ParameterizedTest
    @ValueSource(ints = {8, 9})
    void everySettingIsReachableAndFitsAbovePagerAtDifferentGuiHeights(int toggleCount) {
        for (int height : new int[] {144, 180, 240, 270, 288, 360, 480}) {
            var first = CameraSettingsLayout.forHeight(height, 0, toggleCount);
            var covered = new HashSet<Integer>();
            for (int page = 0; page < first.pages(); page++) {
                var layout = CameraSettingsLayout.forHeight(height, page, toggleCount);
                assertTrue(layout.contentBottom() + 14 <= height);
                for (int index = layout.firstRow(); index < layout.endRow(); index++) {
                    assertTrue(covered.add(index));
                    assertTrue(layout.rowY(index) >= 62);
                    assertTrue(layout.rowY(index) + 20 <= layout.bottom());
                    if (index > layout.firstRow()) assertTrue(layout.rowY(index) >= layout.rowY(index - 1) + 22);
                }
                if (first.pages() > 1) assertTrue(layout.pagerY() >= layout.bottom() + 6);
                if (layout.dividerY() >= 0) {
                    assertTrue(layout.dividerY() > layout.rowY(toggleCount - 1) + 20);
                    assertTrue(layout.dividerY() < layout.rowY(toggleCount));
                }
            }
            assertEquals(toggleCount + 2, covered.size());
        }
    }

    @Test
    void resizingClampsPageAndLargeWindowsDoNotNeedPager() {
        assertEquals(1, CameraSettingsLayout.forHeight(288, 10, 7).pages());
        assertEquals(0, CameraSettingsLayout.forHeight(288, 10, 7).page());
        assertEquals(0, CameraSettingsLayout.forHeight(180, -1, 7).page());
        var last = CameraSettingsLayout.forHeight(180, 100, 7);
        assertEquals(last.pages() - 1, last.page());
    }
}
