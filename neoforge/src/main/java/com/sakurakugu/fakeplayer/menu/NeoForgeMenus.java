package com.sakurakugu.fakeplayer.menu;

import com.sakurakugu.fakeplayer.FakePlayerMod;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

/** NeoForge 菜单类型注册。 */
public final class NeoForgeMenus {
    private static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS =
        DeferredRegister.create(Registries.MENU, FakePlayerMod.MOD_ID);
    private static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<GlobalFakePlayerMenu>> GLOBAL = MENUS.register("global",
        () -> IMenuTypeExtension.create(GlobalFakePlayerMenu::new));
    private static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<PresetManagementMenu>> PRESET = MENUS.register("preset_management",
        () -> IMenuTypeExtension.create(PresetManagementMenu::new));
    private static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<FakePlayerInventoryMenu>> INVENTORY = MENUS.register("inventory",
        () -> IMenuTypeExtension.create(FakePlayerInventoryMenu::new));

    private NeoForgeMenus() {
    }

    public static void register(IEventBus bus) {
        MENUS.register(bus);
        // 这里只注入 DeferredHolder 本身：模组构造阶段注册表尚未绑定，提前 get() 会抛 unbound value。
        ModMenus.GLOBAL_FAKE_PLAYER.install(GLOBAL);
        ModMenus.PRESET_MANAGEMENT.install(PRESET);
        ModMenus.FAKE_PLAYER_INVENTORY.install(INVENTORY);
    }
}
