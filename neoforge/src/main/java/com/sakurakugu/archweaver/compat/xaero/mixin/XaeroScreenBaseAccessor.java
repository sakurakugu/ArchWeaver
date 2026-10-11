package com.sakurakugu.archweaver.compat.xaero.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import xaero.lib.client.gui.ScreenBase;
import xaero.lib.client.gui.widget.dropdown.DropDownWidget;

/** 展开的下拉菜单独占输入，不能把选项点击当成区块绘制。 */
@Mixin(value = ScreenBase.class, remap = false)
public interface XaeroScreenBaseAccessor {
    @Accessor("openDropdown") DropDownWidget archweaver$openDropdown();
}
