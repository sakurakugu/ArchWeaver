package com.sakurakugu.archweaver.client.ui;

import java.util.function.Predicate;
import net.minecraft.client.gui.components.EditBox;

/** 两个平台共用文本校验，避免依赖加载器扩展的输入框方法。 */
public interface TextInputFilter {
    void archweaver$filter(Predicate<String> filter);
    static void set(EditBox box, Predicate<String> filter) { ((TextInputFilter) box).archweaver$filter(filter); }
}
