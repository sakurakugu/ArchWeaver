package com.sakurakugu.archweaver.client.chunkloading;

import com.sakurakugu.archweaver.network.ChunkMapSnapshotPayload;

/** 内置地图与第三方地图共享的最小前端契约。 */
public interface ChunkLoadMapFrontend {
    ChunkLoadMapController controller();
    net.minecraft.client.gui.screens.Screen screen();
    default boolean supportsDimension(String dimension) { return dimension.equals(controller().snapshot().dimension()); }
    default boolean journeyMap() { return false; }
    default void accessDenied() { setEditMode(ChunkMapEditMode.BROWSE); }
    default void dispose() { }
    ClientChunkLoadingState.MapReturnTarget returnTarget();
    boolean highlightRegion(String name, String dimension);
    void clearHighlightedRegion();
    void focusHighlightedRegion();
    void acceptSnapshot(ChunkMapSnapshotPayload snapshot);
    void focus(String dimension, double blockX, double blockZ);
    void setEditMode(ChunkMapEditMode mode);
    void close();
}
