package com.sakurakugu.archweaver.entity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** 别名只影响显示，不参与玩家档案、UUID 或命令目标的解析。 */
public final class FakePlayerAlias {
    public static final int MAX_LENGTH = 32;

    private FakePlayerAlias() {
    }

    /** 只交换显示顺序，真实名称组件保留队伍等原版格式。 */
    public static Component tabName(Component realName, String alias, boolean aliasFirst) {
        if (alias.isEmpty()) {
            return realName.copy().append(Component.translatable("gui.archweaver.fakeplayer.tab_marker")
                .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (aliasFirst) {
            return Component.empty().append(Component.literal(alias).withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(" [").withStyle(ChatFormatting.DARK_GRAY))
                .append(realName.copy()).append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY));
        }
        return realName.copy().append(Component.literal(" [" + alias + "]").withStyle(ChatFormatting.DARK_GRAY));
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
