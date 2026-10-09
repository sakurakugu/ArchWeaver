package com.sakurakugu.archweaver.client.camera;

import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3d;

/** 视图立方体的投影与命中计算；坐标以控件中心为原点，屏幕纵轴向下。 */
public final class ViewCubeMath {
    public record Point(double x, double y) { }
    public record Face(CameraMath.Angle angle, List<Point> points, Point label) { }
    public record Corner(CameraMath.Angle angle, Point point) { }
    public record Geometry(List<Face> faces, List<Corner> corners) {
        public CameraMath.Angle hit(double x, double y) {
            for (Corner corner : corners) {
                if (Math.hypot(x - corner.point.x, y - corner.point.y) <= 5) return corner.angle;
            }
            for (Face face : faces) if (contains(face.points, x, y)) return face.angle;
            return null;
        }
    }

    private ViewCubeMath() { }

    public static double wrap(double yaw) { return ((yaw + 180) % 360 + 360) % 360 - 180; }
    public static double clampPitch(double pitch) { return Math.clamp(pitch, -90, 90); }
    public static boolean dragged(double x, double y) { return Math.hypot(x, y) > 3; }

    public static Geometry project(double yaw, double pitch, double scale) {
        Vector3d forward = CameraMath.forward(yaw, pitch);
        Vector3d right = new Vector3d(-Math.cos(Math.toRadians(yaw)), 0, -Math.sin(Math.toRadians(yaw)));
        Vector3d up = new Vector3d(right).cross(forward);
        List<Face> faces = new ArrayList<>();
        for (CameraMath.Angle angle : CameraMath.Angle.values()) {
            if (angle.ordinal() >= 6) break;
            Vector3d normal = CameraMath.forward(angle.yaw, angle.pitch).negate();
            if (normal.dot(forward) >= -1e-6) continue;
            Vector3d axis = Math.abs(normal.y) > 0.5 ? new Vector3d(1, 0, 0) : new Vector3d(0, 1, 0);
            Vector3d other = new Vector3d(normal).cross(axis);
            List<Point> points = new ArrayList<>();
            // 与原版 GUI 四边形保持同一顶点绕序，避免被管线背面剔除。
            for (int[] signs : new int[][] {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}}) {
                Vector3d vertex = new Vector3d(normal).add(new Vector3d(axis).mul(signs[0])).add(new Vector3d(other).mul(signs[1]));
                points.add(project(vertex, right, up, scale));
            }
            faces.add(new Face(angle, List.copyOf(points), project(normal, right, up, scale)));
        }
        List<Corner> corners = new ArrayList<>();
        for (CameraMath.Angle angle : CameraMath.Angle.values()) {
            if (angle.ordinal() < 6) continue;
            Vector3d vertex = CameraMath.forward(angle.yaw, angle.pitch).negate();
            vertex.set(Math.signum(vertex.x), 1, Math.signum(vertex.z));
            // 顶点只要有一张相邻面可见就可点击；背面的角点不能穿过立方体命中。
            if (faces.stream().anyMatch(face -> CameraMath.forward(face.angle.yaw, face.angle.pitch).negate().dot(vertex) > 0.5)) {
                corners.add(new Corner(angle, project(vertex, right, up, scale)));
            }
        }
        return new Geometry(List.copyOf(faces), List.copyOf(corners));
    }

    private static Point project(Vector3d point, Vector3d right, Vector3d up, double scale) {
        return new Point(point.dot(right) * scale, -point.dot(up) * scale);
    }

    private static boolean contains(List<Point> points, double x, double y) {
        boolean positive = false, negative = false;
        for (int i = 0; i < points.size(); i++) {
            Point a = points.get(i), b = points.get((i + 1) % points.size());
            double cross = (b.x - a.x) * (y - a.y) - (b.y - a.y) * (x - a.x);
            positive |= cross > 1e-6;
            negative |= cross < -1e-6;
        }
        return !(positive && negative);
    }
}
