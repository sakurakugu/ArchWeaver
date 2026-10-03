package com.sakurakugu.archweaver.platform;

/** 客户端配置的类型化平台后端。 */
public interface PlatformClientConfig {
    double markerNameScale();
    void setMarkerNameScale(double value);
    boolean weakLoadingVisible();
    void setWeakLoadingVisible(boolean value);
    /** 控制中心上次停留的页面编号，从 0 开始；页面类型由客户端自行映射。 */
    int mainPageView();
    void setMainPageView(int value);
    void save();
}
