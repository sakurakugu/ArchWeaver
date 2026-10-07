package com.sakurakugu.archweaver.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.junit.jupiter.api.Test;

class FakePlayerAliasTest {
    @Test
    void tabOrderSwitchesWithoutChangingTheRealNameText() {
        var name = Component.literal("robot-1").withStyle(ChatFormatting.BLUE);
        assertEquals("robot-1 [矿场一号]", FakePlayerAlias.tabName(name, "矿场一号", false).getString());
        var aliasFirst = FakePlayerAlias.tabName(name, "矿场一号", true);
        assertEquals("矿场一号 [robot-1]", aliasFirst.getString());
        assertEquals("robot-1", aliasFirst.getSiblings().get(2).getString());
        assertEquals("robot-1", name.getString());
    }

    @Test
    void emptyAliasMarkerTakesTheSameSlotAsTheAlias() {
        var name = Component.literal("robot-1");
        // 没有别名时「假人」占位顶替别名那一格，同样跟随别名优先换边。
        assertEquals(List.of("robot-1", " [", "gui.archweaver.fakeplayer.marker", "]"),
            runs(FakePlayerAlias.tabName(name, "", false)));
        assertEquals(List.of("gui.archweaver.fakeplayer.marker", " [", "robot-1", "]"),
            runs(FakePlayerAlias.tabName(name, "", true)));
    }

    @Test
    void primaryItemIsBrightWhileTheBracketedItemTurnsDarkGray() {
        var team = new PlayerTeam(new Scoreboard(), "red");
        team.setColor(ChatFormatting.RED);
        var displayName = team.getFormattedName(Component.literal("robot-1"));

        for (String alias : List.of("矿场一号", "")) {
            // 别名优先：别名（或「假人」占位）在最前面用白色，真名退进方括号改用深灰。
            var aliasFirstRuns = styledRuns(FakePlayerAlias.tabName(displayName, alias, true));
            assertEquals(TextColor.fromLegacyFormat(ChatFormatting.DARK_GRAY), colorOf(aliasFirstRuns, "robot-1"));
            assertEquals(TextColor.fromLegacyFormat(ChatFormatting.WHITE), colorOfSlot(aliasFirstRuns));

            // 默认顺序：真名是主角，保留自己的颜色；别名（或占位）退进方括号用深灰。
            var nameFirstRuns = styledRuns(FakePlayerAlias.tabName(displayName, alias, false));
            assertEquals(TextColor.fromLegacyFormat(ChatFormatting.RED), colorOf(nameFirstRuns, "robot-1"));
            assertEquals(TextColor.fromLegacyFormat(ChatFormatting.DARK_GRAY), colorOfSlot(nameFirstRuns));
        }
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

    private record StyleRun(String text, Style style) {
    }

    private static List<String> runs(Component component) {
        return styledRuns(component).stream().map(StyleRun::text).toList();
    }

    private static List<StyleRun> styledRuns(Component component) {
        List<StyleRun> runs = new ArrayList<>();
        component.visit((Style style, String text) -> {
            runs.add(new StyleRun(text, style));
            return Optional.empty();
        }, Style.EMPTY);
        return runs;
    }

    private static TextColor colorOf(List<StyleRun> runs, String text) {
        return runs.stream().filter(run -> run.text().equals(text)).findFirst().orElseThrow().style().getColor();
    }

    /** 别名那一格的样式：除开真名与方括号，剩下的那一段就是别名或「假人」占位。 */
    private static TextColor colorOfSlot(List<StyleRun> runs) {
        return runs.stream()
            .filter(run -> !run.text().equals("robot-1") && !run.text().equals(" [") && !run.text().equals("]"))
            .findFirst().orElseThrow().style().getColor();
    }
}
