package dev.anvilcraft.plasticraft.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/** 一份规范快照方块对应的施工操作。 */
public final class ConstructionBuildOp {
    public enum Kind {
        PLACE,
        ATTACHED,
        UNSUPPORTED,
        SEAL,
        DEMOLISH,
        CONTENT,
        FLUID,
        ENTITY
    }

    public enum Status {
        PENDING,
        WAITING_WORLD,
        WAITING_OCCUPIED,
        LEASED,
        DELIVERED,
        SKIPPED
    }

    private final int id;
    private final BlockPos pos;
    private final BlockState target;
    private ItemStack material;
    private final Kind kind;
    private Status status;
    private int order;
    @Nullable
    private UUID leaseAllay;
    @Nullable
    private BlockPos approach;
    private boolean shell;
    private int parentId = -1;
    private int slot = -1;
    @Nullable
    private CompoundTag blockEntity;
    private FluidStack fluid = FluidStack.EMPTY;
    @Nullable
    private CompoundTag entityNbt;
    private ItemStack returnStack = ItemStack.EMPTY;

    public ConstructionBuildOp(
        int id,
        BlockPos pos,
        BlockState target,
        ItemStack material,
        Kind kind,
        Status status,
        int order
    ) {
        this.id = id;
        this.pos = pos.immutable();
        this.target = target;
        this.material = material.copy();
        this.kind = kind;
        this.status = status;
        this.order = order;
    }

    public int id() {
        return this.id;
    }

    public BlockPos pos() {
        return this.pos;
    }

    public BlockState target() {
        return this.target;
    }

    public ItemStack material() {
        return this.material;
    }

    public void setMaterial(ItemStack material) {
        this.material = material.isEmpty() ? ItemStack.EMPTY : material.copy();
    }

    public Kind kind() {
        return this.kind;
    }

    public Status status() {
        return this.status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public int order() {
        return this.order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public Optional<UUID> leaseAllay() {
        return Optional.ofNullable(this.leaseAllay);
    }

    public void setLeaseAllay(@Nullable UUID allayId) {
        this.leaseAllay = allayId;
    }

    public Optional<BlockPos> approach() {
        return Optional.ofNullable(this.approach);
    }

    public void setApproach(@Nullable BlockPos approach) {
        this.approach = approach == null ? null : approach.immutable();
    }

    public boolean shell() {
        return this.shell;
    }

    public void setShell(boolean shell) {
        this.shell = shell;
    }

    public int parentId() {
        return this.parentId;
    }

    public void setParentId(int parentId) {
        this.parentId = parentId;
    }

    public int slot() {
        return this.slot;
    }

    public void setSlot(int slot) {
        this.slot = slot;
    }

    @Nullable
    public CompoundTag blockEntity() {
        return this.blockEntity;
    }

    public void setBlockEntity(@Nullable CompoundTag blockEntity) {
        this.blockEntity = blockEntity == null || blockEntity.isEmpty() ? null : blockEntity.copy();
    }

    public FluidStack fluid() {
        return this.fluid.copy();
    }

    public void setFluid(FluidStack fluid) {
        this.fluid = fluid == null || fluid.isEmpty() ? FluidStack.EMPTY : fluid.copy();
    }

    @Nullable
    public CompoundTag entityNbt() {
        return this.entityNbt;
    }

    public void setEntityNbt(@Nullable CompoundTag entityNbt) {
        this.entityNbt = entityNbt == null || entityNbt.isEmpty() ? null : entityNbt.copy();
    }

    public ItemStack returnStack() {
        return this.returnStack;
    }

    public void setReturnStack(ItemStack returnStack) {
        this.returnStack = returnStack == null || returnStack.isEmpty() ? ItemStack.EMPTY : returnStack.copy();
    }

    public boolean needsMaterial() {
        if (this.kind == Kind.FLUID && !this.fluid.isEmpty()) {
            return true;
        }
        return (this.kind == Kind.PLACE
            || this.kind == Kind.SEAL
            || this.kind == Kind.CONTENT
            || this.kind == Kind.ENTITY)
            && !this.material.isEmpty();
    }

    public boolean isBuildMaterial() {
        return this.kind == Kind.PLACE
            || this.kind == Kind.ATTACHED
            || this.kind == Kind.CONTENT
            || this.kind == Kind.FLUID
            || this.kind == Kind.ENTITY;
    }

    /** PLACE/ATTACHED 才写入施工投影;SEAL/DEMOLISH 的 DELIVERED 只表示该阶段完成。 */
    public boolean writesProjection() {
        return this.kind == Kind.PLACE || this.kind == Kind.ATTACHED;
    }

    public boolean isOpen() {
        return this.status == Status.PENDING
            || this.status == Status.WAITING_WORLD
            || this.status == Status.WAITING_OCCUPIED
            || this.status == Status.LEASED;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Id", this.id);
        tag.putLong("Pos", this.pos.asLong());
        tag.put("Target", NbtUtils.writeBlockState(this.target));
        if (!this.material.isEmpty()) {
            tag.put("Material", this.material.save(registries));
        }
        tag.putString("Kind", this.kind.name());
        tag.putString("Status", this.status.name());
        tag.putInt("Order", this.order);
        if (this.leaseAllay != null) {
            tag.putUUID("LeaseAllay", this.leaseAllay);
        }
        if (this.approach != null) {
            tag.putLong("Approach", this.approach.asLong());
        }
        if (this.shell) {
            tag.putBoolean("Shell", true);
        }
        if (this.parentId >= 0) {
            tag.putInt("ParentId", this.parentId);
        }
        if (this.blockEntity != null) {
            tag.put("BlockEntity", this.blockEntity.copy());
        }
        if (this.slot >= 0) {
            tag.putInt("Slot", this.slot);
        }
        if (!this.fluid.isEmpty()) {
            tag.put("Fluid", this.fluid.save(registries));
        }
        if (this.entityNbt != null) {
            tag.put("EntityNbt", this.entityNbt.copy());
        }
        if (!this.returnStack.isEmpty()) {
            tag.put("Return", this.returnStack.save(registries));
        }
        return tag;
    }

    public static ConstructionBuildOp load(CompoundTag tag, HolderLookup.Provider registries) {
        BlockState target = NbtUtils.readBlockState(
            registries.lookupOrThrow(Registries.BLOCK),
            tag.getCompound("Target")
        );
        ItemStack material = tag.contains("Material")
            ? ItemStack.parse(registries, tag.getCompound("Material")).orElse(ItemStack.EMPTY)
            : ItemStack.EMPTY;
        Kind kind = Kind.valueOf(tag.getString("Kind"));
        Status status = Status.valueOf(tag.getString("Status"));
        ConstructionBuildOp op = new ConstructionBuildOp(
            tag.getInt("Id"),
            BlockPos.of(tag.getLong("Pos")),
            target,
            material,
            kind,
            status,
            tag.getInt("Order")
        );
        if (tag.hasUUID("LeaseAllay")) {
            op.leaseAllay = tag.getUUID("LeaseAllay");
        }
        if (tag.contains("Approach")) {
            op.approach = BlockPos.of(tag.getLong("Approach"));
        }
        op.shell = tag.getBoolean("Shell");
        if (tag.contains("ParentId")) {
            op.parentId = tag.getInt("ParentId");
        }
        if (tag.contains("BlockEntity", Tag.TAG_COMPOUND)) {
            op.blockEntity = tag.getCompound("BlockEntity").copy();
        }
        if (tag.contains("Slot")) {
            op.slot = tag.getInt("Slot");
        }
        if (tag.contains("Fluid")) {
            op.fluid = FluidStack.parse(registries, tag.get("Fluid")).orElse(FluidStack.EMPTY);
        }
        if (tag.contains("EntityNbt", Tag.TAG_COMPOUND)) {
            op.entityNbt = tag.getCompound("EntityNbt").copy();
        }
        if (tag.contains("Return")) {
            op.returnStack = ItemStack.parse(registries, tag.getCompound("Return")).orElse(ItemStack.EMPTY);
        }
        return op;
    }
}
