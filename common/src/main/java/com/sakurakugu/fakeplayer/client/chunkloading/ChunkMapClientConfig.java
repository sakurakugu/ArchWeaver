package com.sakurakugu.fakeplayer.client.chunkloading;

import com.sakurakugu.fakeplayer.platform.PlatformClientConfig;

/** 区块地图的客户端显示设置，具体存储由加载器模块提供。 */
public final class ChunkMapClientConfig {
    private static PlatformClientConfig backend = new Defaults();

    private ChunkMapClientConfig() {
    }

    public static void install(PlatformClientConfig value) { backend = value; }
    public static double markerNameScale() { return backend.markerNameScale(); }
    public static void setMarkerNameScale(double value) { backend.setMarkerNameScale(value); }
    public static boolean weakLoadingVisible() { return backend.weakLoadingVisible(); }
    public static void setWeakLoadingVisible(boolean value) { backend.setWeakLoadingVisible(value); }
    public static void save() { backend.save(); }

    private static final class Defaults implements PlatformClientConfig {
        private double scale = 1.0D;
        private boolean weak = true;
        public double markerNameScale() { return scale; }
        public void setMarkerNameScale(double value) { scale = Math.max(0.5D, Math.min(2.0D, value)); }
        public boolean weakLoadingVisible() { return weak; }
        public void setWeakLoadingVisible(boolean value) { weak = value; }
        public void save() { }
    }
}
