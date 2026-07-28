package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.api.blockentity.EnhancedPlasmaJetExtension;
import dev.anvilcraft.plasticraft.block.HighHeatFuelCauldronBlock;
import dev.anvilcraft.plasticraft.recipe.CondenserTowerProcess;
import dev.anvilcraft.plasticraft.recipe.EnhancedPlasmaJetFuel;
import dev.anvilcraft.plasticraft.recipe.EnhancedPlasmaJetHeat;
import dev.dubhe.anvilcraft.api.heat.HeaterInfo;
import dev.dubhe.anvilcraft.block.HeaterBlock;
import dev.dubhe.anvilcraft.block.PlasmaJetsBlock;
import dev.dubhe.anvilcraft.block.entity.PlasmaJetsBlockEntity.TubeWallLayer;
import dev.dubhe.anvilcraft.block.entity.PlasmaJetsBlockEntity;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/** 喷流抬升到大型容器下方时保留顶层喷流，并处理普通容器气化。 */
@Mixin(PlasmaJetsBlockEntity.class)
abstract class PlasmaJetsBlockEntityMixin implements EnhancedPlasmaJetExtension {
    private static final String TAG_ENHANCED = "plasticraft_enhanced";
    private static final String TAG_LAYERED_FUEL = "plasticraft_layered_fuel";

    @Shadow @Final private Set<TubeWallLayer> tubeWalls;
    @Shadow private BlockPos cauldronPos;
    @Shadow private int duration;
    @Unique private boolean plasticraft$enhanced;
    @Unique private boolean plasticraft$layeredFuel;

    @Override
    public boolean plasticraft$isEnhanced() {
        return this.plasticraft$enhanced;
    }

    @Override
    public void plasticraft$setEnhanced(boolean enhanced) {
        if (this.plasticraft$enhanced == enhanced) return;
        this.plasticraft$enhanced = enhanced;
        PlasmaJetsBlockEntity self = (PlasmaJetsBlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level != null && enhanced) EnhancedPlasmaJetHeat.promoteProducer(level, self.getBlockPos());
        this.plasticraft$syncEnhancedState();
    }

    @Override
    public boolean plasticraft$usesLayeredFuel() {
        return this.plasticraft$layeredFuel;
    }

    @Override
    public void plasticraft$setUsesLayeredFuel(boolean layeredFuel) {
        if (this.plasticraft$layeredFuel == layeredFuel) return;
        this.plasticraft$layeredFuel = layeredFuel;
        this.plasticraft$syncEnhancedState();
    }

    @Unique
    private void plasticraft$syncEnhancedState() {
        PlasmaJetsBlockEntity self = (PlasmaJetsBlockEntity) (Object) this;
        self.setChanged();
        Level level = self.getLevel();
        if (level != null && !level.isClientSide()) {
            BlockState state = self.getBlockState();
            level.sendBlockUpdated(self.getBlockPos(), state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Unique
    private BlockPos plasticraft$basePos() {
        PlasmaJetsBlockEntity self = (PlasmaJetsBlockEntity) (Object) this;
        return this.cauldronPos == null
            ? self.getBlockPos().below(this.tubeWalls.size() + 1)
            : this.cauldronPos;
    }

    @Inject(method = "serverTick", at = @At("HEAD"))
    private void plasticraft$activateEnhancedJet(ServerLevel level, CallbackInfo ci) {
        if (this.plasticraft$enhanced) return;
        EnhancedPlasmaJetFuel.FuelMode mode = EnhancedPlasmaJetFuel.activate(level, this.plasticraft$basePos());
        if (mode == EnhancedPlasmaJetFuel.FuelMode.NONE) return;
        this.plasticraft$enhanced = true;
        this.plasticraft$layeredFuel = mode == EnhancedPlasmaJetFuel.FuelMode.LAYERED;
        if (this.plasticraft$layeredFuel) this.duration = EnhancedPlasmaJetFuel.LAYERED_DURATION;
        EnhancedPlasmaJetHeat.promoteProducer(level, ((PlasmaJetsBlockEntity) (Object) this).getBlockPos());
        this.plasticraft$syncEnhancedState();
    }

    @ModifyArg(
        method = "serverTick",
        at = @At(
            value = "INVOKE",
            target = """
                Ldev/dubhe/anvilcraft/api/heat/HeaterManager;addProducer(\
                Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Level;\
                Ldev/dubhe/anvilcraft/api/heat/HeaterInfo;)V"""
        ),
        index = 2
    )
    private HeaterInfo<?> plasticraft$useEnhancedHeatSource(HeaterInfo<?> ordinary) {
        return this.plasticraft$enhanced ? EnhancedPlasmaJetHeat.replaceOrdinary(ordinary) : ordinary;
    }

    @Inject(method = "tryRaise", at = @At("HEAD"), cancellable = true)
    private void plasticraft$stopAtLargeContainer(CallbackInfoReturnable<Boolean> cir) {
        PlasmaJetsBlockEntity self = (PlasmaJetsBlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level != null && CondenserTowerProcess.isPlasmaPassThrough(
            level.getBlockState(self.getBlockPos().above()))) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "tryRaise", at = @At("RETURN"))
    private void plasticraft$copyEnhancedStateUp(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() || !this.plasticraft$enhanced) return;
        PlasmaJetsBlockEntity self = (PlasmaJetsBlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level == null) return;
        if (level.getBlockEntity(self.getBlockPos().above()) instanceof EnhancedPlasmaJetExtension extension) {
            extension.plasticraft$setEnhanced(true);
            extension.plasticraft$setUsesLayeredFuel(this.plasticraft$layeredFuel);
        }
    }

    @Inject(method = "checkTubeWallIntegrity", at = @At("HEAD"), cancellable = true)
    private void plasticraft$keepInitialJetBelowContainer(Level level, CallbackInfo ci) {
        if (!this.tubeWalls.isEmpty()) return;

        BlockPos jetPos = ((PlasmaJetsBlockEntity) (Object) this).getBlockPos();
        if (!CondenserTowerProcess.isPlasmaPassThrough(level.getBlockState(jetPos.above()))) return;
        if (this.cauldronPos == null || !PlasmaJetsBlock.isValidBaseCauldron(level, this.cauldronPos)) return;

        BlockState heater = level.getBlockState(this.cauldronPos.below());
        if (!heater.is(ModBlocks.HEATER) || heater.getOptionalValue(HeaterBlock.OVERLOAD).orElse(true)) return;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = jetPos.relative(direction);
            if (!level.getBlockState(side).isFaceSturdy(level, side, direction.getOpposite())) return;
        }
        ci.cancel();
    }

    @Inject(method = "serverTick", at = @At("TAIL"))
    private void plasticraft$vaporizeSmallContainer(ServerLevel level, CallbackInfo ci) {
        PlasmaJetsBlockEntity self = (PlasmaJetsBlockEntity) (Object) this;
        if (!self.isRemoved() && level.getBlockState(self.getBlockPos()).is(ModBlocks.PLASMA_JETS)) {
            CondenserTowerProcess.tickSmallContainerAboveJet(level, self.getBlockPos());
        }
    }

    @ModifyArg(
        method = "hurtEntities",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
        ),
        index = 1
    )
    private float plasticraft$doubleEnhancedJetDamage(float original) {
        return this.plasticraft$enhanced ? original * 2.0F : original;
    }

    @Inject(method = "refreshDuration", at = @At("HEAD"), cancellable = true)
    private void plasticraft$consumeEnhancedFuel(Level level, CallbackInfo ci) {
        if (!this.plasticraft$enhanced) return;
        ci.cancel();
        PlasmaJetsBlockEntity self = (PlasmaJetsBlockEntity) (Object) this;
        if (this.plasticraft$layeredFuel) {
            if (--this.duration < 0) {
                if (EnhancedPlasmaJetFuel.consumeLayeredFuel(level, this.plasticraft$basePos())) {
                    this.duration += EnhancedPlasmaJetFuel.LAYERED_DURATION;
                } else {
                    level.removeBlock(self.getBlockPos(), false);
                }
            }
            return;
        }
        if (!EnhancedPlasmaJetFuel.consumeContinuousFuel(level, this.plasticraft$basePos())) {
            level.removeBlock(self.getBlockPos(), false);
        }
    }

    @Inject(method = "setRemoved", at = @At("TAIL"))
    private void plasticraft$clearSpentLayeredCauldron(CallbackInfo ci) {
        PlasmaJetsBlockEntity self = (PlasmaJetsBlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level == null) return;
        EnhancedPlasmaJetHeat.removeProducer(level, self.getBlockPos());
        if (!this.plasticraft$enhanced || !this.plasticraft$layeredFuel || level.isClientSide()) return;
        boolean raising = this.tubeWalls.stream().anyMatch(
            layer -> layer.first().getFirst().south().equals(self.getBlockPos())
        );
        if (!raising) HighHeatFuelCauldronBlock.clearSpent(level, this.plasticraft$basePos());
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void plasticraft$saveEnhancedState(
        CompoundTag tag,
        HolderLookup.Provider registries,
        CallbackInfo ci
    ) {
        tag.putBoolean(TAG_ENHANCED, this.plasticraft$enhanced);
        tag.putBoolean(TAG_LAYERED_FUEL, this.plasticraft$layeredFuel);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void plasticraft$loadEnhancedState(
        CompoundTag tag,
        HolderLookup.Provider registries,
        CallbackInfo ci
    ) {
        this.plasticraft$enhanced = tag.getBoolean(TAG_ENHANCED);
        this.plasticraft$layeredFuel = tag.getBoolean(TAG_LAYERED_FUEL);
    }

    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return ((BlockEntity) (Object) this).saveWithoutMetadata(registries);
    }

    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create((BlockEntity) (Object) this);
    }

}
