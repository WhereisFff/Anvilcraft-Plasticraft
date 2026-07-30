package dev.anvilcraft.plasticraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesiveFallingBlockBehavior;
import dev.anvilcraft.plasticraft.entity.physics.PlasticFallingBlockSupport;
import dev.anvilcraft.plasticraft.event.CatalyticPressAnvilEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** 粘附期间由树脂系统接管普通下落方块的位置和生命周期。 */
@Mixin(value = FallingBlockEntity.class, priority = 1100)
abstract class FallingBlockEntityAdhesionMixin {
    @Shadow
    public boolean dropItem;

    @Inject(method = "tick", at = @At("HEAD"))
    private void plasticraft$notifyCatalyticPress(CallbackInfo callback) {
        FallingBlockEntity entity = (FallingBlockEntity) (Object) this;
        CatalyticPressAnvilEvents.beforeFallingAnvilTick(entity);
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void plasticraft$holdAdhesiveControlledFallingBlock(CallbackInfo callback) {
        FallingBlockEntity entity = (FallingBlockEntity) (Object) this;
        if (AdhesiveFallingBlockBehavior.beforeTick(entity)) callback.cancel();
    }

    @Inject(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/item/FallingBlockEntity;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
            shift = At.Shift.AFTER
        )
    )
    private void plasticraft$detectBondedLanding(CallbackInfo callback) {
        FallingBlockEntity entity = (FallingBlockEntity) (Object) this;
        AdhesiveFallingBlockBehavior.afterMovement(entity);
    }

    @WrapOperation(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/item/FallingBlockEntity;onGround()Z"
        )
    )
    private boolean plasticraft$acceptPlasticEntityLanding(
        FallingBlockEntity entity,
        Operation<Boolean> original
    ) {
        return switch (PlasticFallingBlockSupport.resolveLanding(entity)) {
            case LAND -> true;
            case BOUNCE -> false;
            case BREAK -> {
                Block block = entity.getBlockState().getBlock();
                BlockPos pos = entity.blockPosition();
                entity.discard();
                if (this.dropItem && entity.level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
                    entity.callOnBrokenAfterFall(block, pos);
                    entity.spawnAtLocation(block);
                }
                yield false;
            }
            case NONE -> original.call(entity);
        };
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void plasticraft$resetAdhesiveLifetime(CallbackInfo callback) {
        AdhesiveFallingBlockBehavior.afterTick((FallingBlockEntity) (Object) this);
    }

    @Redirect(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"
        )
    )
    private boolean plasticraft$retainBondAfterLanding(
        Level level,
        BlockPos pos,
        BlockState state,
        int flags
    ) {
        FallingBlockEntity entity = (FallingBlockEntity) (Object) this;
        List<AbstractPlasticEntity> plasticSupports = state.getBlock() instanceof FallingBlock
            ? PlasticFallingBlockSupport.findSupports(level, pos, entity)
            : List.of();
        boolean placed = level.setBlock(pos, state, flags);
        if (placed && level instanceof ServerLevel serverLevel) {
            AdhesiveFallingBlockBehavior.afterLanding(serverLevel, entity, pos);
            if (state.getBlock() instanceof FallingBlock fallingBlock && !plasticSupports.isEmpty()) {
                for (AbstractPlasticEntity support : plasticSupports) {
                    support.plasticraft$recordSupportedFallingBlock(pos);
                }
                serverLevel.scheduleTick(pos, fallingBlock, 2);
            }
        }
        return placed;
    }

    @Redirect(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = """
                Lnet/minecraft/world/level/block/FallingBlock;isFree(\
                Lnet/minecraft/world/level/block/state/BlockState;)Z"""
        )
    )
    private boolean plasticraft$acceptAlignedPlasticEntitySupport(BlockState state) {
        boolean free = FallingBlock.isFree(state);
        if (!free) return false;
        FallingBlockEntity entity = (FallingBlockEntity) (Object) this;
        return !PlasticFallingBlockSupport.hasSupport(entity.level(), entity.blockPosition(), entity);
    }
}
