package dev.anvilcraft.plasticraft.mixin;

import dev.dubhe.anvilcraft.block.entity.fluid.PipeCheckValveBlockEntity;
import dev.dubhe.anvilcraft.block.fluid.PipeBlock;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import dev.dubhe.anvilcraft.recipe.multiblock.BlockPattern;
import dev.dubhe.anvilcraft.recipe.multiblock.BlockPredicateWithState;
import dev.dubhe.anvilcraft.util.LevelLike;
import dev.dubhe.anvilcraft.util.RecipeUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 为本体 {@link RecipeUtil} 创建的 JEI 假世界补全冷凝塔止逆阀数据。
 * 配方谓词只能保存方块状态，无法保存阀门所在面；迁移到 26.1 时应优先改用本体提供的预览方块实体接口。
 */
@Mixin(RecipeUtil.class)
public abstract class RecipeUtilMixin {
    @Inject(method = "asLevelLike", at = @At("RETURN"))
    private static void anvilcraftPlasticraft$renderCondenserValves(
        BlockPattern pattern,
        CallbackInfoReturnable<LevelLike> cir
    ) {
        if (!isCondenserInput(pattern)) {
            return;
        }
        LevelLike level = cir.getReturnValue();
        seedValve(level, new BlockPos(0, 2, 1), Direction.WEST);
        seedValve(level, new BlockPos(2, 2, 1), Direction.EAST);
        seedValve(level, new BlockPos(1, 2, 0), Direction.NORTH);
        seedValve(level, new BlockPos(1, 2, 2), Direction.SOUTH);
    }

    private static boolean isCondenserInput(BlockPattern pattern) {
        return pattern.getSize() == 3
            && pattern.getPredicate(1, 2, 1).getBlock() == Blocks.COPPER_TRAPDOOR
            && isValvePipe(pattern.getPredicate(0, 2, 1), Direction.Axis.X)
            && isValvePipe(pattern.getPredicate(2, 2, 1), Direction.Axis.X)
            && isValvePipe(pattern.getPredicate(1, 2, 0), Direction.Axis.Z)
            && isValvePipe(pattern.getPredicate(1, 2, 2), Direction.Axis.Z);
    }

    private static boolean isValvePipe(BlockPredicateWithState predicate, Direction.Axis axis) {
        return predicate.getBlock() == ModBlocks.PIPE_STRAIGHT.get()
            && predicate.getPropertyValue(PipeBlock.AXIS) == axis
            && Boolean.TRUE.equals(predicate.getPropertyValue(PipeBlock.HAS_CHECK_VALVE));
    }

    private static void seedValve(LevelLike level, BlockPos pos, Direction outward) {
        if (level.getBlockEntity(pos) instanceof PipeCheckValveBlockEntity valve) {
            // LevelLike 会绑定真实客户端世界，写预览数据前临时解绑以免标脏真实区块。
            var parent = valve.getLevel();
            valve.setLevel(null);
            valve.setValve(outward, outward);
            valve.setLevel(parent);
        }
    }
}
