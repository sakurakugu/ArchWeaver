package com.sakurakugu.archweaver.mixin;

import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.component.ResolvableProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Mannequin.class)
public interface MannequinInvoker {
    @org.spongepowered.asm.mixin.gen.Invoker("getImmovable")
    boolean archweaver$getImmovable();

    @Invoker("setProfile")
    void archweaver$setProfile(ResolvableProfile profile);

    @Invoker("setImmovable")
    void archweaver$setImmovable(boolean immovable);

    @Invoker("getDescription")
    net.minecraft.network.chat.Component archweaver$getDescription();

    @Invoker("setDescription")
    void archweaver$setDescription(net.minecraft.network.chat.Component description);

    @Invoker("setHideDescription")
    void archweaver$setHideDescription(boolean hidden);
}
