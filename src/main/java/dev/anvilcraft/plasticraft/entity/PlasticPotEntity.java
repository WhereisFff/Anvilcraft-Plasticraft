package dev.anvilcraft.plasticraft.entity;

import dev.anvilcraft.lib.v2.recipe.cache.IItemHandlerCache;
import dev.anvilcraft.plasticraft.block.PlasticPotBlock;
import dev.anvilcraft.plasticraft.init.PlasticBlocks;
import dev.anvilcraft.plasticraft.item.PlasticItemData;
import dev.dubhe.anvilcraft.api.entity.IEntityCauldron;
import dev.dubhe.anvilcraft.api.fluid.FluidHandlerWrapper;
import dev.dubhe.anvilcraft.api.itemhandler.IItemHandlerHolder;
import dev.dubhe.anvilcraft.api.itemhandler.ItemHandlerUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Movable six-direction pot with Fish Tank-compatible item and fluid storage. */
public class PlasticPotEntity extends AbstractPlasticAnvilEntity
    implements IItemHandlerCache, IItemHandlerHolder, IEntityCauldron {
    public static final int CAPACITY = 1000;
    public static final float COLLISION_SIZE = 1.0F;

    private static final EntityDataAccessor<Integer> FLUID_ID = SynchedEntityData.defineId(
        PlasticPotEntity.class,
        EntityDataSerializers.INT
    );
    private static final EntityDataAccessor<Integer> FLUID_AMOUNT = SynchedEntityData.defineId(
        PlasticPotEntity.class,
        EntityDataSerializers.INT
    );
    private static final EntityDataAccessor<CompoundTag> DISPLAY_ITEMS = SynchedEntityData.defineId(
        PlasticPotEntity.class,
        EntityDataSerializers.COMPOUND_TAG
    );
    private static Supplier<ItemStack> defaultDropSupplier = () -> ItemStack.EMPTY;

    private final ItemStackHandler input = createHandler();
    private final ItemStackHandler output = createHandler();
    private final ItemStackHandler syncedItems = new ItemStackHandler(16);
    private final ItemStackHandler itemHandler = new ItemStackHandler(16) {
        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot < 8 ? PlasticPotEntity.this.output.getStackInSlot(slot)
                : PlasticPotEntity.this.input.getStackInSlot(slot - 8);
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot < 8 ? PlasticPotEntity.this.output.getSlotLimit(slot)
                : PlasticPotEntity.this.input.getSlotLimit(slot - 8);
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
                PlasticPotEntity.this.output.setStackInSlot(slot, stack);
            } else {
                PlasticPotEntity.this.input.setStackInSlot(slot - 8, stack);
            }
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot >= 8 && PlasticPotEntity.this.input.isItemValid(slot - 8, stack);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || !this.isItemValid(slot, stack)) return stack;
            return PlasticPotEntity.this.input.insertItem(slot - 8, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot < 8
                ? PlasticPotEntity.this.output.extractItem(slot, amount, simulate)
                : PlasticPotEntity.this.input.extractItem(slot - 8, amount, simulate);
        }
    };
    private final FluidTank fluidHandler = new FluidTank(CAPACITY) {
        @Override
        protected void onContentsChanged() {
            PlasticPotEntity.this.syncFluidData();
        }
    };

    public static void configureDefaultDrop(Supplier<ItemStack> supplier) {
        defaultDropSupplier = Objects.requireNonNull(supplier, "supplier");
    }

    public PlasticPotEntity(EntityType<? extends PlasticPotEntity> type, Level level) {
        super(type, level);
        this.setDisplayState(PlasticBlocks.PLASTIC_POT.get().defaultBlockState());
    }

    public PlasticPotEntity(
        EntityType<? extends PlasticPotEntity> type,
        Level level,
        Vec3 position,
        BlockState displayState,
        ItemStack dropStack,
        PlasticAnvilOrientation orientation
    ) {
        super(type, level, position, displayState, dropStack, orientation);
    }

    private ItemStackHandler createHandler() {
        return new ItemStackHandler(8) {
            @Override
            protected void onContentsChanged(int slot) {
                PlasticPotEntity.this.hasImpulse = true;
                PlasticPotEntity.this.syncItemData();
            }
        };
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FLUID_ID, -1).define(FLUID_AMOUNT, 0).define(DISPLAY_ITEMS, new CompoundTag());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DISPLAY_ITEMS.equals(key)) this.readSyncedItems(this.entityData.get(DISPLAY_ITEMS));
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(COLLISION_SIZE, COLLISION_SIZE);
    }

    @Override
    protected ItemStack createDefaultDropStack() {
        ItemStack stack = defaultDropSupplier.get();
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        stack = stack.copy();
        BlockState state = this.getDisplayState();
        if (state.hasProperty(PlasticPotBlock.COLOR)) {
            DyeColor color = state.getValue(PlasticPotBlock.COLOR);
            if (color != DyeColor.WHITE) PlasticItemData.setColor(stack, color);
        }
        return stack;
    }

    @Override
    protected void openAnvilMenu(ServerPlayer player) {
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isRemoved() || this.level().isClientSide) return;
        this.absorbTouchingItems();
        this.spillFluidIfNeeded();
    }

    private void absorbTouchingItems() {
        for (ItemEntity item : this.level().getEntitiesOfClass(
            ItemEntity.class,
            this.openingContactArea(),
            Entity::isAlive
        )) {
            if (!item.anvilcraft$isAdsorbable()) continue;
            ItemStack remaining = ItemHandlerUtil.insertItem(this.input, item.getItem().copy(), false);
            if (remaining.isEmpty()) {
                item.discard();
            } else {
                item.setItem(remaining);
            }
        }
    }

    /** Only the open face accepts loose item entities; the rim and closed sides do not. */
    private AABB openingContactArea() {
        AABB box = this.getBoundingBox();
        Direction opening = this.getOrientation().attachmentFace();
        double inset = 0.12D;
        double depth = 0.08D;
        return switch (opening) {
            case DOWN -> new AABB(
                box.minX + inset, box.minY - depth, box.minZ + inset,
                box.maxX - inset, box.minY + depth, box.maxZ - inset
            );
            case UP -> new AABB(
                box.minX + inset, box.maxY - depth, box.minZ + inset,
                box.maxX - inset, box.maxY + depth, box.maxZ - inset
            );
            case WEST -> new AABB(
                box.minX - depth, box.minY + inset, box.minZ + inset,
                box.minX + depth, box.maxY - inset, box.maxZ - inset
            );
            case EAST -> new AABB(
                box.maxX - depth, box.minY + inset, box.minZ + inset,
                box.maxX + depth, box.maxY - inset, box.maxZ - inset
            );
            case NORTH -> new AABB(
                box.minX + inset, box.minY + inset, box.minZ - depth,
                box.maxX - inset, box.maxY - inset, box.minZ + depth
            );
            case SOUTH -> new AABB(
                box.minX + inset, box.minY + inset, box.maxZ - depth,
                box.maxX - inset, box.maxY - inset, box.maxZ + depth
            );
        };
    }

    private void spillFluidIfNeeded() {
        if (this.isMagnetized() || this.getOrientation().attachmentFace() == Direction.UP) return;
        FluidStack fluid = this.fluidHandler.getFluid();
        if (fluid.getAmount() < CAPACITY) return;
        Direction outlet = this.getOrientation().attachmentFace();
        BlockPos target = BlockPos.containing(this.getBoundingBox().getCenter()).relative(outlet);
        BlockState targetState = this.level().getBlockState(target);
        BlockState fluidState = fluid.getFluid().defaultFluidState().createLegacyBlock();
        if (!targetState.canBeReplaced() && targetState.getFluidState().isEmpty()) return;
        if (this.level().setBlock(target, fluidState, Block.UPDATE_ALL)) {
            this.fluidHandler.drain(CAPACITY, IFluidHandler.FluidAction.EXECUTE);
        }
    }

    @Override
    protected void onEntityImpact(Entity support, Direction impactDirection, float fallDistance) {
        if (!(this.level() instanceof ServerLevel level)) return;
        if (!(support instanceof PlasticAnvilEntity anvil)) return;
        if (!canProcessAnvilImpact(anvil, this, impactDirection)) return;
        PlasticRecipeProcessor.process(level, anvil, this);
    }

    public void processAnvilImpact(PlasticAnvilEntity anvil, Direction impactDirection) {
        if (!(this.level() instanceof ServerLevel level)) return;
        if (!canProcessAnvilImpact(anvil, this, impactDirection)) return;
        PlasticRecipeProcessor.process(level, anvil, this);
    }

    /** The recipe surface is the anvil's bottom against the pot's local-up opening. */
    private static boolean canProcessAnvilImpact(
        PlasticAnvilEntity anvil,
        PlasticPotEntity pot,
        Direction impactDirection
    ) {
        Direction anvilBottom = anvil.getOrientation().attachmentFace().getOpposite();
        Direction potTop = pot.getOrientation().attachmentFace();
        return anvilBottom == impactDirection && potTop == impactDirection.getOpposite()
            || potTop == impactDirection && anvilBottom == impactDirection.getOpposite();
    }

    @Override
    protected InteractionResult interactNormally(Player player, InteractionHand hand) {
        ItemStack inHand = player.getItemInHand(hand);
        if (this.tryFluidInteraction(player, hand)) return InteractionResult.sidedSuccess(this.level().isClientSide);
        if (inHand.isEmpty()) {
            if (this.level().isClientSide) return InteractionResult.SUCCESS;
            List<ItemStack> extracted = this.extractAllStacks();
            if (extracted.isEmpty()) return InteractionResult.PASS;
            for (ItemStack stack : extracted) {
                player.getInventory().placeItemBackInInventory(stack);
            }
            return InteractionResult.CONSUME;
        }
        if (this.level().isClientSide) return InteractionResult.SUCCESS;
        ItemStack remaining = ItemHandlerUtil.insertItem(this.input, inHand.copy(), false);
        int inserted = inHand.getCount() - remaining.getCount();
        if (inserted <= 0) return InteractionResult.PASS;
        inHand.shrink(inserted);
        return InteractionResult.CONSUME;
    }

    private boolean tryFluidInteraction(Player player, InteractionHand hand) {
        if (FluidHandlerWrapper.tryInteractWithBottle(player, hand, this.fluidHandler, this.level(), this.blockPosition())) {
            if (!this.level().isClientSide) this.spillFluidIfNeeded();
            return true;
        }
        ItemStack stack = player.getItemInHand(hand);
        boolean filling = FluidUtil.getFluidHandler(stack)
            .map(handler -> !handler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE).isEmpty())
            .orElse(false);
        boolean interacted = FluidUtil.interactWithFluidHandler(player, hand, this.fluidHandler);
        if (interacted && !this.level().isClientSide) {
            player.awardStat(filling ? Stats.FILL_CAULDRON : Stats.USE_CAULDRON);
            player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
            this.spillFluidIfNeeded();
        }
        return interacted;
    }

    private List<ItemStack> extractAllStacks() {
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
    public ItemStackHandler getInput() {
        return this.input;
    }

    @Override
    public ItemStackHandler getOutput() {
        return this.output;
    }

    @Override
    public ItemStackHandler getItemHandler() {
        return this.itemHandler;
    }

    @Override
    public FluidTank getFluidHandler() {
        return this.fluidHandler;
    }

    public FluidStack getSyncedFluid() {
        int id = this.entityData.get(FLUID_ID);
        int amount = this.entityData.get(FLUID_AMOUNT);
        Fluid fluid = id < 0 ? Fluids.EMPTY : BuiltInRegistries.FLUID.byId(id);
        return fluid == null || fluid == Fluids.EMPTY || amount <= 0
            ? FluidStack.EMPTY
            : new FluidStack(fluid, amount);
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
        CompoundTag tag = new CompoundTag();
        ListTag stacks = new ListTag();
        for (int slot = 0; slot < this.itemHandler.getSlots(); slot++) {
            ItemStack stack = this.itemHandler.getStackInSlot(slot);
            if (!stack.isEmpty()) stacks.add(stack.save(this.registryAccess()));
        }
        tag.put("Stacks", stacks);
        this.entityData.set(DISPLAY_ITEMS, tag);
    }

    private void readSyncedItems(CompoundTag tag) {
        for (int slot = 0; slot < this.syncedItems.getSlots(); slot++) {
            this.syncedItems.setStackInSlot(slot, ItemStack.EMPTY);
        }
        ListTag stacks = tag.getList("Stacks", Tag.TAG_COMPOUND);
        int count = Math.min(stacks.size(), this.syncedItems.getSlots());
        for (int slot = 0; slot < count; slot++) {
            this.syncedItems.setStackInSlot(
                slot,
                ItemStack.parseOptional(this.registryAccess(), stacks.getCompound(slot))
            );
        }
    }

    private void syncFluidData() {
        FluidStack fluid = this.fluidHandler.getFluid();
        this.entityData.set(FLUID_ID, fluid.isEmpty() ? -1 : BuiltInRegistries.FLUID.getId(fluid.getFluid()));
        this.entityData.set(FLUID_AMOUNT, fluid.getAmount());
        this.hasImpulse = true;
        this.hurtMarked = true;
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) return false;
        if (!this.level().isClientSide && !this.isRemoved()
            && this.level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
            for (ItemStackHandler handler : new ItemStackHandler[]{this.output, this.input}) {
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    ItemStack stack = handler.extractItem(slot, Integer.MAX_VALUE, false);
                    if (!stack.isEmpty()) this.spawnAtLocation(stack);
                }
            }
        }
        return super.hurt(source, amount);
    }

    @Override
    protected boolean triggersAnvilCraftLandingEvents(Direction impactDirection) {
        // The pot is a recipe container, never the falling anvil that owns the
        // world-down AnvilCraft landing event.
        return false;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        HolderLookup.Provider registries = this.registryAccess();
        tag.put("Fluid", this.fluidHandler.writeToNBT(registries, new CompoundTag()));
        tag.put("Inputs", this.input.serializeNBT(registries));
        tag.put("Outputs", this.output.serializeNBT(registries));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        HolderLookup.Provider registries = this.registryAccess();
        this.fluidHandler.readFromNBT(registries, tag.getCompound("Fluid"));
        this.input.deserializeNBT(registries, tag.getCompound("Inputs"));
        this.output.deserializeNBT(registries, tag.getCompound("Outputs"));
        this.syncFluidData();
        this.syncItemData();
    }
}
