package dev.anvilcraft.plasticraft.blueprint;

import dev.anvilcraft.plasticraft.init.PlasticraftEntityBuildAdapters;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import dev.dubhe.anvilcraft.init.item.ModComponents;
import dev.dubhe.anvilcraft.item.property.component.SavedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 休息室下方一格的容器与掉落物共用供料事务，先取容器再取地面物品。
 * 创造板条箱无限供应建材、流体与工具，不受过滤物限制。
 */
public final class ConstructionMaterialAccess {
    private static final int GIVE_UP_PICKUP_DELAY_TICKS = 100;

    private final ServerLevel level;
    private final BlockPos loungePos;
    @Nullable
    private final IItemHandler items;
    @Nullable
    private final IFluidHandler fluids;
    private final boolean infinite;
    private final boolean hasContainer;
    private final ConstructionGroundItems groundItems;

    private ConstructionMaterialAccess(
        ServerLevel level,
        BlockPos loungePos,
        @Nullable IItemHandler items,
        @Nullable IFluidHandler fluids,
        boolean infinite
    ) {
        this.level = level;
        this.loungePos = loungePos.immutable();
        this.hasContainer = items != null;
        this.groundItems = new ConstructionGroundItems(level, loungePos.below(), items);
        this.items = this.groundItems;
        this.fluids = fluids;
        this.infinite = infinite;
    }

    public static ConstructionMaterialAccess below(ServerLevel level, BlockPos loungePos) {
        BlockPos below = loungePos.below();
        return new ConstructionMaterialAccess(
            level,
            loungePos,
            level.getCapability(Capabilities.ItemHandler.BLOCK, below, Direction.UP),
            level.getCapability(Capabilities.FluidHandler.BLOCK, below, Direction.UP),
            level.getBlockState(below).is(ModBlocks.CREATIVE_CRATE.get())
        );
    }

    public boolean isAvailable() {
        return this.infinite || this.hasContainer || this.fluids != null
            || this.groundItems.hasItems() || this.groundItems.isOpen();
    }

    public boolean isInfinite() {
        return this.infinite;
    }

    @Nullable
    public IItemHandler items() {
        return this.items;
    }

    public Vec3 dropPosition() {
        return Vec3.atCenterOf(this.loungePos).add(0.0D, 0.25D, 0.75D);
    }

    public ItemStack extractTool(Item item) {
        if (this.infinite) return new ItemStack(item);
        if (this.items == null) return ItemStack.EMPTY;
        for (int slot = 0; slot < this.items.getSlots(); slot++) {
            if (!this.items.getStackInSlot(slot).is(item)) continue;
            ItemStack extracted = this.items.extractItem(slot, 1, false);
            if (!extracted.isEmpty()) return extracted;
        }
        return ItemStack.EMPTY;
    }

    public ItemStack extract(ConstructionBuildOp op) {
        if (op.kind() == ConstructionBuildOp.Kind.SEAL
            && (op.material().isEmpty() || FluidSealFill.stateOf(op.material()) == null)) {
            return ItemStack.EMPTY;
        }
        if (!op.needsMaterial()) {
            return ItemStack.EMPTY;
        }
        if (this.infinite) {
            return ConstructionJobController.creativeSupply(op);
        }
        if (op.kind() == ConstructionBuildOp.Kind.FLUID && !op.fluid().isEmpty()) {
            if (!op.material().isEmpty() && this.takeMatching(op.material())) {
                return op.material().copy();
            }
            if (this.takeExactFluid(op.fluid())) {
                return op.material().isEmpty() ? new ItemStack(Items.BUCKET) : op.material().copy();
            }
            return ItemStack.EMPTY;
        }
        if (this.takeMatching(op.material())) {
            return op.material().copy();
        }
        if (op.kind() == ConstructionBuildOp.Kind.ENTITY) {
            return this.takeMatchingResin(op);
        }
        return ItemStack.EMPTY;
    }

    public boolean hasMaterial(ItemStack needed) {
        if (needed.isEmpty() || this.infinite) {
            return true;
        }
        return this.canTakeMatching(needed);
    }

    public boolean hasMaterial(ConstructionBuildOp op) {
        if (op.kind() == ConstructionBuildOp.Kind.SEAL
            && (op.material().isEmpty() || FluidSealFill.stateOf(op.material()) == null)) {
            return false;
        }
        if (!op.needsMaterial() || this.infinite) return true;
        if (op.kind() == ConstructionBuildOp.Kind.FLUID && !op.fluid().isEmpty()) {
            return (!op.material().isEmpty() && this.hasMaterial(op.material())) || this.hasFluid(op.fluid());
        }
        if (this.hasMaterial(op.material())) return true;
        return op.kind() == ConstructionBuildOp.Kind.ENTITY && this.hasMatchingResin(op);
    }

    public boolean hasFluid(FluidStack needed) {
        if (needed.isEmpty()) {
            return false;
        }
        if (this.infinite) {
            return true;
        }
        if (this.items != null) {
            for (int slot = 0; slot < this.items.getSlots(); slot++) {
                if (FluidBuildAdapter.canProvideExactFluid(this.items.getStackInSlot(slot), needed)) {
                    return true;
                }
            }
        }
        if (this.fluids == null) {
            return false;
        }
        FluidStack simulated = this.fluids.drain(needed.copy(), IFluidHandler.FluidAction.SIMULATE);
        return simulated.getAmount() == needed.getAmount()
            && FluidStack.isSameFluidSameComponents(simulated, needed);
    }

    public Map<Item, Integer> countItems() {
        Map<Item, Integer> owned = new HashMap<>();
        if (this.items == null) {
            return owned;
        }
        for (int slot = 0; slot < this.items.getSlots(); slot++) {
            ItemStack stack = this.items.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            owned.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
        return owned;
    }

    public ItemStack insert(ItemStack stack) {
        if (stack.isEmpty() || this.infinite) {
            return ItemStack.EMPTY;
        }
        if (this.items == null) {
            return stack.copy();
        }
        return ItemHandlerHelper.insertItem(this.items, stack.copy(), false);
    }

    public void dropBeside(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        Vec3 drop = Vec3.atCenterOf(this.loungePos).add(0.0D, 0.25D, 0.75D);
        ItemEntity dropped = new ItemEntity(this.level, drop.x, drop.y, drop.z, stack.copy());
        // 容器塞不下才落地,必须短暂禁止拾取,否则收集悦灵会立刻把它吸回来反复丢在同一处
        dropped.setPickUpDelay(GIVE_UP_PICKUP_DELAY_TICKS);
        this.level.addFreshEntity(dropped);
    }

    public ItemStack insertOrDrop(ItemStack stack) {
        ItemStack leftover = this.insert(stack);
        if (!leftover.isEmpty()) {
            this.dropBeside(leftover);
        }
        return leftover;
    }

    private boolean takeMatching(ItemStack needed) {
        if (needed.isEmpty()) {
            return true;
        }
        if (this.items == null) {
            return false;
        }
        if (!this.canTakeMatching(needed)) {
            return false;
        }
        int remaining = needed.getCount();
        List<ItemStack> taken = new ArrayList<>();
        for (int slot = 0; slot < this.items.getSlots() && remaining > 0; slot++) {
            ItemStack stack = this.items.getStackInSlot(slot);
            if (!ConstructionJobController.matchesMaterial(stack, needed)) {
                continue;
            }
            ItemStack extracted = this.items.extractItem(slot, remaining, false);
            if (extracted.isEmpty()) continue;
            taken.add(extracted);
            if (!ConstructionJobController.matchesMaterial(extracted, needed)) break;
            remaining -= extracted.getCount();
        }
        if (remaining > 0) {
            for (ItemStack stack : taken) this.insertOrDrop(stack);
        }
        return remaining <= 0;
    }

    private boolean canTakeMatching(ItemStack needed) {
        if (needed.isEmpty()) return true;
        if (this.items == null) return false;
        int remaining = needed.getCount();
        for (int slot = 0; slot < this.items.getSlots() && remaining > 0; slot++) {
            ItemStack stack = this.items.getStackInSlot(slot);
            if (!ConstructionJobController.matchesMaterial(stack, needed)) continue;
            ItemStack simulated = this.items.extractItem(slot, remaining, true);
            if (ConstructionJobController.matchesMaterial(simulated, needed)) {
                remaining -= simulated.getCount();
            }
        }
        return remaining <= 0;
    }

    private boolean takeExactFluid(FluidStack needed) {
        if (needed.isEmpty()) {
            return false;
        }
        if (this.items != null) {
            for (int slot = 0; slot < this.items.getSlots(); slot++) {
                ItemStack stack = this.items.getStackInSlot(slot).copy();
                if (!FluidBuildAdapter.takeExactFluid(stack, needed)) {
                    continue;
                }
                this.items.extractItem(slot, this.items.getStackInSlot(slot).getCount(), false);
                if (!stack.isEmpty()) {
                    ItemStack leftover = this.insert(stack);
                    if (!leftover.isEmpty()) {
                        this.dropBeside(leftover);
                    }
                }
                return true;
            }
        }
        if (this.fluids == null) {
            return false;
        }
        FluidStack simulated = this.fluids.drain(needed.copy(), IFluidHandler.FluidAction.SIMULATE);
        if (simulated.getAmount() != needed.getAmount()
            || !FluidStack.isSameFluidSameComponents(simulated, needed)) {
            return false;
        }
        this.fluids.drain(needed.copy(), IFluidHandler.FluidAction.EXECUTE);
        return true;
    }

    private ItemStack takeMatchingResin(ConstructionBuildOp op) {
        if (this.items == null) {
            return ItemStack.EMPTY;
        }
        for (int slot = 0; slot < this.items.getSlots(); slot++) {
            ItemStack stack = this.items.getStackInSlot(slot);
            if (!isMatchingResin(stack, op, this.level)) {
                continue;
            }
            ItemStack extracted = this.items.extractItem(slot, 1, false);
            if (!extracted.isEmpty()) {
                return extracted;
            }
        }
        return ItemStack.EMPTY;
    }

    private boolean hasMatchingResin(ConstructionBuildOp op) {
        if (this.items == null) return false;
        for (int slot = 0; slot < this.items.getSlots(); slot++) {
            if (!isMatchingResin(this.items.getStackInSlot(slot), op, this.level)) continue;
            if (!this.items.extractItem(slot, 1, true).isEmpty()) return true;
        }
        return false;
    }

    private static boolean isMatchingResin(ItemStack stack, ConstructionBuildOp op, ServerLevel level) {
        if (!PlasticraftEntityBuildAdapters.isResinCapture(stack) || op.entityNbt() == null) {
            return false;
        }
        EntityType<?> needed = EntityType.by(op.entityNbt()).orElse(null);
        SavedEntity saved = stack.get(ModComponents.SAVED_ENTITY);
        if (needed == null || saved == null) {
            return false;
        }
        Entity captured = saved.toEntity(level);
        if (captured != null && captured.getType() == needed) {
            return true;
        }
        return EntityType.by(saved.tag()).orElse(null) == needed;
    }
}
