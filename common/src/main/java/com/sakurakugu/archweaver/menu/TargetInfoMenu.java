package com.sakurakugu.archweaver.menu;

import com.sakurakugu.archweaver.network.TargetInfoPayload;

/** 两种详情菜单共用的目标信息接收入口。 */
public interface TargetInfoMenu {
    TargetInfoPayload targetInfo();
    void acceptTargetInfo(TargetInfoPayload info);
}
