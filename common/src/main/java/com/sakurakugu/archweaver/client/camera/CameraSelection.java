package com.sakurakugu.archweaver.client.camera;

import java.util.EnumMap;

/** 视角分组与组内选择；不依赖游戏实例，方便验证选择器行为。 */
public final class CameraSelection {
    public enum Category {
        VANILLA(3), FREE(1), ORTHOGRAPHIC(2), SHOULDER(2), ORBIT(1), FIXED(1), FOLLOW(1);

        private final int modes;
        Category(int modes) { this.modes = modes; }
        public int modes() { return modes; }
        public Category next() { return values()[(ordinal() + 1) % values().length]; }
        public String key() { return "camera.archweaver.category." + name().toLowerCase(java.util.Locale.ROOT); }
        public String modeKey(int mode) { return "camera.archweaver.mode." + name().toLowerCase(java.util.Locale.ROOT) + "." + mode; }
    }

    private Category category = Category.VANILLA;
    private final EnumMap<Category, Integer> modes = new EnumMap<>(Category.class);
    private View previous;

    private record View(Category category, int mode) { }

    public Category category() { return category; }
    public int mode() { return mode(category); }
    public int mode(Category value) { return modes.getOrDefault(value, 0); }
    public void select(Category value, int mode) {
        int nextMode = Math.floorMod(mode, value.modes());
        // 只有实际视角变化才记录历史，重复确认或单模式 F5 不覆盖记录。
        if (category != value || mode() != nextMode) previous = new View(category, mode());
        category = value;
        modes.put(value, nextMode);
    }
    public void cycleMode() { select(category, mode() + 1); }

    /** 按类别顺序遍历全部子视角，跨类别时从首项或末项继续。 */
    public void cycleView(boolean forward) {
        int nextMode = mode() + (forward ? 1 : -1);
        if (nextMode >= 0 && nextMode < category.modes()) {
            select(category, nextMode);
            return;
        }
        Category[] categories = Category.values();
        Category nextCategory = categories[Math.floorMod(category.ordinal() + (forward ? 1 : -1), categories.length)];
        select(nextCategory, forward ? 0 : nextCategory.modes() - 1);
    }

    public void clear() { clear(0); }

    /** 用当前原版视角作为新会话起点，避免把初始化当作一次切换。 */
    public void clear(int vanillaMode) {
        category = Category.VANILLA;
        modes.clear();
        modes.put(Category.VANILLA, Math.floorMod(vanillaMode, Category.VANILLA.modes()));
        previous = null;
    }

    /** 临时选择只改副本，确认后再应用，取消时直接丢弃副本。 */
    public CameraSelection copy() {
        CameraSelection copy = new CameraSelection();
        copy.category = category;
        copy.modes.putAll(modes);
        copy.previous = previous;
        return copy;
    }

    /** 预选历史视角只修改副本，取消选择不会影响实际历史。 */
    public CameraSelection selectorSnapshot(boolean preferPrevious) {
        CameraSelection preview = copy();
        if (preferPrevious && previous != null) preview.select(previous.category(), previous.mode());
        return preview;
    }

    /** 只有独立观察模式需要把输入从身体转移到相机。 */
    public static boolean detachedControls(Category category, int mode) {
        return category == Category.FREE || category == Category.ORBIT || category == Category.FOLLOW
            || category == Category.ORTHOGRAPHIC && mode == 1;
    }

    public static boolean blockMovement(Category category, int mode, boolean bodyMovement, boolean screenOpen) {
        return screenOpen || detachedControls(category, mode) && !bodyMovement;
    }

    public static boolean blockInteraction(Category category, int mode, boolean bodyInteraction, boolean screenOpen) {
        return screenOpen || detachedControls(category, mode) && !bodyInteraction;
    }
}
