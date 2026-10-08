package com.sakurakugu.archweaver.entity;

/**
 * 玩偶界面编写的视角与朝向状态。
 *
 * <p>俯仰角、视角偏航角和身体偏航角直接复用原版的 {@code xRot}、{@code yRot}/{@code yHeadRot}
 * 和 {@code yBodyRot}，只有“头身联动”开关没有对应的原版字段，才需要单独保存。
 */
public interface MannequinLook {
    boolean archweaver$bodyFollowsHead();

    void archweaver$setBodyFollowsHead(boolean follows);
}
