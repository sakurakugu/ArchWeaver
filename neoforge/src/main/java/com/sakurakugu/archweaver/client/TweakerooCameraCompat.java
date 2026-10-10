package com.sakurakugu.archweaver.client;

import com.sakurakugu.archweaver.client.camera.CameraExclusivity;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.neoforged.fml.ModList;

/** 通过反射读取移植版 Tweakeroo，避免把它变成硬依赖。 */
public final class TweakerooCameraCompat implements CameraExclusivity.Backend {
    private static final String MOD_ID = "tweakeroo";
    private static final String FEATURE_TOGGLE =
        "fi.dy.masa.tweakeroo.config.FeatureToggle";
    private static final String FREE_CAMERA = "TWEAK_FREE_CAMERA";
    private static final String BOOLEAN_VALUE = "getBooleanValue";
    private static final String SET_BOOLEAN_VALUE = "setBooleanValue";

    private final boolean enabled;
    private final Field freeCameraToggle;
    private final Method booleanValue;
    private final Method setBooleanValue;

    private TweakerooCameraCompat(boolean enabled, Field freeCameraToggle, Method booleanValue, Method setBooleanValue) {
        this.enabled = enabled;
        this.freeCameraToggle = freeCameraToggle;
        this.booleanValue = booleanValue;
        this.setBooleanValue = setBooleanValue;
    }

    public static TweakerooCameraCompat create() {
        if (!ModList.get().isLoaded(MOD_ID)) return disabled();
        try {
            Class<?> featureToggleClass = Class.forName(FEATURE_TOGGLE, false,
                TweakerooCameraCompat.class.getClassLoader());
            Field toggle = featureToggleClass.getField(FREE_CAMERA);
            Method value = toggle.getType().getMethod(BOOLEAN_VALUE);
            Method setter;
            try { setter = toggle.getType().getMethod(SET_BOOLEAN_VALUE, boolean.class); }
            catch (NoSuchMethodException ignored) { setter = null; }
            return new TweakerooCameraCompat(true, toggle, value, setter);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return disabled();
        }
    }

    private static TweakerooCameraCompat disabled() {
        return new TweakerooCameraCompat(false, null, null, null);
    }

    @Override
    public boolean externalInstalled() {
        return ModList.get().isLoaded(MOD_ID);
    }

    @Override
    public boolean externalControllable() {
        return enabled && setBooleanValue != null;
    }

    @Override
    public boolean externalAvailable() {
        return enabled;
    }

    @Override
    public boolean externalEnabled() {
        if (!enabled) return false;
        try {
            return (boolean) booleanValue.invoke(freeCameraToggle.get(null));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            throw new IllegalStateException("Cannot read Tweakeroo free camera", ignored);
        }
    }

    @Override
    public boolean setExternalEnabled(boolean enabled) {
        if (!externalControllable()) return false;
        try {
            setBooleanValue.invoke(freeCameraToggle.get(null), enabled);
            return externalEnabled() == enabled;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }
}
