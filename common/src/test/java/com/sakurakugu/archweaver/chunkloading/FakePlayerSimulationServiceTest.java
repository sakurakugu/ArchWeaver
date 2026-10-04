package com.sakurakugu.archweaver.chunkloading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FakePlayerSimulationServiceTest {
    @Test
    void rollsBackTicketChangesEvenWhenFailedWriteAlreadyTookEffect() {
        UUID id = UUID.randomUUID();
        Set<Long> active = new HashSet<>(Set.of(1L, 2L));
        List<String> calls = new ArrayList<>();
        var changes = List.of(
            new FakePlayerSimulationService.TicketChange(null, 1L, false),
            new FakePlayerSimulationService.TicketChange(null, 3L, true));

        assertThrows(IllegalStateException.class, () -> FakePlayerSimulationService.applyChanges(
            id, changes, (level, owner, chunk, add) -> {
                assertEquals(id, owner);
                calls.add((add ? "+" : "-") + chunk);
                if (add) active.add(chunk);
                else active.remove(chunk);
                if (chunk == 3L && add) throw new IllegalStateException("票据写入后失败");
            }));

        assertEquals(Set.of(1L, 2L), active);
        assertEquals(List.of("-1", "+3", "-3", "+1"), calls);
    }

    @Test
    void appliesAllTicketChangesOnSuccess() {
        UUID id = UUID.randomUUID();
        Set<Long> active = new HashSet<>(Set.of(1L));
        var changes = List.of(
            new FakePlayerSimulationService.TicketChange(null, 1L, false),
            new FakePlayerSimulationService.TicketChange(null, 2L, true));

        FakePlayerSimulationService.applyChanges(id, changes, (level, owner, chunk, add) -> {
            if (add) active.add(chunk);
            else active.remove(chunk);
        });

        assertEquals(Set.of(2L), active);
    }
}
