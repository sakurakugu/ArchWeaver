package com.sakurakugu.archweaver.mixin;

import com.sakurakugu.archweaver.entity.MannequinManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.level.Level;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 管理玩偶只接受被动位移，装饰模式跳过呼吸、药水与生物伤害。 */
@Mixin(LivingEntity.class)
public abstract class MannequinBehaviorMixin extends Entity {
    @Shadow public float xxa;
    @Shadow public float yya;
    @Shadow public float zza;
    @Shadow protected boolean jumping;

    protected MannequinBehaviorMixin(EntityType<?> type, Level level) { super(type, level); }

    private boolean archweaver$isManaged() {
        return (Object) this instanceof Mannequin && entityTags().contains(MannequinManager.MANAGED_TAG);
    }

    @Inject(method = "baseTick", at = @At("HEAD"), cancellable = true)
    private void archweaver$decorativeTick(CallbackInfo callback) {
        if (archweaver$isManaged() && isNoGravity() && ((LivingEntity) (Object) this).isAlive()) {
            super.baseTick();
            setAirSupply(getMaxAirSupply());
            clearFire();
            callback.cancel();
        }
    }

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void archweaver$clearInput(CallbackInfo callback) {
        if (archweaver$isManaged()) { xxa = yya = zza = 0; jumping = false; }
    }

    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void archweaver$disableDamage(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> callback) {
        if (archweaver$isManaged() && isNoGravity()
            && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) callback.setReturnValue(false);
    }

    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private void archweaver$disablePush(CallbackInfoReturnable<Boolean> callback) {
        if (archweaver$isManaged() && ((MannequinInvoker) (Object) this).archweaver$getImmovable()) callback.setReturnValue(false);
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void archweaver$keepPosition(Vec3 input, CallbackInfo callback) {
        if (archweaver$isManaged() && ((MannequinInvoker) (Object) this).archweaver$getImmovable()) {
            setDeltaMovement(Vec3.ZERO);
            callback.cancel();
        }
    }
}
