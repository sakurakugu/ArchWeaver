package com.sakurakugu.archweaver.menu;

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
import net.minecraft.world.level.GameType;

/**
 * {@link FakePlayerMenuAction} 与原版菜单按钮协议单个整数之间的编解码。
 *
 * <p>整数高 8 位是动作类型，低 24 位是参数；参数位段按动作类型各自划分，未使用的位必须为零。
 * 解码对越界的类型、参数和多余置位都直接报错，避免非法编号被静默当成别的动作执行。
 *
 * <p>线上编号与 {@link Simple}、{@link Control} 等枚举的声明顺序绑定，因此这些枚举只能追加，
 * 不能重排或删除已有取值。
 */
public final class FakePlayerMenuActionCodec {
    /** 动作类型所在的位段，占据高 8 位。 */
    private static final int KIND_SHIFT = 24;
    /** 参数位段的掩码，占据低 24 位。 */
    private static final int PAYLOAD_MASK = 0xFFFFFF;

    private static final int SIMPLE_MASK = 0x1F;
    private static final int HOTBAR_SLOT_MASK = 0xF;
    /** 丢弃数值占低 7 位，第 7 位为百分比，第 8 位为连续模式。 */
    private static final int DROP_MASK = 0x1FF;
    private static final int DROP_VALUE_MASK = 0x7F;
    private static final int DROP_PERCENTAGE_BIT = 1 << 7;
    private static final int DROP_CONTINUOUS_BIT = 1 << 8;
    private static final int TRANSFER_MASK = 0x7;
    private static final int AUTOMATION_MASK = 0x3;
    private static final int DIRECTION_MASK = 0x3;
    private static final int CONTINUOUS_MASK = 0x3;
    /** 控制项占低 4 位，第 4 位为按下状态。 */
    private static final int HELD_MASK = 0x1F;
    private static final int HELD_CONTROL_MASK = 0xF;
    private static final int HELD_PRESSED_BIT = 1 << 4;
    /** 持续动作占低 2 位，间隔减一占第 2 到第 8 位。 */
    private static final int INTERVAL_MASK = 0x1FF;
    private static final int INTERVAL_CONTINUOUS_MASK = 0x3;
    /** 身体朝向以 180 为偏移存放在低 9 位。 */
    private static final int BODY_YAW_MASK = 0x1FF;
    private static final int BODY_YAW_OFFSET = 180;
    private static final int GAME_MODE_MASK = 0x3;

    private static final Simple[] SIMPLE_ACTIONS = Simple.values();
    private static final Control[] CONTROLS = Control.values();
    private static final FakePlayerActions.MoveDirection[] MOVE_DIRECTIONS =
        FakePlayerActions.MoveDirection.values();
    private static final FakePlayerActions.ScheduledAction[] CONTINUOUS_ACTIONS =
        FakePlayerActions.ScheduledAction.values();

    private FakePlayerMenuActionCodec() {
    }

    /** 把动作编码为原版菜单按钮协议可传输的整数。 */
    public static int encode(FakePlayerMenuAction action) {
        Kind kind;
        int payload;
        switch (action) {
            case Simple simple -> {
                kind = Kind.SIMPLE;
                payload = simple.ordinal();
            }
            case HotbarSelect hotbarSelect -> {
                kind = Kind.HOTBAR_SELECT;
                payload = hotbarSelect.slot();
            }
            case Drop drop -> {
                kind = Kind.DROP;
                payload = drop.value()
                    | (drop.percentage() ? DROP_PERCENTAGE_BIT : 0)
                    | (drop.continuous() ? DROP_CONTINUOUS_BIT : 0);
            }
            case Transfer transfer -> {
                kind = Kind.TRANSFER;
                payload = (transfer.toTarget() ? 1 : 0)
                    | (transfer.all() ? 1 << 1 : 0)
                    | (transfer.includeHotbar() ? 1 << 2 : 0);
            }
            case Automation automation -> {
                kind = Kind.AUTOMATION;
                payload = automation.index();
            }
            case ToggleMove toggleMove -> {
                kind = Kind.TOGGLE_MOVE;
                payload = toggleMove.direction().ordinal();
            }
            case ToggleContinuous toggleContinuous -> {
                kind = Kind.TOGGLE_CONTINUOUS;
                payload = toggleContinuous.action().ordinal();
            }
            case Held held -> {
                kind = Kind.HELD;
                payload = held.control().ordinal() | (held.pressed() ? HELD_PRESSED_BIT : 0);
            }
            case ContinuousInterval continuousInterval -> {
                kind = Kind.CONTINUOUS_INTERVAL;
                payload = continuousInterval.action().ordinal()
                    | (continuousInterval.interval() - 1) << 2;
            }
            case SetBodyYaw setBodyYaw -> {
                kind = Kind.SET_BODY_YAW;
                payload = setBodyYaw.yaw() + BODY_YAW_OFFSET;
            }
            case SetGameMode setGameMode -> {
                kind = Kind.SET_GAME_MODE;
                payload = setGameMode.gameType().getId();
            }
        }
        return kind.ordinal() << KIND_SHIFT | payload;
    }

    /**
     * 还原动作。
     *
     * @throws IllegalArgumentException 编号类型未知、参数越界或存在多余的置位
     */
    public static FakePlayerMenuAction decode(int raw) {
        int kind = raw >>> KIND_SHIFT & 0xFF;
        int payload = raw & PAYLOAD_MASK;
        Kind[] kinds = Kind.values();
        if (kind >= kinds.length) {
            throw new IllegalArgumentException("未知的菜单动作编号: " + raw);
        }
        return switch (kinds[kind]) {
            case SIMPLE -> pick(SIMPLE_ACTIONS, parameter(payload, SIMPLE_MASK));
            case HOTBAR_SELECT -> new HotbarSelect(parameter(payload, HOTBAR_SLOT_MASK));
            case DROP -> {
                int bits = parameter(payload, DROP_MASK);
                yield new Drop(bits & DROP_VALUE_MASK, (bits & DROP_PERCENTAGE_BIT) != 0,
                    (bits & DROP_CONTINUOUS_BIT) != 0);
            }
            case TRANSFER -> {
                int bits = parameter(payload, TRANSFER_MASK);
                yield new Transfer((bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0);
            }
            case AUTOMATION -> new Automation(parameter(payload, AUTOMATION_MASK));
            case TOGGLE_MOVE -> new ToggleMove(pick(MOVE_DIRECTIONS, parameter(payload, DIRECTION_MASK)));
            case TOGGLE_CONTINUOUS ->
                new ToggleContinuous(pick(CONTINUOUS_ACTIONS, parameter(payload, CONTINUOUS_MASK)));
            case HELD -> {
                int bits = parameter(payload, HELD_MASK);
                yield new Held(pick(CONTROLS, bits & HELD_CONTROL_MASK), (bits & HELD_PRESSED_BIT) != 0);
            }
            case CONTINUOUS_INTERVAL -> {
                int bits = parameter(payload, INTERVAL_MASK);
                yield new ContinuousInterval(pick(CONTINUOUS_ACTIONS, bits & INTERVAL_CONTINUOUS_MASK),
                    (bits >>> 2) + 1);
            }
            case SET_BODY_YAW ->
                new SetBodyYaw(parameter(payload, BODY_YAW_MASK) - BODY_YAW_OFFSET);
            case SET_GAME_MODE -> new SetGameMode(GameType.byId(parameter(payload, GAME_MODE_MASK)));
        };
    }

    /** 取出参数，同时确认参数位段之外没有多余的置位。 */
    private static int parameter(int payload, int mask) {
        if ((payload & ~mask) != 0) {
            throw new IllegalArgumentException("菜单动作参数越界: " + payload);
        }
        return payload & mask;
    }

    /** 按枚举序号取回取值，序号超出声明数量说明编号非法。 */
    private static <T> T pick(T[] values, int index) {
        if (index >= values.length) {
            throw new IllegalArgumentException("菜单动作参数超出取值范围: " + index);
        }
        return values[index];
    }

    /** 动作类型的位段取值。只能追加，不能重排，否则会改变线上编号。 */
    private enum Kind {
        SIMPLE,
        HOTBAR_SELECT,
        DROP,
        TRANSFER,
        AUTOMATION,
        TOGGLE_MOVE,
        TOGGLE_CONTINUOUS,
        HELD,
        CONTINUOUS_INTERVAL,
        SET_BODY_YAW,
        SET_GAME_MODE
    }
}
