package com.sakurakugu.archweaver.artifice.event;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.sakurakugu.archweaver.api.ArchWeaverApi;
import com.sakurakugu.archweaver.api.chunk.ChunkLoadingApi;
import com.sakurakugu.archweaver.api.fakeplayer.FakePlayerApi;
import com.sakurakugu.archweaver.artifice.ArtificeMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** 内容版的游戏事件订阅。 */
@EventBusSubscriber(modid = ArtificeMod.MOD_ID)
public final class ArtificeEvents {
    private ArtificeEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("artifice")
            .then(Commands.literal("selftest")
                .requires(source -> Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(source))
                .executes(ArtificeEvents::selfTest)));
    }

    /**
     * 跨模组调用自检：读一遍核心模组的真实状态。
     *
     * <p>骨架期用来证明 内容版 → API → 核心 这条链真的通了。
     * 等有了真实内容就可以删掉。(目前只是用于空壳的测试用的)
     */
    private static int selfTest(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        int regions = ChunkLoadingApi.regions(server).size();
        int fakePlayers = FakePlayerApi.all(server).size();
        context.getSource().sendSuccess(() -> Component.literal(
            "ArchWeaver API v" + ArchWeaverApi.API_VERSION
                + " | 加载区域 " + regions
                + " | 在线假人 " + fakePlayers), false);
        return 1;
    }
}
