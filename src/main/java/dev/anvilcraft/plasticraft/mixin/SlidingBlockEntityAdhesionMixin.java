package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.block.BondedFallingBlocks;
import dev.anvilcraft.plasticraft.entity.adhesive.SlidingAdhesionData;
import dev.anvilcraft.plasticraft.init.PlasticraftAttachments;
import dev.dubhe.anvilcraft.entity.SlidingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.commons.lang3.tuple.Triple;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 在方块进入和离开滑动实体时交接树脂粘合数据。 */
@Mixin(SlidingBlockEntity.class)
abstract class SlidingBlockEntityAdhesionMixin {
    @Unique
    private BondedFallingBlocks.MovementSnapshot plasticraft$adhesionSnapshot =
        BondedFallingBlocks.MovementSnapshot.empty();

    @Inject(
        method = "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Ljava/lang/Iterable;)V",
        at = @At("RETURN")
    )
    private void plasticraft$captureAdhesion(
        Level level,
        BlockPos origin,
        Direction movement,
        Iterable<Triple<BlockPos, BlockState, Optional<BlockEntity>>> blocks,
        CallbackInfo callback
    ) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        List<BlockPos> positions = new ArrayList<>();
        for (Triple<BlockPos, BlockState, Optional<BlockEntity>> block : blocks) {
            positions.add(block.getLeft().immutable());
        }
        this.plasticraft$adhesionSnapshot = BondedFallingBlocks.takeForMovement(serverLevel, positions);
        if (!this.plasticraft$adhesionSnapshot.isEmpty()) {
            SlidingBlockEntity self = (SlidingBlockEntity) (Object) this;
            self.setData(
                PlasticraftAttachments.SLIDING_BLOCK_ADHESION,
                SlidingAdhesionData.from(this.plasticraft$adhesionSnapshot, origin)
            );
        }
    }

    @Inject(method = "stop", at = @At("TAIL"))
    private void plasticraft$restoreAdhesion(CallbackInfo callback) {
        SlidingBlockEntity self = (SlidingBlockEntity) (Object) this;
        if (!(self.level() instanceof ServerLevel serverLevel) || this.plasticraft$adhesionSnapshot.isEmpty()) return;
        BlockPos offset = self.blockPosition().subtract(self.getStartPos());
        BondedFallingBlocks.restoreAfterMovement(serverLevel, this.plasticraft$adhesionSnapshot, offset);
        this.plasticraft$adhesionSnapshot = BondedFallingBlocks.MovementSnapshot.empty();
    }
}
