package dev.anvilcraft.plasticraft.entity;

import dev.anvilcraft.plasticraft.entity.collision.BuiltInPlasticEntityModels;
import dev.anvilcraft.plasticraft.entity.collision.PlasticEntityGeometry;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.item.PlasticItemData;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.anvilcraft.plasticraft.material.PlasticMaterial;
import dev.anvilcraft.plasticraft.molding.product.PlasticCauldronLayout;
import dev.anvilcraft.plasticraft.recipe.CauldronImpactRecipeProcessor;
import dev.dubhe.anvilcraft.api.itemhandler.PollableItemHandler;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** 带有兼容鱼缸的物品和流体存储能力的可移动六向釜。 */
public class HardenedResinCauldronEntity extends AbstractCauldronPlasticEntity {
    private static final PlasticEntityGeometry GEOMETRY = BuiltInPlasticEntityModels
        .HARDENED_RESIN_CAULDRON
        .geometry();
    public static final int CAPACITY = 1000;
    public static final float COLLISION_SIZE = 1.0F;
    private static final double FLUID_INNER_INSET = 0.126D;
    private static final double FLUID_BOTTOM = 0.251D;
    private static final double FLUID_HEIGHT = 0.685D;
    private static final VoxelShape RESIN_ENTRY_OPENING = Block.box(
        2.0D, 0.0D, 2.0D, 14.0D, 16.0D, 14.0D
    );
    private static final PlasticEntityGeometry RESIN_ENTRY_GEOMETRY = BuiltInPlasticEntityModels
        .HARDENED_RESIN_CAULDRON_ENTRY;

    private static final EntityDataAccessor<Integer> FLUID_ID = SynchedEntityData.defineId(
        HardenedResinCauldronEntity.class,
        EntityDataSerializers.INT
    );
    private static final EntityDataAccessor<Integer> FLUID_AMOUNT = SynchedEntityData.defineId(
        HardenedResinCauldronEntity.class,
        EntityDataSerializers.INT
    );
    private static final EntityDataAccessor<Integer> FLUID_COLOR = SynchedEntityData.defineId(
        HardenedResinCauldronEntity.class,
        EntityDataSerializers.INT
    );
    private static final EntityDataAccessor<CompoundTag> DISPLAY_ITEMS = SynchedEntityData.defineId(
        HardenedResinCauldronEntity.class,
        EntityDataSerializers.COMPOUND_TAG
    );
    private static Supplier<ItemStack> defaultDropSupplier = () -> ItemStack.EMPTY;

    private final PollableItemHandler input = createInputHandler();
    private final ItemStackHandler output = createOutputHandler();
    private final ItemStackHandler emptyRecipeHandler = new ItemStackHandler(0);
    private final ItemStackHandler recipeInput = createDelegatingHandler(
        () -> this.processingOutput ? this.output : this.input
    );
    private final ItemStackHandler recipeOutput = createDelegatingHandler(
        () -> this.processingOutput ? this.emptyRecipeHandler : this.output
    );
    private final ItemStackHandler syncedItems = new ItemStackHandler(16);
    private final ItemStackHandler itemHandler = new ItemStackHandler(16) {
        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot < 8 ? HardenedResinCauldronEntity.this.output.getStackInSlot(slot)
                : HardenedResinCauldronEntity.this.input.getStackInSlot(slot - 8);
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot < 8 ? HardenedResinCauldronEntity.this.output.getSlotLimit(slot)
                : HardenedResinCauldronEntity.this.input.getSlotLimit(slot - 8);
        }

        @Override
        protected int getStackLimit(int slot, ItemStack stack) {
            if (slot < 8) return this.getSlotLimit(slot);
            return Math.min(this.getSlotLimit(slot), stack.getMaxStackSize());
        }

        @Override
        public void setSize(int size) {
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            if (slot < 8) {
                HardenedResinCauldronEntity.this.output.setStackInSlot(slot, stack);
            } else {
                HardenedResinCauldronEntity.this.input.setStackInSlot(slot - 8, stack);
            }
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot >= 8 && HardenedResinCauldronEntity.this.input.isItemValid(slot - 8, stack);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || !this.isItemValid(slot, stack)) return stack;
            return HardenedResinCauldronEntity.this.input.insertItem(slot - 8, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot < 8
                ? HardenedResinCauldronEntity.this.output.extractItem(slot, amount, simulate)
                : HardenedResinCauldronEntity.this.input.extractItem(slot - 8, amount, simulate);
        }
    };
    private final FluidTank fluidHandler = new FluidTank(CAPACITY) {
        @Override
        protected void onContentsChanged() {
            HardenedResinCauldronEntity.this.syncFluidData();
        }
    };
    public static void configureDefaultDrop(Supplier<ItemStack> supplier) {
        defaultDropSupplier = Objects.requireNonNull(supplier, "supplier");
    }

    public HardenedResinCauldronEntity(EntityType<? extends HardenedResinCauldronEntity> type, Level level) {
        super(type, level);
        this.setDisplayState(PlasticraftBlocks.HARDEND_RESIN_CAULDRON.get().defaultBlockState());
    }

    public HardenedResinCauldronEntity(
        EntityType<? extends HardenedResinCauldronEntity> type,
        Level level,
        Vec3 position,
        BlockState displayState,
        ItemStack dropStack,
        PlasticEntityOrientation orientation
    ) {
        super(type, level, position, displayState, HardenedResinCauldronContents.withoutContents(dropStack), orientation);
        HardenedResinCauldronContents.get(dropStack).ifPresent(this::restorePickedContents);
    }

    private PollableItemHandler createInputHandler() {
        return new PollableItemHandler(8) {
            @Override
            protected int getEmptyOrSmallerSlot(ItemStack stack) {
                int slot = -1;
                int countInSlot = Integer.MAX_VALUE;
                for (int i = this.getSlots() - 1; i >= 0; i--) {
                    ItemStack stackInSlot = this.getStackInSlot(i);
                    if (stackInSlot.isEmpty()) {
                        slot = i;
                        continue;
                    }
                    if (!ItemStack.isSameItemSameComponents(stackInSlot, stack)) continue;
                    if (countInSlot != Integer.MAX_VALUE) return -1;
                    int stackInSlotCount = stackInSlot.getCount();
                    if (stackInSlotCount < this.getStackLimit(i, stackInSlot)) {
                        slot = i;
                        countInSlot = stackInSlotCount;
                    } else {
                        return -1;
                    }
                }
                return slot;
            }

            @Override
            protected void onContentsChanged(int slot) {
                HardenedResinCauldronEntity.this.contentsChanged();
            }
        };
    }

    private ItemStackHandler createOutputHandler() {
        return new ItemStackHandler(8) {
            @Override
            protected void onContentsChanged(int slot) {
                if (!HardenedResinCauldronEntity.this.autoOutputting
                    && !HardenedResinCauldronEntity.this.restoringData) {
                    HardenedResinCauldronEntity.this.tryAutoOutputResults();
                }
                HardenedResinCauldronEntity.this.contentsChanged();
            }
        };
    }

    private ItemStackHandler createDelegatingHandler(Supplier<ItemStackHandler> delegate) {
        return new ItemStackHandler(0) {
            @Override
            public int getSlots() {
                return delegate.get().getSlots();
            }

            @Override
            public ItemStack getStackInSlot(int slot) {
                return delegate.get().getStackInSlot(slot);
            }

            @Override
            public void setStackInSlot(int slot, ItemStack stack) {
                delegate.get().setStackInSlot(slot, stack);
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return delegate.get().insertItem(slot, stack, simulate);
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return delegate.get().extractItem(slot, amount, simulate);
            }

            @Override
            public int getSlotLimit(int slot) {
                return delegate.get().getSlotLimit(slot);
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return delegate.get().isItemValid(slot, stack);
            }
        };
    }

    private void contentsChanged() {
        this.hasImpulse = true;
        this.syncItemData();
        if (!this.restoringData) this.bondedDataDirty = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FLUID_ID, -1)
            .define(FLUID_AMOUNT, 0)
            .define(FLUID_COLOR, DyeColor.WHITE.getId())
            .define(DISPLAY_ITEMS, new CompoundTag());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DISPLAY_ITEMS.equals(key)) this.readSyncedItems(this.entityData.get(DISPLAY_ITEMS));
    }

    @Override
    protected PlasticEntityGeometry getLocalGeometry() {
        return GEOMETRY;
    }

    @Override
    protected PlasticEntityGeometry cauldronResinEntryGeometry() {
        return RESIN_ENTRY_GEOMETRY;
    }

    @Override
    protected VoxelShape cauldronResinEntryOpening() {
        return RESIN_ENTRY_OPENING;
    }

    @Override
    protected ItemStack createDefaultDropStack() {
        ItemStack stack = defaultDropSupplier.get();
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        stack = stack.copy();
        PlasticItemData.setMaterial(stack, "hardened_resin");
        return stack;
    }

    @Override
    public ItemStack getCompletePickResult() {
        ItemStack stack = super.getCompletePickResult();
        if (!stack.isEmpty()) HardenedResinCauldronContents.set(stack, this.createPickContents());
        return stack;
    }

    @Override
    protected ItemStack prepareInitialPickResult(ItemStack stack) {
        HardenedResinCauldronContents.clear(stack);
        return stack;
    }

    private HardenedResinCauldronContents createPickContents() {
        ItemStackHandler handler = this.getSyncedItemHandler();
        List<HardenedResinCauldronContents.StoredItem> items = new ArrayList<>();
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty()) items.add(new HardenedResinCauldronContents.StoredItem(slot, stack));
        }
        FluidStack fluid = this.level().isClientSide ? this.getSyncedFluid() : this.fluidHandler.getFluid();
        return new HardenedResinCauldronContents(
            items,
            fluid,
            Optional.ofNullable(this.getOutletLocalDirection()),
            this.anvilcraft$isIgnited()
        );
    }

    private void restorePickedContents(HardenedResinCauldronContents contents) {
        boolean previousRestoring = this.restoringData;
        this.restoringData = true;
        try {
            for (int slot = 0; slot < this.itemHandler.getSlots(); slot++) {
                this.itemHandler.setStackInSlot(slot, ItemStack.EMPTY);
            }
            for (HardenedResinCauldronContents.StoredItem item : contents.items()) {
                this.itemHandler.setStackInSlot(item.slot(), item.stack());
            }
            this.fluidHandler.setFluid(contents.fluid());
            this.setOutletLocalDirection(contents.outletSide().orElse(null));
            this.anvilcraft$setIgnited(contents.ignited());
        } finally {
            this.restoringData = previousRestoring;
            this.syncItemData();
            this.syncFluidData();
        }
    }

    /** 硬化树脂不耐热，锅外的世界熔岩同样会把它烧毁。 */
    @Override
    protected boolean resistsWorldLava() {
        return false;
    }

    @Override
    protected List<ItemStack> extractAllStacks() {
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemStackHandler handler : new ItemStackHandler[]{this.output, this.input}) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack extracted = handler.extractItem(slot, Integer.MAX_VALUE, false);
                if (!extracted.isEmpty()) stacks.add(extracted);
            }
        }
        return stacks;
    }

    @Override
    protected void prepareAnvilHammerPickup(Player player) {
        for (ItemStack stack : this.extractAllStacks()) {
            player.getInventory().placeItemBackInInventory(stack);
        }
    }

    @Override
    public ItemStackHandler getInput() {
        return CauldronImpactRecipeProcessor.canAccessRecipeInventory(this)
            ? this.recipeInput
            : this.emptyRecipeHandler;
    }

    @Override
    public ItemStackHandler getOutput() {
        return CauldronImpactRecipeProcessor.canAccessRecipeInventory(this)
            ? this.recipeOutput
            : this.emptyRecipeHandler;
    }

    @Override
    public ItemStackHandler getItemHandler() {
        return this.itemHandler;
    }

    @Override
    public FluidTank getFluidHandler() {
        return this.fluidHandler;
    }

    @Override
    protected IItemHandler cauldronInputHandler() {
        return this.input;
    }

    @Override
    protected IItemHandler cauldronOutputHandler() {
        return this.output;
    }

    @Override
    protected void onCauldronContentsChanged() {
        this.contentsChanged();
    }

    /** 内壁内缩后按底层流体占比抬高上表面，得到锅内液体占据的世界盒。 */
    @Override
    protected AABB cauldronFluidArea() {
        AABB box = this.getBoundingBox();
        double fill = Math.clamp((double) this.fluidHandler.getFluidAmount() / CAPACITY, 0.0D, 1.0D);
        return new AABB(
            box.minX + FLUID_INNER_INSET,
            box.minY + FLUID_BOTTOM,
            box.minZ + FLUID_INNER_INSET,
            box.maxX - FLUID_INNER_INSET,
            box.minY + FLUID_BOTTOM + FLUID_HEIGHT * fill,
            box.maxZ - FLUID_INNER_INSET
        );
    }

    @Override
    protected int cauldronFluidCapacity() {
        return CAPACITY;
    }

    @Override
    public boolean plasticraft$isCauldron() {
        return true;
    }

    @Override
    public PlasticCauldronLayout plasticraft$cauldronLayout() {
        return PlasticCauldronLayout.NORMAL;
    }

    @Override
    public FluidStack plasticraft$bottomFluid() {
        return this.fluidHandler.getFluid();
    }

    @Override
    public IFluidHandler plasticraft$bottomFluidAccess() {
        return this.fluidHandler;
    }

    public FluidStack getSyncedFluid() {
        int id = this.entityData.get(FLUID_ID);
        int amount = this.entityData.get(FLUID_AMOUNT);
        Fluid fluid = id < 0 ? Fluids.EMPTY : BuiltInRegistries.FLUID.byId(id);
        if (fluid == null || fluid == Fluids.EMPTY || amount <= 0) return FluidStack.EMPTY;
        FluidStack stack = new FluidStack(fluid, amount);
        if (PlasticMaterial.fromMelt(stack).map(PlasticMaterial::supportsDyeing).orElse(false)) {
            PlasticMeltColor.set(stack, DyeColor.byId(this.entityData.get(FLUID_COLOR)));
        }
        return stack;
    }

    public ItemStackHandler getSyncedItemHandler() {
        return this.level().isClientSide ? this.syncedItems : this.itemHandler;
    }

    public List<ItemStack> getSyncedItems() {
        ItemStackHandler handler = this.getSyncedItemHandler();
        List<ItemStack> stacks = new ArrayList<>();
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty()) stacks.add(stack);
        }
        return stacks;
    }

    private void syncItemData() {
        if (this.level().isClientSide) return;
        this.entityData.set(DISPLAY_ITEMS, this.createDisplayItemsTag(this.registryAccess()));
    }

    private CompoundTag createDisplayItemsTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        ListTag stacks = new ListTag();
        for (int slot = 0; slot < this.itemHandler.getSlots(); slot++) {
            ItemStack stack = this.itemHandler.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            CompoundTag entry = new CompoundTag();
            entry.putInt("Slot", slot);
            entry.put("Stack", stack.save(registries));
            stacks.add(entry);
        }
        tag.put("Stacks", stacks);
        return tag;
    }

    private void readSyncedItems(CompoundTag tag) {
        for (int slot = 0; slot < this.syncedItems.getSlots(); slot++) {
            this.syncedItems.setStackInSlot(slot, ItemStack.EMPTY);
        }
        ListTag stacks = tag.getList("Stacks", Tag.TAG_COMPOUND);
        int count = Math.min(stacks.size(), this.syncedItems.getSlots());
        for (int index = 0; index < count; index++) {
            CompoundTag entry = stacks.getCompound(index);
            int slot = entry.getInt("Slot");
            if (slot < 0 || slot >= this.syncedItems.getSlots()) continue;
            this.syncedItems.setStackInSlot(
                slot,
                ItemStack.parseOptional(this.registryAccess(), entry.getCompound("Stack"))
            );
        }
    }

    private void syncFluidData() {
        FluidStack fluid = this.fluidHandler.getFluid();
        this.entityData.set(FLUID_ID, fluid.isEmpty() ? -1 : BuiltInRegistries.FLUID.getId(fluid.getFluid()));
        this.entityData.set(FLUID_AMOUNT, fluid.getAmount());
        this.entityData.set(FLUID_COLOR, PlasticMeltColor.get(fluid).getId());
        this.hasImpulse = true;
        this.hurtMarked = true;
        if (!this.restoringData) this.bondedDataDirty = true;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        HolderLookup.Provider registries = this.registryAccess();
        tag.put("Fluid", this.fluidHandler.writeToNBT(registries, new CompoundTag()));
        tag.put("Inputs", this.input.serializeNBT(registries));
        tag.put("Outputs", this.output.serializeNBT(registries));
        tag.putBoolean("Ignited", this.anvilcraft$isIgnited());
        Direction outletSide = this.getOutletLocalDirection();
        if (outletSide != null) tag.putString("OutletSide", outletSide.getName());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.restoringData = true;
        try {
            super.readAdditionalSaveData(tag);
            Direction outletSide = Direction.byName(tag.getString("OutletSide"));
            this.setOutletLocalDirection(
                outletSide != null && outletSide.getAxis().isHorizontal() ? outletSide : null
            );
            HolderLookup.Provider registries = this.registryAccess();
            this.fluidHandler.readFromNBT(registries, tag.getCompound("Fluid"));
            this.input.deserializeNBT(registries, tag.getCompound("Inputs"));
            this.output.deserializeNBT(registries, tag.getCompound("Outputs"));
            this.anvilcraft$setIgnited(tag.getBoolean("Ignited") || tag.getBoolean("ignited"));
            this.syncFluidData();
            if (this.level().isClientSide) {
                // Bonded block renderers recreate this entity from its saved NBT instead
                // of receiving the normal spawn data update, so seed their display cache.
                this.readSyncedItems(this.createDisplayItemsTag(registries));
            } else {
                this.syncItemData();
            }
        } finally {
            this.restoringData = false;
        }
    }
}
