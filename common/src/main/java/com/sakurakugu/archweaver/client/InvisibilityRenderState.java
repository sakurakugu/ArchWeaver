package com.sakurakugu.archweaver.client;

/** 渲染层读取提取阶段保存的隐身等级。 */
public interface InvisibilityRenderState {
    int archweaver$invisibilityLevel();

    void archweaver$setInvisibilityLevel(int level);
}
