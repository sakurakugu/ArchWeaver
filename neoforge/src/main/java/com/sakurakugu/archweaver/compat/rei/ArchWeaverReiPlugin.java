package com.sakurakugu.archweaver.compat.rei;

import com.sakurakugu.archweaver.client.FakePlayerInventoryScreen;
import com.sakurakugu.archweaver.client.MannequinInventoryScreen;
import java.util.List;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.screen.ExclusionZones;
import me.shedaniel.rei.forge.REIPluginClient;
import net.minecraft.client.renderer.Rect2i;

/** 由 REI 在客户端自行发现；未安装 REI 时不加载此类。 */
@REIPluginClient
public final class ArchWeaverReiPlugin implements REIClientPlugin {
    @Override
    public void registerExclusionZones(ExclusionZones zones) {
        zones.register(FakePlayerInventoryScreen.class, screen -> toRectangles(screen.getGuiExtraAreas()));
        zones.register(MannequinInventoryScreen.class, screen -> toRectangles(screen.getGuiExtraAreas()));
    }

    /** 每次查询都使用当前可见区域，与 JEI 的侧栏展开和下拉菜单避让保持一致。 */
    private static List<Rectangle> toRectangles(List<Rect2i> areas) {
        return areas.stream()
            .map(area -> new Rectangle(area.getX(), area.getY(), area.getWidth(), area.getHeight()))
            .toList();
    }
}
