package com.sakurakugu.archweaver.artifice;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/** 内容版模组常量和日志入口。加载器入口位于对应平台模块。 */
public final class ArtificeMod {
    public static final String MOD_ID = "archweaver_artifice";
    public static final Logger LOGGER = LogUtils.getLogger();

    private ArtificeMod() {
    }
}
