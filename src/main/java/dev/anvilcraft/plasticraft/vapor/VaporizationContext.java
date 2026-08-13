package dev.anvilcraft.plasticraft.vapor;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.Objects;

/** 一次大型炼药锅气化事务共享的位置与状态。仅在服务器线程构造。 */
public record VaporizationContext(ServerLevel level, VaporizationCauldron cauldron) {
    public VaporizationContext {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(cauldron, "cauldron");
    }

    public BlockPos cauldronPos() {
        return this.cauldron.vaporizationPos();
    }

    /** 大型炼药锅顶面中心正上方的第一格。 */
    public BlockPos outletPos() {
        return this.cauldronPos().above(2);
    }

    public FluidStack topFluid() {
        return this.cauldron.getTopVaporizationFluid().copy();
    }
}
