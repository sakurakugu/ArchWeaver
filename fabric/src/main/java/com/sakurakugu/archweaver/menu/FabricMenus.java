package com.sakurakugu.archweaver.menu;

import com.sakurakugu.archweaver.ArchWeaverMod;
import com.sakurakugu.archweaver.platform.PlatformMenus;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

/** 用字节数组承载共享菜单的额外数据，保持两平台菜单协议一致。 */
public final class FabricMenus {
    public static void register() {
        ModMenus.GLOBAL_FAKE_PLAYER.install(() -> GLOBAL);
        ModMenus.PRESET_MANAGEMENT.install(() -> PRESET);
        ModMenus.FAKE_PLAYER_INVENTORY.install(() -> INVENTORY);
        ModMenus.MANNEQUIN_INVENTORY.install(() -> MANNEQUIN);
        PlatformMenus.install((player, provider, writer) -> player.openMenu(new ExtendedMenuProvider<byte[]>() {
            public Component getDisplayName() { return provider.getDisplayName(); }
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player viewer) {
                return provider.createMenu(id, inventory, viewer);
            }
            public boolean shouldCloseCurrentScreen() { return false; }
            public byte[] getScreenOpeningData(ServerPlayer viewer) {
                var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), viewer.registryAccess());
                try {
                    writer.accept(buffer);
                    byte[] bytes = new byte[buffer.readableBytes()];
                    buffer.readBytes(bytes);
                    return bytes;
                } finally { buffer.release(); }
            }
        }));
    }
    private static <T extends AbstractContainerMenu> MenuType<T> register(String name, Factory<T> factory) {
        return Registry.register(BuiltInRegistries.MENU, Identifier.fromNamespaceAndPath(ArchWeaverMod.MOD_ID, name),
            new ExtendedMenuType<>((id, inventory, bytes) -> {
                var buffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes), inventory.player.registryAccess());
                try { return factory.create(id, inventory, buffer); }
                finally { buffer.release(); }
            }, ByteBufCodecs.BYTE_ARRAY));
    }
    private static final MenuType<GlobalFakePlayerMenu> GLOBAL = register("global", GlobalFakePlayerMenu::new);
    private static final MenuType<PresetManagementMenu> PRESET = register("preset_management", PresetManagementMenu::new);
    private static final MenuType<FakePlayerInventoryMenu> INVENTORY = register("inventory", FakePlayerInventoryMenu::new);
    private static final MenuType<MannequinInventoryMenu> MANNEQUIN = register("mannequin_inventory", MannequinInventoryMenu::new);
    private interface Factory<T> { T create(int id, Inventory inventory, RegistryFriendlyByteBuf buffer); }
}
