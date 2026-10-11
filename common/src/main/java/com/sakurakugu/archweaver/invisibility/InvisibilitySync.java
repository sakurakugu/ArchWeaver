package com.sakurakugu.archweaver.invisibility;

import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.world.entity.LivingEntity;

/** 等级变化只通知正在追踪目标的玩家，由平台提供发送实现。 */
public final class InvisibilitySync {
    private static Consumer<LivingEntity> sender = entity -> {};

    private InvisibilitySync() {
    }

    public static void install(Consumer<LivingEntity> value) {
        sender = Objects.requireNonNull(value);
    }

    public static void broadcast(LivingEntity entity) {
        sender.accept(entity);
    }
}
