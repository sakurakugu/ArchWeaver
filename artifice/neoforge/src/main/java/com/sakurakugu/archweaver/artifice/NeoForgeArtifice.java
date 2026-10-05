package com.sakurakugu.archweaver.artifice;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/** 内容版 NeoForge 入口。方块物品注册以后加在这里。 */
@Mod(ArtificeMod.MOD_ID)
public final class NeoForgeArtifice {
    public NeoForgeArtifice(IEventBus modBus, ModContainer container) {
        ArtificeMod.LOGGER.info("ArchWeaver Artifice 已加载");
    }
}
