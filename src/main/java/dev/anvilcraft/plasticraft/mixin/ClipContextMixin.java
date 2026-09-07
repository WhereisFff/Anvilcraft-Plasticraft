package dev.anvilcraft.plasticraft.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.anvilcraft.plasticraft.entity.collision.BondedPlasticShapeIndex;
import dev.anvilcraft.plasticraft.entity.collision.PlasticProjectileClipContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClipContext.class)
abstract class ClipContextMixin implements PlasticProjectileClipContext {
    @Shadow
    @Final
    private ClipContext.Block block;

    @Shadow
    @Final
    private CollisionContext collisionContext;

    @Override
    public boolean plasticraft$usesExactPlasticCollision() {
        return this.block == ClipContext.Block.COLLIDER
            && this.collisionContext instanceof EntityCollisionContext context
            && context.getEntity() instanceof Projectile;
    }

    @ModifyReturnValue(method = "getBlockShape", at = @At("RETURN"))
    private VoxelShape plasticraft$excludeCompatibilityShape(
        VoxelShape original, BlockState state, BlockGetter level, BlockPos pos
    ) {
        if (!this.plasticraft$usesExactPlasticCollision()) return original;
        BondedPlasticShapeIndex.Entry entry = BondedPlasticShapeIndex.entryAt(level, pos);
        // 精确命中在整条射线遍历后统一比较，避免粗略轮廓提前截断后方方块和流体查询。
        return entry != null && !entry.convexShapes().isEmpty() && BondedPlasticShapeIndex.isCurrent(level, pos)
            ? Shapes.empty() : original;
    }
}
