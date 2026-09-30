package com.sakurakugu.fakeplayer.mixin;

import com.sakurakugu.fakeplayer.entity.FakeServerPlayer;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 世界冻结时只让真人及其乘坐的实体继续运行，假玩家应随世界一起冻结。 */
@Mixin(TickRateManager.class)
public abstract class TickRateManagerMixin {
    @Shadow
    public abstract boolean runsNormally();

    @Inject(method = "isEntityFrozen", at = @At("RETURN"), cancellable = true)
    private void fakeplayer$freezeFakePlayers(Entity entity, CallbackInfoReturnable<Boolean> callbackInfo) {
        if (callbackInfo.getReturnValue() || runsNormally()) {
            return;
        }
        if (isRealPlayer(entity)) {
            return;
        }
        boolean carriesRealPlayer = false;
        for (Entity passenger : entity.getIndirectPassengers()) {
            if (isRealPlayer(passenger)) {
                carriesRealPlayer = true;
                break;
            }
        }
        if (!carriesRealPlayer) {
            callbackInfo.setReturnValue(true);
        }
    }

    private static boolean isRealPlayer(Entity entity) {
        // 客户端玩家不是 ServerPlayer，必须使用两端共有的 Player 类型判断。
        return entity instanceof Player && !(entity instanceof FakeServerPlayer);
    }
}
