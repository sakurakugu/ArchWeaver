package com.sakurakugu.archweaver.client.camera;

import java.util.Objects;

/** 外部相机模组的互斥状态，由平台侧安装实现。 */
public final class CameraExclusivity {
    private static Backend backend = () -> false;
    private static boolean lastExternalEnabled;
    private static boolean readFailed;

    private CameraExclusivity() {
    }

    public interface Backend {
        boolean externalEnabled();
        default boolean externalInstalled() { return false; }
        default boolean externalAvailable() { return false; }
        default boolean externalControllable() { return false; }
        default boolean setExternalEnabled(boolean enabled) { return false; }
    }

    public static void install(Backend value) {
        backend = Objects.requireNonNull(value);
        lastExternalEnabled = false;
        readFailed = false;
    }

    public static boolean shouldYield() {
        return paused() || externalEnabled() || readFailed && externalAvailable();
    }

    /** 安装状态不受互斥开关影响，关闭互斥后仍可在设置页重新开启。 */
    public static boolean externalInstalled() {
        try { return backend.externalInstalled(); }
        catch (RuntimeException | LinkageError ignored) { return false; }
    }

    public static boolean externalAvailable() {
        if (!CameraPreferences.get(CameraPreferences.Toggle.TWEAKEROO_EXCLUSIVITY)) return false;
        try { return backend.externalAvailable(); }
        catch (RuntimeException | LinkageError ignored) { return false; }
    }

    public static boolean externalEnabled() {
        if (!externalAvailable()) return false;
        try {
            lastExternalEnabled = backend.externalEnabled();
            readFailed = false;
        } catch (RuntimeException | LinkageError ignored) {
            // 读取失败不能抢占上次已确认开启的外部相机。
            readFailed = true;
        }
        return lastExternalEnabled;
    }

    public static boolean externalControlAvailable() {
        try { return externalAvailable() && backend.externalControllable(); }
        catch (RuntimeException | LinkageError ignored) { return false; }
    }

    public static boolean setExternalEnabled(boolean enabled) {
        if (paused() || !externalControlAvailable()) return false;
        try {
            if (!backend.setExternalEnabled(enabled)) return false;
            lastExternalEnabled = backend.externalEnabled();
            readFailed = false;
            return lastExternalEnabled == enabled;
        } catch (RuntimeException | LinkageError ignored) {
            readFailed = true;
            return false;
        }
    }

    public static boolean paused() {
        return CameraPreferences.get(CameraPreferences.Toggle.PAUSE_ARCHWEAVER);
    }

    public static boolean selectionBlocked() {
        boolean enabled = externalEnabled();
        return paused() || externalAvailable() && (readFailed || enabled && !externalControlAvailable());
    }

    public static String blockedReasonKey() {
        return paused() ? "camera.archweaver.paused" : "camera.archweaver.external_unavailable";
    }

    /** 外部相机使用确定的目标状态，重复确认不会反转开关。 */
    public static boolean prepareSelection(CameraSelection.Category category, Runnable releaseCamera) {
        if (selectionBlocked()) return false;
        if (category == CameraSelection.Category.FREE && externalControlAvailable()) {
            releaseCamera.run();
            return setExternalEnabled(true);
        }
        return !externalEnabled() || setExternalEnabled(false);
    }

    /** 视角历史记录实际使用的视角，外部相机也参与上一个视角的切换。 */
    public static void synchronizeSelection(CameraSelection history, CameraSelection.Category category, int mode) {
        if (externalEnabled()) history.select(CameraSelection.Category.FREE, 0);
        else history.select(category, mode);
    }
}
