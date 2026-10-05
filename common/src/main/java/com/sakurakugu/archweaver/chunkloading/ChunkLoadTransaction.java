package com.sakurakugu.archweaver.chunkloading;

import com.sakurakugu.archweaver.ArchWeaverMod;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.server.level.ServerLevel;

/**
 * 一次编辑操作的票据账本。
 *
 * <p>票据变更先记账再下发：失败的那一次也可能已经生效，所以它也要记进去。有了账本，
 * 回滚只需撤销本事务真正动过的区块，不必像以前那样把全服票据推倒重建。
 *
 * <p>账本只管票据，落盘由 {@link ChunkLoaderManager} 的提交管线在整批成功或整批回滚时
 * 统一做一次，这样一次批量编辑不会写出上百份中间态备份、挤掉备份轮转里的恢复点。
 *
 * <p>账本只覆盖手动区域票据。假玩家票据由 {@link FakePlayerSimulationService} 自己维护，
 * 失败后按配置重新派生，不在这里回滚。
 */
public final class ChunkLoadTransaction {
    private final ChunkTicketService tickets;
    private final List<Change> changes = new ArrayList<>();

    ChunkLoadTransaction(ChunkTicketService tickets) {
        this.tickets = tickets;
    }

    public static ChunkLoadTransaction create() {
        return new ChunkLoadTransaction(ChunkLoaderManager.ticketService());
    }

    /** 账本当前位置，用作回滚边界。 */
    public int mark() {
        return changes.size();
    }

    /**
     * 下发一批票据变更。本次调用失败时只回滚到自己的入口，
     * 同事务里更早成功的操作留给持有者按自己的边界决定去留。
     */
    public void apply(ServerLevel level, Collection<ChunkLoadClaim> claims, boolean add) {
        int mark = mark();
        try {
            for (ChunkLoadClaim claim : claims) {
                changes.add(new Change(level, claim, add));
                write(level, claim, add);
            }
        } catch (RuntimeException exception) {
            rollbackTo(mark);
            throw exception;
        }
    }

    /**
     * 尽力下发一批票据变更，单个失败只记日志且不记账。
     *
     * <p>用于恢复备份这类必须走完全程的操作：撤销不掉的区块留在原地并留下日志，
     * 比中断剩下的区域、让世界和配置更不一致要好。
     */
    public void applyLenient(ServerLevel level, Collection<ChunkLoadClaim> claims, boolean add) {
        for (ChunkLoadClaim claim : claims) {
            try {
                write(level, claim, add);
            } catch (RuntimeException exception) {
                ArchWeaverMod.LOGGER.error("下发区块票据失败", exception);
            }
        }
    }

    /** 逆序撤销 {@code mark} 之后的票据，并丢弃这段账目。 */
    public void rollbackTo(int mark) {
        for (int index = changes.size() - 1; index >= mark; index--) {
            Change change = changes.remove(index);
            try {
                write(change.level(), change.claim(), !change.add());
            } catch (RuntimeException exception) {
                ArchWeaverMod.LOGGER.error("回滚区块票据失败", exception);
            }
        }
    }

    private void write(ServerLevel level, ChunkLoadClaim claim, boolean add) {
        if (add) {
            tickets.add(level, claim);
        } else {
            tickets.remove(level, claim);
        }
    }

    private record Change(ServerLevel level, ChunkLoadClaim claim, boolean add) {
    }
}
