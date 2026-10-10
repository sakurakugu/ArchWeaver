package com.sakurakugu.archweaver.client.camera;

import com.sakurakugu.archweaver.client.camera.CameraSelection.Category;
import org.joml.Vector3d;

/** 身体瞄准标记的显示规则与射线终点，不依赖游戏实例。 */
public final class BodyAimIndicators {
    private BodyAimIndicators() { }

    public static boolean showCrosshair(Category category, boolean interactionBlocked, boolean enabled) {
        return category != Category.VANILLA && !interactionBlocked && enabled;
    }

    public static boolean showRay(Category category, boolean interactionBlocked, boolean enabled, boolean whenBlocked) {
        return category != Category.VANILLA && category != Category.SHOULDER
            && enabled && (!interactionBlocked || whenBlocked);
    }

    public static Vector3d rayEnd(Vector3d eye, Vector3d direction, Vector3d hit, double blockReach, double entityReach) {
        return hit != null ? new Vector3d(hit) : new Vector3d(eye).fma(Math.max(blockReach, entityReach), direction);
    }
}
