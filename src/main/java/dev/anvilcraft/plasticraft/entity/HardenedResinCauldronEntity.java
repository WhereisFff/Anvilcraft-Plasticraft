package dev.anvilcraft.plasticraft.entity;

import dev.anvilcraft.plasticraft.entity.collision.BuiltInPlasticEntityModels;
import dev.anvilcraft.plasticraft.entity.collision.PlasticEntityGeometry;
import dev.anvilcraft.plasticraft.init.PlasticraftDataComponents;
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
import net.minecraft.nbt.NbtOps;
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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.DelegatingResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemUtil;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

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
        PlasticraftDataComponents.COMPOUND_TAG
    );
    private static Supplier<ItemStack> defaultDropSupplier = () -> ItemStack.EMPTY;

    private final PollableItemHandler input = createInputHandler();
    private final ItemStacksResourceHandler output = createOutputHandler();
    private final ItemStacksResourceHandler emptyRecipeHandler = new ItemStacksResourceHandler(0);
    private final ResourceHandler<ItemResource> recipeInput = new DelegatingResourceHandler<>(
        () -> this.processingOutput ? this.output : this.input
    );
    private final ResourceHandler<ItemResource> recipeOutput = new DelegatingResourceHandler<>(
        () -> this.processingOutput ? this.emptyRecipeHandler : this.output
    );
    private final ItemStacksResourceHandler syncedItems = new ItemStacksResourceHandler(16);
    private final ResourceHandler<ItemResource> itemHandler = new CombinedResourceHandler<>(List.of(
        new DelegatingResourceHandler<>(this.output) {
            @Override
            public boolean isValid(int slot, ItemResource resource) {
                return false;
            }

            @Override
            public int insert(int slot, ItemResource resource, int amount, TransactionContext transaction) {
                return 0;
            }

            @Override
            public int insert(ItemResource resource, int amount, TransactionContext transaction) {
                return 0;
            }
        },
        this.input
    ));
    private final FluidStacksResourceHandler fluidHandler = new FluidStacksResourceHandler(1, CAPACITY) {
        @Override
        protected void onContentsChanged(int index, FluidStack previous) {
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
            protected int getEmptyOrSmallerSlot(ItemResource resource) {
                int slot = -1;
                int countInSlot = Integer.MAX_VALUE;
                for (int i = this.size() - 1; i >= 0; i--) {
                    ItemStack stackInSlot = ItemUtil.getStack(this, i);
                    if (stackInSlot.isEmpty()) {
                        slot = i;
                        continue;
                    }
                    if (!resource.matches(stackInSlot)) continue;
                    if (countInSlot != Integer.MAX_VALUE) return -1;
                    int stackInSlotCount = stackInSlot.getCount();
                    if (stackInSlotCount < this.getCapacityAsIntDirect(i, resource)) {
                        slot = i;
                        countInSlot = stackInSlotCount;
                    } else {
                        return -1;
                    }
                }
                return slot;
            }

            @Override
            protected void onContentsChanged(int slot, ItemStack previous) {
                HardenedResinCauldronEntity.this.contentsChanged();
            }
        };
    }

    private ItemStacksResourceHandler createOutputHandler() {
        return new ItemStacksResourceHandler(8) {
            @Override
            protected void onContentsChanged(int slot, ItemStack previous) {
                if (!HardenedResinCauldronEntity.this.autoOutputting
                    && !HardenedResinCauldronEntity.this.restoringData) {
                    HardenedResinCauldronEntity.this.tryAutoOutputResults();
                }
                HardenedResinCauldronEntity.this.contentsChanged();
            }
        };
    }

    private void contentsChanged() {
        this.needsSync = true;
        this.syncItemData();
        if (!this.restoringData) {
            this.bondedDataDirty = true;
        }
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
        ResourceHandler<ItemResource> handler = this.getSyncedItemHandler();
        List<HardenedResinCauldronContents.StoredItem> items = new ArrayList<>();
        for (int slot = 0; slot < handler.size(); slot++) {
            ItemStack stack = ItemUtil.getStack(handler, slot);
            if (!stack.isEmpty()) items.add(new HardenedResinCauldronContents.StoredItem(slot, stack));
        }
        FluidStack fluid = this.level().isClientSide() ? this.getSyncedFluid() : FluidUtil.getStack(this.fluidHandler, 0);
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
            for (int slot = 0; slot < this.itemHandler.size(); slot++) {
                (slot < 8 ? this.output : this.input).set(slot % 8, ItemResource.EMPTY, 0);
            }
            for (HardenedResinCauldronContents.StoredItem item : contents.items()) {
                (item.slot() < 8 ? this.output : this.input).set(item.slot() % 8, ItemResource.of(item.stack()), item.stack().getCount());
            }
            this.fluidHandler.set(0, FluidResource.of(contents.fluid()), contents.fluid().getAmount());
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
        for (ItemStacksResourceHandler handler : List.of(this.output, this.input)) {
            for (int slot = 0; slot < handler.size(); slot++) {
                ItemStack extracted = IItemHandler.of(handler).extractItem(slot, Integer.MAX_VALUE, false);
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
    public ResourceHandler<ItemResource> getInput() {
        return CauldronImpactRecipeProcessor.canAccessRecipeInventory(this)
            ? this.recipeInput
            : this.emptyRecipeHandler;
    }

    @Override
    public ResourceHandler<ItemResource> getOutput() {
        return CauldronImpactRecipeProcessor.canAccessRecipeInventory(this)
            ? this.recipeOutput
            : this.emptyRecipeHandler;
    }

    @Override
    public ResourceHandler<ItemResource> getItemHandler() {
        return this.itemHandler;
    }

    @Override
    public ResourceHandler<FluidResource> getFluidHandler() {
        return this.fluidHandler;
    }

    @Override
    protected ResourceHandler<ItemResource> cauldronInputHandler() {
        return this.input;
    }

    @Override
    protected ResourceHandler<ItemResource> cauldronOutputHandler() {
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
        double fill = Math.clamp((double) this.fluidHandler.getAmountAsInt(0) / CAPACITY, 0.0D, 1.0D);
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
        return FluidUtil.getStack(this.fluidHandler, 0);
    }

    @Override
    public ResourceHandler<FluidResource> plasticraft$bottomFluidAccess() {
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

    public ResourceHandler<ItemResource> getSyncedItemHandler() {
        return this.level().isClientSide() ? this.syncedItems : this.itemHandler;
    }

    public List<ItemStack> getSyncedItems() {
        ResourceHandler<ItemResource> handler = this.getSyncedItemHandler();
        List<ItemStack> stacks = new ArrayList<>();
        for (int slot = 0; slot < handler.size(); slot++) {
            ItemStack stack = ItemUtil.getStack(handler, slot);
            if (!stack.isEmpty()) stacks.add(stack);
        }
        return stacks;
    }

    private void syncItemData() {
        if (this.level().isClientSide()) return;
        this.entityData.set(DISPLAY_ITEMS, this.createDisplayItemsTag(this.registryAccess()));
    }

    private CompoundTag createDisplayItemsTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        ListTag stacks = new ListTag();
        for (int slot = 0; slot < this.itemHandler.size(); slot++) {
            ItemStack stack = ItemUtil.getStack(this.itemHandler, slot);
            if (stack.isEmpty()) continue;
            CompoundTag entry = new CompoundTag();
            entry.putInt("Slot", slot);
            entry.store("Stack", ItemStack.CODEC, registries.createSerializationContext(NbtOps.INSTANCE), stack);
            stacks.add(entry);
        }
        tag.put("Stacks", stacks);
        return tag;
    }

    private void readSyncedItems(CompoundTag tag) {
        for (int slot = 0; slot < this.syncedItems.size(); slot++) {
            this.syncedItems.set(slot, ItemResource.EMPTY, 0);
        }
        ListTag stacks = tag.getListOrEmpty("Stacks");
        int count = Math.min(stacks.size(), this.syncedItems.size());
        for (int index = 0; index < count; index++) {
            CompoundTag entry = stacks.getCompoundOrEmpty(index);
            int slot = entry.getIntOr("Slot", 0);
            if (slot < 0 || slot >= this.syncedItems.size()) continue;
            ItemStack stack = entry.read("Stack", ItemStack.CODEC, this.registryAccess().createSerializationContext(NbtOps.INSTANCE)).orElse(ItemStack.EMPTY);
            this.syncedItems.set(slot, ItemResource.of(stack), stack.getCount());
        }
    }

    private void syncFluidData() {
        FluidStack fluid = FluidUtil.getStack(this.fluidHandler, 0);
        this.entityData.set(FLUID_ID, fluid.isEmpty() ? -1 : BuiltInRegistries.FLUID.getId(fluid.getFluid()));
        this.entityData.set(FLUID_AMOUNT, fluid.getAmount());
        this.entityData.set(FLUID_COLOR, PlasticMeltColor.get(fluid).getId());
        this.needsSync = true;
        this.hurtMarked = true;
        if (!this.restoringData) this.bondedDataDirty = true;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput tag) {
        super.addAdditionalSaveData(tag);
        tag.putChild("Fluid", this.fluidHandler);
        tag.putChild("Inputs", this.input);
        tag.putChild("Outputs", this.output);
        tag.putBoolean("Ignited", this.anvilcraft$isIgnited());
        Direction outletSide = this.getOutletLocalDirection();
        if (outletSide != null) tag.putString("OutletSide", outletSide.getName());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput tag) {
        this.restoringData = true;
        try {
            super.readAdditionalSaveData(tag);
            Direction outletSide = Direction.byName(tag.getStringOr("OutletSide", ""));
            this.setOutletLocalDirection(
                outletSide != null && outletSide.getAxis().isHorizontal() ? outletSide : null
            );
            HolderLookup.Provider registries = this.registryAccess();
            tag.readChild("Fluid", this.fluidHandler);
            tag.readChild("Inputs", this.input);
            tag.readChild("Outputs", this.output);
            this.anvilcraft$setIgnited(tag.getBooleanOr("Ignited", false) || tag.getBooleanOr("ignited", false));
            this.syncFluidData();
            if (this.level().isClientSide()) {
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
