package com.sakurakugu.archweaver.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.sakurakugu.archweaver.client.ClientScreenNavigation;
import com.sakurakugu.archweaver.client.chunkloading.ChunkMapFrontends;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** 在原版切换界面的同一入口维护返回栈和地图前端。 */
@Mixin(Minecraft.class)
public abstract class ScreenNavigationMixin {
    @Shadow public Screen screen;
    @WrapMethod(method = "setScreen")
    private void archweaver$navigate(Screen next, Operation<Void> original) {
        Screen replacement = ChunkMapFrontends.guard(screen, next);
        ClientScreenNavigation.onOpening(screen, replacement);
        if (screen != null) ClientScreenNavigation.onClosing(screen);
        original.call(replacement);
    }
}
