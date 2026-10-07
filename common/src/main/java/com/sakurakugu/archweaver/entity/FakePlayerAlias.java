package com.sakurakugu.archweaver.entity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** 别名只影响显示，不参与玩家档案、UUID 或命令目标的解析。 */
public final class FakePlayerAlias {
    public static final int MAX_LENGTH = 32;

    private FakePlayerAlias() {
    }

    /**
     * 别名那一格始终放最前或最后，另一项放进方括号，因此别名为空时的「假人」占位也会跟着换边。
     * 颜色跟着位置走：前面那一项用白色，退进方括号的那一项用深灰。
     */
    public static Component tabName(Component realName, String alias, boolean aliasFirst) {
        if (aliasFirst) {
            // 真名退进方括号，只换成次要色，文字与点击悬停等原版格式都保留。
            return Component.empty().append(aliasSlot(alias, ChatFormatting.WHITE))
                .append(bracket(" [")).append(realName.copy().withStyle(ChatFormatting.DARK_GRAY))
                .append(bracket("]"));
        }
        // 真名是主角时不动它的样式，队伍颜色等原版格式照旧生效。
        return realName.copy().append(bracket(" [")).append(aliasSlot(alias, ChatFormatting.DARK_GRAY))
            .append(bracket("]"));
    }

    /** 别名那一格的内容：别名本身，别名为空时换成「假人」占位，与头顶名牌、假人列表共用同一份文案。 */
    private static Component aliasSlot(String alias, ChatFormatting color) {
        return (alias.isEmpty()
            ? Component.translatable("gui.archweaver.fakeplayer.marker")
            : Component.literal(alias)).withStyle(color);
    }

    private static Component bracket(String text) {
        return Component.literal(text).withStyle(ChatFormatting.DARK_GRAY);
    }

    /** 空白别名表示恢复默认标记；允许中文和空格，拒绝控制字符与格式代码。 */
    public static String normalize(String value) {
        String alias = value.strip();
        if (alias.length() > MAX_LENGTH || alias.codePoints().anyMatch(character ->
            Character.isISOControl(character) || character == '§'
                || character >= Character.MIN_SURROGATE && character <= Character.MAX_SURROGATE)) {
            throw new IllegalArgumentException("别名最多 32 个字符，不能包含控制字符或格式代码");
        }
        return alias;
    }
}
