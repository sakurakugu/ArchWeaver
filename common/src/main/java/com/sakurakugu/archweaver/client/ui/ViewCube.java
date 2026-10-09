package com.sakurakugu.archweaver.client.ui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sakurakugu.archweaver.client.camera.CameraMath;
import com.sakurakugu.archweaver.client.camera.ViewCubeMath;
import java.util.List;
import java.util.function.DoubleSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

/** 正交视角导航立方体：点击面或上方角点选择预设，拖动连续旋转。 */
public final class ViewCube extends Button {
    @FunctionalInterface
    public interface RotationConsumer { void accept(double yaw, double pitch); }

    private final DoubleSupplier yaw;
    private final DoubleSupplier pitch;
    private final RotationConsumer change;
    private boolean pressed, dragging;
    private double startX, startY, startYaw, startPitch;
    private CameraMath.Angle pressedAngle;

    public ViewCube(int x, int y, int size, DoubleSupplier yaw, DoubleSupplier pitch, RotationConsumer change) {
        super(x, y, size, size, Component.translatable("camera.archweaver.cube.hint"), b -> {}, DEFAULT_NARRATION);
        this.yaw = yaw;
        this.pitch = pitch;
        this.change = change;
        setTooltip(Tooltip.create(getMessage()));
    }

    public boolean pressed() { return pressed; }
    private ViewCubeMath.Geometry geometry() { return ViewCubeMath.project(yaw.getAsDouble(), pitch.getAsDouble(), (width - 12) / 3.5); }
    private double centerX() { return getX() + width / 2.0; }
    private double centerY() { return getY() + height / 2.0; }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        var geometry = geometry();
        var hover = isMouseOver(mouseX, mouseY) ? geometry.hit(mouseX - centerX(), mouseY - centerY()) : null;
        setTooltip(Tooltip.create(hover == null ? getMessage() : Component.translatable(hover.key()).append("\n").append(getMessage())));
        for (var face : geometry.faces()) {
            int color = face.angle() == hover ? 0xFF73C8EC : switch (face.angle()) {
                case TOP, BOTTOM -> 0xFFE0E0E0;
                case NORTH, SOUTH -> 0xFFB0B8C0;
                default -> 0xFF8C98A4;
            };
            graphics.submitGuiElementRenderState(new CubeFace(new Matrix3x2f(graphics.pose()), graphics.peekScissorStack(),
                new ScreenRectangle(getX(), getY(), width, height), face.points(), (float) centerX(), (float) centerY(), color));
        }
        // 标签与角点在面片之后提交，避免被相邻面的绘制覆盖。
        var font = Minecraft.getInstance().font;
        for (var face : geometry.faces()) {
            // 接近侧边时面片会退化成窄条，此时不让方向文字溢出到邻面。
            double area = 0;
            for (int i = 0; i < 4; i++) {
                var a = face.points().get(i);
                var b = face.points().get((i + 1) % 4);
                area += a.x() * b.y() - b.x() * a.y();
            }
            if (Math.abs(area) < 200) continue;
            graphics.centeredText(font, Component.translatable("camera.archweaver.cube.face." + face.angle().name().toLowerCase(java.util.Locale.ROOT)),
                (int) Math.round(centerX() + face.label().x()), (int) Math.round(centerY() + face.label().y()) - 4, 0xFF202830);
        }
        for (var corner : geometry.corners()) {
            int x = (int) Math.round(centerX() + corner.point().x()), y = (int) Math.round(centerY() + corner.point().y());
            graphics.fill(x - 2, y - 2, x + 3, y + 3, corner.angle() == hover ? 0xFF55FFFF : 0xFF394957);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!active || !visible || event.button() != 0 || !isMouseOver(event.x(), event.y())) return false;
        pressed = true;
        dragging = false;
        startX = event.x(); startY = event.y();
        startYaw = yaw.getAsDouble(); startPitch = pitch.getAsDouble();
        pressedAngle = geometry().hit(startX - centerX(), startY - centerY());
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (!pressed || event.button() != 0) return false;
        double dx = event.x() - startX, dy = event.y() - startY;
        dragging |= ViewCubeMath.dragged(dx, dy);
        if (dragging) change.accept(ViewCubeMath.wrap(startYaw + dx * 2), ViewCubeMath.clampPitch(startPitch + dy * 2));
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (!pressed || event.button() != 0) return false;
        if (!dragging && !ViewCubeMath.dragged(event.x() - startX, event.y() - startY) && pressedAngle != null) {
            change.accept(pressedAngle.yaw, pressedAngle.pitch);
            playDownSound(Minecraft.getInstance().getSoundManager());
        }
        pressed = dragging = false;
        return true;
    }

    /** 面片顶点在提取时冻结，沿用 GUI 管线，不触碰世界渲染状态。 */
    private record CubeFace(Matrix3x2fc pose, ScreenRectangle scissorArea, ScreenRectangle bounds,
                            List<ViewCubeMath.Point> points, float x, float y, int color) implements GuiElementRenderState {
        @Override public RenderPipeline pipeline() { return RenderPipelines.GUI; }
        @Override public TextureSetup textureSetup() { return TextureSetup.noTexture(); }
        @Override public void buildVertices(VertexConsumer consumer) {
            for (var point : points) consumer.addVertexWith2DPose(pose, x + (float) point.x(), y + (float) point.y()).setColor(color);
        }
    }
}
