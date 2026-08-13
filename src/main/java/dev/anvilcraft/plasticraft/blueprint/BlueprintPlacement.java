package dev.anvilcraft.plasticraft.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

/**
 * 蓝图放置参数与坐标变换。快照局部坐标以零点为枢轴按原版 {@link StructureTemplate}
 * 语义镜像和旋转,再平移到锚点;方块状态使用原版 mirror/rotate,保证与最终提交一致。
 */
public record BlueprintPlacement(BlockPos anchor, Rotation rotation, Mirror mirror) {
    public static BlueprintPlacement of(ConstructionJob job) {
        return new BlueprintPlacement(job.anchor(), job.rotation(), job.mirror());
    }

    /** 快照局部方块坐标对应的世界坐标。 */
    public BlockPos worldOf(BlockPos local) {
        return StructureTemplate.transform(local, this.mirror, this.rotation, BlockPos.ZERO).offset(this.anchor);
    }

    /** 快照局部连续坐标对应的世界坐标,用于实体条目。 */
    public Vec3 worldOf(Vec3 local) {
        Vec3 transformed = StructureTemplate.transform(local, this.mirror, this.rotation, BlockPos.ZERO);
        return transformed.add(this.anchor.getX(), this.anchor.getY(), this.anchor.getZ());
    }

    /** 应用镜像与旋转后的方块状态。 */
    public BlockState stateOf(BlockState state) {
        return state.mirror(this.mirror).rotate(this.rotation);
    }

    /** 放置后整个蓝图的世界包围盒。 */
    public BoundingBox bounds(Vec3i size) {
        BlockPos first = this.worldOf(BlockPos.ZERO);
        BlockPos second = this.worldOf(new BlockPos(size.getX() - 1, size.getY() - 1, size.getZ() - 1));
        return BoundingBox.fromCorners(first, second);
    }
}
