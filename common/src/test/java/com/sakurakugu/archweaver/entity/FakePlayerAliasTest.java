package com.sakurakugu.archweaver.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class FakePlayerAliasTest {
    @Test
    void tabOrderSwitchesWithoutChangingRealNameOrItsStyle() {
        var name = Component.literal("robot-1").withStyle(ChatFormatting.BLUE);
        assertEquals("robot-1 [矿场一号]", FakePlayerAlias.tabName(name, "矿场一号", false).getString());
        var aliasFirst = FakePlayerAlias.tabName(name, "矿场一号", true);
        assertEquals("矿场一号 [robot-1]", aliasFirst.getString());
        assertEquals(name, aliasFirst.getSiblings().get(2));
        assertEquals("robot-1", name.getString());
        assertEquals(FakePlayerAlias.tabName(name, "", false), FakePlayerAlias.tabName(name, "", true));
    }

    @Test
    void acceptsChineseSpacesAndEmojiWithoutChangingIdentityText() {
        assertEquals("矿场 一号 ⛏️", FakePlayerAlias.normalize("  矿场 一号 ⛏️  "));
        assertEquals("", FakePlayerAlias.normalize("   "));
        assertEquals("矿".repeat(32), FakePlayerAlias.normalize("矿".repeat(32)));
    }

    @Test
    void rejectsOverlongAliasesControlsFormattingCodesAndBrokenSurrogates() {
        assertThrows(IllegalArgumentException.class, () -> FakePlayerAlias.normalize("矿".repeat(33)));
        assertThrows(IllegalArgumentException.class, () -> FakePlayerAlias.normalize("矿\n场"));
        assertThrows(IllegalArgumentException.class, () -> FakePlayerAlias.normalize("§a矿场"));
        assertThrows(IllegalArgumentException.class, () -> FakePlayerAlias.normalize("矿\uD800"));
    }
}
