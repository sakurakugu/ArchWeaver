package com.sakurakugu.fakeplayer.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.sakurakugu.fakeplayer.chunkloading.ChunkLoaderManager;
import com.sakurakugu.fakeplayer.chunkloading.FakePlayerSimulationService;
import com.sakurakugu.fakeplayer.chunkloading.FakePlayerLoadMode;
import com.sakurakugu.fakeplayer.chunkloading.ManualLoadRegion;
import com.sakurakugu.fakeplayer.config.FakePlayerConfig;
import com.sakurakugu.fakeplayer.entity.FakePlayerManager;
import com.sakurakugu.fakeplayer.entity.FakeServerPlayer;
import com.sakurakugu.fakeplayer.network.ChunkMapSnapshotPayload;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import com.sakurakugu.fakeplayer.platform.PlatformNetworking;

/** 提供区块票加载点的创建、配置和生命周期命令。 */
public final class ChunkLoaderCommand {
    private ChunkLoaderCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("chunkloader")
            .requires(FakePlayerConfig::canUseCommands)
            .executes(context -> openMap(context, false))
            .then(Commands.literal("list").executes(context -> openMap(context, true)))
            .then(Commands.literal("backup").executes(ChunkLoaderCommand::backup))
            .then(Commands.literal("restore").then(Commands.literal("confirm")
                .executes(ChunkLoaderCommand::restore)))
            .then(Commands.literal("info").then(anchorArgument().executes(ChunkLoaderCommand::info)))
            .then(Commands.literal("add")
                .then(Commands.argument("anchor", StringArgumentType.word())
                    .then(Commands.argument("radius", IntegerArgumentType.integer(0,
                            ChunkLoaderManager.ABSOLUTE_MAX_RADIUS))
                        .executes(ChunkLoaderCommand::add))))
            .then(Commands.literal("enable").then(anchorArgument()
                .executes(context -> setEnabled(context, true))))
            .then(Commands.literal("disable").then(anchorArgument()
                .executes(context -> setEnabled(context, false))))
            .then(Commands.literal("fake").then(fakeArgument()
                .then(Commands.literal("info").executes(ChunkLoaderCommand::fakeInfo))
                .then(Commands.literal("mode").then(Commands.literal("player")
                    .executes(context -> setFakeMode(context, FakePlayerLoadMode.PLAYER, 0)))
                    .then(Commands.literal("doll").then(Commands.argument("distance",
                        IntegerArgumentType.integer(0, 32))
                        .executes(context -> setFakeMode(context, FakePlayerLoadMode.DOLL,
                            IntegerArgumentType.getInteger(context, "distance"))))))))
            .then(Commands.literal("remove").then(anchorArgument().executes(ChunkLoaderCommand::remove)))
            .then(Commands.literal("configure").then(anchorArgument()
                .then(Commands.argument("radius", IntegerArgumentType.integer(0,
                        ChunkLoaderManager.ABSOLUTE_MAX_RADIUS))
                    .executes(ChunkLoaderCommand::configure)))));
    }

    private static int openMap(CommandContext<CommandSourceStack> context, boolean management)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        PlatformNetworking.sendToPlayer(player, ChunkMapSnapshotPayload.create(player,
            ChunkLoaderManager.data(context.getSource().getServer()), true, management));
        return 1;
    }

    private static int backup(CommandContext<CommandSourceStack> context) {
        if (!ChunkLoaderManager.backup(context.getSource().getServer())) {
            return failure(context, Component.translatable("commands.fakeplayer.chunkloader.backup_failed"));
        }
        context.getSource().sendSuccess(
            () -> Component.translatable("commands.fakeplayer.chunkloader.backup_created"), false);
        return 1;
    }

    private static int restore(CommandContext<CommandSourceStack> context) {
        var result = ChunkLoaderManager.restoreLatestBackup(context.getSource().getServer());
        if (!result.successful()) {
            return failure(context, Component.translatable("commands.fakeplayer.chunkloader.failed", result.reason()));
        }
        context.getSource().sendSuccess(
            () -> Component.translatable("commands.fakeplayer.chunkloader.backup_restored"), true);
        return 1;
    }

    private static int add(CommandContext<CommandSourceStack> context) {
        int radius = IntegerArgumentType.getInteger(context, "radius");
        if (radius > FakePlayerConfig.maxChunkLoadingRadius()) {
            return failure(context, Component.translatable("commands.fakeplayer.chunkloader.radius_limit",
                FakePlayerConfig.maxChunkLoadingRadius()));
        }
        String name = StringArgumentType.getString(context, "anchor");
        BlockPos position = BlockPos.containing(context.getSource().getPosition());
        var result = ChunkLoaderManager.add(context.getSource().getServer(), name,
            context.getSource().getLevel(), position, radius);
        if (!result.successful()) {
            return failure(context, Component.translatable("commands.fakeplayer.chunkloader.failed", result.reason()));
        }
        ManualLoadRegion anchor = result.region().orElseThrow();
        context.getSource().sendSuccess(() -> Component.translatable("commands.fakeplayer.chunkloader.added",
            anchor.name(), position.toShortString(), anchor.dimension(), radius), true);
        return 1;
    }

    private static int setEnabled(CommandContext<CommandSourceStack> context, boolean enabled) {
        String name = StringArgumentType.getString(context, "anchor");
        var result = ChunkLoaderManager.setEnabled(context.getSource().getServer(), name, enabled);
        if (!result.successful()) {
            return failure(context, Component.translatable("commands.fakeplayer.chunkloader.failed", result.reason()));
        }
        context.getSource().sendSuccess(() -> Component.translatable(
            enabled ? "commands.fakeplayer.chunkloader.enabled" : "commands.fakeplayer.chunkloader.disabled", name), true);
        return 1;
    }

    private static int configure(CommandContext<CommandSourceStack> context) {
        int radius = IntegerArgumentType.getInteger(context, "radius");
        if (radius > FakePlayerConfig.maxChunkLoadingRadius()) {
            return failure(context, Component.translatable("commands.fakeplayer.chunkloader.radius_limit",
                FakePlayerConfig.maxChunkLoadingRadius()));
        }
        String name = StringArgumentType.getString(context, "anchor");
        var result = ChunkLoaderManager.configure(context.getSource().getServer(), name, radius);
        if (!result.successful()) {
            return failure(context, Component.translatable("commands.fakeplayer.chunkloader.failed", result.reason()));
        }
        ManualLoadRegion anchor = result.region().orElseThrow();
        context.getSource().sendSuccess(() -> Component.translatable("commands.fakeplayer.chunkloader.configured",
            anchor.name(), radius), true);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "anchor");
        var result = ChunkLoaderManager.remove(context.getSource().getServer(), name);
        if (!result.successful()) {
            return failure(context, Component.translatable("commands.fakeplayer.chunkloader.failed", result.reason()));
        }
        context.getSource().sendSuccess(
            () -> Component.translatable("commands.fakeplayer.chunkloader.removed", name), true);
        return 1;
    }

    private static int setFakeMode(CommandContext<CommandSourceStack> context, FakePlayerLoadMode mode,
                                   int distance) {
        FakeServerPlayer fake = getFake(context);
        if (fake == null) return 0;
        var result = FakePlayerSimulationService.setPolicy(context.getSource().getServer(), fake.getUUID(), mode, distance);
        if (!result.successful()) {
            return failure(context, Component.translatable("commands.fakeplayer.chunkloader.fake_mode_failed", result.reason()));
        }
        if (mode == FakePlayerLoadMode.PLAYER) {
            context.getSource().sendSuccess(() -> Component.translatable(
                "commands.fakeplayer.chunkloader.fake_mode_set", fake.getName().getString(),
                Component.translatable("commands.fakeplayer.chunkloader.fake_mode_player")), true);
        } else {
            context.getSource().sendSuccess(() -> Component.translatable(
                "commands.fakeplayer.chunkloader.fake_mode_set_doll", fake.getName().getString(), distance), true);
        }
        return 1;
    }

    private static int fakeInfo(CommandContext<CommandSourceStack> context) {
        FakeServerPlayer fake = getFake(context);
        if (fake == null) return 0;
        var policy = ChunkLoaderManager.data(context.getSource().getServer()).policy(fake.getUUID()).orElse(null);
        FakePlayerLoadMode mode = policy == null ? FakePlayerLoadMode.PLAYER : policy.mode();
        int distance = policy == null ? 0
            : policy.usesCustomSimulation() ? FakePlayerSimulationService.dollSimulationDistance(fake)
            : policy.simulationDistance();
        Component modeLabel = Component.translatable(mode == FakePlayerLoadMode.PLAYER
            ? "commands.fakeplayer.chunkloader.fake_mode_player"
            : "commands.fakeplayer.chunkloader.fake_mode_doll");
        context.getSource().sendSuccess(() -> Component.translatable(
            "commands.fakeplayer.chunkloader.fake_mode_info", fake.getName().getString(), modeLabel, distance), false);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "anchor");
        ManualLoadRegion anchor = ChunkLoaderManager.data(context.getSource().getServer()).region(name).orElse(null);
        if (anchor == null) {
            return failure(context, Component.translatable("commands.fakeplayer.chunkloader.not_found", name));
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.fakeplayer.chunkloader.info",
            anchor.name(), anchor.enabled(), anchor.dimension(), "-", 0,
            anchor.chunks().size()), false);
        return 1;
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> anchorArgument() {
        return Commands.argument("anchor", StringArgumentType.word()).suggests((context, builder) ->
            SharedSuggestionProvider.suggest(ChunkLoaderManager.data(context.getSource().getServer()).regions().stream()
                .map(ManualLoadRegion::name), builder));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> fakeArgument() {
        return Commands.argument("fake", StringArgumentType.word()).suggests((context, builder) ->
            SharedSuggestionProvider.suggest(FakePlayerManager.all(context.getSource().getServer()).stream()
                .map(fake -> fake.getGameProfile().name()), builder));
    }

    private static FakeServerPlayer getFake(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "fake");
        FakeServerPlayer fake = FakePlayerManager.find(context.getSource().getServer(), name);
        if (fake == null) {
            context.getSource().sendFailure(Component.translatable("commands.fakeplayer.not_found", name));
        }
        return fake;
    }

    private static int failure(CommandContext<CommandSourceStack> context, Component message) {
        context.getSource().sendFailure(message);
        return 0;
    }
}
