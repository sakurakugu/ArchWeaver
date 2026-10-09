package com.sakurakugu.archweaver.client.camera;

import org.joml.Vector3d;

/** 选择器独立的预览会话；自由模式绕观察中心旋转，不写入相机偏好或选择历史。 */
public final class OrthographicPreview {
    private Vector3d center;
    private int mode;
    private double yaw, pitch;

    public OrthographicPreview(Vector3d center, double yaw, double pitch) {
        this.center = new Vector3d(center);
        rotation(yaw, pitch);
    }

    public double yaw() { return yaw; }
    public double pitch() { return pitch; }
    public int mode() { return mode; }
    public void rotation(double yaw, double pitch) {
        this.yaw = ViewCubeMath.wrap(yaw);
        this.pitch = ViewCubeMath.clampPitch(pitch);
    }

    public void mode(int mode, Vector3d playerCenter) {
        if (this.mode == 0 && mode == 1) center = new Vector3d(playerCenter);
        this.mode = mode;
    }

    public Vector3d position(Vector3d playerCenter, double scale) {
        Vector3d pivot = mode == 0 ? new Vector3d(playerCenter) : new Vector3d(center);
        return pivot.sub(CameraMath.forward(yaw, pitch).mul(scale * 1.5));
    }

    /** 首次进入自由预览使用打开界面时捕获的中心，而不是覆盖为玩家位置。 */
    public void initialMode(int mode) { this.mode = mode; }
}
