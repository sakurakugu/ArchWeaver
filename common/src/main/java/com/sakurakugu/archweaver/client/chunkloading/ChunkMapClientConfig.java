package com.sakurakugu.archweaver.client.chunkloading;

import com.sakurakugu.archweaver.platform.PlatformClientConfig;
import org.jetbrains.annotations.ApiStatus;

/** 客户端显示与界面偏好设置，具体存储由加载器模块提供。 */
public final class ChunkMapClientConfig {
    private static PlatformClientConfig backend = new Defaults();

    private ChunkMapClientConfig() {
    }

    @ApiStatus.Internal
    public static void install(PlatformClientConfig value) { backend = value; }
    public static double markerNameScale() { return backend.markerNameScale(); }
    public static void setMarkerNameScale(double value) { backend.setMarkerNameScale(value); }
    public static boolean weakLoadingVisible() { return backend.weakLoadingVisible(); }
    public static void setWeakLoadingVisible(boolean value) { backend.setWeakLoadingVisible(value); }
    public static int mainPageView() { return backend.mainPageView(); }
    public static void setMainPageView(int value) { backend.setMainPageView(value); }
    public static void save() { backend.save(); }

    private static final class Defaults implements PlatformClientConfig {
        private double scale = 1.0D;
        private boolean weak = true;
        private int mainPageView;
        public double markerNameScale() { return scale; }
        public void setMarkerNameScale(double value) { scale = Math.max(0.5D, Math.min(2.0D, value)); }
        public boolean weakLoadingVisible() { return weak; }
        public void setWeakLoadingVisible(boolean value) { weak = value; }
        public int mainPageView() { return mainPageView; }
        public void setMainPageView(int value) { mainPageView = value; }
        public void save() { }
    }
}
