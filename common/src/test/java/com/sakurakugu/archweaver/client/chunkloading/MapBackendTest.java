package com.sakurakugu.archweaver.client.chunkloading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class MapBackendTest {
    @Test void installedMapsRespectTheExplicitSelection() {
        var installed = EnumSet.allOf(MapBackend.class);
        for (var backend : MapBackend.values()) assertEquals(backend, MapBackend.resolve(backend, installed));
    }

    @Test void missingPreferredMapFallsBackToBuiltinEvenWhenAnotherMapIsInstalled() {
        assertEquals(MapBackend.BUILTIN, MapBackend.resolve(MapBackend.XAERO,
            EnumSet.of(MapBackend.BUILTIN, MapBackend.JOURNEYMAP)));
        assertEquals(MapBackend.BUILTIN, MapBackend.resolve(MapBackend.JOURNEYMAP,
            EnumSet.of(MapBackend.BUILTIN, MapBackend.XAERO)));
    }

    @Test void installingThirdPartyMapsDoesNotChangeBuiltinSelection() {
        assertEquals(MapBackend.BUILTIN, MapBackend.resolve(MapBackend.BUILTIN, EnumSet.allOf(MapBackend.class)));
    }
}
