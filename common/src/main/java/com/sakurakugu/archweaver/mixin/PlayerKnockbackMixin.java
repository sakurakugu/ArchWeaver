package com.sakurakugu.archweaver.mixin;

import com.sakurakugu.archweaver.entity.FakeServerPlayer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 假玩家没有客户端接收速度包，受玩家攻击时必须保留服务端计算出的击退速度。 */
@Mixin(Player.class)
public abstract class PlayerKnockbackMixin {
    @WrapOperation(
        method = "causeExtraKnockback",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/world/entity/Entity;hurtMarked:Z",
            ordinal = 0
        )
    )
    private boolean archweaver$keepServerKnockback(Entity target, Operation<Boolean> original) {
        return original.call(target) && !(target instanceof FakeServerPlayer);
    }
}
