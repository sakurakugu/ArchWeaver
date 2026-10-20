package com.sakurakugu.archweaver.compat.journeymap.mixin;

import com.sakurakugu.archweaver.compat.journeymap.ArchWeaverJourneyMapPlugin;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** JourneyMap API 尚无全屏信息栏扩展接口；未安装时跳过此可选目标。 */
@Pseudo
@Mixin(targets = "journeymap.client.ui.fullscreen.layer.BlockInfoLayer$BlockInfoStep", remap = false)
public abstract class JourneyMapBlockInfoMixin {
    @ModifyArg(method = "draw", at = @At(value = "INVOKE",
        target = "Ljourneymap/client/render/draw/DrawUtil;drawLabel(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/network/chat/Component;Ljourneymap/client/ui/theme/Theme$LabelSpec;DDLjourneymap/client/render/draw/DrawUtil$HAlign;Ljourneymap/client/render/draw/DrawUtil$VAlign;DD)V"),
        index = 1, require = 0)
    private Component archweaver$appendChunkLoadingInfo(Component original) {
        return ArchWeaverJourneyMapPlugin.appendStatusInfo(original);
    }
}
