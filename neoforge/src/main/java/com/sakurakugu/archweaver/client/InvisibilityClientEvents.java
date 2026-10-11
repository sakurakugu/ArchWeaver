package com.sakurakugu.archweaver.client;

import com.sakurakugu.archweaver.ArchWeaverMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** 跟踪客户端实体生命周期，避免跨世界或编号复用残留等级。 */
@EventBusSubscriber(modid = ArchWeaverMod.MOD_ID, value = Dist.CLIENT)
public final class InvisibilityClientEvents {
    private InvisibilityClientEvents() {
    }

    @SubscribeEvent
    public static void joined(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) ClientInvisibility.apply(event.getEntity());
    }

    @SubscribeEvent
    public static void left(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) ClientInvisibility.remove(event.getEntity());
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Pre event) {
        ClientInvisibility.updateLevel();
    }

    @SubscribeEvent
    public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientInvisibility.clear();
    }
}
