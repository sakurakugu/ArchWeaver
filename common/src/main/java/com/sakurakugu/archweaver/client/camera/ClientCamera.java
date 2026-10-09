package com.sakurakugu.archweaver.client.camera;

import com.sakurakugu.archweaver.client.camera.CameraPreferences.NumberSetting;
import com.sakurakugu.archweaver.client.camera.CameraPreferences.Toggle;
import com.sakurakugu.archweaver.client.camera.CameraSelection.Category;
import java.util.UUID;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/** 只改变客户端相机，不替换玩家实体，不改变服务端坐标或区块订阅。 */
public final class ClientCamera {
    private static final CameraSelection SELECTION = new CameraSelection();
    private static ClientLevel level;
    private static LocalPlayer player;
    private static CameraType returnType = CameraType.FIRST_PERSON;
    private static Vec3 position = Vec3.ZERO, oldPosition = Vec3.ZERO;
    private static Vec3 orbitCenter;
    private static UUID targetId;
    private static float yaw, pitch, oldYaw, oldPitch;
    private static boolean preferencesDirty;

    private ClientCamera() { }
    public static Category category() { return SELECTION.category(); }
    public static int mode() { return SELECTION.mode(); }
    public static int rememberedMode(Category category) { return SELECTION.mode(category); }
    public static CameraSelection selectionSnapshot() {
        // 原版 F5 可能刚在本帧切换，打开选择器前先同步真实视角。
        if (!active()) SELECTION.select(Category.VANILLA, Minecraft.getInstance().options.getCameraType().ordinal());
        return SELECTION.selectorSnapshot(CameraPreferences.get(Toggle.SELECT_PREVIOUS));
    }
    public static boolean active() { return category() != Category.VANILLA; }
    public static boolean orthographic() { return category() == Category.ORTHOGRAPHIC; }
    public static float yaw() { return yaw; }
    public static float pitch() { return pitch; }
    public static UUID targetId() { return targetId; }
    public static boolean hasBlockCenter() { return orbitCenter != null; }

    private static boolean cameraScreen() {
        var screen = Minecraft.getInstance().screen;
        return screen instanceof CameraSelectorScreen || screen instanceof CameraPanelScreen;
    }

    public static boolean blockMovement() {
        return CameraSelection.blockMovement(category(), mode(), CameraPreferences.get(Toggle.BODY_MOVEMENT), cameraScreen());
    }

    public static boolean blockInteraction() {
        return CameraSelection.blockInteraction(category(), mode(), CameraPreferences.get(Toggle.BODY_INTERACTION), cameraScreen());
    }

    public static boolean cameraMouse() {
        return active() && Minecraft.getInstance().screen == null &&
            (orthographic() && mode() == 0 || CameraSelection.detachedControls(category(), mode()) && !CameraPreferences.get(Toggle.BODY_MOVEMENT));
    }

    /** 跟随目标尚未选定时先打开面板，实际视角保持不变。 */
    public static boolean select(Category next, int nextMode) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.player.isAlive()) return false;
        if (next == Category.FOLLOW && resolveTarget() == null) {
            HitResult hit = bodyHit();
            if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() != mc.player) targetId = entityHit.getEntity().getUUID();
            else {
                mc.setScreen(new CameraPanelScreen(true));
                return false;
            }
        }
        Category previous = category();
        if (previous == Category.VANILLA) {
            returnType = mc.options.getCameraType();
            SELECTION.select(Category.VANILLA, returnType.ordinal());
        }
        if (next != previous) {
            Camera camera = mc.gameRenderer.getMainCamera();
            position = oldPosition = camera.position();
            yaw = oldYaw = camera.yRot();
            pitch = oldPitch = camera.xRot();
            if (next == Category.ORTHOGRAPHIC) {
                yaw = oldYaw = (float) CameraPreferences.get(NumberSetting.YAW);
                pitch = oldPitch = (float) CameraPreferences.get(NumberSetting.PITCH);
            }
            if (next == Category.ORBIT) orbitCenter = null;
        } else if (next == Category.ORTHOGRAPHIC && nextMode != mode()) {
            position = oldPosition = mc.gameRenderer.getMainCamera().position();
        }
        SELECTION.select(next, nextMode);
        mc.options.setCameraType(next == Category.VANILLA ? CameraType.values()[SELECTION.mode()] : CameraType.THIRD_PERSON_BACK);
        mc.gameRenderer.checkEntityPostEffect(next == Category.VANILLA && SELECTION.mode() == 0 ? mc.getCameraEntity() : null);
        if (blockInteraction() && mc.gameMode != null) {
            mc.gameMode.stopDestroyBlock();
            if (mc.player.isUsingItem()) mc.gameMode.releaseUsingItem(mc.player);
        }
        mc.levelRenderer.needsUpdate();
        return true;
    }

    public static void cycleMode() {
        select(category(), (mode() + 1) % category().modes());
    }

    public static void angle(CameraMath.Angle angle) { rotation(angle.yaw, angle.pitch); }
    public static void rotation(double y, double p) {
        yaw = oldYaw = wrap((float) y);
        pitch = oldPitch = (float) Math.clamp(p, -90, 90);
        // 面板也可在原版视角预先配置下次进入正交视角的角度。
        saveRotation();
    }

    public static void turn(double x, double y) {
        oldYaw = yaw = wrap(yaw + (float) (x * 0.15));
        oldPitch = pitch = Math.clamp(pitch + (float) (y * 0.15), -90, 90);
        rememberRotation();
    }

    private static float wrap(float value) { return (float) (((value + 180) % 360 + 360) % 360 - 180); }
    private static void rememberRotation() {
        if (orthographic()) saveRotation();
    }
    private static void saveRotation() {
        CameraPreferences.set(NumberSetting.YAW, yaw);
        CameraPreferences.set(NumberSetting.PITCH, pitch);
        preferencesDirty = true;
    }

    /** 身体移动开启时滚轮留给快捷栏，其余情况下调整相机参数。 */
    public static boolean scroll(double amount) {
        if (!active() || Minecraft.getInstance().screen != null || category() == Category.FIXED) return false;
        if (CameraSelection.detachedControls(category(), mode()) && CameraPreferences.get(Toggle.BODY_MOVEMENT)) return false;
        NumberSetting setting = orthographic() ? NumberSetting.SCALE : switch (category()) {
            case FREE -> NumberSetting.SPEED;
            case SHOULDER -> NumberSetting.SHOULDER_DISTANCE;
            default -> NumberSetting.DISTANCE;
        };
        CameraPreferences.set(setting, CameraPreferences.get(setting) * Math.pow(1.12, setting == NumberSetting.SPEED ? amount : -amount));
        preferencesDirty = true;
        return true;
    }

    public static void tick(Minecraft mc) {
        if (level != mc.level || player != mc.player || mc.player == null || !mc.player.isAlive()) {
            reset(mc);
            level = mc.level;
            player = mc.player;
        }
        if (mc.player == null || mc.level == null) return;
        if (!active()) {
            SELECTION.select(Category.VANILLA, mc.options.getCameraType().ordinal());
            return;
        }
        if (blockInteraction() && mc.gameMode != null) {
            mc.gameMode.stopDestroyBlock();
            if (mc.player.isUsingItem()) mc.gameMode.releaseUsingItem(mc.player);
        }
        if (category() == Category.FOLLOW && resolveTarget() == null) {
            reset(mc);
            mc.player.sendOverlayMessage(Component.translatable("camera.archweaver.target_lost"));
            return;
        }
        oldPosition = position;
        oldYaw = yaw;
        oldPitch = pitch;
        if (mc.screen == null && mc.isWindowActive() && !mc.isPaused()) {
            if (category() == Category.FREE || orthographic() && mode() == 1) {
                if (!CameraPreferences.get(Toggle.BODY_MOVEMENT)) {
                    var keys = mc.options;
                    double forward = (keys.keyUp.isDown() ? 1 : 0) - (keys.keyDown.isDown() ? 1 : 0);
                    double right = (keys.keyRight.isDown() ? 1 : 0) - (keys.keyLeft.isDown() ? 1 : 0);
                    double up = (keys.keyJump.isDown() ? 1 : 0) - (keys.keyShift.isDown() ? 1 : 0);
                    double radians = Math.toRadians(yaw);
                    Vec3 motion = new Vec3(-Math.sin(radians) * forward - Math.cos(radians) * right, up,
                        Math.cos(radians) * forward - Math.sin(radians) * right);
                    if (motion.lengthSqr() > 1) motion = motion.normalize();
                    position = position.add(motion.scale(CameraPreferences.get(NumberSetting.SPEED) * (keys.keySprint.isDown() ? 3 : 1)));
                }
            }
            if (category() == Category.ORBIT && CameraPreferences.get(Toggle.AUTO_ORBIT)) {
                yaw = wrap(yaw + (float) CameraPreferences.get(NumberSetting.ORBIT_SPEED) / 20);
            }
        }
        if (preferencesDirty && mc.screen == null && mc.level.getGameTime() % 40 == 0) flushPreferences();
    }

    public static void flushPreferences() {
        if (preferencesDirty) { CameraPreferences.save(); preferencesDirty = false; }
    }

    public static void reset(Minecraft mc) {
        if (active()) {
            mc.options.setCameraType(returnType);
            mc.gameRenderer.checkEntityPostEffect(returnType.isFirstPerson() ? mc.getCameraEntity() : null);
            mc.levelRenderer.needsUpdate();
        }
        SELECTION.clear(mc.options.getCameraType().ordinal());
        targetId = null;
        orbitCenter = null;
        position = oldPosition = Vec3.ZERO;
        flushPreferences();
        if (cameraScreen()) mc.setScreen(null);
    }

    public static Entity resolveTarget() {
        Minecraft mc = Minecraft.getInstance();
        if (targetId == null || mc.level == null) return null;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity.getUUID().equals(targetId) && entity.isAlive() && !entity.isRemoved()) return entity;
        }
        return null;
    }

    public static void follow(Entity entity) {
        targetId = entity.getUUID();
        select(Category.FOLLOW, 0);
    }

    public static HitResult bodyHit() {
        var mc = Minecraft.getInstance();
        return mc.player == null ? null : mc.player.raycastHitResult(1, mc.player);
    }

    public static boolean useBlockCenter() {
        if (bodyHit() instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            orbitCenter = Vec3.atCenterOf(hit.getBlockPos());
            return true;
        }
        return false;
    }
    public static void usePlayerCenter() { orbitCenter = null; }
    public static void refix() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        position = oldPosition = mc.player.getEyePosition();
        yaw = oldYaw = mc.player.getYRot();
        pitch = oldPitch = mc.player.getXRot();
    }

    /** 原版先更新玩家相机，再在视锥构建之前覆写观察变换。 */
    public static Transform transform(float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || mc.player == null || mc.level == null) return null;
        float y = oldYaw + wrap(yaw - oldYaw) * partial;
        float p = oldPitch + (pitch - oldPitch) * partial;
        Vec3 pos = oldPosition.lerp(position, partial);
        if (orthographic() && mode() == 0) {
            pos = mc.player.getEyePosition(partial).subtract(direction(y, p).scale(CameraPreferences.get(NumberSetting.SCALE) * 1.5));
        } else if (category() == Category.SHOULDER) {
            y = mc.player.getViewYRot(partial);
            p = mc.player.getViewXRot(partial);
            double radians = Math.toRadians(y);
            double offset = CameraPreferences.get(NumberSetting.SHOULDER_OFFSET) * (mode() == 0 ? -1 : 1);
            Vec3 eye = mc.player.getEyePosition(partial);
            pos = eye.subtract(direction(y, p).scale(CameraPreferences.get(NumberSetting.SHOULDER_DISTANCE)))
                .add(-Math.cos(radians) * offset, 0, -Math.sin(radians) * offset);
            pos = avoidWall(eye, pos);
        } else if (category() == Category.ORBIT || category() == Category.FOLLOW) {
            Entity target = category() == Category.FOLLOW ? resolveTarget() : mc.player;
            if (target != null) {
                Vec3 center = category() == Category.ORBIT && orbitCenter != null ? orbitCenter : target.getEyePosition(partial);
                pos = avoidWall(center, center.subtract(direction(y, p).scale(CameraPreferences.get(NumberSetting.DISTANCE))));
            }
        }
        return new Transform(pos, y, p);
    }

    private static Vec3 avoidWall(Vec3 from, Vec3 to) {
        var mc = Minecraft.getInstance();
        Vec3 direction = to.subtract(from).normalize();
        double distance = from.distanceTo(to);
        // 八个角的碰撞检测避免偏移后的肩后相机穿进墙面。
        for (int i = 0; i < 8; i++) {
            Vec3 offset = new Vec3((i & 1) == 0 ? -0.1 : 0.1, (i & 2) == 0 ? -0.1 : 0.1, (i & 4) == 0 ? -0.1 : 0.1);
            HitResult hit = mc.level.clip(new ClipContext(from.add(offset), to.add(offset), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() != HitResult.Type.MISS) distance = Math.min(distance, Math.max(0, from.add(offset).distanceTo(hit.getLocation()) - 0.05));
        }
        return from.add(direction.scale(distance));
    }

    private static Vec3 direction(float y, float p) {
        Vector3d v = CameraMath.forward(y, p);
        return new Vec3(v.x, v.y, v.z);
    }

    public record Transform(Vec3 position, float yaw, float pitch) { }
}
