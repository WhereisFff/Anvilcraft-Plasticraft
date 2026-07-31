package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.block.HighViscosityResinFluidBlock;
import dev.anvilcraft.plasticraft.entity.adhesive.EntityBondManager;
import dev.anvilcraft.plasticraft.entity.adhesive.EntityBondState;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesiveBondingService;
import dev.anvilcraft.plasticraft.init.ModAttachments;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在实体移动入口阻止非玩家脱离其当前接触的高粘性树脂。 */
@Mixin(Entity.class)
abstract class EntityHighViscosityResinMixin {
    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void plasticraft$ignoreBondedMemberPush(Entity other, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (EntityBondManager.areInSameComponent(entity, other)) ci.cancel();
    }

    @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true)
    private Vec3 plasticraft$stopNonPlayerMovement(Vec3 movement) {
        Entity entity = (Entity) (Object) this;
        if (entity.hasData(ModAttachments.ADHESIVE_TRANSIT)
            || entity.hasData(ModAttachments.ENTITY_ADHESION)
                && !AdhesiveBondingService.isElasticMotion(entity)) {
            entity.setDeltaMovement(Vec3.ZERO);
            entity.hasImpulse = true;
            return Vec3.ZERO;
        }
        EntityBondState bonds = entity.getExistingDataOrNull(ModAttachments.ENTITY_BONDS.get());
        if (bonds != null
            && !bonds.leaderUuid().equals(entity.getUUID())
            && !AdhesiveBondingService.isElasticMotion(entity)) {
            entity.setDeltaMovement(Vec3.ZERO);
            entity.hasImpulse = true;
            return Vec3.ZERO;
        }
        if (movement.equals(Vec3.ZERO)) return movement;
        if (HighViscosityResinFluidBlock.isUniversalPlasticMeltTouching(entity)) return movement;
        if (entity instanceof Player) return movement;
        if (!HighViscosityResinFluidBlock.isEntityTouching(entity)) return movement;
        entity.setDeltaMovement(Vec3.ZERO);
        entity.hasImpulse = true;
        return Vec3.ZERO;
    }
}
