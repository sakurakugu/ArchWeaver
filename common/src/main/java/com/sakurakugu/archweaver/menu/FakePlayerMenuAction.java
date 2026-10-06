package com.sakurakugu.archweaver.menu;

import com.sakurakugu.archweaver.entity.FakePlayerActions;
import net.minecraft.world.level.GameType;

import java.util.Objects;

/**
 * 假人菜单按钮动作的类型化表示。
 *
 * <p>传输层受原版 {@code ServerboundContainerButtonClickPacket} 限制只能携带一个整数，
 * 与整数的互转由 {@link FakePlayerMenuActionCodec} 负责。新增动作时只需要追加一个记录或
 * {@link Simple} 取值，不必再手工分配动作编号区间，也不会影响已有编号。
 *
 * <p>各实现类在构造时校验参数范围，因此拿到实例即可认为参数合法。
 *
 * <p>一次点击的完整链路：界面按钮 → {@code FakePlayerInventoryScreen.sendAction} →
 * {@link FakePlayerMenuActionCodec#encode} → 原版按钮包 → {@code FakePlayerInventoryMenu.clickMenuButton}
 * → {@link FakePlayerMenuActionCodec#decode} → 穷尽 switch 分派。容器 ID 由原版包携带并用于路由，
 * 权限由菜单在收到编号后自行校验，因此动作本身这两样都不带。
 *
 * <p>排查「按钮点了没反应」时先确认编号能否被 {@link FakePlayerMenuActionCodec#decode} 解出：
 * 解不出会被 {@code clickMenuButton} 静默丢弃并返回 false，界面上不会有任何提示。
 */
public sealed interface FakePlayerMenuAction {
    /** 不带参数的动作。 */
    enum Simple implements FakePlayerMenuAction {
        ENDER_CHEST,
        REMOVE,
        POSSESS,
        MOUNT,
        MOUNT_NEARBY,
        MOUNT_ANYTHING,
        DISMOUNT,
        /** 单次向前移动一格；持续移动见 {@link Held}。 */
        MOVE_FORWARD,
        MOVE_BACKWARD,
        MOVE_LEFT,
        MOVE_RIGHT,
        JUMP,
        ATTACK_ONCE,
        USE_ONCE,
        TURN_LEFT,
        TURN_RIGHT,
        SNEAK,
        FLY_UP,
        FLY_DOWN,
        TOGGLE_FLIGHT,
        TOGGLE_BODY_FOLLOWS_HEAD,
        STOP_ALL
    }

    /** 可长按持续执行的控制项。 */
    enum Control {
        MOVE_FORWARD,
        MOVE_BACKWARD,
        MOVE_LEFT,
        MOVE_RIGHT,
        TURN_LEFT,
        TURN_RIGHT,
        ATTACK,
        USE,
        JUMP,
        FLY_UP,
        FLY_DOWN
    }

    /** 选中假人快捷栏的第 {@code slot} 格。 */
    record HotbarSelect(int slot) implements FakePlayerMenuAction {
        /** 快捷栏槽位的最大索引。 */
        public static final int MAX_SLOT = 8;

        public HotbarSelect {
            if (slot < 0 || slot > MAX_SLOT) {
                throw new IllegalArgumentException("快捷栏槽位越界: " + slot);
            }
        }
    }

    /** 丢弃物品；{@code value} 按 {@code percentage} 决定是个数还是百分比。 */
    record Drop(int value, boolean percentage, boolean continuous) implements FakePlayerMenuAction {
        /** 按数量丢弃时允许的最大值。 */
        public static final int MAX_AMOUNT = 64;
        /** 按百分比丢弃时允许的最大值。 */
        public static final int MAX_PERCENTAGE = 100;

        public Drop {
            int maximum = percentage ? MAX_PERCENTAGE : MAX_AMOUNT;
            if (value < 1 || value > maximum) {
                throw new IllegalArgumentException("丢弃数值必须在 1 到 " + maximum + " 之间: " + value);
            }
        }
    }

    /** 在操作者与假人之间转移物品。 */
    record Transfer(boolean toTarget, boolean all, boolean includeHotbar) implements FakePlayerMenuAction {
    }

    /** 切换自动化开关，{@code index} 对应 {@code FakePlayerAutomation} 的设置序号。 */
    record Automation(int index) implements FakePlayerMenuAction {
        /** 自动化设置的最大序号。 */
        public static final int MAX_INDEX = 3;

        public Automation {
            if (index < 0 || index > MAX_INDEX) {
                throw new IllegalArgumentException("自动化设置序号越界: " + index);
            }
        }
    }

    /** 切换某个方向的持续移动。 */
    record ToggleMove(FakePlayerActions.MoveDirection direction) implements FakePlayerMenuAction {
        public ToggleMove {
            Objects.requireNonNull(direction, "direction");
        }
    }

    /** 切换攻击、使用或跳跃的持续执行。 */
    record ToggleContinuous(FakePlayerActions.ScheduledAction action) implements FakePlayerMenuAction {
        public ToggleContinuous {
            requireContinuous(action);
        }
    }

    /** 按下或松开一个可长按的控制项。 */
    record Held(Control control, boolean pressed) implements FakePlayerMenuAction {
        public Held {
            Objects.requireNonNull(control, "control");
        }
    }

    /** 设置持续动作的间隔，取值范围为 1 到 {@link #MAX_INTERVAL}。 */
    record ContinuousInterval(FakePlayerActions.ScheduledAction action, int interval)
        implements FakePlayerMenuAction {
        /** 间隔的最大值，单位为游戏刻。 */
        public static final int MAX_INTERVAL = 100;

        public ContinuousInterval {
            requireContinuous(action);
            if (interval < 1 || interval > MAX_INTERVAL) {
                throw new IllegalArgumentException("持续动作间隔越界: " + interval);
            }
        }
    }

    /** 设置身体朝向。 */
    record SetBodyYaw(int yaw) implements FakePlayerMenuAction {
        /** 身体朝向的最小角度。 */
        public static final int MIN_YAW = -180;
        /** 身体朝向的最大角度。 */
        public static final int MAX_YAW = 179;

        public SetBodyYaw {
            if (yaw < MIN_YAW || yaw > MAX_YAW) {
                throw new IllegalArgumentException("身体朝向越界: " + yaw);
            }
        }
    }

    /** 切换假人游戏模式。 */
    record SetGameMode(GameType gameType) implements FakePlayerMenuAction {
        public SetGameMode {
            Objects.requireNonNull(gameType, "gameType");
        }
    }

    /** 持续动作只支持攻击、使用和跳跃，丢弃不属于可切换的持续动作。 */
    private static void requireContinuous(FakePlayerActions.ScheduledAction action) {
        if (action == null || action == FakePlayerActions.ScheduledAction.DROP) {
            throw new IllegalArgumentException("不支持持续执行的动作: " + action);
        }
    }
}
