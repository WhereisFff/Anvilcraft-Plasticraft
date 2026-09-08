package dev.anvilcraft.plasticraft.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 容器槽后追加下方一格内的掉落物槽，读取快照不取得物品所有权。 */
final class ConstructionGroundItems implements IItemHandler {
    private final ServerLevel level;
    private final AABB bounds;
    private final boolean acceptReturns;
    private final List<ItemEntity> entities;
    @Nullable
    private final IItemHandler container;
    private final int containerSlots;

    ConstructionGroundItems(ServerLevel level, BlockPos pos, @Nullable IItemHandler container) {
        this.level = level;
        this.bounds = new AABB(pos);
        this.container = container;
        this.containerSlots = container == null ? 0 : container.getSlots();
        this.acceptReturns = container == null;
        this.entities = new ArrayList<>(level.getEntitiesOfClass(ItemEntity.class, this.bounds, this::available));
        this.entities.sort(Comparator.comparing(ItemEntity::getUUID));
    }

    boolean hasItems() {
        return this.entities.stream().anyMatch(this::available);
    }

    boolean isOpen() {
        BlockPos pos = BlockPos.containing(this.bounds.getCenter());
        return this.level.getBlockState(pos).getCollisionShape(this.level, pos).isEmpty();
    }

    private boolean available(ItemEntity entity) {
        return entity.isAlive() && !entity.getItem().isEmpty() && !entity.hasPickUpDelay()
            && this.bounds.contains(entity.position());
    }

    @Override
    public int getSlots() {
        return this.containerSlots + this.entities.size() + 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (this.container != null && slot < this.containerSlots) return this.container.getStackInSlot(slot);
        slot -= this.containerSlots;
        if (slot < 0 || slot >= this.entities.size()) return ItemStack.EMPTY;
        ItemEntity entity = this.entities.get(slot);
        if (!this.available(entity)) return ItemStack.EMPTY;
        ItemStack stack = entity.getItem().copy();
        ConstructionDebris.clear(stack);
        return stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (this.container != null && slot < this.containerSlots) return this.container.extractItem(slot, amount, simulate);
        ItemStack available = this.getStackInSlot(slot);
        if (amount <= 0 || available.isEmpty()) return ItemStack.EMPTY;
        ItemStack result = available.copyWithCount(Math.min(amount, available.getCount()));
        if (simulate) return result;
        ItemEntity entity = this.entities.get(slot - this.containerSlots);
        ItemStack remaining = entity.getItem().copy();
        ConstructionDebris debris = ConstructionDebris.get(remaining);
        remaining.shrink(result.getCount());
        if (remaining.isEmpty()) entity.discard();
        else entity.setItem(remaining);
        if (debris != null) {
            ConstructionJobStore store = ConstructionJobStore.get(this.level);
            ConstructionJobProgress progress = store.get(debris.jobId());
            if (progress != null) {
                progress.addDebrisExternal(debris.operationId(), result.getCount());
                store.markDirty();
            }
        }
        return result;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (this.container != null && slot < this.containerSlots) return this.container.insertItem(slot, stack, simulate);
        slot -= this.containerSlots;
        if (stack.isEmpty()) return ItemStack.EMPTY;
        if (!this.acceptReturns || !this.isOpen() || slot != this.entities.size()) return stack;
        if (!simulate) {
            Vec3 pos = this.bounds.getCenter();
            ItemEntity entity = new ItemEntity(this.level, pos.x, this.bounds.minY + 0.1D, pos.z, stack.copy());
            entity.setDeltaMovement(Vec3.ZERO);
            entity.setPickUpDelay(0);
            if (!this.level.addFreshEntity(entity)) return stack;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        if (this.container != null && slot < this.containerSlots) return this.container.getSlotLimit(slot);
        return 64;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (this.container != null && slot < this.containerSlots) return this.container.isItemValid(slot, stack);
        return this.acceptReturns && this.isOpen() && slot == this.containerSlots + this.entities.size();
    }
}
