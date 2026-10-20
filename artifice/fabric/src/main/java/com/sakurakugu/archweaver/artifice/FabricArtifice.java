package com.sakurakugu.archweaver.artifice;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/** Fabric 内容版入口。 */
public final class FabricArtifice implements ModInitializer {
    public void onInitialize() {
        FabricArtificePotions.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, access, selection) -> ArtificeCommands.register(dispatcher));
        ArtificeMod.LOGGER.info("ArchWeaver Artifice 已加载");
    }
}
