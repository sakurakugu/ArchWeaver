package com.sakurakugu.archweaver.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sakurakugu.archweaver.entity.FakePlayerActions;
import com.sakurakugu.archweaver.menu.FakePlayerMenuAction.Automation;
import com.sakurakugu.archweaver.menu.FakePlayerMenuAction.ContinuousInterval;
import com.sakurakugu.archweaver.menu.FakePlayerMenuAction.Control;
import com.sakurakugu.archweaver.menu.FakePlayerMenuAction.Drop;
import com.sakurakugu.archweaver.menu.FakePlayerMenuAction.Held;
import com.sakurakugu.archweaver.menu.FakePlayerMenuAction.HotbarSelect;
import com.sakurakugu.archweaver.menu.FakePlayerMenuAction.SetBodyYaw;
import com.sakurakugu.archweaver.menu.FakePlayerMenuAction.SetGameMode;
import com.sakurakugu.archweaver.menu.FakePlayerMenuAction.Simple;
import com.sakurakugu.archweaver.menu.FakePlayerMenuAction.ToggleContinuous;
import com.sakurakugu.archweaver.menu.FakePlayerMenuAction.ToggleMove;
import com.sakurakugu.archweaver.menu.FakePlayerMenuAction.Transfer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.GameType;
import org.junit.jupiter.api.Test;

class FakePlayerMenuActionCodecTest {
    @Test
    void everyActionSurvivesARoundTrip() {
        for (FakePlayerMenuAction action : allActions()) {
            int encoded = FakePlayerMenuActionCodec.encode(action);
            assertEquals(action, FakePlayerMenuActionCodec.decode(encoded),
                "动作往返后发生了变化: " + action);
        }
    }

    @Test
    void encodedCodesStayInsideOneIntAndNeverRepeat() {
        List<FakePlayerMenuAction> actions = allActions();
        List<Integer> codes = new ArrayList<>(actions.size());
        for (FakePlayerMenuAction action : actions) {
            int encoded = FakePlayerMenuActionCodec.encode(action);
            assertTrue(encoded >= 0, "动作编号必须落在正整数范围: " + action);
            codes.add(encoded);
        }
        assertEquals(codes.size(), codes.stream().distinct().count(),
            "不同的动作被编码成了同一个编号");
    }

    /** 线上编号依赖枚举声明顺序，这里把几个代表性编号钉住，避免重排枚举后静默改变协议。 */
    @Test
    void wireCodesAreStable() {
        assertEquals(0, FakePlayerMenuActionCodec.encode(Simple.ENDER_CHEST));
        assertEquals(0x1000003, FakePlayerMenuActionCodec.encode(new HotbarSelect(3)));
        assertEquals(0x7000010, FakePlayerMenuActionCodec.encode(new Held(Control.MOVE_FORWARD, true)));
        assertEquals(0x7000000, FakePlayerMenuActionCodec.encode(new Held(Control.MOVE_FORWARD, false)));
        assertEquals(0x9000000, FakePlayerMenuActionCodec.encode(new SetBodyYaw(-180)));
        assertEquals(0x9000167, FakePlayerMenuActionCodec.encode(new SetBodyYaw(179)));
    }

    @Test
    void boundaryValuesSurviveARoundTrip() {
        assertEquals(new Drop(64, false, false), roundTrip(new Drop(64, false, false)));
        assertEquals(new Drop(1, true, true), roundTrip(new Drop(1, true, true)));
        assertEquals(new Drop(100, true, true), roundTrip(new Drop(100, true, true)));
        assertEquals(new HotbarSelect(8), roundTrip(new HotbarSelect(8)));
        assertEquals(new Automation(3), roundTrip(new Automation(3)));
        assertEquals(new ContinuousInterval(FakePlayerActions.ScheduledAction.JUMP, 1),
            roundTrip(new ContinuousInterval(FakePlayerActions.ScheduledAction.JUMP, 1)));
        assertEquals(new ContinuousInterval(FakePlayerActions.ScheduledAction.ATTACK, 100),
            roundTrip(new ContinuousInterval(FakePlayerActions.ScheduledAction.ATTACK, 100)));
    }

    @Test
    void unknownActionKindIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> FakePlayerMenuActionCodec.decode(0xFF << 24));
    }

    @Test
    void parametersOutsideTheirBitFieldAreRejected() {
        // 丢弃动作只使用低 9 位，第 20 位被置位说明编号非法。
        assertThrows(IllegalArgumentException.class,
            () -> FakePlayerMenuActionCodec.decode(2 << 24 | 1 << 20));
    }

    @Test
    void outOfRangeParametersAreRejected() {
        // 快捷栏槽位只有 0 到 8。
        assertThrows(IllegalArgumentException.class,
            () -> FakePlayerMenuActionCodec.decode(1 << 24 | 9));
        // 不带参数的动作只有 21 个，序号 31 越界。
        assertThrows(IllegalArgumentException.class,
            () -> FakePlayerMenuActionCodec.decode(31));
        // 可长按的控制项只有 11 个，序号 11 越界。
        assertThrows(IllegalArgumentException.class,
            () -> FakePlayerMenuActionCodec.decode(7 << 24 | 11));
        // 丢弃数值从 1 开始，0 非法。
        assertThrows(IllegalArgumentException.class,
            () -> FakePlayerMenuActionCodec.decode(2 << 24));
        // 丢弃不能作为持续动作。
        assertThrows(IllegalArgumentException.class,
            () -> FakePlayerMenuActionCodec.decode(6 << 24 | FakePlayerActions.ScheduledAction.DROP.ordinal()));
    }

    private static FakePlayerMenuAction roundTrip(FakePlayerMenuAction action) {
        return FakePlayerMenuActionCodec.decode(FakePlayerMenuActionCodec.encode(action));
    }

    private static List<FakePlayerMenuAction> allActions() {
        List<FakePlayerMenuAction> actions = new ArrayList<>();
        actions.addAll(List.of(Simple.values()));
        for (Control control : Control.values()) {
            actions.add(new Held(control, true));
            actions.add(new Held(control, false));
        }
        for (FakePlayerActions.MoveDirection direction : FakePlayerActions.MoveDirection.values()) {
            actions.add(new ToggleMove(direction));
        }
        for (FakePlayerActions.ScheduledAction scheduled : FakePlayerActions.ScheduledAction.values()) {
            if (scheduled == FakePlayerActions.ScheduledAction.DROP) {
                continue;
            }
            actions.add(new ToggleContinuous(scheduled));
            for (int interval : new int[] {1, 50, ContinuousInterval.MAX_INTERVAL}) {
                actions.add(new ContinuousInterval(scheduled, interval));
            }
        }
        for (GameType gameType : GameType.values()) {
            actions.add(new SetGameMode(gameType));
        }
        for (int slot = 0; slot <= HotbarSelect.MAX_SLOT; slot++) {
            actions.add(new HotbarSelect(slot));
        }
        for (int index = 0; index <= Automation.MAX_INDEX; index++) {
            actions.add(new Automation(index));
        }
        for (boolean toTarget : new boolean[] {true, false}) {
            for (boolean all : new boolean[] {true, false}) {
                for (boolean includeHotbar : new boolean[] {true, false}) {
                    actions.add(new Transfer(toTarget, all, includeHotbar));
                }
            }
        }
        for (boolean percentage : new boolean[] {true, false}) {
            for (boolean continuous : new boolean[] {true, false}) {
                int maximum = percentage
                    ? Drop.MAX_PERCENTAGE
                    : Drop.MAX_AMOUNT;
                actions.add(new Drop(1, percentage, continuous));
                actions.add(new Drop(maximum, percentage, continuous));
            }
        }
        for (int yaw : new int[] {
            SetBodyYaw.MIN_YAW, -1, 0, 1, SetBodyYaw.MAX_YAW
        }) {
            actions.add(new SetBodyYaw(yaw));
        }
        return actions;
    }
}
