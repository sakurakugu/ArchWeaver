package com.sakurakugu.archweaver.menu;

import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.world.inventory.MenuType;

/** 菜单类型的加载器无关引用，由平台模块完成注册。 */
public final class ModMenus {
    public static final Handle<GlobalFakePlayerMenu> GLOBAL_FAKE_PLAYER = new Handle<>();
    public static final Handle<PresetManagementMenu> PRESET_MANAGEMENT = new Handle<>();
    public static final Handle<FakePlayerInventoryMenu> FAKE_PLAYER_INVENTORY = new Handle<>();
    public static final Handle<MannequinInventoryMenu> MANNEQUIN_INVENTORY = new Handle<>();

    private ModMenus() {
    }

    public static final class Handle<T extends net.minecraft.world.inventory.AbstractContainerMenu> implements Supplier<MenuType<T>> {
        private Supplier<MenuType<T>> source;

        /** 平台只注入取值来源，真正的取值推迟到菜单创建时，避免在注册表绑定前求值。 */
        public void install(Supplier<MenuType<T>> menu) { source = Objects.requireNonNull(menu); }

        @Override public MenuType<T> get() {
            if (source == null) throw new IllegalStateException("菜单尚未由平台注册");
            return source.get();
        }
    }
}
