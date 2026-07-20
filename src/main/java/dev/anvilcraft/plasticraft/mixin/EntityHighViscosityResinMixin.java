package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.block.HighViscosityResinFluidBlock;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** 在实体移动入口阻止非玩家脱离其当前接触的高粘性树脂。 */
@Mixin(Entity.class)
abstract class EntityHighViscosityResinMixin {
    @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true)
    private Vec3 plasticraft$stopNonPlayerMovement(Vec3 movement) {
        Entity entity = (Entity) (Object) this;
        if (entity instanceof Player || movement.equals(Vec3.ZERO)) return movement;
        if (!HighViscosityResinFluidBlock.isEntityTouching(entity)) return movement;
        entity.setDeltaMovement(Vec3.ZERO);
        entity.hasImpulse = true;
        return Vec3.ZERO;
    }
}
