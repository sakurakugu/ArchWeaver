package com.sakurakugu.archweaver.compat.xaero;

import com.sakurakugu.archweaver.client.chunkloading.ChunkMapViewport;

/** 兼容层只通过这个桥接访问原生地图的私有视口和菜单状态。 */
public interface XaeroMapAccess {
    ChunkMapViewport archweaver$viewport();
    boolean archweaver$inputBlocked();
    void archweaver$center(double x, double z, double spanX, double spanZ);
}
