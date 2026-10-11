package com.sakurakugu.archweaver.client.chunkloading;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ChunkMapViewportTest {
    @Test void guiScaleAndWindowSizePreserveNativeBlockCoordinates() {
        for (int guiScale : new int[] {1, 2, 3, 4}) {
            var viewport = ChunkMapViewport.xaero(100, -200, 2, 1920, 1080, 1920 / guiScale, 1080 / guiScale);
            // 原生地图中窗口中心右侧 64 像素、上方 32 像素，对应世界偏移 +32、-16。
            assertEquals(132, viewport.blockX((960.0 + 64) / guiScale), 1e-9);
            assertEquals(-216, viewport.blockZ((540.0 - 32) / guiScale), 1e-9);
            assertEquals((960.0 + 64) / guiScale, viewport.screenX(132), 1e-9);
            assertEquals((540.0 - 32) / guiScale, viewport.screenY(-216), 1e-9);
        }
    }

    @Test void panningAndZoomingKeepScreenAndWorldTransformsInverse() {
        for (double zoom : new double[] {.001953125, .0625, 1, 50, 1000000}) {
            var viewport = new ChunkMapViewport(-123.75, 456.125, zoom, 853, 479);
            assertEquals(426.5, viewport.screenX(-123.75));
            assertEquals(239.5, viewport.screenY(456.125));
            for (double coordinate : new double[] {-30000000, -16.01, 0, 16, 30000000}) {
                assertEquals(coordinate, viewport.blockX(viewport.screenX(coordinate)), 1e-6);
                assertEquals(coordinate, viewport.blockZ(viewport.screenY(coordinate)), 1e-6);
            }
        }
    }

    @Test void chunkSelectionFloorsNegativeCoordinatesAtChunkBoundaries() {
        var viewport = new ChunkMapViewport(0, 0, 1, 800, 600);
        for (var entry : new double[][] {{-16.01, -2}, {-16, -1}, {-.01, -1}, {0, 0}, {15.99, 0}, {16, 1}}) {
            assertEquals((int) entry[1], viewport.chunkX(viewport.screenX(entry[0])));
            assertEquals((int) entry[1], viewport.chunkZ(viewport.screenY(entry[0])));
        }
    }

    @Test void invalidNativeViewportsAreRejectedBeforePainting() {
        assertThrows(IllegalArgumentException.class, () -> new ChunkMapViewport(0, 0, 0, 800, 600));
        assertThrows(IllegalArgumentException.class, () -> new ChunkMapViewport(Double.NaN, 0, 1, 800, 600));
        assertThrows(IllegalArgumentException.class, () -> new ChunkMapViewport(0, 0, Double.POSITIVE_INFINITY, 800, 600));
        assertThrows(IllegalArgumentException.class, () -> ChunkMapViewport.xaero(0, 0, 1, 0, 600, 800, 600));
    }
}
