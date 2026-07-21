package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.api.entity.PlasmaExperienceOrbExtension;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 让喷流灼烧生成的经验球持续向建筑限高方向飞行。 */
@Mixin(ExperienceOrb.class)
abstract class PlasmaExperienceOrbMixin implements PlasmaExperienceOrbExtension {
    @Unique
    private static final String PLASMA_KEY = "anvilcraftplasticraft:plasma_xp";

    @Unique
    private boolean plasticraft$plasmaProduced;

    @Override
    public boolean plasticraft$isPlasmaProduced() {
        return this.plasticraft$plasmaProduced;
    }

    @Override
    public void plasticraft$setPlasmaProduced(boolean produced) {
        this.plasticraft$plasmaProduced = produced;
    }

    @Inject(
        method = "canMerge(Lnet/minecraft/world/entity/ExperienceOrb;)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void plasticraft$keepPlasmaMarker(ExperienceOrb other, CallbackInfoReturnable<Boolean> cir) {
        if (other instanceof PlasmaExperienceOrbExtension extension
            && this.plasticraft$plasmaProduced != extension.plasticraft$isPlasmaProduced()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void plasticraft$riseAndExpire(CallbackInfo ci) {
        if (!this.plasticraft$plasmaProduced) return;
        ExperienceOrb orb = (ExperienceOrb) (Object) this;
        if (orb.getY() >= orb.level().getMaxBuildHeight()) {
            orb.discard();
            return;
        }
        Vec3 motion = orb.getDeltaMovement();
        orb.setDeltaMovement(motion.x * 0.96D, Math.max(motion.y, 0.055D), motion.z * 0.96D);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void plasticraft$saveMarker(CompoundTag tag, CallbackInfo ci) {
        if (this.plasticraft$plasmaProduced) tag.putBoolean(PLASMA_KEY, true);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void plasticraft$loadMarker(CompoundTag tag, CallbackInfo ci) {
        this.plasticraft$plasmaProduced = tag.getBoolean(PLASMA_KEY);
    }
}
