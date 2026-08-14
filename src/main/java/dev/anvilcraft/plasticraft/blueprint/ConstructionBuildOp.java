package dev.anvilcraft.plasticraft.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
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
        DEMOLISH
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
    private UUID leaseDrone;
    @Nullable
    private BlockPos approach;
    private boolean shell;

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

    public Optional<UUID> leaseDrone() {
        return Optional.ofNullable(this.leaseDrone);
    }

    public void setLeaseDrone(@Nullable UUID droneId) {
        this.leaseDrone = droneId;
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

    public boolean needsMaterial() {
        return (this.kind == Kind.PLACE || this.kind == Kind.SEAL) && !this.material.isEmpty();
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
        if (this.leaseDrone != null) {
            tag.putUUID("LeaseDrone", this.leaseDrone);
        }
        if (this.approach != null) {
            tag.putLong("Approach", this.approach.asLong());
        }
        if (this.shell) {
            tag.putBoolean("Shell", true);
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
        if (tag.hasUUID("LeaseDrone")) {
            op.leaseDrone = tag.getUUID("LeaseDrone");
        }
        if (tag.contains("Approach")) {
            op.approach = BlockPos.of(tag.getLong("Approach"));
        }
        op.shell = tag.getBoolean("Shell");
        return op;
    }
}
