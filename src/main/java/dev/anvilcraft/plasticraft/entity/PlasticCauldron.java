package dev.anvilcraft.plasticraft.entity;

import dev.anvilcraft.plasticraft.molding.product.PlasticCauldronLayout;
import dev.anvilcraft.lib.v2.recipe.cache.IItemHandlerCache;
import dev.dubhe.anvilcraft.api.entity.IEntityCauldron;
import dev.dubhe.anvilcraft.api.itemhandler.IItemHandlerHolder;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 实体炼药锅的统一契约。硬化树脂炼药锅与成型塑料炼药锅共用实体实现，配方、方块实体与事件层仍只面向
 * 本接口编程，以便同一套锅语义也能代理到粘合后的方块形态。
 */
public interface PlasticCauldron extends IItemHandlerCache, IItemHandlerHolder, IEntityCauldron {
    /** 承载锅语义的实体本身；接口无法继承 {@link Entity}，跨类调用需要它兜底。 */
    AbstractPlasticEntity plasticraft$cauldronEntity();

    /**
     * 该实体当前是否真的作为炼药锅工作。通用塑料实体无条件实现本接口，
     * 但只有成型类型为炼药锅时才具备锅行为，所有查询入口都必须先过这道闸。
     */
    boolean plasticraft$isCauldron();

    /** 槽位与流体分层布局，普通锅与大型锅在此分流。 */
    PlasticCauldronLayout plasticraft$cauldronLayout();

    /** 原始层序中的最底层流体，供普通锅及底部加工查询使用。 */
    FluidStack plasticraft$bottomFluid();

    /** 从底层优先读写的流体句柄。 */
    IFluidHandler plasticraft$bottomFluidAccess();

    /** 当前可点燃的表层流体，大型锅覆盖为最顶层。 */
    default FluidStack plasticraft$ignitionFluid() {
        return this.plasticraft$bottomFluid();
    }

    default IFluidHandler plasticraft$ignitionFluidAccess() {
        return this.plasticraft$bottomFluidAccess();
    }

    double getFluidSurfaceY();

    List<ItemStack> getSyncedItems();

    boolean hasOutlet();

    @Nullable Direction getOutletDirection();

    boolean clearOutletFacing(Direction worldDirection);

    /** 把配方产物塞回锅内，返回未能容纳的剩余部分。 */
    ItemStack insertRecipeOutput(ItemStack stack);

    void processAnvilImpact(AbstractPlasticEntity anvil, Direction impactDirection);

    void processSweptAnvilImpact(
        AbstractPlasticEntity anvil,
        Vec3 anvilStartPosition,
        Vec3 anvilEndPosition,
        Vec3 potStartPosition,
        Vec3 potEndPosition
    );

    boolean tryClaimRecipeImpact(@Nullable Entity source);

    void beginRecipeProcessing();

    void finishRecipeProcessing();

    boolean plasticraft$wasBurnedByLava();

    boolean plasticraft$consumeBondedDataDirty();

    boolean plasticraft$isEntityInsidePlasticMelt(Entity entity);

    /** 对锅内熔体中的实体施加减速；方块形态由 {@code entityInside} 逐实体调用。 */
    void plasticraft$stickEntityInPlasticMelt(Entity entity);

    void plasticraft$tickBonded();

    /** 重新声明为抽象，避免宿主漏实现时静默沿用本体接口的「永不点燃」默认值。 */
    @Override
    boolean anvilcraft$isIgnited();

    @Override
    void anvilcraft$setIgnited(boolean ignited);

    /** 塑料锅按 mB 计量，不做整锅转移。 */
    @Override
    default boolean anvilcraft$usesWholeCauldronFluidTransfers() {
        return false;
    }

    default Level level() {
        return this.plasticraft$cauldronEntity().level();
    }

    default AABB getBoundingBox() {
        return this.plasticraft$cauldronEntity().getBoundingBox();
    }

    default Vec3 position() {
        return this.plasticraft$cauldronEntity().position();
    }

    default boolean isRemoved() {
        return this.plasticraft$cauldronEntity().isRemoved();
    }

    default int getId() {
        return this.plasticraft$cauldronEntity().getId();
    }

    default PlasticEntityOrientation getOrientation() {
        return this.plasticraft$cauldronEntity().getOrientation();
    }
}
