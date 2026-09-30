package com.sakurakugu.archweaver.platform;

/** 客户端配置的类型化平台后端。 */
public interface PlatformClientConfig {
    double markerNameScale();
    void setMarkerNameScale(double value);
    boolean weakLoadingVisible();
    void setWeakLoadingVisible(boolean value);
    void save();
}
