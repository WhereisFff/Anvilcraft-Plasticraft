package dev.anvilcraft.plasticraft.blueprint;

import dev.anvilcraft.plasticraft.init.PlasticraftEntityBuildAdapters;
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
import java.util.Map;

/**
 * 任务休息室下表面物流:只读下方一格朝上的物品/流体能力。
 * 能力存在但空是缺料;能力整体消失才是来源不可用。
 */
public final class ConstructionMaterialAccess {
    private final ServerLevel level;
    private final BlockPos loungePos;
    @Nullable
    private final IItemHandler items;
    @Nullable
    private final IFluidHandler fluids;

    private ConstructionMaterialAccess(
        ServerLevel level,
        BlockPos loungePos,
        @Nullable IItemHandler items,
        @Nullable IFluidHandler fluids
    ) {
        this.level = level;
        this.loungePos = loungePos.immutable();
        this.items = items;
        this.fluids = fluids;
    }

    public static ConstructionMaterialAccess below(ServerLevel level, BlockPos loungePos) {
        BlockPos below = loungePos.below();
        return new ConstructionMaterialAccess(
            level,
            loungePos,
            level.getCapability(Capabilities.ItemHandler.BLOCK, below, Direction.UP),
            level.getCapability(Capabilities.FluidHandler.BLOCK, below, Direction.UP)
        );
    }

    public boolean isAvailable() {
        return this.items != null || this.fluids != null;
    }

    @Nullable
    public IItemHandler items() {
        return this.items;
    }

    public Vec3 dropPosition() {
        return Vec3.atCenterOf(this.loungePos).add(0.0D, 0.25D, 0.75D);
    }

    public ItemStack extract(ConstructionBuildOp op) {
        if (op.kind() == ConstructionBuildOp.Kind.SEAL
            && (op.material().isEmpty() || FluidSealFill.stateOf(op.material()) == null)) {
            return ItemStack.EMPTY;
        }
        if (!op.needsMaterial()) {
            return ItemStack.EMPTY;
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
        if (needed.isEmpty()) {
            return true;
        }
        return this.countMatching(needed) >= needed.getCount();
    }

    public boolean hasFluid(FluidStack needed) {
        if (needed.isEmpty()) {
            return false;
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
        if (stack.isEmpty()) {
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
        this.level.addFreshEntity(new ItemEntity(this.level, drop.x, drop.y, drop.z, stack.copy()));
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
        int remaining = needed.getCount();
        for (int slot = 0; slot < this.items.getSlots() && remaining > 0; slot++) {
            ItemStack stack = this.items.getStackInSlot(slot);
            if (!ConstructionJobController.matchesMaterial(stack, needed)) {
                continue;
            }
            ItemStack extracted = this.items.extractItem(slot, remaining, false);
            remaining -= extracted.getCount();
        }
        return remaining <= 0;
    }

    private int countMatching(ItemStack needed) {
        if (this.items == null) {
            return 0;
        }
        int count = 0;
        for (int slot = 0; slot < this.items.getSlots(); slot++) {
            ItemStack stack = this.items.getStackInSlot(slot);
            if (ConstructionJobController.matchesMaterial(stack, needed)) {
                count += stack.getCount();
            }
        }
        return count;
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
                    ItemStack leftover = this.items.insertItem(slot, stack, false);
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
