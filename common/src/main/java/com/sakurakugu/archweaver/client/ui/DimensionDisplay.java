package com.sakurakugu.archweaver.client.ui;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

/** 界面统一显示维度名称；缺少翻译的自定义维度保留标识。 */
public final class DimensionDisplay {
    private DimensionDisplay() { }

    public static Component name(String id) {
        String key = "gui.archweaver.dimension." + id.replace(':', '.');
        if (I18n.exists(key)) return Component.translatable(key);
        String modKey = "dimension." + id.replace(':', '.').replace('/', '.');
        return I18n.exists(modKey) ? Component.translatable(modKey) : Component.literal(id);
    }
}
