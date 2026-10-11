package com.sakurakugu.archweaver.platform;

import com.sakurakugu.archweaver.client.chunkloading.MapBackend;

/** 客户端配置的类型化平台后端。 */
public interface PlatformClientConfig {
    double markerNameScale();
    void setMarkerNameScale(double value);
    boolean weakLoadingVisible();
    void setWeakLoadingVisible(boolean value);
    /** 是否在区块地图上叠加其他真人玩家，默认关闭。 */
    default boolean realPlayersVisible() { return false; }
    default void setRealPlayersVisible(boolean value) { }
    /** 控制中心上次停留的页面编号，从 0 开始；页面类型由客户端自行映射。 */
    int mainPageView();
    void setMainPageView(int value);
    default MapBackend mapBackend() { return MapBackend.BUILTIN; }
    default void setMapBackend(MapBackend value) { }
    void save();
}
