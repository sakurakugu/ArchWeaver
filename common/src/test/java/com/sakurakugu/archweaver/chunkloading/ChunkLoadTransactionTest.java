package com.sakurakugu.archweaver.chunkloading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.Test;

class ChunkLoadTransactionTest {
    private static final long FIRST = 1L;
    private static final long SECOND = 2L;
    private static final long THIRD = 3L;

    @Test
    void batchRollbackUndoesEveryAppliedClaim() {
        Recorder recorder = new Recorder();
        ChunkLoadTransaction transaction = new ChunkLoadTransaction(recorder);

        transaction.apply(null, List.of(claim(FIRST)), true);
        transaction.apply(null, List.of(claim(SECOND)), false);
        transaction.rollbackTo(0);

        assertEquals(List.of("+1", "-2", "+2", "-1"), recorder.calls);
    }

    @Test
    void failedApplyRollsBackOnlyItsOwnClaims() {
        Recorder recorder = new Recorder();
        recorder.failingAdd = THIRD;
        ChunkLoadTransaction transaction = new ChunkLoadTransaction(recorder);

        int batchMark = transaction.mark();
        transaction.apply(null, List.of(claim(FIRST)), true);
        assertThrows(IllegalStateException.class,
            () -> transaction.apply(null, List.of(claim(SECOND), claim(THIRD)), true));
        // 失败的那一次可能已经生效，必须连同它一起撤销；更早的编辑不受影响
        assertEquals(List.of("+1", "+2", "+3", "-3", "-2"), recorder.calls);

        transaction.rollbackTo(batchMark);
        assertEquals(List.of("+1", "+2", "+3", "-3", "-2", "-1"), recorder.calls);
    }

    @Test
    void lenientApplyKeepsGoingAfterAFailureAndStaysOutOfTheLedger() {
        Recorder recorder = new Recorder();
        recorder.failingAdd = SECOND;
        ChunkLoadTransaction transaction = new ChunkLoadTransaction(recorder);

        transaction.applyLenient(null, List.of(claim(FIRST), claim(SECOND), claim(THIRD)), true);

        assertEquals(List.of("+1", "+2", "+3"), recorder.calls);
        assertEquals(0, transaction.mark());
    }

    private static ChunkLoadClaim claim(long chunk) {
        return new ChunkLoadClaim(LoadOwner.manualRegion(UUID.randomUUID()), null, chunk);
    }

    /** 记录每次票据读写；{@code failingAdd} 上的写入先记账再抛异常，模拟"失败的那一次已经生效"。 */
    private static final class Recorder implements ChunkTicketService {
        private final List<String> calls = new ArrayList<>();
        private long failingAdd = Long.MIN_VALUE;

        @Override
        public void add(ServerLevel level, ChunkLoadClaim claim) {
            calls.add("+" + claim.chunk());
            if (claim.chunk() == failingAdd) {
                throw new IllegalStateException("票据写入后失败");
            }
        }

        @Override
        public void remove(ServerLevel level, ChunkLoadClaim claim) {
            calls.add("-" + claim.chunk());
        }
    }
}
