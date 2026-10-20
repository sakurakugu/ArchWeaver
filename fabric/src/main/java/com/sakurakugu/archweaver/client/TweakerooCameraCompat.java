package com.sakurakugu.archweaver.client;

import com.sakurakugu.archweaver.client.camera.CameraExclusivity;
import com.sakurakugu.archweaver.client.camera.CameraPreferences.Toggle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumMap;
import net.fabricmc.loader.api.FabricLoader;

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
    private final EnumMap<Toggle, BooleanConfig> bodyConfigs = new EnumMap<>(Toggle.class);
    private Method saveConfigs;

    /** 每个配置独立探测，字段缺失不影响相机开关或另一个身体开关。 */
    private record BooleanConfig(Object value, Method getter, Method setter) {
        static BooleanConfig find(String fieldName) {
            try {
                Class<?> generic = Class.forName("fi.dy.masa.tweakeroo.config.Configs$Generic", false,
                    TweakerooCameraCompat.class.getClassLoader());
                Field field = generic.getField(fieldName);
                return new BooleanConfig(field.get(null), field.getType().getMethod(BOOLEAN_VALUE),
                    field.getType().getMethod(SET_BOOLEAN_VALUE, boolean.class));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return null;
            }
        }

        boolean read() throws ReflectiveOperationException {
            return (boolean) getter.invoke(value);
        }

        boolean write(boolean enabled) throws ReflectiveOperationException {
            setter.invoke(value, enabled);
            return read() == enabled;
        }
    }

    private TweakerooCameraCompat(boolean enabled, Field freeCameraToggle, Method booleanValue, Method setBooleanValue) {
        this.enabled = enabled;
        this.freeCameraToggle = freeCameraToggle;
        this.booleanValue = booleanValue;
        this.setBooleanValue = setBooleanValue;
        if (enabled) {
            bodyConfigs.put(Toggle.BODY_INTERACTION, BooleanConfig.find("FREE_CAMERA_PLAYER_INPUTS"));
            bodyConfigs.put(Toggle.BODY_MOVEMENT, BooleanConfig.find("FREE_CAMERA_PLAYER_MOVEMENT"));
            try {
                saveConfigs = Class.forName("fi.dy.masa.tweakeroo.config.Configs", false,
                    TweakerooCameraCompat.class.getClassLoader()).getMethod("saveToFile");
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                saveConfigs = null;
            }
        }
    }

    public static TweakerooCameraCompat create() {
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) return disabled();
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
        return FabricLoader.getInstance().isModLoaded(MOD_ID);
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

    @Override
    public boolean externalBodyToggleAvailable(Toggle toggle) {
        return enabled && saveConfigs != null && bodyConfigs.get(toggle) != null;
    }

    @Override
    public boolean externalBodyToggle(Toggle toggle) {
        if (!externalBodyToggleAvailable(toggle)) throw new IllegalStateException("Tweakeroo body control unavailable");
        try {
            return bodyConfigs.get(toggle).read();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            throw new IllegalStateException("Cannot read Tweakeroo body control", ignored);
        }
    }

    @Override
    public boolean setExternalBodyToggle(Toggle toggle, boolean enabled) {
        if (!externalBodyToggleAvailable(toggle)) return false;
        try {
            return bodyConfigs.get(toggle).write(enabled);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    @Override
    public boolean saveExternalBodyToggles() {
        if (saveConfigs == null) return false;
        try {
            saveConfigs.invoke(null);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }
}
