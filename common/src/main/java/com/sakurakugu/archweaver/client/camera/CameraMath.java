package com.sakurakugu.archweaver.client.camera;

import org.joml.Matrix4f;
import org.joml.Vector3d;

/** 共享正交投影、角度预设和屏幕射线计算。 */
public final class CameraMath {
    public enum Angle {
        TOP(0, 90), BOTTOM(0, -90), NORTH(0, 0), SOUTH(180, 0), EAST(90, 0), WEST(-90, 0),
        ISO_NW(-45, 35.2643897), ISO_NE(45, 35.2643897), ISO_SE(135, 35.2643897), ISO_SW(-135, 35.2643897);

        public final double yaw, pitch;
        Angle(double yaw, double pitch) { this.yaw = yaw; this.pitch = pitch; }
        public String key() { return "camera.archweaver.angle." + name().toLowerCase(java.util.Locale.ROOT); }
    }

    public record Ray(Vector3d origin, Vector3d direction) { }
    public record Rotation(double yaw, double pitch) { }
    private CameraMath() { }

    public static Matrix4f orthographic(double height, double aspect, float far, boolean zeroToOne) {
        float halfHeight = (float) (height / 2), halfWidth = (float) (height * aspect / 2);
        return new Matrix4f().setOrtho(-halfWidth, halfWidth, -halfHeight, halfHeight, 0.05F, far, zeroToOne);
    }

    public static Vector3d forward(double yaw, double pitch) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        return new Vector3d(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
    }

    public static Vector3d shoulderPosition(Vector3d eye, double yaw, double pitch, double distance, double offset) {
        double radians = Math.toRadians(yaw);
        return new Vector3d(eye).sub(forward(yaw, pitch).mul(distance))
            .add(-Math.cos(radians) * offset, 0, -Math.sin(radians) * offset);
    }

    /** 目标在眼后或与眼重合时保持相机方向，避免贴墙时身体翻转。 */
    public static Rotation aimRotation(Vector3d eye, Vector3d target, double cameraYaw, double cameraPitch) {
        Vector3d view = new Vector3d(target).sub(eye);
        if (view.lengthSquared() < 1.0E-8 || view.dot(forward(cameraYaw, cameraPitch)) <= 0) {
            return new Rotation(cameraYaw, cameraPitch);
        }
        double horizontal = Math.hypot(view.x, view.z);
        double yaw = horizontal < 1.0E-8 ? cameraYaw : Math.toDegrees(Math.atan2(-view.x, view.z));
        return new Rotation(yaw, Math.toDegrees(Math.atan2(-view.y, horizontal)));
    }

    /** x、y 为屏幕归一化坐标，y 向上；正交射线平行，只有起点发生平移。 */
    public static Ray orthographicRay(Vector3d position, double yaw, double pitch, double height, double aspect, double x, double y) {
        Vector3d forward = forward(yaw, pitch);
        Vector3d right = new Vector3d(-Math.cos(Math.toRadians(yaw)), 0, -Math.sin(Math.toRadians(yaw)));
        Vector3d up = new Vector3d(right).cross(forward);
        return new Ray(new Vector3d(position).add(right.mul(x * height * aspect / 2)).add(up.mul(y * height / 2)), forward);
    }
}
