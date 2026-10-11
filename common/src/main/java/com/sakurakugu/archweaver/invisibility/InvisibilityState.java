package com.sakurakugu.archweaver.invisibility;

/** 客户端实体保存服务端下发的隐身等级，不写入存档。 */
public interface InvisibilityState {
    int archweaver$invisibilityLevel();

    void archweaver$setInvisibilityLevel(int level);
}
