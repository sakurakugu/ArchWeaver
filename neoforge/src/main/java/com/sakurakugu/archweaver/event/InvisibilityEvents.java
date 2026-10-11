package com.sakurakugu.archweaver.event;

import com.sakurakugu.archweaver.ArchWeaverMod;
import com.sakurakugu.archweaver.invisibility.InvisibilityLevels;
import com.sakurakugu.archweaver.network.InvisibilityLevelPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.EffectParticleModificationEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** 服务端补发追踪状态，并过滤高级隐身实体的所有药水粒子。 */
@EventBusSubscriber(modid = ArchWeaverMod.MOD_ID)
public final class InvisibilityEvents {
    private InvisibilityEvents() {
    }

    @SubscribeEvent
    public static void startTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof LivingEntity entity) {
            PacketDistributor.sendToPlayer(viewer, new InvisibilityLevelPayload(entity));
        }
    }

    @SubscribeEvent
    public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        syncSelf(event);
    }

    @SubscribeEvent
    public static void respawned(PlayerEvent.PlayerRespawnEvent event) {
        syncSelf(event);
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        syncSelf(event);
    }

    private static void syncSelf(PlayerEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new InvisibilityLevelPayload(player));
        }
    }

    @SubscribeEvent
    public static void effectParticles(EffectParticleModificationEvent event) {
        if (InvisibilityLevels.hidesParticles(event.getEntity())) event.setVisible(false);
    }
}
