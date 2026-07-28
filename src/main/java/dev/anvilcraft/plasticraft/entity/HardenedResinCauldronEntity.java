package dev.anvilcraft.plasticraft.entity;

import dev.anvilcraft.lib.v2.recipe.cache.IItemHandlerCache;
import dev.anvilcraft.plasticraft.block.HardenedResinCauldronBlock;
import dev.anvilcraft.plasticraft.block.IgnitedFluidEffects;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.block.entity.UniversalPlasticMeltBlockEntity;
import dev.anvilcraft.plasticraft.entity.collision.PlasticEntityCollisionShapes;
import dev.anvilcraft.plasticraft.entity.physics.PlasticEntityPhysics;
import dev.anvilcraft.plasticraft.init.block.ModBlocks;
import dev.anvilcraft.plasticraft.init.block.ModFluids;
import dev.anvilcraft.plasticraft.item.PlasticItemData;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.anvilcraft.plasticraft.item.ResinAnvilHammerItem;
import dev.anvilcraft.plasticraft.recipe.CauldronImpactRecipeProcessor;
import dev.anvilcraft.plasticraft.recipe.PlasticOilCatalysis;
import dev.dubhe.anvilcraft.api.entity.IEntityCauldron;
import dev.dubhe.anvilcraft.api.fluid.network.FluidNetworkManager;
import dev.dubhe.anvilcraft.api.itemhandler.IItemHandlerHolder;
import dev.dubhe.anvilcraft.api.itemhandler.ItemHandlerUtil;
import dev.dubhe.anvilcraft.api.itemhandler.PollableItemHandler;
import dev.dubhe.anvilcraft.block.HeaterBlock;
import dev.dubhe.anvilcraft.block.PlasmaJetsBlock;
import dev.dubhe.anvilcraft.block.entity.LargeCauldronBlockEntity;
import dev.dubhe.anvilcraft.init.block.ModFluidTags;
import dev.dubhe.anvilcraft.init.item.ModItemTags;
import dev.dubhe.anvilcraft.item.AnvilHammerItem;
import dev.dubhe.anvilcraft.util.AnvilUtil;
import javax.annotation.Nullable;
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
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static dev.dubhe.anvilcraft.init.block.ModBlocks.HEATER;

/** 带有兼容鱼缸的物品和流体存储能力的可移动六向釜。 */
public class HardenedResinCauldronEntity extends AbstractPlasticEntity
    implements IItemHandlerCache, IItemHandlerHolder, IEntityCauldron {
    public static final int CAPACITY = 1000;
    public static final float COLLISION_SIZE = 1.0F;
    private static final double FLUID_INNER_INSET = 0.126D;
    private static final double FLUID_BOTTOM = 0.251D;
    private static final double FLUID_HEIGHT = 0.685D;
    private static final double SWEPT_RECIPE_CONTACT_RELEASE_DISTANCE = 1.0D;
    private static final Vec3 UNIVERSAL_MELT_STICK_SPEED = new Vec3(0.25D, 0.05D, 0.25D);
    private static final EntityDimensions EJECTED_ITEM_DIMENSIONS = EntityDimensions.scalable(0.25F, 0.25F);
    private static final double ITEM_EJECTION_GAP = 0.02D;
    private static final double ITEM_EJECTION_INSET = 0.15D;
    private static final int ITEM_EJECTION_SEARCH_STEPS = 4;
    private static final VoxelShape RESIN_ENTRY_OPENING = Block.box(
        2.0D, 0.0D, 2.0D, 14.0D, 16.0D, 14.0D
    );
    private static final VoxelShape RESIN_ENTRY_COLLISION = Shapes.join(
        HardenedResinCauldronBlock.COLLISION_SHAPE,
        Block.box(0.0D, 0.0D, 0.0D, 16.0D, 4.0D, 16.0D),
        BooleanOp.AND
    ).optimize();
    private static final Map<PlasticEntityOrientation, VoxelShape> RESIN_ENTRY_OPENINGS =
        new ConcurrentHashMap<>();
    private static final Map<PlasticEntityOrientation, VoxelShape> RESIN_ENTRY_COLLISIONS =
        new ConcurrentHashMap<>();

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
    private static final EntityDataAccessor<Integer> OUTLET_SIDE = SynchedEntityData.defineId(
        HardenedResinCauldronEntity.class,
        EntityDataSerializers.INT
    );
    private static final EntityDataAccessor<Boolean> IGNITED = SynchedEntityData.defineId(
        HardenedResinCauldronEntity.class,
        EntityDataSerializers.BOOLEAN
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
            HardenedResinCauldronEntity.this.burnIfFilledWithLava();
        }
    };
    private BlockPos fluidNetworkPos;
    private AbstractPlasticEntity activeRecipeContact;
    private Entity lastRecipeImpactSource;
    private boolean activeRecipeContactFromSweep;
    private boolean processingOutput;
    private boolean autoOutputting;
    private boolean restoringData;
    private boolean refreshingIgnited;
    private boolean bondedDataDirty;
    private boolean burnedByLava;
    private boolean leftLavaSource;
    private long lastRecipeImpactGameTime = Long.MIN_VALUE;
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
        if (!this.restoringData) this.bondedDataDirty = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FLUID_ID, -1)
            .define(FLUID_AMOUNT, 0)
            .define(FLUID_COLOR, DyeColor.WHITE.getId())
            .define(DISPLAY_ITEMS, new CompoundTag())
            .define(OUTLET_SIDE, -1)
            .define(IGNITED, false);
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
    protected VoxelShape getLocalCollisionShape() {
        return HardenedResinCauldronBlock.COLLISION_SHAPE;
    }

    @Override
    public VoxelShape plasticraft$getCollisionShape(Entity mover, Vec3 requestedMovement) {
        if (!(mover instanceof ResinAnvilEntity anvil)
            || !this.allowsResinAnvilToCrossOpening(anvil, requestedMovement)) {
            return this.plasticraft$getCollisionShape();
        }
        VoxelShape relative = RESIN_ENTRY_COLLISIONS.computeIfAbsent(
            this.getOrientation(),
            orientation -> PlasticEntityCollisionShapes.rotate(RESIN_ENTRY_COLLISION, orientation)
        );
        return relative.move(this.getX() - 0.5D, this.getY(), this.getZ() - 0.5D);
    }

    /** 仅在树脂砧底座从开口外跨入时移除锅壁；锅底始终保留。 */
    private boolean allowsResinAnvilToCrossOpening(ResinAnvilEntity anvil, Vec3 requestedMovement) {
        Direction anvilBottom = anvil.getOrientation().attachmentFace().getOpposite();
        Direction openingDirection = this.getOrientation().attachmentFace();
        if (anvilBottom != openingDirection.getOpposite()) return false;
        Vec3 bottomNormal = Vec3.atLowerCornerOf(anvilBottom.getNormal());
        if (requestedMovement.dot(bottomNormal) <= PlasticEntityPhysics.FACE_EPSILON) return false;

        AABB anvilBounds = anvil.plasticraft$getCollisionBox().bounds();
        AABB potBounds = this.plasticraft$getCollisionBox().bounds();
        double openingGap = (
            faceCoordinate(potBounds, openingDirection) - faceCoordinate(anvilBounds, anvilBottom)
        ) * anvilBottom.getAxisDirection().getStep();
        if (openingGap < -PlasticEntityPhysics.FACE_EPSILON) return false;

        VoxelShape relativeOpening = RESIN_ENTRY_OPENINGS.computeIfAbsent(
            this.getOrientation(),
            orientation -> PlasticEntityCollisionShapes.rotate(RESIN_ENTRY_OPENING, orientation)
        );
        AABB opening = relativeOpening
            .move(this.getX() - 0.5D, this.getY(), this.getZ() - 0.5D)
            .bounds();
        boolean foundLeadingComponent = false;
        for (AABB component : anvil.plasticraft$getCollisionBox().components()) {
            if (Math.abs(faceCoordinate(component, anvilBottom) - faceCoordinate(anvilBounds, anvilBottom))
                > PlasticEntityPhysics.FACE_EPSILON) {
                continue;
            }
            foundLeadingComponent = true;
            if (!containsTangentially(opening, component, anvilBottom.getAxis())) return false;
        }
        return foundLeadingComponent;
    }

    private static boolean containsTangentially(AABB outer, AABB inner, Direction.Axis normalAxis) {
        double epsilon = PlasticEntityPhysics.FACE_EPSILON;
        return switch (normalAxis) {
            case X -> inner.minY >= outer.minY - epsilon && inner.maxY <= outer.maxY + epsilon
                && inner.minZ >= outer.minZ - epsilon && inner.maxZ <= outer.maxZ + epsilon;
            case Y -> inner.minX >= outer.minX - epsilon && inner.maxX <= outer.maxX + epsilon
                && inner.minZ >= outer.minZ - epsilon && inner.maxZ <= outer.maxZ + epsilon;
            case Z -> inner.minX >= outer.minX - epsilon && inner.maxX <= outer.maxX + epsilon
                && inner.minY >= outer.minY - epsilon && inner.maxY <= outer.maxY + epsilon;
        };
    }

    private static double faceCoordinate(AABB box, Direction direction) {
        return switch (direction) {
            case DOWN -> box.minY;
            case UP -> box.maxY;
            case WEST -> box.minX;
            case EAST -> box.maxX;
            case NORTH -> box.minZ;
            case SOUTH -> box.maxZ;
        };
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
        return this.resolveOutletDirection(localDirection);
    }

    private Direction resolveOutletDirection(Direction localDirection) {
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
        if (!this.restoringData) this.bondedDataDirty = true;
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide && this.burnIfTouchingLava()) return;
        AABB previousBox = this.getBoundingBox();
        super.tick();
        if (this.isRemoved()) return;
        this.stickEntitiesInUniversalMelt();
        if (this.level().isClientSide) return;
        if (this.burnIfCrossingLava(previousBox)) return;
        this.tickFunctionalState();
    }

    /** 方块化后只推进容器功能，不执行落方块实体的重力和碰撞物理。 */
    public void plasticraft$tickBonded() {
        if (this.isRemoved() || this.level().isClientSide) return;
        if (this.burnIfTouchingLava()) return;
        if (this.time < Integer.MAX_VALUE) this.time++;
        this.tickCount++;
        this.tickFunctionalState();
    }

    /** 让实体形态硬化树脂锅中的通用塑料熔体也施加蜘蛛网式减速。 */
    private void stickEntitiesInUniversalMelt() {
        FluidStack fluid = this.fluidHandler.getFluid();
        if (!fluid.is(ModFluids.UNIVERSAL_PLASTIC_MELT.get())) return;
        if (this.getOrientation().attachmentFace() != Direction.UP) return;
        AABB box = this.getBoundingBox();
        double fill = Math.clamp((double) fluid.getAmount() / CAPACITY, 0.0D, 1.0D);
        AABB fluidArea = new AABB(
            box.minX + FLUID_INNER_INSET,
            box.minY + FLUID_BOTTOM,
            box.minZ + FLUID_INNER_INSET,
            box.maxX - FLUID_INNER_INSET,
            box.minY + FLUID_BOTTOM + FLUID_HEIGHT * fill,
            box.maxZ - FLUID_INNER_INSET
        );
        for (Entity entity : this.level().getEntitiesOfClass(
            Entity.class,
            fluidArea,
            candidate -> candidate != this && candidate.isAlive()
        )) {
            this.plasticraft$stickEntityInUniversalMelt(entity);
        }
    }

    public boolean plasticraft$isEntityInsideUniversalMelt(Entity entity) {
        FluidStack fluid = this.fluidHandler.getFluid();
        if (!fluid.is(ModFluids.UNIVERSAL_PLASTIC_MELT.get())
            || this.getOrientation().attachmentFace() != Direction.UP) return false;
        AABB box = this.getBoundingBox();
        double fill = Math.clamp((double) fluid.getAmount() / CAPACITY, 0.0D, 1.0D);
        AABB fluidArea = new AABB(
            box.minX + FLUID_INNER_INSET,
            box.minY + FLUID_BOTTOM,
            box.minZ + FLUID_INNER_INSET,
            box.maxX - FLUID_INNER_INSET,
            box.minY + FLUID_BOTTOM + FLUID_HEIGHT * fill,
            box.maxZ - FLUID_INNER_INSET
        );
        return fluidArea.intersects(entity.getBoundingBox());
    }

    public void plasticraft$stickEntityInUniversalMelt(Entity entity) {
        if (this.plasticraft$isEntityInsideUniversalMelt(entity)) {
            entity.makeStuckInBlock(this.getDisplayState(), UNIVERSAL_MELT_STICK_SPEED);
        }
    }

    public boolean plasticraft$wasBurnedByLava() {
        return this.burnedByLava;
    }

    public boolean plasticraft$leftLavaSource() {
        return this.leftLavaSource;
    }

    private void burnIfFilledWithLava() {
        if (this.restoringData || this.level().isClientSide || this.isRemoved()) return;
        if (this.fluidHandler.getFluid().is(FluidTags.LAVA)) this.burnFromLava(true);
    }

    private boolean burnIfTouchingLava() {
        boolean filledWithLava = this.fluidHandler.getFluid().is(FluidTags.LAVA);
        if (!filledWithLava && !this.isTouchingWorldLava()) return false;
        this.burnFromLava(filledWithLava);
        return true;
    }

    private boolean isTouchingWorldLava() {
        return this.isTouchingWorldLava(this.getBoundingBox());
    }

    private boolean burnIfCrossingLava(AABB previousBox) {
        if (this.fluidHandler.getFluid().is(FluidTags.LAVA)) {
            this.burnFromLava(true);
            return true;
        }
        AABB currentBox = this.getBoundingBox();
        Vec3 movement = currentBox.getCenter().subtract(previousBox.getCenter());
        double distance = Math.max(Math.abs(movement.x), Math.max(Math.abs(movement.y), Math.abs(movement.z)));
        int samples = Math.max(1, Mth.ceil(distance / 0.5D));
        for (int sample = 1; sample <= samples; sample++) {
            if (this.isTouchingWorldLava(previousBox.move(movement.scale(sample / (double) samples)))) {
                this.burnFromLava(false);
                return true;
            }
        }
        return false;
    }

    private boolean isTouchingWorldLava(AABB box) {
        int minX = Mth.floor(box.minX + 1.0E-6D);
        int maxX = Mth.floor(box.maxX - 1.0E-6D);
        int minY = Mth.floor(box.minY + 1.0E-6D);
        int maxY = Mth.floor(box.maxY - 1.0E-6D);
        int minZ = Mth.floor(box.minZ + 1.0E-6D);
        int maxZ = Mth.floor(box.maxZ - 1.0E-6D);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.set(x, y, z);
                    FluidState fluid = this.level().getFluidState(cursor);
                    if (fluid.is(FluidTags.LAVA)
                        && y + fluid.getHeight(this.level(), cursor) > box.minY + 1.0E-6D) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void burnFromLava(boolean leaveLavaSource) {
        if (this.level().isClientSide || this.isRemoved()) return;
        this.burnedByLava = true;
        BlockPos effectPos = BlockPos.containing(this.getBoundingBox().getCenter());
        this.level().levelEvent(2001, effectPos, Block.getId(this.getDisplayState()));
        this.level().gameEvent(this, GameEvent.BLOCK_DESTROY, effectPos);
        if (leaveLavaSource) {
            this.leftLavaSource = this.level().setBlock(
                effectPos,
                Fluids.LAVA.defaultFluidState().createLegacyBlock(),
                Block.UPDATE_ALL
            );
        }
        this.discard();
    }

    public boolean plasticraft$consumeBondedDataDirty() {
        boolean dirty = this.bondedDataDirty;
        this.bondedDataDirty = false;
        return dirty;
    }

    private void tickFunctionalState() {
        this.tryIgniteFromOpeningContacts();
        this.refreshIgnited();
        this.hurtEntitiesInIgnitedFluid();
        this.refreshRecipeContact();
        this.updateFluidNetworkRegistration();
        if (!this.ejectItemsIfNeeded()) this.absorbTouchingItems();
        if (this.level() instanceof ServerLevel serverLevel) {
            PlasticOilCatalysis.tickResinCauldron(serverLevel, this);
        }
        this.spillFluidIfNeeded();
        this.trySpawnPlasmaJets();
    }

    private void hurtEntitiesInIgnitedFluid() {
        if (!this.anvilcraft$isIgnited() || this.fluidHandler.isEmpty()) return;
        AABB box = this.getBoundingBox();
        double fill = Math.clamp((double) this.fluidHandler.getFluidAmount() / CAPACITY, 0.0D, 1.0D);
        AABB fluidArea = new AABB(
            box.minX + FLUID_INNER_INSET,
            box.minY + FLUID_BOTTOM,
            box.minZ + FLUID_INNER_INSET,
            box.maxX - FLUID_INNER_INSET,
            box.minY + FLUID_BOTTOM + FLUID_HEIGHT * fill,
            box.maxZ - FLUID_INNER_INSET
        );
        for (Entity entity : this.level().getEntitiesOfClass(
            Entity.class,
            fluidArea,
            entity -> entity != this && entity.isAlive()
        )) {
            IgnitedFluidEffects.hurt(entity, this.level(), this.fluidHandler.getFluid());
        }
    }

    /** 与鱼缸一致，开口接触到燃烧中的实体时点燃可燃流体。 */
    private void tryIgniteFromOpeningContacts() {
        if (this.anvilcraft$isIgnited() || !this.canIgniteFluid()) return;
        if (this.level().getEntitiesOfClass(
            Entity.class,
            this.openingContactArea(),
            entity -> entity != this && entity.isAlive() && entity.isOnFire()
        ).isEmpty()) return;
        this.anvilcraft$setIgnited(true);
    }

    @Override
    public boolean anvilcraft$isIgnited() {
        return this.entityData.get(IGNITED);
    }

    @Override
    public void anvilcraft$setIgnited(boolean ignited) {
        boolean next = ignited && this.canIgniteFluid();
        if (this.entityData.get(IGNITED) == next) return;
        this.entityData.set(IGNITED, next);
        this.hasImpulse = true;
        this.hurtMarked = true;
        if (!this.restoringData) this.bondedDataDirty = true;
    }

    private boolean canIgniteFluid() {
        return this.getOrientation().attachmentFace() == Direction.UP
            && this.fluidHandler.getFluid().is(ModFluidTags.IGNITABLE);
    }

    private void refreshIgnited() {
        if (this.refreshingIgnited) return;
        this.refreshingIgnited = true;
        try {
            if (!this.canIgniteFluid()) {
                this.anvilcraft$setIgnited(false);
                return;
            }
            if (this.anvilcraft$isIgnited()) return;
            for (ItemStackHandler handler : new ItemStackHandler[]{this.input, this.output}) {
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    ItemStack stack = handler.getStackInSlot(slot);
                    if (stack.is(ModItemTags.FIRE_STARTER)) {
                        handler.extractItem(slot, 1, false);
                        this.anvilcraft$setIgnited(true);
                        return;
                    }
                    if (stack.is(ModItemTags.UNBROKEN_FIRE_STARTER)) {
                        this.anvilcraft$setIgnited(true);
                        return;
                    }
                }
            }
        } finally {
            this.refreshingIgnited = false;
        }
    }

    private void trySpawnPlasmaJets() {
        if (!this.anvilcraft$isIgnited()
            || this.getOrientation().attachmentFace() != Direction.UP
            || this.fluidHandler.getFluidAmount() < 250
            || this.time % 10 != 0) {
            return;
        }
        BlockPos occupied = BlockPos.containing(this.getBoundingBox().getCenter());
        BlockState heater = this.level().getBlockState(occupied.below());
        if (!heater.is(HEATER)
            || heater.getValue(HeaterBlock.OVERLOAD)) {
            return;
        }
        PlasmaJetsBlock.trySpawn(occupied.above(), this.level());
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
        AABB collisionArea = this.getBoundingBox().deflate(1.0E-4D);
        for (ItemEntity item : this.level().getEntitiesOfClass(
            ItemEntity.class,
            collisionArea,
            entity -> entity.isAlive() && collisionArea.contains(entity.getBoundingBox().getCenter())
        )) {
            this.absorbItem(item);
        }
        for (ItemEntity item : this.level().getEntitiesOfClass(
            ItemEntity.class,
            this.openingContactArea(),
            Entity::isAlive
        )) {
            if (!item.anvilcraft$isAdsorbable()) continue;
            this.absorbItem(item);
        }
    }

    private void absorbItem(ItemEntity item) {
        ItemStack remaining = ItemHandlerUtil.insertItem(this.input, item.getItem().copy(), false);
        if (remaining.isEmpty()) {
            item.discard();
        } else {
            item.setItem(remaining);
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
        if (fluid.isEmpty()) return;
        if (fluid.getAmount() < CAPACITY) {
            this.fluidHandler.drain(fluid.getAmount(), IFluidHandler.FluidAction.EXECUTE);
            return;
        }
        BlockPos target = this.openingTargetPosition();
        BlockState fluidState = fluid.getFluid().defaultFluidState().createLegacyBlock();
        if (this.isOpeningBlocked(target)) return;
        if (!fluidState.isAir()) {
            this.level().setBlock(target, fluidState, Block.UPDATE_ALL);
            if (this.level().getBlockEntity(target) instanceof UniversalPlasticMeltBlockEntity melt) {
                melt.setColor(PlasticMeltColor.get(fluid));
            }
        }
        this.fluidHandler.drain(CAPACITY, IFluidHandler.FluidAction.EXECUTE);
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

    /** 检测真实凹形碰撞不会裁剪的砧底穿越锅口事件。 */
    void processSweptAnvilImpact(
        AbstractPlasticEntity anvil,
        Vec3 anvilStartPosition,
        Vec3 anvilEndPosition,
        Vec3 potStartPosition,
        Vec3 potEndPosition
    ) {
        Direction anvilBottom = anvil.getOrientation().attachmentFace().getOpposite();
        Direction potTop = this.getOrientation().attachmentFace();
        if (anvilBottom != potTop.getOpposite()) return;

        double startGap = recipeSurfaceGap(
            anvil,
            anvilStartPosition,
            this,
            potStartPosition,
            anvilBottom
        );
        double endGap = recipeSurfaceGap(
            anvil,
            anvilEndPosition,
            this,
            potEndPosition,
            anvilBottom
        );
        double closingDistance = startGap - endGap;
        if (closingDistance <= 0.04D
            || startGap < -PlasticEntityPhysics.FACE_EPSILON
            || endGap > PlasticEntityPhysics.FACE_EPSILON) {
            return;
        }

        AABB anvilSweep = sweptEntityBox(anvil, anvilStartPosition, anvilEndPosition);
        AABB potSweep = sweptEntityBox(this, potStartPosition, potEndPosition);
        if (tangentialOverlap(anvilSweep, potSweep, anvilBottom) <= PlasticEntityPhysics.FACE_EPSILON) return;

        Vec3 anvilMovement = anvilEndPosition.subtract(anvilStartPosition);
        Vec3 potMovement = potEndPosition.subtract(potStartPosition);
        Vec3 normal = Vec3.atLowerCornerOf(anvilBottom.getNormal());
        Direction impactDirection = anvilMovement.dot(normal) >= -potMovement.dot(normal)
            ? anvilBottom
            : potTop;
        boolean began = this.tryBeginAnvilImpact(anvil, impactDirection, true);
        if (!(this.level() instanceof ServerLevel level) || !began) {
            return;
        }
        double contactFraction = Mth.clamp(startGap / closingDistance, 0.0D, 1.0D);
        Vec3 contactPotPosition = potStartPosition.lerp(potEndPosition, contactFraction);
        CauldronImpactRecipeProcessor.processAtPotPosition(level, anvil, this, contactPotPosition);
    }

    private static double recipeSurfaceGap(
        AbstractPlasticEntity anvil,
        Vec3 anvilPosition,
        HardenedResinCauldronEntity pot,
        Vec3 potPosition,
        Direction anvilBottom
    ) {
        Vec3 normal = Vec3.atLowerCornerOf(anvilBottom.getNormal());
        Vec3 anvilCenter = anvilPosition.add(0.0D, anvil.getBbHeight() * 0.5D, 0.0D);
        Vec3 potCenter = potPosition.add(0.0D, pot.getBbHeight() * 0.5D, 0.0D);
        Vec3 anvilSurface = anvilCenter.add(normal.scale(0.5D));
        Vec3 potSurface = potCenter.subtract(normal.scale(0.5D));
        return potSurface.subtract(anvilSurface).dot(normal);
    }

    private static AABB sweptEntityBox(Entity entity, Vec3 startPosition, Vec3 endPosition) {
        Vec3 currentPosition = entity.position();
        AABB start = entity.getBoundingBox().move(startPosition.subtract(currentPosition));
        AABB end = entity.getBoundingBox().move(endPosition.subtract(currentPosition));
        return start.minmax(end);
    }

    private static double tangentialOverlap(AABB first, AABB second, Direction direction) {
        double x = Math.max(0.0D, Math.min(first.maxX, second.maxX) - Math.max(first.minX, second.minX));
        double y = Math.max(0.0D, Math.min(first.maxY, second.maxY) - Math.max(first.minY, second.minY));
        double z = Math.max(0.0D, Math.min(first.maxZ, second.maxZ) - Math.max(first.minZ, second.minZ));
        return switch (direction.getAxis()) {
            case X -> y * z;
            case Y -> x * z;
            case Z -> x * y;
        };
    }

    private boolean tryBeginAnvilImpact(AbstractPlasticEntity anvil, Direction impactDirection) {
        return this.tryBeginAnvilImpact(anvil, impactDirection, false);
    }

    private boolean tryBeginAnvilImpact(
        AbstractPlasticEntity anvil,
        Direction impactDirection,
        boolean sweptImpact
    ) {
        if (!canProcessAnvilImpact(anvil, this, impactDirection)) return false;
        if (!sweptImpact && !this.hasRecipeSurfaceContact(anvil)) return true;
        if (this.activeRecipeContact == anvil) return false;
        this.activeRecipeContact = anvil;
        this.activeRecipeContactFromSweep = sweptImpact;
        return true;
    }

    private void refreshRecipeContact() {
        if (this.activeRecipeContact == null) return;
        if (this.hasRecipeSurfaceContact(this.activeRecipeContact)) return;
        if (this.activeRecipeContactFromSweep && this.isInsideSweptRecipeContactEnvelope(this.activeRecipeContact)) {
            return;
        }
        this.activeRecipeContact = null;
        this.activeRecipeContactFromSweep = false;
    }

    private boolean hasRecipeSurfaceContact(AbstractPlasticEntity anvil) {
        Direction potTop = this.getOrientation().attachmentFace();
        Direction anvilBottom = anvil.getOrientation().attachmentFace().getOpposite();
        return anvilBottom == potTop.getOpposite()
            && (PlasticEntityPhysics.isSupportCandidate(this, anvil, potTop)
                || this.getBoundingBox()
                    .inflate(PlasticEntityPhysics.SUPPORT_PROBE_DEPTH)
                    .intersects(anvil.getBoundingBox()));
    }

    /** 扫掠穿越后保留一格迟滞，避免磁力振荡在同一次相遇中重复加工输出。 */
    private boolean isInsideSweptRecipeContactEnvelope(AbstractPlasticEntity anvil) {
        Direction potTop = this.getOrientation().attachmentFace();
        Direction anvilBottom = anvil.getOrientation().attachmentFace().getOpposite();
        return anvil.isAlive()
            && anvilBottom == potTop.getOpposite()
            && this.getBoundingBox()
                .inflate(SWEPT_RECIPE_CONTACT_RELEASE_DISTANCE)
                .intersects(anvil.getBoundingBox());
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
            if (changed != null) this.removeOpposingOutlet(this.resolveOutletDirection(changed));
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

    private void removeOpposingOutlet(Direction outletDirection) {
        BlockPos occupiedPos = BlockPos.containing(this.getBoundingBox().getCenter());
        BlockPos targetPos = occupiedPos.relative(outletDirection);
        Direction opposingDirection = outletDirection.getOpposite();
        for (HardenedResinCauldronEntity cauldron : this.level().getEntitiesOfClass(
            HardenedResinCauldronEntity.class,
            new AABB(targetPos),
            entity -> entity != this
                && BlockPos.containing(entity.getBoundingBox().getCenter()).equals(targetPos)
        )) {
            cauldron.clearOutletFacing(opposingDirection);
        }
        if (this.level().getBlockEntity(targetPos) instanceof BondedEntityBlockEntity bonded) {
            bonded.clearCauldronOutletFacing(opposingDirection);
        }
    }

    public boolean clearOutletFacing(Direction direction) {
        if (this.getOutletDirection() != direction) return false;
        this.setOutletLocalDirection(null);
        return true;
    }

    @Override
    protected InteractionResult interactNormally(Player player, InteractionHand hand) {
        ItemStack inHand = player.getItemInHand(hand);
        if (inHand.getItem() instanceof AnvilHammerItem) {
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        // 原版打火石必须优先于通用物品插入逻辑处理，否则会被塞进锅内输入栏。
        if (inHand.is(Items.FLINT_AND_STEEL)) {
            if (!this.canIgniteFluid()) return InteractionResult.PASS;
            if (!this.level().isClientSide && !this.anvilcraft$isIgnited()) {
                this.anvilcraft$setIgnited(true);
                inHand.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
                this.level().playSound(
                    null,
                    this.blockPosition(),
                    SoundEvents.FLINTANDSTEEL_USE,
                    SoundSource.BLOCKS,
                    1.0F,
                    1.0F
                );
                this.gameEvent(GameEvent.BLOCK_CHANGE, null);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        if (inHand.is(ModItemTags.FIRE_STARTER) || inHand.is(ModItemTags.UNBROKEN_FIRE_STARTER)) {
            if (!this.canIgniteFluid()) return InteractionResult.PASS;
            if (!this.level().isClientSide && !this.anvilcraft$isIgnited()) {
                this.anvilcraft$setIgnited(true);
                if (inHand.is(ModItemTags.FIRE_STARTER) && !player.getAbilities().instabuild) inHand.shrink(1);
            }
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
        List<IItemHandler> targets = this.getOutletTargets(occupiedPos.relative(outletDirection));
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

    private List<IItemHandler> getOutletTargets(BlockPos targetPos) {
        LargeCauldronBlockEntity cauldron = LargeCauldronBlockEntity.getMain(
            this.level(),
            targetPos,
            this.level().getBlockState(targetPos)
        );
        if (cauldron != null) {
            return List.of(cauldron.getInputHandler());
        }
        return ItemHandlerUtil.getTargetItemHandlerList(targetPos, null, this.level());
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

    public void beginRecipeProcessing() {
        boolean hasInput = !isEmpty(this.input);
        long gameTime = this.level().getGameTime();
        this.processingOutput = !hasInput
            && !isEmpty(this.output)
            && gameTime != this.lastRecipeProcessingGameTime;
        if (hasInput || this.processingOutput) this.lastRecipeProcessingGameTime = gameTime;
    }

    /** 保证巨型铁砧同一刻发布的多个落点不会重复加工同一个锅。 */
    public boolean tryClaimRecipeImpact(Entity source) {
        long gameTime = this.level().getGameTime();
        if (this.lastRecipeImpactSource == source && this.lastRecipeImpactGameTime == gameTime) return false;
        this.lastRecipeImpactSource = source;
        this.lastRecipeImpactGameTime = gameTime;
        return true;
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

    /** 返回当前朝上的开口处液面高度，供喷流粒子定位使用。 */
    public double getFluidSurfaceY() {
        AABB box = this.getBoundingBox();
        float fill = Math.clamp((float) this.fluidHandler.getFluidAmount() / CAPACITY, 0.0F, 1.0F);
        return box.minY + 0.251D + fill * 0.685D;
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
        if (fluid == null || fluid == Fluids.EMPTY || amount <= 0) return FluidStack.EMPTY;
        FluidStack stack = new FluidStack(fluid, amount);
        PlasticMeltColor.set(stack, DyeColor.byId(this.entityData.get(FLUID_COLOR)));
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
            if (!stack.isEmpty()) stacks.add(stack.save(registries));
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
        this.entityData.set(FLUID_COLOR, PlasticMeltColor.get(fluid).getId());
        this.hasImpulse = true;
        this.hurtMarked = true;
        if (!this.restoringData) this.bondedDataDirty = true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof Player player
            && source.getDirectEntity() == player
            && player.getMainHandItem().getItem() instanceof AnvilHammerItem) {
            if (!this.level().isClientSide) {
                BlockPos impactPos = CauldronImpactRecipeProcessor.recipePotCell(this);
                if (player.getMainHandItem().getItem() instanceof ResinAnvilHammerItem) {
                    ResinAnvilHammerItem.triggerAnvilImpact(player, this.level(), impactPos);
                } else {
                    AnvilHammerItem.dropAnvil(player, this.level(), impactPos);
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
