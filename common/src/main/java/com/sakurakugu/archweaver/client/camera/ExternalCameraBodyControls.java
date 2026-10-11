package com.sakurakugu.archweaver.client.camera;

import com.sakurakugu.archweaver.client.camera.CameraPreferences.Toggle;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.function.BooleanSupplier;

/** 选择器里的外部身体开关立即生效；本次进入前的明确修改在相机创建后恢复。 */
final class ExternalCameraBodyControls {
    private final EnumSet<Toggle> editedBeforeActivation = EnumSet.noneOf(Toggle.class);

    boolean flip(Toggle toggle) {
        Boolean current = CameraExclusivity.externalBodyToggle(toggle);
        if (current == null) return false;
        boolean beforeActivation = !CameraExclusivity.externalEnabled();
        boolean value = !current;
        if (!CameraExclusivity.setExternalBodyToggle(toggle, value)) return false;
        if (beforeActivation) editedBeforeActivation.add(toggle);
        return true;
    }

    boolean selectFreeCamera(BooleanSupplier selection) {
        // 以确认前的实时配置为准，避免覆盖外部快捷键在本次修改后的新状态。
        EnumMap<Toggle, Boolean> values = new EnumMap<>(Toggle.class);
        editedBeforeActivation.forEach(toggle -> {
            Boolean value = CameraExclusivity.externalBodyToggle(toggle);
            if (value != null) values.put(toggle, value);
        });
        if (!selection.getAsBoolean()) return false;
        if (CameraExclusivity.externalEnabled()) values.forEach(CameraExclusivity::setExternalBodyToggle);
        editedBeforeActivation.clear();
        return true;
    }
}
