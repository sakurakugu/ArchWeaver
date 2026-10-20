package com.sakurakugu.archweaver.compat.xaero.mixin;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** 从加载器的发现结果判断可选依赖，不提前加载 Xaero 类或读取尚未准备好的字节码。 */
public final class XaeroMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) { }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return FabricLoader.getInstance().isModLoaded("xaeroworldmap")
            && FabricLoader.getInstance().isModLoaded("xaerolib");
    }
    @Override public String getRefMapperConfig() { return null; }
    @Override public List<String> getMixins() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) { }
    @Override public void preApply(String name, ClassNode node, String mixin, IMixinInfo info) { }
    @Override public void postApply(String name, ClassNode node, String mixin, IMixinInfo info) { }
}
