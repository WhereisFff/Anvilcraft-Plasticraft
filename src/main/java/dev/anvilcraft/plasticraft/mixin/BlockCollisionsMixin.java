package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.entity.collision.BondedPlasticShapeIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Iterator;
import java.util.function.BiFunction;

/** 在常规扫描结束前补入锚点位于扫描范围外的动态制品碰撞。 */
@Mixin(BlockCollisions.class)
abstract class BlockCollisionsMixin<T> {
    @Shadow
    @Final
    private AABB box;
    @Shadow
    @Final
    private CollisionContext context;
    @Shadow
    @Final
    private VoxelShape entityShape;
    @Shadow
    @Final
    private CollisionGetter collisionGetter;
    @Shadow
    @Final
    private boolean onlySuffocatingBlocks;
    @Shadow
    @Final
    private BiFunction<BlockPos.MutableBlockPos, VoxelShape, T> resultProvider;

    @Unique
    private Iterator<BondedPlasticShapeIndex.Entry> plasticraft$extendedEntries;

    @Inject(
        method = "computeNext",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/BlockCollisions;endOfData()Ljava/lang/Object;"
        ),
        cancellable = true
    )
    private void plasticraft$appendExtendedPlasticCollision(CallbackInfoReturnable<T> cir) {
        if (this.plasticraft$extendedEntries == null) {
            this.plasticraft$extendedEntries = BondedPlasticShapeIndex
                .extendedCollisionEntries(this.collisionGetter, this.box)
                .iterator();
        }
        while (this.plasticraft$extendedEntries.hasNext()) {
            BondedPlasticShapeIndex.Entry entry = this.plasticraft$extendedEntries.next();
            BlockPos anchor = entry.anchor();
            if (!BondedPlasticShapeIndex.isCurrent(this.collisionGetter, anchor)) continue;
            BlockState state = this.collisionGetter.getBlockState(anchor);
            if (this.onlySuffocatingBlocks && !state.isSuffocating(this.collisionGetter, anchor)) continue;
            VoxelShape localShape = state.getCollisionShape(this.collisionGetter, anchor, this.context);
            VoxelShape worldShape = localShape.move(anchor.getX(), anchor.getY(), anchor.getZ());
            if (worldShape.isEmpty()
                || !Shapes.joinIsNotEmpty(worldShape, this.entityShape, BooleanOp.AND)) {
                continue;
            }
            cir.setReturnValue(this.resultProvider.apply(anchor.mutable(), worldShape));
            return;
        }
    }
}
