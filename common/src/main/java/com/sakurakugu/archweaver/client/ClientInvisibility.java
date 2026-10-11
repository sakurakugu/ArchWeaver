package com.sakurakugu.archweaver.client;

import com.sakurakugu.archweaver.invisibility.InvisibilityState;
import com.sakurakugu.archweaver.network.InvisibilityLevelPayload;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

/** 补发包可能先于实体加入世界到达，暂存后在实体加入时应用。 */
public final class ClientInvisibility {
    private static final Map<UUID, Integer> LEVELS = new HashMap<>();
    private static ClientLevel currentLevel;

    private ClientInvisibility() {
    }

    public static void accept(InvisibilityLevelPayload payload) {
        updateLevel();
        if (currentLevel == null || !currentLevel.dimension().identifier().equals(payload.dimension())) return;
        LEVELS.put(payload.uuid(), Math.clamp(payload.level(), 0, 4));
        Entity entity = currentLevel.getEntity(payload.entityId());
        if (entity != null && entity.getUUID().equals(payload.uuid())) apply(entity);
    }

    public static void apply(Entity entity) {
        updateLevel();
        Integer level = LEVELS.get(entity.getUUID());
        if (level != null && entity instanceof InvisibilityState state) state.archweaver$setInvisibilityLevel(level);
    }

    public static void remove(Entity entity) {
        if (entity.level() == currentLevel) LEVELS.remove(entity.getUUID());
    }

    public static void updateLevel() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != currentLevel) {
            LEVELS.clear();
            currentLevel = level;
        }
    }

    public static void clear() {
        LEVELS.clear();
        currentLevel = null;
    }
}
