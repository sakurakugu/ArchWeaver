package com.sakurakugu.archweaver.compat.jei;

import com.sakurakugu.archweaver.ArchWeaverMod;
import com.sakurakugu.archweaver.client.FakePlayerInventoryScreen;
import com.sakurakugu.archweaver.client.MannequinInventoryScreen;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.Identifier;

/** 由 JEI 在客户端自行发现；未安装 JEI 时不加载此类。 */
@JeiPlugin
public final class ArchWeaverJeiPlugin implements IModPlugin {
    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(ArchWeaverMod.MOD_ID, "gui");
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(FakePlayerInventoryScreen.class,
            new IGuiContainerHandler<FakePlayerInventoryScreen>() {
                @Override
                public List<Rect2i> getGuiExtraAreas(FakePlayerInventoryScreen screen) {
                    return screen.getGuiExtraAreas();
                }
            });
        registration.addGuiContainerHandler(MannequinInventoryScreen.class,
            new IGuiContainerHandler<MannequinInventoryScreen>() {
                @Override
                public List<Rect2i> getGuiExtraAreas(MannequinInventoryScreen screen) {
                    return screen.getGuiExtraAreas();
                }
            });
    }
}
