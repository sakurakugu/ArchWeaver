package com.sakurakugu.archweaver.client.chunkloading;

/** 外部地图的屏幕变换；所有输入与覆盖物共用这一份换算，负坐标向下取整。 */
public record ChunkMapViewport(double centerX, double centerZ, double pixelsPerBlock, int width, int height) {
    public ChunkMapViewport {
        if (!Double.isFinite(centerX) || !Double.isFinite(centerZ)
            || !Double.isFinite(pixelsPerBlock) || pixelsPerBlock <= 0 || width <= 0 || height <= 0)
            throw new IllegalArgumentException("地图视口参数非法");
    }
    public static ChunkMapViewport xaero(double centerX, double centerZ, double mapScale,
                                         int windowWidth, int windowHeight, int guiWidth, int guiHeight) {
        // Xaero 的 scale 以窗口像素为单位，原版鼠标事件和 GUI 绘制使用 GUI 像素。
        if (windowWidth <= 0 || windowHeight <= 0) throw new IllegalArgumentException("窗口尺寸非法");
        return new ChunkMapViewport(centerX, centerZ, mapScale * guiWidth / windowWidth, guiWidth, guiHeight);
    }
    public double screenX(double blockX) { return width / 2.0 + (blockX - centerX) * pixelsPerBlock; }
    public double screenY(double blockZ) { return height / 2.0 + (blockZ - centerZ) * pixelsPerBlock; }
    public double blockX(double screenX) { return centerX + (screenX - width / 2.0) / pixelsPerBlock; }
    public double blockZ(double screenY) { return centerZ + (screenY - height / 2.0) / pixelsPerBlock; }
    public int chunkX(double screenX) { return (int) Math.floor(blockX(screenX)) >> 4; }
    public int chunkZ(double screenY) { return (int) Math.floor(blockZ(screenY)) >> 4; }
}
