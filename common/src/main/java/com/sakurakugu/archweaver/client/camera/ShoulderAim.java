package com.sakurakugu.archweaver.client.camera;

import org.joml.Vector3d;

/** 未命中或目标落在眼后时沿用肩后瞄准补偿，避免物体边缘处身体突然回正。 */
final class ShoulderAim {
    private double yawOffset;
    private double pitchOffset;

    CameraMath.Rotation rotation(Vector3d eye, Vector3d target, double cameraYaw, double cameraPitch) {
        if (target != null) {
            Vector3d view = new Vector3d(target).sub(eye);
            // 肩后相机可能先命中眼睛后方的近处侧面，此时不能用回正角度覆盖已有补偿。
            if (view.lengthSquared() >= 1.0E-8 && view.dot(CameraMath.forward(cameraYaw, cameraPitch)) > 0) {
                CameraMath.Rotation aim = CameraMath.aimRotation(eye, target, cameraYaw, cameraPitch);
                yawOffset = wrap(aim.yaw() - cameraYaw);
                pitchOffset = aim.pitch() - cameraPitch;
                return aim;
            }
        }
        return new CameraMath.Rotation(wrap(cameraYaw + yawOffset), Math.clamp(cameraPitch + pitchOffset, -90, 90));
    }

    void reset() {
        yawOffset = 0;
        pitchOffset = 0;
    }

    private static double wrap(double value) {
        return ((value + 180) % 360 + 360) % 360 - 180;
    }
}
