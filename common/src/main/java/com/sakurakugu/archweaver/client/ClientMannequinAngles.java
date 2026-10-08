package com.sakurakugu.archweaver.client;

import com.sakurakugu.archweaver.persistence.MannequinSavedData;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/** 客户端缓存服务端同步的玩偶四肢角度。 */
public final class ClientMannequinAngles {
    private static final Map<UUID, MannequinSavedData.Angles[]> VALUES = new ConcurrentHashMap<>();
    private ClientMannequinAngles() { }
    public static void clear() { VALUES.clear(); }
    public static void set(UUID id, MannequinSavedData.Angles leftArm, MannequinSavedData.Angles rightArm,
                           MannequinSavedData.Angles leftLeg, MannequinSavedData.Angles rightLeg) {
        VALUES.put(id, new MannequinSavedData.Angles[] {leftArm, rightArm, leftLeg, rightLeg});
    }
    public static MannequinSavedData.Angles get(UUID id, int limb) {
        var values = VALUES.get(id);
        return values == null ? MannequinSavedData.Angles.ZERO : values[limb];
    }
}
