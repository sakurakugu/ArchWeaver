package com.sakurakugu.archweaver.artifice.event;

import com.sakurakugu.archweaver.artifice.ArtificeCommands;
import com.sakurakugu.archweaver.artifice.ArtificeMod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** 内容版的游戏事件订阅。 */
@EventBusSubscriber(modid = ArtificeMod.MOD_ID)
public final class ArtificeEvents {
    private ArtificeEvents() { }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        ArtificeCommands.register(event.getDispatcher());
    }
}
