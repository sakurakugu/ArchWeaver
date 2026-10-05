package com.sakurakugu.archweaver.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.sakurakugu.archweaver.chunkloading.ChunkLoaderManager;
import com.sakurakugu.archweaver.chunkloading.FakePlayerSimulationService;
import com.sakurakugu.archweaver.chunkloading.FakePlayerLoadMode;
import com.sakurakugu.archweaver.chunkloading.ManualLoadRegion;
import com.sakurakugu.archweaver.config.ArchWeaverConfig;
import com.sakurakugu.archweaver.entity.FakePlayerManager;
import com.sakurakugu.archweaver.entity.FakeServerPlayer;
import com.sakurakugu.archweaver.network.ChunkMapOpenTarget;
import com.sakurakugu.archweaver.network.ChunkMapSnapshotPayload;
import com.sakurakugu.archweaver.network.OpenMainPagePayload;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import com.sakurakugu.archweaver.platform.PlatformNetworking;

/** 提供区块票加载点的创建、启停和生命周期命令。 */
public final class ChunkLoaderCommand {
    private ChunkLoaderCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("chunkloader")
            .requires(ArchWeaverConfig::canUseCommands)
            .executes(context -> openMap(context, ChunkMapOpenTarget.MAP))
            .then(Commands.literal("list").executes(context -> openMap(context, ChunkMapOpenTarget.MANAGEMENT)))
            .then(Commands.literal("gui")
                .executes(context -> openMainPage(context, OpenMainPagePayload.View.MAP))
                .then(Commands.literal("map").executes(context -> openMap(context, ChunkMapOpenTarget.MAP))))
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
            .then(Commands.literal("remove").then(anchorArgument().executes(ChunkLoaderCommand::remove))));
    }

    private static int openMap(CommandContext<CommandSourceStack> context, ChunkMapOpenTarget openTarget)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        PlatformNetworking.sendToPlayer(player, ChunkMapSnapshotPayload.create(player,
            ChunkLoaderManager.data(context.getSource().getServer()), openTarget));
        return 1;
    }

    /** 主页面由客户端按快照渲染，服务端只能发通知让客户端自己去开。 */
    private static int openMainPage(CommandContext<CommandSourceStack> context, OpenMainPagePayload.View view) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            return failure(context, Component.translatable("commands.archweaver.fakeplayer.player_only"));
        }
        PlatformNetworking.sendToPlayer(player, new OpenMainPagePayload(view));
        return 1;
    }

    private static int backup(CommandContext<CommandSourceStack> context) {
        if (!ChunkLoaderManager.backup(context.getSource().getServer())) {
            return failure(context, Component.translatable("commands.archweaver.chunkloader.backup_failed"));
        }
        context.getSource().sendSuccess(
            () -> Component.translatable("commands.archweaver.chunkloader.backup_created"), false);
        return 1;
    }

    private static int restore(CommandContext<CommandSourceStack> context) {
        var result = ChunkLoaderManager.restoreLatestBackup(context.getSource().getServer());
        if (!result.successful()) {
            return failure(context, Component.translatable("commands.archweaver.chunkloader.failed", result.reason()));
        }
        context.getSource().sendSuccess(
            () -> Component.translatable("commands.archweaver.chunkloader.backup_restored"), true);
        return 1;
    }

    private static int add(CommandContext<CommandSourceStack> context) {
        int radius = IntegerArgumentType.getInteger(context, "radius");
        if (radius > ArchWeaverConfig.maxChunkLoadingRadius()) {
            return failure(context, Component.translatable("commands.archweaver.chunkloader.radius_limit",
                ArchWeaverConfig.maxChunkLoadingRadius()));
        }
        String name = StringArgumentType.getString(context, "anchor");
        BlockPos position = BlockPos.containing(context.getSource().getPosition());
        Identifier dimension = context.getSource().getLevel().dimension().identifier();
        var result = ChunkLoaderManager.add(context.getSource().getServer(), name,
            context.getSource().getLevel(), position, radius);
        if (!result.successful()) {
            return failure(context, Component.translatable("commands.archweaver.chunkloader.failed", result.reason()));
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.archweaver.chunkloader.added",
            name, position.toShortString(), dimension, radius), true);
        return 1;
    }

    private static int setEnabled(CommandContext<CommandSourceStack> context, boolean enabled) {
        String name = StringArgumentType.getString(context, "anchor");
        var result = ChunkLoaderManager.setEnabled(context.getSource().getServer(), name, enabled);
        if (!result.successful()) {
            return failure(context, Component.translatable("commands.archweaver.chunkloader.failed", result.reason()));
        }
        context.getSource().sendSuccess(() -> Component.translatable(
            enabled ? "commands.archweaver.chunkloader.enabled" : "commands.archweaver.chunkloader.disabled", name), true);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "anchor");
        var result = ChunkLoaderManager.remove(context.getSource().getServer(), name);
        if (!result.successful()) {
            return failure(context, Component.translatable("commands.archweaver.chunkloader.failed", result.reason()));
        }
        context.getSource().sendSuccess(
            () -> Component.translatable("commands.archweaver.chunkloader.removed", name), true);
        return 1;
    }

    private static int setFakeMode(CommandContext<CommandSourceStack> context, FakePlayerLoadMode mode,
                                   int distance) {
        FakeServerPlayer fake = getFake(context);
        if (fake == null) return 0;
        var result = FakePlayerSimulationService.setPolicy(context.getSource().getServer(), fake.getUUID(), mode, distance);
        if (!result.successful()) {
            return failure(context, Component.translatable("commands.archweaver.chunkloader.fake_mode_failed", result.reason()));
        }
        if (mode == FakePlayerLoadMode.PLAYER) {
            context.getSource().sendSuccess(() -> Component.translatable(
                "commands.archweaver.chunkloader.fake_mode_set", fake.getName().getString(),
                Component.translatable("commands.archweaver.chunkloader.fake_mode_player")), true);
        } else {
            context.getSource().sendSuccess(() -> Component.translatable(
                "commands.archweaver.chunkloader.fake_mode_set_doll", fake.getName().getString(), distance), true);
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
            ? "commands.archweaver.chunkloader.fake_mode_player"
            : "commands.archweaver.chunkloader.fake_mode_doll");
        context.getSource().sendSuccess(() -> Component.translatable(
            "commands.archweaver.chunkloader.fake_mode_info", fake.getName().getString(), modeLabel, distance), false);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "anchor");
        ManualLoadRegion anchor = ChunkLoaderManager.data(context.getSource().getServer()).region(name).orElse(null);
        if (anchor == null) {
            return failure(context, Component.translatable("commands.archweaver.chunkloader.not_found", name));
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.archweaver.chunkloader.info",
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
            context.getSource().sendFailure(Component.translatable("commands.archweaver.fakeplayer.not_found", name));
        }
        return fake;
    }

    private static int failure(CommandContext<CommandSourceStack> context, Component message) {
        context.getSource().sendFailure(message);
        return 0;
    }
}
