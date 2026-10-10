package com.sakurakugu.archweaver.client.camera;

/** 设置页按可用高度分页，给分割线、页码与面板内边距预留位置。 */
record CameraSettingsLayout(int toggleCount, int capacity, int page) {
    private static final int TOP = 62;
    private static final int PITCH = 22;
    private static final int ROW_HEIGHT = 20;
    private static final int GROUP_GAP = 12;
    private static final int BOTTOM_MARGIN = 14;
    private static final int PAGER_SPACE = 26;

    static CameraSettingsLayout forHeight(int height, int requestedPage, int toggleCount) {
        int total = toggleCount + 2;
        int fullBottom = TOP + (total - 1) * PITCH + ROW_HEIGHT + GROUP_GAP;
        int capacity = fullBottom <= height - BOTTOM_MARGIN ? total
            : Math.clamp((height - BOTTOM_MARGIN - PAGER_SPACE - TOP - ROW_HEIGHT - GROUP_GAP) / PITCH + 1, 1, total);
        int pages = (total + capacity - 1) / capacity;
        return new CameraSettingsLayout(toggleCount, capacity, Math.clamp(requestedPage, 0, pages - 1));
    }

    int pages() { return (toggleCount + 2 + capacity - 1) / capacity; }
    int firstRow() { return page * capacity; }
    int endRow() { return Math.min(toggleCount + 2, firstRow() + capacity); }
    int rowY(int index) {
        return TOP + (index - firstRow()) * PITCH + (firstRow() < toggleCount && index >= toggleCount ? GROUP_GAP : 0);
    }
    int dividerY() { return firstRow() < toggleCount && endRow() > toggleCount ? rowY(toggleCount) - 8 : -1; }
    int bottom() { return rowY(endRow() - 1) + ROW_HEIGHT; }
    int pagerY() { return bottom() + 6; }
    int contentBottom() { return bottom() + (pages() > 1 ? PAGER_SPACE : 0); }
}
