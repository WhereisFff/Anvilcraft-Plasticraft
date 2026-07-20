package dev.anvilcraft.plasticraft.entity;

import dev.anvilcraft.lib.v2.recipe.cache.IItemHandlerCache;
import dev.anvilcraft.plasticraft.entity.physics.PlasticEntityPhysics;
import dev.anvilcraft.plasticraft.init.block.ModBlocks;
import dev.anvilcraft.plasticraft.item.PlasticItemData;
import dev.anvilcraft.plasticraft.recipe.CauldronImpactRecipeProcessor;
import dev.dubhe.anvilcraft.api.entity.IEntityCauldron;
import dev.dubhe.anvilcraft.api.fluid.network.FluidNetworkManager;
import dev.dubhe.anvilcraft.api.itemhandler.IItemHandlerHolder;
import dev.dubhe.anvilcraft.api.itemhandler.ItemHandlerUtil;
import dev.dubhe.anvilcraft.api.itemhandler.PollableItemHandler;
import dev.dubhe.anvilcraft.item.AnvilHammerItem;
import dev.dubhe.anvilcraft.util.AnvilUtil;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import javax.annotation.Nullable;

/** 带有兼容鱼缸的物品和流体存储能力的可移动六向釜。 */
public class HardenedResinCauldronEntity extends AbstractPlasticEntity
    implements IItemHandlerCache, IItemHandlerHolder, IEntityCauldron {
    public static final int CAPACITY = 1000;
    public static final float COLLISION_SIZE = 1.0F;
    private static final EntityDimensions EJECTED_ITEM_DIMENSIONS = EntityDimensions.scalable(0.25F, 0.25F);
    private static final double ITEM_EJECTION_GAP = 0.02D;
    private static final double ITEM_EJECTION_INSET = 0.15D;
    private static final int ITEM_EJECTION_SEARCH_STEPS = 4;

    private static final EntityDataAccessor<Integer> FLUID_ID = SynchedEntityData.defineId(
        HardenedResinCauldronEntity.class,
        EntityDataSerializers.INT
    );
    private static final EntityDataAccessor<Integer> FLUID_AMOUNT = SynchedEntityData.defineId(
        HardenedResinCauldronEntity.class,
        EntityDataSerializers.INT
    );
    private static final EntityDataAccessor<CompoundTag> DISPLAY_ITEMS = SynchedEntityData.defineId(
        HardenedResinCauldronEntity.class,
        EntityDataSerializers.COMPOUND_TAG
    );
    private static final EntityDataAccessor<Integer> OUTLET_SIDE = SynchedEntityData.defineId(
        HardenedResinCauldronEntity.class,
        EntityDataSerializers.INT
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
    private BlockPos fluidNetworkPos;
    private AbstractPlasticEntity activeRecipeContact;
    private boolean processingOutput;
    private boolean autoOutputting;
    private boolean restoringData;
    private long lastRecipeProcessingGameTime = Long.MIN_VALUE;

    public static void configureDefaultDrop(Supplier<ItemStack> supplier) {
        defaultDropSupplier = Objects.requireNonNull(supplier, "supplier");
    }

    public HardenedResinCauldronEntity(EntityType<? extends HardenedResinCauldronEntity> type, Level level) {
        super(type, level);
        this.setDisplayState(ModBlocks.HARDEND_RESIN_CAULDRON.get().defaultBlockState());
    }

    public HardenedResinCauldronEntity(
        EntityType<? extends HardenedResinCauldronEntity> type,
        Level level,
        Vec3 position,
        BlockState displayState,
        ItemStack dropStack,
        PlasticEntityOrientation orientation
    ) {
        super(type, level, position, displayState, dropStack, orientation);
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
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FLUID_ID, -1)
            .define(FLUID_AMOUNT, 0)
            .define(DISPLAY_ITEMS, new CompoundTag())
            .define(OUTLET_SIDE, -1);
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
        PlasticItemData.setMaterial(stack, "hardened_resin");
        return stack;
    }

    @Override
    protected boolean supportsHammerRotation() {
        return true;
    }

    @Override
    protected void openAnvilMenu(ServerPlayer player) {
    }

    public boolean hasOutlet() {
        return this.getOutletLocalDirection() != null;
    }

    public @Nullable Direction getOutletLocalDirection() {
        int directionId = this.entityData.get(OUTLET_SIDE);
        if (directionId < 0 || directionId >= Direction.values().length) return null;
        Direction direction = Direction.from3DDataValue(directionId);
        return direction.getAxis().isHorizontal() ? direction : null;
    }

    public @Nullable Direction getOutletDirection() {
        Direction localDirection = this.getOutletLocalDirection();
        if (localDirection == null) return null;
        PlasticEntityOrientation orientation = this.getOrientation();
        return switch (localDirection) {
            case NORTH -> orientation.longAxis().getOpposite();
            case SOUTH -> orientation.longAxis();
            case WEST -> orientation.orthogonalAxis().getOpposite();
            case EAST -> orientation.orthogonalAxis();
            default -> null;
        };
    }

    private void setOutletLocalDirection(@Nullable Direction direction) {
        int directionId = direction == null ? -1 : direction.get3DDataValue();
        this.entityData.set(OUTLET_SIDE, directionId);
        this.hasImpulse = true;
        this.hurtMarked = true;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isRemoved() || this.level().isClientSide) return;
        this.refreshRecipeContact();
        this.updateFluidNetworkRegistration();
        if (!this.ejectItemsIfNeeded()) this.absorbTouchingItems();
        this.spillFluidIfNeeded();
    }

    private void updateFluidNetworkRegistration() {
        BlockPos currentPos = BlockPos.containing(this.getBoundingBox().getCenter());
        if (currentPos.equals(this.fluidNetworkPos)) return;
        if (this.fluidNetworkPos != null) {
            FluidNetworkManager.INSTANCE.removeContainer(this.level(), this.fluidNetworkPos);
        }
        this.fluidNetworkPos = currentPos.immutable();
        FluidNetworkManager.INSTANCE.addContainer(this.level(), this.fluidNetworkPos);
    }

    private void unregisterFromFluidNetwork() {
        if (this.fluidNetworkPos == null || this.level().isClientSide) return;
        FluidNetworkManager.INSTANCE.removeContainer(this.level(), this.fluidNetworkPos);
        this.fluidNetworkPos = null;
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        this.unregisterFromFluidNetwork();
        super.remove(reason);
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

    public boolean shouldUseGravityAlignedItemLayout() {
        if (this.isMagnetized()) return false;
        Direction opening = this.getOrientation().attachmentFace();
        return opening.getAxis().isHorizontal()
            || opening == Direction.DOWN && this.findItemEjectionPosition().isEmpty();
    }

    public boolean shouldEjectStoredItems() {
        return this.hasUnsealedDownwardOpening() && this.findItemEjectionPosition().isPresent();
    }

    private boolean ejectItemsIfNeeded() {
        if (!this.hasUnsealedDownwardOpening()) return false;
        Optional<Vec3> ejectionPosition = this.findItemEjectionPosition();
        if (ejectionPosition.isEmpty()) return true;
        Vec3 position = ejectionPosition.get();
        for (ItemStack stack : this.extractAllStacks()) {
            ItemEntity item = new ItemEntity(
                this.level(),
                position.x,
                position.y,
                position.z,
                stack
            );
            item.setDeltaMovement(0.0D, -0.08D, 0.0D);
            item.setDefaultPickUpDelay();
            this.level().addFreshEntity(item);
        }
        // 倒置时始终跳过吸入；开口受阻时保留库存，畅通时避免重新收回刚掉出的物品。
        return true;
    }

    private boolean hasUnsealedDownwardOpening() {
        return !this.isMagnetized() && this.getOrientation().attachmentFace() == Direction.DOWN;
    }

    private Optional<Vec3> findItemEjectionPosition() {
        BlockPos target = this.openingTargetPosition();
        if (this.isOpeningBlocked(target)) return Optional.empty();

        AABB box = this.getBoundingBox();
        Vec3 center = box.getCenter();
        double y = box.minY - EJECTED_ITEM_DIMENSIONS.height() - ITEM_EJECTION_GAP;
        double minX = box.minX + ITEM_EJECTION_INSET;
        double maxX = box.maxX - ITEM_EJECTION_INSET;
        double minZ = box.minZ + ITEM_EJECTION_INSET;
        double maxZ = box.maxZ - ITEM_EJECTION_INSET;
        double preferredX = clamp(target.getX() + 0.5D, minX, maxX);
        double preferredZ = clamp(target.getZ() + 0.5D, minZ, maxZ);

        // 优先选择靠近锅中心的位置，再沿未阻挡方块方向逐步外移。
        for (int step = 0; step <= ITEM_EJECTION_SEARCH_STEPS; step++) {
            double progress = (double) step / ITEM_EJECTION_SEARCH_STEPS;
            Vec3 candidate = new Vec3(
                center.x + (preferredX - center.x) * progress,
                y,
                center.z + (preferredZ - center.z) * progress
            );
            if (this.isItemEjectionPositionFree(candidate)) return Optional.of(candidate);
        }

        double[] xCandidates = {minX, (minX + center.x) * 0.5D, center.x, (center.x + maxX) * 0.5D, maxX};
        double[] zCandidates = {minZ, (minZ + center.z) * 0.5D, center.z, (center.z + maxZ) * 0.5D, maxZ};
        for (double x : xCandidates) {
            for (double z : zCandidates) {
                Vec3 candidate = new Vec3(x, y, z);
                if (this.isItemEjectionPositionFree(candidate)) return Optional.of(candidate);
            }
        }

        // 极端贴边时允许从液体所选空闲方块的中心掉出，避免实体生成在相邻实体方块内。
        Vec3 targetCenter = new Vec3(target.getX() + 0.5D, y, target.getZ() + 0.5D);
        return this.isItemEjectionPositionFree(targetCenter) ? Optional.of(targetCenter) : Optional.empty();
    }

    private boolean isItemEjectionPositionFree(Vec3 position) {
        AABB itemBox = EJECTED_ITEM_DIMENSIONS.makeBoundingBox(position).inflate(0.01D);
        return !this.level().getBlockCollisions(null, itemBox).iterator().hasNext();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /** 只有开口面接收散落物品实体，边沿和封闭侧面均不接收。 */
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
        BlockPos target = this.openingTargetPosition();
        BlockState fluidState = fluid.getFluid().defaultFluidState().createLegacyBlock();
        if (this.isOpeningBlocked(target)) return;
        if (this.level().setBlock(target, fluidState, Block.UPDATE_ALL)) {
            this.fluidHandler.drain(CAPACITY, IFluidHandler.FluidAction.EXECUTE);
        }
    }

    private BlockPos openingTargetPosition() {
        Direction opening = this.getOrientation().attachmentFace();
        return BlockPos.containing(this.getBoundingBox().getCenter()).relative(opening);
    }

    private boolean isOpeningBlocked(BlockPos target) {
        BlockState targetState = this.level().getBlockState(target);
        return !targetState.canBeReplaced() && targetState.getFluidState().isEmpty();
    }

    @Override
    protected void onEntityImpact(Entity support, Direction impactDirection, float fallDistance) {
        if (!(this.level() instanceof ServerLevel level)) return;
        if (!(support instanceof AbstractPlasticEntity anvil)) return;
        if (!this.tryBeginAnvilImpact(anvil, impactDirection)) return;
        CauldronImpactRecipeProcessor.process(level, anvil, this);
    }

    public void processAnvilImpact(AbstractPlasticEntity anvil, Direction impactDirection) {
        if (!(this.level() instanceof ServerLevel level)) return;
        if (!this.tryBeginAnvilImpact(anvil, impactDirection)) return;
        CauldronImpactRecipeProcessor.process(level, anvil, this);
    }

    private boolean tryBeginAnvilImpact(AbstractPlasticEntity anvil, Direction impactDirection) {
        if (!canProcessAnvilImpact(anvil, this, impactDirection)) return false;
        if (!this.hasRecipeSurfaceContact(anvil)) return true;
        if (this.activeRecipeContact == anvil) return false;
        this.activeRecipeContact = anvil;
        return true;
    }

    private void refreshRecipeContact() {
        if (this.activeRecipeContact != null && !this.hasRecipeSurfaceContact(this.activeRecipeContact)) {
            this.activeRecipeContact = null;
        }
    }

    private boolean hasRecipeSurfaceContact(AbstractPlasticEntity anvil) {
        Direction potTop = this.getOrientation().attachmentFace();
        Direction anvilBottom = anvil.getOrientation().attachmentFace().getOpposite();
        return anvilBottom == potTop.getOpposite()
            && PlasticEntityPhysics.isSupportCandidate(this, anvil, potTop);
    }

    /** 配方接触面为砧的底面与釜局部向上的开口面。 */
    private static boolean canProcessAnvilImpact(
        AbstractPlasticEntity anvil,
        HardenedResinCauldronEntity pot,
        Direction impactDirection
    ) {
        Direction anvilBottom = anvil.getOrientation().attachmentFace().getOpposite();
        Direction potTop = pot.getOrientation().attachmentFace();
        return anvilBottom == impactDirection && potTop == impactDirection.getOpposite()
            || potTop == impactDirection && anvilBottom == impactDirection.getOpposite();
    }

    @Override
    protected InteractionResult interactWithAnvilHammer(
        Player player,
        InteractionHand hand,
        Direction interactionFace
    ) {
        Direction localSide = this.resolveOutletLocalSide(interactionFace, player);
        BlockPos occupiedPos = BlockPos.containing(this.getBoundingBox().getCenter());
        if (localSide == null
            || !player.getAbilities().mayBuild
            || !this.level().mayInteract(player, occupiedPos)) {
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        if (!this.level().isClientSide) {
            Direction current = this.getOutletLocalDirection();
            Direction changed = current == localSide ? null : localSide;
            this.setOutletLocalDirection(changed);
            if (changed != null) this.tryAutoOutputResults();
            this.level().playSound(
                null,
                this.blockPosition(),
                SoundEvents.SMITHING_TABLE_USE,
                SoundSource.BLOCKS,
                1.0F,
                1.0F
            );
            this.gameEvent(GameEvent.ENTITY_INTERACT, player);
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    private @Nullable Direction resolveOutletLocalSide(Direction interactionFace, Player player) {
        PlasticEntityOrientation orientation = this.getOrientation();
        Direction attachmentFace = orientation.attachmentFace();
        if (interactionFace == attachmentFace.getOpposite()) return null;

        Direction outletDirection = interactionFace == attachmentFace
            ? this.openingSideDirection(player)
            : interactionFace;
        if (outletDirection == orientation.longAxis()) return Direction.SOUTH;
        if (outletDirection == orientation.longAxis().getOpposite()) return Direction.NORTH;
        if (outletDirection == orientation.orthogonalAxis()) return Direction.EAST;
        if (outletDirection == orientation.orthogonalAxis().getOpposite()) return Direction.WEST;
        return null;
    }

    private Direction openingSideDirection(Player player) {
        PlasticEntityOrientation orientation = this.getOrientation();
        Direction playerDirection = player.getDirection();
        // 鱼缸从顶部开口取玩家水平朝向；对墙面附着的锅，水平朝向可能与开口法线平行，
        // 此时才退回视线在附着平面内的最近侧面。
        if (playerDirection.getAxis() != orientation.attachmentFace().getAxis()) {
            return playerDirection;
        }
        return this.closestSideDirection(player.getLookAngle());
    }

    private Direction closestSideDirection(Vec3 lookDirection) {
        PlasticEntityOrientation orientation = this.getOrientation();
        Direction[] sides = {
            orientation.longAxis(),
            orientation.longAxis().getOpposite(),
            orientation.orthogonalAxis(),
            orientation.orthogonalAxis().getOpposite()
        };
        Direction closest = sides[0];
        double closestDot = -Double.MAX_VALUE;
        for (Direction side : sides) {
            double dot = lookDirection.dot(Vec3.atLowerCornerOf(side.getNormal()));
            if (dot > closestDot) {
                closest = side;
                closestDot = dot;
            }
        }
        return closest;
    }

    @Override
    protected InteractionResult interactNormally(Player player, InteractionHand hand) {
        ItemStack inHand = player.getItemInHand(hand);
        if (inHand.getItem() instanceof AnvilHammerItem) {
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
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

    public void tryAutoOutputResults() {
        Direction outletDirection = this.getOutletDirection();
        if (outletDirection == null || this.level().isClientSide || this.autoOutputting) return;

        BlockPos occupiedPos = BlockPos.containing(this.getBoundingBox().getCenter());
        List<IItemHandler> targets = ItemHandlerUtil.getTargetItemHandlerList(
            occupiedPos.relative(outletDirection),
            null,
            this.level()
        );
        this.autoOutputting = true;
        try {
            if (targets == null || targets.isEmpty()) {
                if (this.isOutletBlocked(outletDirection)) return;
                for (int slot = 0; slot < this.output.getSlots(); slot++) {
                    ItemStack stack = this.output.extractItem(slot, Integer.MAX_VALUE, false);
                    if (!stack.isEmpty()) this.popResourceFromOutlet(outletDirection, stack);
                }
                return;
            }

            for (IItemHandler target : targets) {
                for (int slot = 0; slot < this.output.getSlots(); slot++) {
                    ItemStack extracted = this.output.extractItem(slot, Integer.MAX_VALUE, true);
                    if (extracted.isEmpty()) continue;
                    ItemStack remaining = ItemHandlerUtil.insertItem(target, extracted, true);
                    if (remaining.getCount() == extracted.getCount()) continue;
                    remaining = ItemHandlerUtil.insertItem(
                        target,
                        this.output.extractItem(slot, Integer.MAX_VALUE, false),
                        false
                    );
                    if (!remaining.isEmpty()) ItemHandlerUtil.insertItem(this.output, remaining, false);
                }
            }
        } finally {
            this.autoOutputting = false;
            this.contentsChanged();
        }
    }

    private boolean isOutletBlocked(Direction outletDirection) {
        BlockPos occupiedPos = BlockPos.containing(this.getBoundingBox().getCenter());
        return AnvilUtil.isOutletBlocked(
            this.level(),
            occupiedPos.relative(outletDirection),
            this.outletCenter(outletDirection),
            outletDirection
        );
    }

    private void popResourceFromOutlet(Direction outletDirection, ItemStack stack) {
        Vec3 position = this.outletCenter(outletDirection)
            .add(Vec3.atLowerCornerOf(outletDirection.getNormal()).scale(0.25D));
        Vec3 movement = Vec3.atLowerCornerOf(outletDirection.getNormal()).scale(0.1D);
        ItemEntity item = new ItemEntity(
            this.level(),
            position.x,
            position.y,
            position.z,
            stack,
            movement.x,
            movement.y,
            movement.z
        );
        item.anvilcraft$setIsAdsorbable(true);
        this.level().addFreshEntity(item);
    }

    private Vec3 outletCenter(Direction outletDirection) {
        AABB box = this.getBoundingBox();
        Vec3 center = box.getCenter();
        double faceDistance = switch (outletDirection.getAxis()) {
            case X -> box.getXsize() * 0.5D;
            case Y -> box.getYsize() * 0.5D;
            case Z -> box.getZsize() * 0.5D;
        };
        Vec3 faceOffset = Vec3.atLowerCornerOf(outletDirection.getNormal()).scale(faceDistance);
        Vec3 heightOffset = Vec3.atLowerCornerOf(
            this.getOrientation().attachmentFace().getNormal()
        ).scale(-1.0D / 16.0D);
        return center.add(faceOffset).add(heightOffset);
    }

    private boolean tryFluidInteraction(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean interacted = FluidUtil.interactWithFluidHandler(player, hand, this.fluidHandler);
        if (interacted && !this.level().isClientSide) {
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
    protected void prepareAnvilHammerPickup(Player player) {
        for (ItemStack stack : this.extractAllStacks()) {
            player.getInventory().placeItemBackInInventory(stack);
        }
    }

    @Override
    public ItemStackHandler getInput() {
        return this.recipeInput;
    }

    @Override
    public ItemStackHandler getOutput() {
        return this.recipeOutput;
    }

    public void beginRecipeProcessing() {
        boolean hasInput = !isEmpty(this.input);
        long gameTime = this.level().getGameTime();
        this.processingOutput = !hasInput
            && !isEmpty(this.output)
            && gameTime != this.lastRecipeProcessingGameTime;
        if (hasInput || this.processingOutput) this.lastRecipeProcessingGameTime = gameTime;
    }

    public void finishRecipeProcessing() {
        this.processingOutput = false;
    }

    public ItemStack insertRecipeOutput(ItemStack stack) {
        return ItemHandlerUtil.insertItem(this.output, stack, false);
    }

    private static boolean isEmpty(ItemStackHandler handler) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (!handler.getStackInSlot(slot).isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStackHandler getItemHandler() {
        return this.itemHandler;
    }

    @Override
    public FluidTank getFluidHandler() {
        return this.fluidHandler;
    }

    // 对应的 AnvilCraft API 存在于本地相邻构建中，但并非每个已发布的 1.6.0 快照都包含它。
    // 当该 API 可用时，此签名仍会正确覆写它。
    public boolean anvilcraft$usesWholeCauldronFluidTransfers() {
        return false;
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
        if (source.getEntity() instanceof Player player
            && source.getDirectEntity() == player
            && player.getMainHandItem().getItem() instanceof AnvilHammerItem) {
            if (!this.level().isClientSide) {
                this.beginRecipeProcessing();
                try {
                    AnvilHammerItem.dropAnvil(
                        player,
                        this.level(),
                        CauldronImpactRecipeProcessor.recipePotCell(this)
                    );
                } finally {
                    this.finishRecipeProcessing();
                }
            }
            // 不把锤击视作有效伤害，避免锅和库存掉落，也避免锤子的攻击逻辑再次消耗耐久。
            return false;
        }
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
        // 釜是配方容器，绝不是负责触发 AnvilCraft 世界向下落地事件的下落砧。
        return false;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        HolderLookup.Provider registries = this.registryAccess();
        tag.put("Fluid", this.fluidHandler.writeToNBT(registries, new CompoundTag()));
        tag.put("Inputs", this.input.serializeNBT(registries));
        tag.put("Outputs", this.output.serializeNBT(registries));
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
            this.syncFluidData();
            this.syncItemData();
        } finally {
            this.restoringData = false;
        }
    }
}
