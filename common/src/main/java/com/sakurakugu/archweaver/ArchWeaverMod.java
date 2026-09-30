package com.sakurakugu.archweaver;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/** 通用模组常量和日志入口。加载器入口位于对应平台模块。 */
public final class ArchWeaverMod {
    public static final String MOD_ID = "archweaver";
    public static final Logger LOGGER = LogUtils.getLogger();

    private ArchWeaverMod() {
    }
}
