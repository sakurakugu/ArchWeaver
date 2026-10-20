package com.sakurakugu.archweaver.mixin;

import com.sakurakugu.archweaver.client.ui.TextInputFilter;
import java.util.function.Predicate;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 写入或粘贴前检查最终文本，选中替换与原版保持一致。 */
@Mixin(EditBox.class)
public abstract class EditBoxFilterMixin implements TextInputFilter {
    @Shadow private String value;
    @Shadow private int cursorPos;
    @Shadow private int highlightPos;
    @Shadow private int maxLength;
    @Unique private Predicate<String> archweaver$filter = text -> true;
    @Override public void archweaver$filter(Predicate<String> filter) { archweaver$filter = filter; }
    @Inject(method = "setValue", at = @At("HEAD"), cancellable = true)
    private void archweaver$validateValue(String text, CallbackInfo ci) {
        if (!archweaver$filter.test(text)) ci.cancel();
    }
    @Inject(method = "insertText", at = @At("HEAD"), cancellable = true)
    private void archweaver$validateInsert(String text, CallbackInfo ci) {
        int start = Math.min(cursorPos, highlightPos), end = Math.max(cursorPos, highlightPos);
        int capacity = maxLength - value.length() + end - start;
        if (capacity <= 0) return;
        String clean = net.minecraft.util.StringUtil.filterText(text);
        int length = Math.min(clean.length(), capacity);
        // 原版截断时不会拆开 UTF-16 代理对，校验也必须使用相同的最终文本。
        if (clean.length() > capacity && Character.isHighSurrogate(clean.charAt(length - 1))) length--;
        if (!archweaver$filter.test(value.substring(0, start) + clean.substring(0, length) + value.substring(end))) ci.cancel();
    }
    @Inject(method = "deleteCharsToPos", at = @At("HEAD"), cancellable = true)
    private void archweaver$validateDelete(int position, CallbackInfo ci) {
        // 选中删除会进入 insertText，由上面的注入统一检查。
        if (value.isEmpty() || cursorPos != highlightPos) return;
        int start = Math.min(position, cursorPos), end = Math.max(position, cursorPos);
        if (start != end && !archweaver$filter.test(value.substring(0, start) + value.substring(end))) ci.cancel();
    }
}
