package com.sakurakugu.archweaver.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sakurakugu.archweaver.ArchWeaverMod;
import com.sakurakugu.archweaver.config.ArchWeaverConfig;
import com.sakurakugu.archweaver.automation.FakePlayerAutomation;
import com.sakurakugu.archweaver.entity.FakePlayerActions;
import com.sakurakugu.archweaver.entity.FakePlayerActions.MoveDirection;
import com.sakurakugu.archweaver.entity.FakePlayerActions.RepeatMode;
import com.sakurakugu.archweaver.entity.FakePlayerManager;
import com.sakurakugu.archweaver.entity.FakePlayerPossession;
import com.sakurakugu.archweaver.entity.ProfileResolver;
import com.sakurakugu.archweaver.entity.FakeServerPlayer;
import com.sakurakugu.archweaver.entity.ExternalFakePlayerSupport;
import com.sakurakugu.archweaver.entity.MannequinManager;
import com.sakurakugu.archweaver.chunkloading.ChunkLoaderManager;
import com.sakurakugu.archweaver.menu.FakePlayerMenuOpener;
import com.sakurakugu.archweaver.network.ChunkMapOpenTarget;
import com.sakurakugu.archweaver.network.ChunkMapSnapshotPayload;
import com.sakurakugu.archweaver.network.OpenMainPagePayload;
import com.sakurakugu.archweaver.persistence.FakePlayerPersistence;
import com.sakurakugu.archweaver.platform.PlatformNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.GameModeArgument;
import net.minecraft.commands.arguments.coordinates.RotationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** 注册并处理 {@code /fakeplayer}、{@code /fakeplayer player} 与 {@code /player} 命令。 */
public final class FakePlayerCommand {
    private FakePlayerCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("fakeplayer")
                .requires(ArchWeaverConfig::canUseCommands)
                .executes(context -> spawn(context, nextName(context)))
                .then(Commands.literal("spawn")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .executes(context -> spawn(context, name(context)))))
                .then(Commands.literal("kill")
                    .then(fakeNameArgument().executes(FakePlayerCommand::kill)))
                .then(Commands.literal("possess")
                    .then(fakeNameArgument().executes(FakePlayerCommand::possess)))
                .then(Commands.literal("unpossess").executes(FakePlayerCommand::unpossess))
                .then(Commands.literal("list").executes(FakePlayerCommand::list))
                .then(mannequinCommand())
                .then(guiCommand("gui"))
                .then(PresetCommand.presetCommand())
                .then(PresetCommand.groupCommand())
                .then(Commands.literal("player").then(playerTargetCommand()))
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(context -> spawn(context, name(context))))
        );

        // /player <名称> <动作> 与 /fakeplayer player <名称> <动作> 等价，两份命令树各自独立注册。
        dispatcher.register(
            Commands.literal("player")
                .requires(ArchWeaverConfig::canUseCommands)
                .then(playerTargetCommand())
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> mannequinCommand() {
        return Commands.literal("mannequin")
            .executes(context -> spawnMannequin(context, nextMannequinName(context)))
            .then(Commands.argument("name", StringArgumentType.word())
                .executes(context -> spawnMannequin(context, name(context))))
            .then(Commands.literal("spawn")
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(context -> spawnMannequin(context, name(context)))))
            .then(Commands.literal("kill")
                .then(mannequinNameArgument().executes(FakePlayerCommand::killMannequin)))
            .then(Commands.literal("delete")
                .then(mannequinNameArgument().executes(FakePlayerCommand::deleteMannequin)));
    }

    private static String nextMannequinName(CommandContext<CommandSourceStack> context) {
        int index = 1;
        while (MannequinManager.find(context.getSource().getServer(), "mannequin-" + index).isPresent()) index++;
        return "mannequin-" + index;
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> mannequinNameArgument() {
        return Commands.argument("name", StringArgumentType.word())
            .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                MannequinManager.registered(context.getSource().getServer()).stream()
                    .map(value -> value.name()), builder));
    }

    private static int spawnMannequin(CommandContext<CommandSourceStack> context, String name) {
        CommandSourceStack source = context.getSource();
        if (!name.matches("[A-Za-z0-9_-]{1,16}")) {
            source.sendFailure(Component.translatable("commands.archweaver.fakeplayer.invalid_name"));
            return 0;
        }
        try {
            var record = MannequinManager.find(source.getServer(), name).orElse(null);
            if (record != null) {
                if (MannequinManager.loaded(source.getServer(), record.uuid()).isPresent()) {
                    source.sendFailure(Component.translatable("commands.archweaver.fakeplayer.duplicate", name));
                    return 0;
                }
                MannequinManager.load(source.getServer(), record.uuid());
                source.sendSuccess(() -> Component.literal("已加载玩偶 " + record.name()), true);
            } else {
                var mannequin = MannequinManager.spawn(source.getServer(), source.getLevel(), name,
                    source.getPosition(), source.getRotation().y);
                source.sendSuccess(() -> Component.literal("已生成玩偶 " + mannequin.getName().getString()), true);
            }
            refreshList(source);
            return 1;
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.translatable("commands.archweaver.fakeplayer.duplicate", name));
            return 0;
        } catch (RuntimeException exception) {
            ArchWeaverMod.LOGGER.error("生成玩偶 {} 失败", name, exception);
            source.sendFailure(Component.literal("玩偶生成失败"));
            return 0;
        }
    }

    private static int killMannequin(CommandContext<CommandSourceStack> context) {
        String name = name(context);
        var record = MannequinManager.find(context.getSource().getServer(), name).orElse(null);
        if (record == null || !MannequinManager.unload(context.getSource().getServer(), record.uuid())) {
            context.getSource().sendFailure(Component.literal("找不到已加载玩偶 " + name));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("已卸载玩偶 " + record.name()), true);
        refreshList(context.getSource());
        return 1;
    }

    private static int deleteMannequin(CommandContext<CommandSourceStack> context) {
        String name = name(context);
        var record = MannequinManager.find(context.getSource().getServer(), name).orElse(null);
        if (record == null || !MannequinManager.remove(context.getSource().getServer(), record.uuid(), true)) {
            context.getSource().sendFailure(Component.literal("找不到玩偶 " + name));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("已删除玩偶 " + record.name()), true);
        refreshList(context.getSource());
        return 1;
    }

    /** {@code /player <名称> <动作>} 与 {@code /fakeplayer player <名称> <动作>} 共用的目标参数与动作分支。 */
    private static RequiredArgumentBuilder<CommandSourceStack, String> playerTargetCommand() {
        RequiredArgumentBuilder<CommandSourceStack, String> target =
            Commands.argument("name", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                    context.getSource().getServer().getPlayerList().getPlayers().stream()
                        .map(value -> value.getGameProfile().name()), builder));

        target.then(spawnCommand());
        target.then(Commands.literal("kill").executes(FakePlayerCommand::kill));
        target.then(Commands.literal("load").executes(FakePlayerCommand::load));
        target.then(Commands.literal("delete")
            .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
            .executes(FakePlayerCommand::delete));
        target.then(Commands.literal("alias")
            .executes(context -> setAlias(context, ""))
            .then(Commands.argument("alias", StringArgumentType.greedyString())
                .executes(context -> setAlias(context, StringArgumentType.getString(context, "alias")))));
        target.then(Commands.literal("shadow").executes(FakePlayerCommand::shadow));
        target.then(Commands.literal("gui")
            .executes(FakePlayerCommand::openPlayerGui)
            .then(Commands.literal("enderchest").executes(FakePlayerCommand::openEnderChest)));
        // 只把动作与输入设置恢复为默认值，不动背包等数据。
        target.then(Commands.literal("config")
            .then(Commands.literal("default").executes(FakePlayerCommand::resetSettings))
            .then(Commands.literal("reset").executes(FakePlayerCommand::resetSettings)));
        target.then(automationCommand());
        target.then(ChunkLoaderCommand.simulationCommand(FakePlayerCommand::getFake));
        target.then(Commands.literal("possess").executes(FakePlayerCommand::possess));
        target.then(Commands.literal("unpossess").executes(FakePlayerCommand::unpossessTarget));
        target.then(dropCommand("drop", false));
        target.then(dropCommand("dropStack", true));
        target.then(Commands.literal("hotbar")
            .then(Commands.argument("slot", IntegerArgumentType.integer(1, 9))
                .executes(FakePlayerCommand::hotbar)));
        target.then(simpleAction("swapHands", fake -> fake.actions().swapHands()));
        target.then(Commands.literal("mount")
            .executes(context -> mount(context, false))
            .then(Commands.argument("anything", StringArgumentType.word())
                .executes(context -> mount(context, true))));
        target.then(simpleAction("dismount", fake -> fake.actions().dismount()));
        target.then(lookCommand());
        target.then(moveCommand());
        target.then(repeatingCommand("jump", (fake, mode, interval) -> fake.actions().jump(mode, interval)));
        target.then(flightAction("fly", true));
        target.then(flightAction("unfly", false));
        target.then(simpleAction("sneak", fake -> fake.actions().setSneaking(true)));
        target.then(simpleAction("unsneak", fake -> fake.actions().setSneaking(false)));
        target.then(simpleAction("sprint", fake -> fake.actions().setSprinting(true)));
        target.then(simpleAction("unsprint", fake -> fake.actions().setSprinting(false)));
        target.then(turnCommand());
        target.then(repeatingCommand("attack", (fake, mode, interval) -> fake.actions().attack(mode, interval)));
        target.then(repeatingCommand("use", (fake, mode, interval) -> fake.actions().use(mode, interval)));
        target.then(simpleAction("stop", fake -> fake.actions().stop()));
        return target;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> automationCommand() {
        LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("automation");
        String[] names = {"autoReplenishment", "shulkerReplenishment", "autoReplaceTools", "autoFishing"};
        for (int index = 0; index < names.length; index++) {
            command.then(automationSetting(names[index], index));
        }
        return command;
    }

    private static int setAlias(CommandContext<CommandSourceStack> context, String alias) {
        FakeServerPlayer fake = getFake(context);
        if (fake == null) {
            return 0;
        }
        if (FakePlayerPossession.isPossessed(fake)) {
            context.getSource().sendFailure(Component.translatable("gui.archweaver.fakeplayer.possess_locked"));
            return 0;
        }
        try {
            fake.setAlias(alias);
        } catch (IllegalArgumentException exception) {
            context.getSource().sendFailure(Component.translatable("commands.archweaver.fakeplayer.invalid_alias"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable(
            fake.alias().isEmpty() ? "commands.archweaver.fakeplayer.alias_cleared"
                : "commands.archweaver.fakeplayer.alias_set", fake.getGameProfile().name(), fake.alias()), true);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> automationSetting(String name, int index) {
        LiteralArgumentBuilder<CommandSourceStack> setting = Commands.literal(name)
            .executes(context -> setAutomation(context, index, null));
        setting.then(Commands.literal("on").executes(context -> setAutomation(context, index, true)));
        setting.then(Commands.literal("off").executes(context -> setAutomation(context, index, false)));
        setting.then(Commands.literal("toggle").executes(context -> setAutomation(context, index, null)));
        return setting;
    }

    private static int setAutomation(CommandContext<CommandSourceStack> context, int index, Boolean value)
        throws CommandSyntaxException {
        FakeServerPlayer fake = getFake(context);
        FakePlayerAutomation.AutomationState state = fake.automation().settings();
        boolean next = value == null ? automationValue(state, index) : value;
        if (value == null) {
            next = !next;
        }
        boolean enabled = next;
        fake.automation().setSettings(withAutomationValue(state, index, enabled));
        context.getSource().sendSuccess(() -> Component.translatable(
            "commands.archweaver.fakeplayer.automation_set", fake.getGameProfile().name(), indexName(index),
            enabled ? Component.translatable("gui.archweaver.fakeplayer.automation.enabled")
                : Component.translatable("gui.archweaver.fakeplayer.automation.disabled")), true);
        return 1;
    }

    private static boolean automationValue(FakePlayerAutomation.AutomationState state, int index) {
        return switch (index) {
            case 0 -> state.autoReplenishment();
            case 1 -> state.autoReplenishmentFromShulkerBoxes();
            case 2 -> state.autoReplaceTools();
            default -> state.autoFishing();
        };
    }

    private static FakePlayerAutomation.AutomationState withAutomationValue(
        FakePlayerAutomation.AutomationState state, int index, boolean value
    ) {
        return switch (index) {
            case 0 -> state.withAutoReplenishment(value);
            case 1 -> state.withAutoReplenishmentFromShulkerBoxes(value);
            case 2 -> state.withAutoReplaceTools(value);
            default -> state.withAutoFishing(value);
        };
    }

    private static String indexName(int index) {
        return switch (index) {
            case 0 -> "autoReplenishment";
            case 1 -> "shulkerReplenishment";
            case 2 -> "autoReplaceTools";
            default -> "autoFishing";
        };
    }

    private static LiteralArgumentBuilder<CommandSourceStack> spawnCommand() {
        LiteralArgumentBuilder<CommandSourceStack> spawn = Commands.literal("spawn")
            .executes(context -> spawn(context, name(context)));
        spawn.then(gamemodeBranch("gamemode"));
        // Carpet 语法仅在 Carpet 已加载时提供，避免无 Carpet 时扩展 /player 语法。
        if (isCarpetLoaded()) spawn.then(gamemodeBranch("in"));

        var position = Commands.argument("position", Vec3Argument.vec3())
            .executes(context -> spawn(context, name(context)));
        var rotation = Commands.argument("direction", RotationArgument.rotation())
            .executes(context -> spawn(context, name(context)));
        var dimension = Commands.argument("dimension", DimensionArgument.dimension())
            .executes(context -> spawn(context, name(context)));
        dimension.then(gamemodeBranch("gamemode"));
        rotation.then(gamemodeBranch("gamemode"));
        rotation.then(Commands.literal("in")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(dimension));
        position.then(Commands.literal("facing").then(rotation));
        spawn.then(Commands.literal("at").then(position));
        return spawn;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> gamemodeBranch(String literal) {
        return Commands.literal(literal)
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(Commands.argument("gamemode", GameModeArgument.gameMode())
                .executes(context -> spawn(context, name(context))));
    }

    private static boolean isCarpetLoaded() {
        try {
            Class.forName("carpet.CarpetSettings", false, FakePlayerCommand.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }

    private static LiteralArgumentBuilder<CommandSourceStack> repeatingCommand(String literal, RepeatingAction action) {
        LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal(literal)
            .executes(context -> repeat(context, action, RepeatMode.ONCE, 1));
        addRepeatOptions(command, action);
        return command;
    }

    private static void addRepeatOptions(LiteralArgumentBuilder<CommandSourceStack> command, RepeatingAction action) {
        command.then(Commands.literal("continuous")
            .executes(context -> repeat(context, action, RepeatMode.CONTINUOUS, 1)));
        command.then(Commands.literal("once")
            .executes(context -> repeat(context, action, RepeatMode.ONCE, 1)));
        command.then(Commands.literal("interval")
            .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                .executes(context -> repeat(context, action, RepeatMode.INTERVAL,
                    IntegerArgumentType.getInteger(context, "ticks")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> dropCommand(String literal, boolean wholeStack) {
        LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal(literal);
        addDropOptions(command, wholeStack, DropTarget.MAIN_HAND);
        for (String targetName : new String[] {
            "mainhand", "offhand", "head", "chest", "legs", "feet", "armor", "all"
        }) {
            DropTarget target = DropTarget.valueOf(targetName.toUpperCase());
            LiteralArgumentBuilder<CommandSourceStack> branch = Commands.literal(targetName);
            addDropOptions(branch, wholeStack, target);
            command.then(branch);
        }
        LiteralArgumentBuilder<CommandSourceStack> slot = Commands.literal("slot")
            .then(Commands.argument("inventorySlot", IntegerArgumentType.integer(0, 35))
                .executes(context -> drop(context, wholeStack, DropTarget.SLOT, RepeatMode.ONCE, 1))
                .then(Commands.literal("continuous")
                    .executes(context -> drop(context, wholeStack, DropTarget.SLOT, RepeatMode.CONTINUOUS, 1)))
                .then(Commands.literal("once")
                    .executes(context -> drop(context, wholeStack, DropTarget.SLOT, RepeatMode.ONCE, 1)))
                .then(Commands.literal("interval")
                    .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                        .executes(context -> drop(context, wholeStack, DropTarget.SLOT, RepeatMode.INTERVAL,
                            IntegerArgumentType.getInteger(context, "ticks"))))));
        command.then(slot);
        // 文档中的 0~35 直接槽位语法由整数参数提供。
        command.then(Commands.argument("inventorySlot", IntegerArgumentType.integer(0, 35))
            .executes(context -> drop(context, wholeStack, DropTarget.SLOT, RepeatMode.ONCE, 1))
            .then(Commands.literal("continuous")
                .executes(context -> drop(context, wholeStack, DropTarget.SLOT, RepeatMode.CONTINUOUS, 1)))
            .then(Commands.literal("once")
                .executes(context -> drop(context, wholeStack, DropTarget.SLOT, RepeatMode.ONCE, 1)))
            .then(Commands.literal("interval")
                .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                    .executes(context -> drop(context, wholeStack, DropTarget.SLOT, RepeatMode.INTERVAL,
                        IntegerArgumentType.getInteger(context, "ticks"))))));
        return command;
    }

    private static void addDropOptions(
        LiteralArgumentBuilder<CommandSourceStack> command,
        boolean wholeStack,
        DropTarget target
    ) {
        command.executes(context -> drop(context, wholeStack, target, RepeatMode.ONCE, 1));
        command.then(Commands.literal("continuous")
            .executes(context -> drop(context, wholeStack, target, RepeatMode.CONTINUOUS, 1)));
        command.then(Commands.literal("once")
            .executes(context -> drop(context, wholeStack, target, RepeatMode.ONCE, 1)));
        command.then(Commands.literal("interval")
            .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                .executes(context -> drop(context, wholeStack, target, RepeatMode.INTERVAL,
                    IntegerArgumentType.getInteger(context, "ticks")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> lookCommand() {
        LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("look");
        command.then(simpleDirection("north", Direction.NORTH));
        command.then(simpleDirection("south", Direction.SOUTH));
        command.then(simpleDirection("west", Direction.WEST));
        command.then(simpleDirection("east", Direction.EAST));
        command.then(simpleDirection("up", Direction.UP));
        command.then(simpleDirection("down", Direction.DOWN));
        command.then(Commands.literal("at")
            .then(Commands.argument("position", Vec3Argument.vec3())
                .executes(context -> withFake(context,
                    fake -> fake.actions().lookAt(Vec3Argument.getVec3(context, "position"))))));
        return command;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> simpleDirection(String name, Direction direction) {
        return Commands.literal(name).executes(context -> withFake(context, fake -> fake.actions().look(direction)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> moveCommand() {
        LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("move");
        command.then(moveDirection("forward", MoveDirection.FORWARD));
        command.then(moveDirection("backward", MoveDirection.BACKWARD));
        command.then(moveDirection("left", MoveDirection.LEFT));
        command.then(moveDirection("right", MoveDirection.RIGHT));
        return command;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> moveDirection(String name, MoveDirection direction) {
        return Commands.literal(name).executes(context -> withFake(context, fake -> fake.actions().move(direction)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> turnCommand() {
        return Commands.literal("turn")
            .then(Commands.literal("back").executes(context -> withFake(context, fake -> fake.actions().turn(180.0F))))
            .then(Commands.literal("left").executes(context -> withFake(context, fake -> fake.actions().turn(-90.0F))))
            .then(Commands.literal("right").executes(context -> withFake(context, fake -> fake.actions().turn(90.0F))))
            .then(Commands.argument("pitch", FloatArgumentType.floatArg(-90.0F, 90.0F))
                .then(Commands.argument("yaw", FloatArgumentType.floatArg())
                    .executes(context -> withFake(context, fake -> fake.actions().setRotation(
                        FloatArgumentType.getFloat(context, "pitch"),
                        FloatArgumentType.getFloat(context, "yaw"))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> simpleAction(
        String name,
        java.util.function.Consumer<FakeServerPlayer> action
    ) {
        return Commands.literal(name).executes(context -> withFake(context, action));
    }

    /** {@code fly} 与 {@code unfly}：分别开启和关闭飞行，写法与 sneak/unsneak 保持一致。 */
    private static LiteralArgumentBuilder<CommandSourceStack> flightAction(String literal, boolean flying) {
        return Commands.literal(literal).executes(context -> setFlight(context, flying));
    }

    private static int setFlight(CommandContext<CommandSourceStack> context, boolean target) {
        FakeServerPlayer fake = getFake(context);
        if (fake == null) {
            return 0;
        }
        if (FakePlayerPossession.isPossessed(fake)) {
            context.getSource().sendFailure(Component.translatable("gui.archweaver.fakeplayer.possess_locked"));
            return 0;
        }
        // 旁观模式禁止关闭飞行，不具备飞行能力时禁止开启，由动作层统一守卫。
        if (!fake.actions().setFlying(target)) {
            context.getSource().sendFailure(Component.translatable(
                "commands.archweaver.fakeplayer.flight_locked", fake.getGameProfile().name()));
            return 0;
        }
        FakePlayerPersistence.track(fake);
        context.getSource().sendSuccess(() -> Component.translatable(
            "commands.archweaver.fakeplayer.flight_set", fake.getGameProfile().name(),
            Component.translatable(target
                ? "gui.archweaver.fakeplayer.automation.enabled"
                : "gui.archweaver.fakeplayer.automation.disabled")), true);
        return 1;
    }

    private static int repeat(
        CommandContext<CommandSourceStack> context,
        RepeatingAction action,
        RepeatMode mode,
        int interval
    ) {
        return withFake(context, fake -> action.run(fake, mode, interval));
    }

    private static int drop(
        CommandContext<CommandSourceStack> context,
        boolean wholeStack,
        DropTarget target,
        RepeatMode mode,
        int interval
    ) {
        return withFake(context, fake -> {
            if (target == DropTarget.ALL) {
                fake.actions().dropAll(wholeStack, mode, interval);
                return;
            }
            if (target == DropTarget.ARMOR) {
                fake.actions().dropArmor(wholeStack, mode, interval);
                return;
            }
            int slot = switch (target) {
                case MAINHAND -> fake.getInventory().getSelectedSlot();
                case OFFHAND -> 40;
                case HEAD -> 39;
                case CHEST -> 38;
                case LEGS -> 37;
                case FEET -> 36;
                case SLOT -> IntegerArgumentType.getInteger(context, "inventorySlot");
                case ARMOR, ALL -> throw new IllegalStateException();
            };
            fake.actions().drop(slot, wholeStack, mode, interval);
        });
    }

    private static int hotbar(CommandContext<CommandSourceStack> context) {
        return withFake(context, fake -> fake.getInventory().setSelectedSlot(
            IntegerArgumentType.getInteger(context, "slot") - 1));
    }

    private static int mount(CommandContext<CommandSourceStack> context, boolean anything) {
        FakeServerPlayer fake = getFake(context);
        if (fake == null) {
            return 0;
        }
        if (FakePlayerPossession.isPossessed(fake)) {
            context.getSource().sendFailure(Component.translatable("gui.archweaver.fakeplayer.possess_locked"));
            return 0;
        }
        if (fake.actions().mountNearest(anything)) {
            return success(context);
        }
        context.getSource().sendFailure(Component.translatable("commands.archweaver.fakeplayer.no_mount"));
        return 0;
    }

    private static int withFake(
        CommandContext<CommandSourceStack> context,
        java.util.function.Consumer<FakeServerPlayer> action
    ) {
        FakeServerPlayer fake = getFake(context);
        if (fake == null) {
            return 0;
        }
        if (FakePlayerPossession.isPossessed(fake)) {
            context.getSource().sendFailure(Component.translatable("gui.archweaver.fakeplayer.possess_locked"));
            return 0;
        }
        action.accept(fake);
        FakePlayerPersistence.track(fake);
        return success(context);
    }

    private static FakeServerPlayer getFake(CommandContext<CommandSourceStack> context) {
        String name = name(context);
        FakeServerPlayer fake = FakePlayerManager.find(context.getSource().getServer(), name);
        if (fake == null) {
            context.getSource().sendFailure(Component.translatable("commands.archweaver.fakeplayer.not_found", name));
        }
        return fake;
    }

    private static int success(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(
            () -> Component.translatable("commands.archweaver.fakeplayer.action_done", name(context)), false);
        return 1;
    }

    private static int resetSettings(CommandContext<CommandSourceStack> context) {
        FakeServerPlayer fake = getFake(context);
        if (fake == null) {
            return 0;
        }
        if (FakePlayerPossession.isPossessed(fake)) {
            context.getSource().sendFailure(Component.translatable("gui.archweaver.fakeplayer.possess_locked"));
            return 0;
        }
        fake.actions().restore(FakePlayerActions.State.EMPTY);
        FakePlayerPersistence.track(fake);
        context.getSource().sendSuccess(
            () -> Component.translatable("commands.archweaver.fakeplayer.settings_reset", name(context)), false);
        return 1;
    }

    private static int spawn(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Vec3 position = argumentOrDefault(() -> Vec3Argument.getVec3(context, "position"), source.getPosition());
        Vec2 rotation = argumentOrDefault(
            () -> RotationArgument.getRotation(context, "direction").getRotation(source), source.getRotation());
        ServerLevel level = argumentOrDefault(
            () -> DimensionArgument.getDimension(context, "dimension"), source.getLevel());
        ServerPlayer sender = source.getPlayer();
        GameType defaultGameType = sender == null ? GameType.CREATIVE : sender.gameMode.getGameModeForPlayer();
        GameType gameType = argumentOrDefault(
            () -> GameModeArgument.getGameMode(context, "gamemode"), defaultGameType);
        boolean flying = sender != null && sender.getAbilities().flying;
        if (gameType == GameType.SPECTATOR) {
            flying = true;
        } else if (gameType.isSurvival()) {
            flying = false;
        }

        return requestSpawn(source, name, level, position, rotation, gameType, flying);
    }

    /** 使用菜单查看者当前的位置、朝向与游戏模式生成假人。 */
    public static void spawnFromMenu(ServerPlayer player, String name) {
        spawnFromMenu(player, name, false);
    }

    public static void spawnFromMenu(ServerPlayer player, String name, boolean mannequin) {
        if (mannequin) {
            try {
                MannequinManager.spawn(player.level().getServer(), (ServerLevel) player.level(), name, player.position(), player.getYRot());
                player.sendSystemMessage(Component.literal("已生成玩偶 " + name));
                refreshList(player.createCommandSourceStack());
            } catch (RuntimeException exception) {
                player.sendSystemMessage(Component.literal("玩偶生成失败"));
            }
            return;
        }
        CommandSourceStack source = player.createCommandSourceStack();
        GameType gameType = player.gameMode.getGameModeForPlayer();
        boolean flying = player.getAbilities().flying;
        if (gameType == GameType.SPECTATOR) {
            flying = true;
        } else if (gameType.isSurvival()) {
            flying = false;
        }
        requestSpawn(source, name, source.getLevel(), source.getPosition(), source.getRotation(), gameType, flying);
    }

    private static int requestSpawn(
        CommandSourceStack source,
        String name,
        ServerLevel level,
        Vec3 position,
        Vec2 rotation,
        GameType gameType,
        boolean flying
    ) {
        // 旁观模式的假人无法执行常规交互，统一以创造模式生成。
        GameType effectiveGameType = gameType == GameType.SPECTATOR ? GameType.CREATIVE : gameType;
        if (!name.matches("[A-Za-z0-9_-]{1,16}")) {
            source.sendFailure(Component.translatable("commands.archweaver.fakeplayer.invalid_name"));
            return 0;
        }
        if (!validateSpawnPosition(source, level, position)) {
            return 0;
        }
        MinecraftServer server = source.getServer();
        if (server.getPlayerList().getPlayerByName(name) != null) {
            source.sendFailure(Component.translatable("commands.archweaver.fakeplayer.duplicate", name));
            return 0;
        }

        boolean requestedFlying = flying;
        source.sendSuccess(() -> Component.translatable("commands.archweaver.fakeplayer.resolving_profile", name), false);
        ProfileResolver.resolve(server, name).whenCompleteAsync((result, throwable) -> {
            if (throwable != null) {
                ArchWeaverMod.LOGGER.error("解析假玩家 {} 的档案时发生异常", name, throwable);
                source.sendFailure(Component.translatable("commands.archweaver.fakeplayer.profile_service_unavailable", name));
                return;
            }
            if (!result.successful()) {
                sendProfileFailure(source, name, result.status());
                return;
            }
            spawnResolved(source, level, result.profile(), position, rotation, effectiveGameType, requestedFlying);
        }, server);
        return 1;
    }

    private static boolean validateSpawnPosition(CommandSourceStack source, ServerLevel level, Vec3 position) {
        if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)
            || !Level.isInSpawnableBounds(BlockPos.containing(position))) {
            source.sendFailure(Component.translatable("commands.archweaver.fakeplayer.position_outside_world"));
            return false;
        }

        AABB bounds = EntityType.PLAYER.getDimensions().makeBoundingBox(position);
        if (!level.getWorldBorder().isWithinBounds(bounds)) {
            source.sendFailure(Component.translatable("commands.archweaver.fakeplayer.position_outside_border"));
            return false;
        }
        return true;
    }

    private static void spawnResolved(
        CommandSourceStack source,
        ServerLevel level,
        GameProfile profile,
        Vec3 position,
        Vec2 rotation,
        GameType gameType,
        boolean flying
    ) {
        MinecraftServer server = source.getServer();
        NameAndId identity = new NameAndId(profile);
        if (server.getPlayerList().getPlayers().stream().anyMatch(player ->
            player.getUUID().equals(profile.id())
                || player.getGameProfile().name().equalsIgnoreCase(profile.name()))) {
            source.sendFailure(Component.translatable("commands.archweaver.fakeplayer.duplicate", profile.name()));
            return;
        }
        if (server.getPlayerList().getBans().isBanned(identity)) {
            source.sendFailure(Component.translatable("commands.archweaver.fakeplayer.profile_banned", profile.name()));
            return;
        }
        if (server.getPlayerList().isUsingWhitelist()
            && !server.getPlayerList().isWhiteListed(identity)
            && !server.getPlayerList().isOp(identity)) {
            source.sendFailure(Component.translatable("commands.archweaver.fakeplayer.profile_not_whitelisted", profile.name()));
            return;
        }
        // 档案查询期间世界边界可能变化，因此创建前再检查一次位置。
        if (!validateSpawnPosition(source, level, position)) {
            return;
        }

        try {
            FakeServerPlayer fake = FakePlayerManager.spawn(
                server, level, profile, position, rotation, gameType, flying);
            source.sendSuccess(() -> Component.translatable(
                "commands.archweaver.fakeplayer.spawned", fake.getGameProfile().name()), true);
            refreshList(source);
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.translatable("commands.archweaver.fakeplayer.duplicate", profile.name()));
        } catch (RuntimeException exception) {
            ArchWeaverMod.LOGGER.error("生成假玩家 {} 时发生异常", profile.name(), exception);
            source.sendFailure(Component.translatable("commands.archweaver.fakeplayer.spawn_failed", profile.name()));
        }
    }

    private static void sendProfileFailure(
        CommandSourceStack source,
        String name,
        ProfileResolver.Status status
    ) {
        String key = switch (status) {
            case BUSY -> "commands.archweaver.fakeplayer.profile_busy";
            case SERVICE_UNAVAILABLE -> "commands.archweaver.fakeplayer.profile_service_unavailable";
            default -> "commands.archweaver.fakeplayer.profile_not_found";
        };
        source.sendFailure(Component.translatable(key, name));
    }

    /** 命令或控制台修改列表后立即通知所有有管理权限的查看者。 */
    private static void refreshList(CommandSourceStack source) {
        com.sakurakugu.archweaver.entity.TargetListSync.refresh(source.getServer());
    }

    private static <T> T argumentOrDefault(ArgumentSupplier<T> supplier, T fallback) throws CommandSyntaxException {
        try {
            return supplier.get();
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private static int kill(CommandContext<CommandSourceStack> context) {
        ServerPlayer target = FakePlayerManager.findAny(context.getSource().getServer(), name(context));
        if (target == null) {
            return 0;
        }
        String targetName = target.getGameProfile().name();
        if (target instanceof FakeServerPlayer fake) FakePlayerManager.kill(fake);
        else ExternalFakePlayerSupport.unload(target);
        context.getSource().sendSuccess(() -> Component.translatable("commands.archweaver.fakeplayer.killed", targetName), true);
        refreshList(context.getSource());
        return 1;
    }

    private static int load(CommandContext<CommandSourceStack> context) {
        String target = name(context);
        FakeServerPlayer online = FakePlayerManager.find(context.getSource().getServer(), target);
        if (online != null) {
            context.getSource().sendFailure(Component.literal("假玩家已加载：" + target));
            return 0;
        }
        FakePlayerPersistence.LoadResult result = FakePlayerPersistence.loadRegistered(
            context.getSource().getServer(), target);
        if (!result.successful()) {
            context.getSource().sendFailure(Component.literal(result.reason()));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("已加载假玩家 " + target), true);
        refreshList(context.getSource());
        return 1;
    }

    private static int delete(CommandContext<CommandSourceStack> context) {
        String target = name(context);
        ServerPlayer loaded = FakePlayerManager.findAny(context.getSource().getServer(), target);
        if (loaded == null) {
            var result = FakePlayerPersistence.loadRegistered(context.getSource().getServer(), target);
            if (!result.successful()) {
                context.getSource().sendFailure(Component.literal(result.reason()));
                return 0;
            }
            loaded = result.player().orElseThrow();
        }
        FakePlayerManager.delete(loaded);
        context.getSource().sendSuccess(() -> Component.literal("已掉落全部物品并卸载假玩家 " + target), true);
        refreshList(context.getSource());
        return 1;
    }

    private static int shadow(CommandContext<CommandSourceStack> context) {
        String name = name(context);
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getServer().getPlayerList().getPlayerByName(name);
        if (player == null) {
            source.sendFailure(Component.translatable("commands.archweaver.fakeplayer.player_not_found", name));
            return 0;
        }
        if (player instanceof FakeServerPlayer) {
            source.sendFailure(Component.translatable("commands.archweaver.fakeplayer.cannot_shadow_fake", name));
            return 0;
        }
        ServerPlayer sender = source.getPlayer();
        if (!Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(source) && sender != player) {
            source.sendFailure(Component.translatable("commands.archweaver.fakeplayer.cannot_shadow_other"));
            return 0;
        }
        if (source.getServer().isSingleplayerOwner(player.nameAndId())) {
            source.sendFailure(Component.translatable("commands.archweaver.fakeplayer.cannot_shadow_owner"));
            return 0;
        }
        try {
            FakePlayerManager.shadow(player);
            source.sendSuccess(() -> Component.translatable("commands.archweaver.fakeplayer.shadowed", name), true);
            return 1;
        } catch (RuntimeException exception) {
            ArchWeaverMod.LOGGER.error("为真玩家 {} 创建替身时发生异常", name, exception);
            source.sendFailure(Component.translatable("commands.archweaver.fakeplayer.shadow_failed", name));
            return 0;
        }
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        String names = FakePlayerManager.all(context.getSource().getServer()).stream()
            .map(player -> player.getGameProfile().name()
                + (player.alias().isEmpty() ? "" : " [" + player.alias() + "]"))
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .reduce((left, right) -> left + ", " + right)
            .orElse(Component.translatable("commands.archweaver.fakeplayer.none").getString());
        context.getSource().sendSuccess(() -> Component.translatable("commands.archweaver.fakeplayer.list", names), false);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> guiCommand(String name) {
        return Commands.literal(name)
            .executes(FakePlayerCommand::openGui)
            .then(fakeNameArgument().executes(FakePlayerCommand::openPlayerGui));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> fakeNameArgument() {
        return Commands.argument("name", StringArgumentType.word())
            .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                FakePlayerManager.all(context.getSource().getServer()).stream()
                    .map(player -> player.getGameProfile().name()), builder));
    }

    /** 主页面由客户端按快照渲染，服务端只能发通知让客户端自己去开。 */
    private static int openGui(CommandContext<CommandSourceStack> context) {
        ServerPlayer viewer = context.getSource().getPlayer();
        if (viewer == null) {
            context.getSource().sendFailure(Component.translatable("commands.archweaver.fakeplayer.player_only"));
            return 0;
        }
        PlatformNetworking.sendToPlayer(viewer, new OpenMainPagePayload());
        return 1;
    }

    private static int openPlayerGui(CommandContext<CommandSourceStack> context) {
        ServerPlayer viewer = context.getSource().getPlayer();
        FakeServerPlayer fake = getFake(context);
        if (viewer == null || fake == null) {
            if (viewer == null) {
                context.getSource().sendFailure(Component.translatable("commands.archweaver.fakeplayer.player_only"));
            }
            return 0;
        }
        FakePlayerMenuOpener.openInventory(viewer, fake);
        return 1;
    }

    private static int openEnderChest(CommandContext<CommandSourceStack> context) {
        ServerPlayer viewer = context.getSource().getPlayer();
        FakeServerPlayer fake = getFake(context);
        if (viewer == null || fake == null) {
            if (viewer == null) {
                context.getSource().sendFailure(Component.translatable("commands.archweaver.fakeplayer.player_only"));
            }
            return 0;
        }
        FakePlayerMenuOpener.openEnderChest(viewer, fake);
        return 1;
    }

    private static int possess(CommandContext<CommandSourceStack> context) {
        ServerPlayer viewer = context.getSource().getPlayer();
        FakeServerPlayer fake = getFake(context);
        if (viewer == null || fake == null) {
            if (viewer == null) {
                context.getSource().sendFailure(Component.translatable("commands.archweaver.fakeplayer.player_only"));
            }
            return 0;
        }
        if (!FakePlayerPossession.start(viewer, fake)) {
            return 0;
        }
        context.getSource().sendSuccess(
            () -> Component.translatable("commands.archweaver.fakeplayer.possessed", fake.getGameProfile().name()), false);
        return 1;
    }

    private static int unpossess(CommandContext<CommandSourceStack> context) {
        ServerPlayer viewer = context.getSource().getPlayer();
        if (viewer == null) {
            context.getSource().sendFailure(Component.translatable("commands.archweaver.fakeplayer.player_only"));
            return 0;
        }
        if (!FakePlayerPossession.stop(viewer)) {
            context.getSource().sendFailure(Component.translatable("commands.archweaver.fakeplayer.not_possessing"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.archweaver.fakeplayer.unpossessed"), false);
        return 1;
    }

    private static int unpossessTarget(CommandContext<CommandSourceStack> context) {
        ServerPlayer viewer = context.getSource().getPlayer();
        FakeServerPlayer fake = getFake(context);
        if (viewer == null || fake == null) {
            if (viewer == null) {
                context.getSource().sendFailure(Component.translatable("commands.archweaver.fakeplayer.player_only"));
            }
            return 0;
        }
        if (!FakePlayerPossession.isControlling(viewer, fake)) {
            context.getSource().sendFailure(Component.translatable("commands.archweaver.fakeplayer.not_possessing_target",
                fake.getGameProfile().name()));
            return 0;
        }
        return unpossess(context);
    }

    private static String nextName(CommandContext<CommandSourceStack> context) {
        int index = 1;
        while (context.getSource().getServer().getPlayerList().getPlayerByName("robot-" + index) != null) {
            index++;
        }
        return "robot-" + index;
    }

    private static String name(CommandContext<CommandSourceStack> context) {
        return StringArgumentType.getString(context, "name");
    }

    private enum DropTarget {
        MAINHAND,
        OFFHAND,
        HEAD,
        CHEST,
        LEGS,
        FEET,
        ARMOR,
        ALL,
        SLOT;

        private static final DropTarget MAIN_HAND = MAINHAND;
        private static final DropTarget OFF_HAND = OFFHAND;
    }

    @FunctionalInterface
    private interface RepeatingAction {
        void run(FakeServerPlayer fake, RepeatMode mode, int interval);
    }

    @FunctionalInterface
    private interface ArgumentSupplier<T> {
        T get() throws CommandSyntaxException;
    }
}
