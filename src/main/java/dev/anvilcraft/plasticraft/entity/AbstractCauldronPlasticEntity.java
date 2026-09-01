package dev.anvilcraft.plasticraft.entity;

import dev.anvilcraft.plasticraft.block.IgnitedFluidEffects;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.block.entity.UniversalPlasticMeltBlockEntity;
import dev.anvilcraft.plasticraft.entity.physics.PlasticEntityPhysics;
import dev.anvilcraft.plasticraft.entity.collision.PlasticEntityCollisionBox;
import dev.anvilcraft.plasticraft.entity.collision.PlasticEntityCollisionShapes;
import dev.anvilcraft.plasticraft.entity.collision.PlasticEntityGeometry;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.anvilcraft.plasticraft.item.ResinAnvilHammerItem;
import dev.anvilcraft.plasticraft.material.PlasticMaterial;
import dev.anvilcraft.plasticraft.recipe.CauldronImpactRecipeProcessor;
import dev.anvilcraft.plasticraft.recipe.PlasticOilCatalysis;
import dev.dubhe.anvilcraft.api.fluid.network.FluidNetworkManager;
import dev.dubhe.anvilcraft.api.itemhandler.ItemHandlerUtil;
import dev.dubhe.anvilcraft.block.HeaterBlock;
import dev.dubhe.anvilcraft.block.PlasmaJetsBlock;
import dev.dubhe.anvilcraft.block.entity.LargeCauldronBlockEntity;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import dev.dubhe.anvilcraft.init.block.ModFluidTags;
import dev.dubhe.anvilcraft.init.item.ModItemTags;
import dev.dubhe.anvilcraft.item.AnvilHammerItem;
import dev.dubhe.anvilcraft.util.AnvilUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * 实体炼药锅的共享实现。出料口、点燃、流体网络、物品吞吐与落砧配方接触判定对硬化树脂锅和
 * 成型塑料锅完全一致，差异只在仓储载体与液面几何，因此把一致部分放在本类，差异部分留作抽象钩子。
 */
public abstract class AbstractCauldronPlasticEntity extends AbstractPlasticEntity implements PlasticCauldron {
    private static final double SWEPT_RECIPE_CONTACT_RELEASE_DISTANCE = 1.0D;
    /** 锅内塑料熔体施加的蜘蛛网式减速，与熔体流体方块保持同一手感。 */
    private static final Vec3 MELT_STICK_SPEED = new Vec3(0.25D, 0.05D, 0.25D);

    private static final EntityDimensions EJECTED_ITEM_DIMENSIONS = EntityDimensions.scalable(0.25F, 0.25F);
    private static final double ITEM_EJECTION_GAP = 0.02D;
    private static final double ITEM_EJECTION_INSET = 0.15D;
    private static final int ITEM_EJECTION_SEARCH_STEPS = 4;

    protected static final EntityDataAccessor<Integer> OUTLET_SIDE = SynchedEntityData.defineId(
        AbstractCauldronPlasticEntity.class,
        EntityDataSerializers.INT
    );
    protected static final EntityDataAccessor<Boolean> IGNITED = SynchedEntityData.defineId(
        AbstractCauldronPlasticEntity.class,
        EntityDataSerializers.BOOLEAN
    );

    private BlockPos fluidNetworkPos;
    private AbstractPlasticEntity activeRecipeContact;
    private Entity lastRecipeImpactSource;
    private boolean activeRecipeContactFromSweep;
    protected boolean processingOutput;
    protected boolean autoOutputting;
    protected boolean restoringData;
    private boolean refreshingIgnited;
    protected boolean bondedDataDirty;
    private boolean burnedByLava;

    private long lastRecipeImpactGameTime = Long.MIN_VALUE;
    private long lastRecipeProcessingGameTime = Long.MIN_VALUE;

    protected AbstractCauldronPlasticEntity(
        EntityType<? extends AbstractCauldronPlasticEntity> entityType,
        Level level
    ) {
        super(entityType, level);
    }

    protected AbstractCauldronPlasticEntity(
        EntityType<? extends AbstractCauldronPlasticEntity> entityType,
        Level level,
        Vec3 position,
        BlockState displayState,
        ItemStack dropStack,
        PlasticEntityOrientation orientation
    ) {
        super(entityType, level, position, displayState, dropStack, orientation);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OUTLET_SIDE, -1)
            .define(IGNITED, false);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (!this.plasticraft$isCauldron()) return;
        Direction outletSide = this.getOutletLocalDirection();
        if (outletSide != null) tag.putString("OutletSide", outletSide.getName());
        tag.putBoolean("Ignited", this.anvilcraft$isIgnited());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        boolean previousRestoring = this.restoringData;
        this.restoringData = true;
        try {
            super.readAdditionalSaveData(tag);
            if (!this.plasticraft$isCauldron()) return;
            Direction outletSide = Direction.byName(tag.getString("OutletSide"));
            this.setOutletLocalDirection(
                outletSide != null && outletSide.getAxis().isHorizontal() ? outletSide : null
            );
            this.anvilcraft$setIgnited(tag.getBoolean("Ignited") || tag.getBoolean("ignited"));
        } finally {
            this.restoringData = previousRestoring;
        }
    }

    @Override
    public AbstractPlasticEntity plasticraft$cauldronEntity() {
        return this;
    }

    /** 输入槽句柄。硬化树脂锅存在实体 NBT，成型塑料锅存在制品仓储，故交由宿主给出。 */
    protected abstract IItemHandler cauldronInputHandler();

    /** 输出槽句柄，与 {@link #cauldronInputHandler()} 同源。 */
    protected abstract IItemHandler cauldronOutputHandler();

    /** 槽位内容变更后的同步与落库回调。 */
    protected abstract void onCauldronContentsChanged();

    /** 取出锅内全部物品，用于倒扣掉落与铁砧锤拾取。 */
    protected abstract List<ItemStack> extractAllStacks();

    /** 销毁前清理宿主持久化在实体之外的内容；普通实体字段无需额外处理。 */
    protected void discardPersistentContents() {
    }

    /** 当前底层流体占据的世界盒，供点燃伤害与熔体粘滞判定使用。 */
    protected abstract AABB cauldronFluidArea();

    /** 底层单层流体容量，单位 mB。 */
    protected abstract int cauldronFluidCapacity();

    /** 容器本体是否耐受锅外世界的熔岩。 */
    protected abstract boolean resistsWorldLava();

    /** 树脂砧进入锅口时使用的锅底碰撞几何。 */
    protected abstract PlasticEntityGeometry cauldronResinEntryGeometry();

    /** 树脂砧进入锅口时允许通过的局部开口轮廓。 */
    protected abstract VoxelShape cauldronResinEntryOpening();

    /** 返回当前朝上的开口处液面高度，供喷流粒子定位使用。 */
    public double getFluidSurfaceY() {
        return this.cauldronFluidArea().maxY;
    }

    /** 让锅内的塑料熔体也对落入其中的实体施加减速；两侧都要跑，否则客户端预测会抖。 */
    protected void stickEntitiesInPlasticMelt() {
        if (!this.plasticraft$isCauldron()) return;
        if (!PlasticMaterial.isMelt(this.plasticraft$bottomFluid())) return;
        if (this.getOrientation().attachmentFace() != Direction.UP) return;
        for (Entity entity : this.level().getEntitiesOfClass(
            Entity.class,
            this.cauldronFluidArea(),
            candidate -> candidate != this && candidate.isAlive()
        )) {
            this.plasticraft$stickEntityInPlasticMelt(entity);
        }
    }

    @Override
    public void plasticraft$stickEntityInPlasticMelt(Entity entity) {
        if (this.plasticraft$isEntityInsidePlasticMelt(entity)) {
            entity.makeStuckInBlock(this.getDisplayState(), MELT_STICK_SPEED);
        }
    }

    @Override
    public boolean plasticraft$isEntityInsidePlasticMelt(Entity entity) {
        if (!this.plasticraft$isCauldron()) return false;
        if (!PlasticMaterial.isMelt(this.plasticraft$bottomFluid())) return false;
        if (this.getOrientation().attachmentFace() != Direction.UP) return false;
        return this.cauldronFluidArea().intersects(entity.getBoundingBox());
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

    protected Direction resolveOutletDirection(Direction localDirection) {
        PlasticEntityOrientation orientation = this.getOrientation();
        return switch (localDirection) {
            case NORTH -> orientation.longAxis().getOpposite();
            case SOUTH -> orientation.longAxis();
            case WEST -> orientation.orthogonalAxis().getOpposite();
            case EAST -> orientation.orthogonalAxis();
            default -> null;
        };
    }

    protected void setOutletLocalDirection(@Nullable Direction direction) {
        int directionId = direction == null ? -1 : direction.get3DDataValue();
        this.entityData.set(OUTLET_SIDE, directionId);
        this.hasImpulse = true;
        this.hurtMarked = true;
        if (!this.restoringData) this.bondedDataDirty = true;
    }

    public boolean plasticraft$consumeBondedDataDirty() {
        boolean dirty = this.bondedDataDirty;
        this.bondedDataDirty = false;
        return dirty;
    }

    @Override
    public void tick() {
        if (this.burnIfTouchingLava()) return;
        AABB previousBox = this.getBoundingBox();
        super.tick();
        if (this.isRemoved()) return;
        // 熔体减速两侧都要跑，否则客户端预测会与服务端不一致
        this.stickEntitiesInPlasticMelt();
        if (this.burnIfCrossingLava(previousBox)) return;
        this.tickAdditionalState();
        if (!this.level().isClientSide && this.plasticraft$isCauldron()) this.tickFunctionalState();
    }

    /** 宿主追加的每刻状态推进，两侧都会执行；实体已被移除或已被熔岩烧毁时不再调用。 */
    protected void tickAdditionalState() {
    }

    protected void tickFunctionalState() {
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

    /**
     * 方块化后只推进容器功能，不执行落方块实体的重力和碰撞物理。
     * {@code time} 与 {@code tickCount} 仍需自增，否则等离子喷流等按 tick 取模的节流永远不触发。
     */
    @Override
    public final void plasticraft$tickBonded() {
        if (this.isRemoved() || this.level().isClientSide || !this.plasticraft$isCauldron()) return;
        if (this.burnIfTouchingLava()) return;
        if (this.time < Integer.MAX_VALUE) this.time++;
        this.tickCount++;
        this.tickFunctionalState();
    }

    @Override
    public boolean plasticraft$wasBurnedByLava() {
        return this.burnedByLava;
    }

    /** 锅外世界熔岩会烧毁不耐热的容器。 */
    protected boolean burnIfTouchingLava() {
        if (!this.plasticraft$isCauldron() || this.level().isClientSide) return false;
        if (!this.isTouchingWorldLava(this.getBoundingBox())) return false;
        this.burnFromWorldLava();
        return true;
    }

    /** 单帧位移可能整格跨过熔岩，按半格步长回溯采样，避免高速下漏判。 */
    protected boolean burnIfCrossingLava(AABB previousBox) {
        if (!this.plasticraft$isCauldron() || this.level().isClientSide) return false;
        AABB currentBox = this.getBoundingBox();
        Vec3 movement = currentBox.getCenter().subtract(previousBox.getCenter());
        double distance = Math.max(Math.abs(movement.x), Math.max(Math.abs(movement.y), Math.abs(movement.z)));
        int samples = Math.max(1, Mth.ceil(distance / 0.5D));
        for (int sample = 1; sample <= samples; sample++) {
            if (this.isTouchingWorldLava(previousBox.move(movement.scale(sample / (double) samples)))) {
                this.burnFromWorldLava();
                return true;
            }
        }
        return false;
    }

    private boolean isTouchingWorldLava(AABB box) {
        if (this.resistsWorldLava()) return false;
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

    /** 世界熔岩烧毁容器。 */
    protected void burnFromWorldLava() {
        if (this.level().isClientSide || this.isRemoved()) return;
        this.discardPersistentContents();
        this.burnedByLava = true;
        BlockPos effectPos = BlockPos.containing(this.getBoundingBox().getCenter());
        this.level().levelEvent(2001, effectPos, Block.getId(this.getDisplayState()));
        this.level().gameEvent(this, GameEvent.BLOCK_DESTROY, effectPos);
        this.discard();
    }

    private void hurtEntitiesInIgnitedFluid() {
        if (!this.anvilcraft$isIgnited() || this.plasticraft$bottomFluid().isEmpty()) return;
        AABB fluidArea = this.cauldronFluidArea();
        for (Entity entity : this.level().getEntitiesOfClass(
            Entity.class,
            fluidArea,
            entity -> entity != this && entity.isAlive()
        )) {
            IgnitedFluidEffects.hurt(entity, this.level(), this.plasticraft$bottomFluid());
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

    protected boolean canIgniteFluid() {
        return this.getOrientation().attachmentFace() == Direction.UP
            && this.plasticraft$bottomFluid().is(ModFluidTags.IGNITABLE);
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
            for (IItemHandler handler : new IItemHandler[]{this.cauldronInputHandler(), this.cauldronOutputHandler()}) {
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
            || this.plasticraft$bottomFluid().getAmount() < 250
            || this.time % 10 != 0) {
            return;
        }
        BlockPos occupied = BlockPos.containing(this.getBoundingBox().getCenter());
        @Nullable BlockState heater = PlasticCauldronWorkBlockFinder.find(
            this,
            AbstractCauldronPlasticEntity::heaterState
        );
        if (heater == null
            || heater.getValue(HeaterBlock.OVERLOAD)) {
            return;
        }
        PlasmaJetsBlock.trySpawn(occupied.above(), this.level());
    }

    private static @Nullable BlockState heaterState(BlockState state) {
        return state.is(ModBlocks.HEATER) ? state : null;
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
        ItemStack remaining = ItemHandlerUtil.insertItem(this.cauldronInputHandler(), item.getItem().copy(), false);
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

    protected void spillFluidIfNeeded() {
        if (this.isMagnetized() || this.getOrientation().attachmentFace() == Direction.UP) return;
        FluidStack fluid = this.plasticraft$bottomFluid();
        if (fluid.isEmpty()) return;
        if (fluid.getAmount() < this.cauldronFluidCapacity()) {
            this.plasticraft$bottomFluidAccess().drain(fluid.getAmount(), IFluidHandler.FluidAction.EXECUTE);
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
        this.plasticraft$bottomFluidAccess().drain(this.cauldronFluidCapacity(), IFluidHandler.FluidAction.EXECUTE);
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
        if (!this.plasticraft$isCauldron()) {
            super.onEntityImpact(support, impactDirection, fallDistance);
            return;
        }
        if (!(this.level() instanceof ServerLevel level)) return;
        if (!(support instanceof AbstractPlasticEntity anvil)) return;
        if (!this.tryBeginAnvilImpact(anvil, impactDirection)) return;
        CauldronImpactRecipeProcessor.processEntityLandingImpact(level, anvil, this, this.position());
    }

    public void processAnvilImpact(AbstractPlasticEntity anvil, Direction impactDirection) {
        if (!(this.level() instanceof ServerLevel level)) return;
        if (!this.tryBeginAnvilImpact(anvil, impactDirection)) return;
        CauldronImpactRecipeProcessor.processEntityLandingImpact(level, anvil, this, this.position());
    }

    /** 检测真实凹形碰撞不会裁剪的砧底穿越锅口事件。 */
    public void processSweptAnvilImpact(
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
        CauldronImpactRecipeProcessor.processEntityLandingImpact(level, anvil, this, contactPotPosition);
    }

    private static double recipeSurfaceGap(
        AbstractPlasticEntity anvil,
        Vec3 anvilPosition,
        AbstractCauldronPlasticEntity pot,
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
        AbstractCauldronPlasticEntity pot,
        Direction impactDirection
    ) {
        Direction anvilBottom = anvil.getOrientation().attachmentFace().getOpposite();
        Direction potTop = pot.getOrientation().attachmentFace();
        return anvilBottom == impactDirection && potTop == impactDirection.getOpposite()
            || potTop == impactDirection && anvilBottom == impactDirection.getOpposite();
    }

    @Override
    public VoxelShape plasticraft$getCollisionShape(Entity mover, Vec3 requestedMovement) {
        return this.plasticraft$getCollisionBox(mover, requestedMovement).shape();
    }

    @Override
    public PlasticEntityCollisionBox plasticraft$getCollisionBox(Entity mover, Vec3 requestedMovement) {
        if (mover instanceof ResinAnvilEntity anvil
            && this.allowsResinAnvilToCrossOpening(anvil, requestedMovement)) {
            return this.cauldronResinEntryGeometry().collisionBoxAt(this.position(), this.getOrientation());
        }
        return this.plasticraft$getCollisionBox();
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

        PlasticEntityGeometry entryGeometry = this.cauldronResinEntryGeometry();
        VoxelShape relativeOpening = PlasticEntityCollisionShapes.rotate(
            this.cauldronResinEntryOpening(),
            this.getOrientation(),
            entryGeometry.rotationPivot()
        );
        Vec3 translation = this.position().subtract(entryGeometry.entityOrigin());
        AABB opening = relativeOpening.move(translation.x, translation.y, translation.z).bounds();
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

    /** 铁砧锤右键切换出料口朝向；同一面重复点击取消出料口。 */
    @Override
    protected InteractionResult interactWithAnvilHammer(
        Player player, InteractionHand hand, Direction interactionFace
    ) {
        if (!this.plasticraft$isCauldron()) return super.interactWithAnvilHammer(player, hand, interactionFace);
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
                null, this.blockPosition(), SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1.0F, 1.0F
            );
            this.gameEvent(GameEvent.ENTITY_INTERACT, player);
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    @Override
    protected InteractionResult interactNormally(Player player, InteractionHand hand) {
        if (!this.plasticraft$isCauldron()) return super.interactNormally(player, hand);
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
                    null, this.blockPosition(), SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 1.0F
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
        if (this.tryFluidInteraction(player, hand)) {
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
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
        ItemStack remaining = ItemHandlerUtil.insertItem(this.cauldronInputHandler(), inHand.copy(), false);
        int inserted = inHand.getCount() - remaining.getCount();
        if (inserted <= 0) return InteractionResult.PASS;
        player.setItemInHand(hand, remaining);
        return InteractionResult.CONSUME;
    }

    private boolean tryFluidInteraction(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean interacted = FluidUtil.interactWithFluidHandler(player, hand, this.getFluidHandler());
        if (interacted && !this.level().isClientSide) {
            player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
            this.spillFluidIfNeeded();
        }
        return interacted;
    }

    /** 锅被普通伤害摧毁时散落物品；流体由宿主清空，不写入锅的掉落物。 */
    protected boolean dropsContentsOnDestruction() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!this.plasticraft$isCauldron()) return super.hurt(source, amount);
        if (source.getEntity() instanceof Player player
            && player.getMainHandItem().getItem() instanceof AnvilHammerItem
            // 普通铁砧锤通过 DamageTypes.FALLING_ANVIL 伤害玩家目标，directEntity 为空；树脂锤
            // 的专用攻击事件仍使用 playerAttack，因此保留 directEntity==player 这一路径。
            && (source.is(DamageTypes.FALLING_ANVIL) || source.getDirectEntity() == player)) {
            if (!this.level().isClientSide) {
                // 锤击落点只用于锁定真正被砸到的锅；配方工作格仍由锅底另行推导。
                BlockPos impactPos = BlockPos.containing(this.getBoundingBox().getCenter());
                CauldronImpactRecipeProcessor.beginHammerImpact(this);
                try {
                    if (player.getMainHandItem().getItem() instanceof ResinAnvilHammerItem) {
                        ResinAnvilHammerItem.triggerAnvilImpact(player, this.level(), impactPos);
                    } else {
                        AnvilHammerItem.dropAnvil(player, this.level(), impactPos);
                    }
                } finally {
                    CauldronImpactRecipeProcessor.finishHammerImpact();
                }
            }
            // 不把锤击视作有效伤害，避免锅和库存掉落，也避免锤子的攻击逻辑再次消耗耐久。
            return false;
        }
        if (this.isInvulnerableTo(source)) return false;
        if (!this.level().isClientSide && !this.isRemoved()) {
            List<ItemStack> contents = this.dropsContentsOnDestruction()
                ? this.extractAllStacks()
                : List.of();
            if (this.level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
                for (ItemStack stack : contents) {
                    if (!stack.isEmpty()) this.spawnAtLocation(stack);
                }
            }
            this.discardPersistentContents();
        }
        return super.hurt(source, amount);
    }

    @Override
    protected boolean triggersAnvilCraftLandingEvents(Direction impactDirection) {
        // 釜是配方容器，绝不是负责触发 AnvilCraft 世界向下落地事件的下落砧。
        return !this.plasticraft$isCauldron() && super.triggersAnvilCraftLandingEvents(impactDirection);
    }

    protected @Nullable Direction resolveOutletLocalSide(Direction interactionFace, Player player) {
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

    protected void removeOpposingOutlet(Direction outletDirection) {
        BlockPos occupiedPos = BlockPos.containing(this.getBoundingBox().getCenter());
        BlockPos targetPos = occupiedPos.relative(outletDirection);
        Direction opposingDirection = outletDirection.getOpposite();
        for (PlasticCauldron cauldron : PlasticCauldrons.findIn(
            this.level(),
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

    public void tryAutoOutputResults() {
        Direction outletDirection = this.getOutletDirection();
        if (outletDirection == null || this.level().isClientSide || this.autoOutputting) return;

        BlockPos occupiedPos = BlockPos.containing(this.getBoundingBox().getCenter());
        List<IItemHandler> targets = this.getOutletTargets(occupiedPos.relative(outletDirection));
        this.autoOutputting = true;
        try {
            if (targets == null || targets.isEmpty()) {
                if (this.isOutletBlocked(outletDirection)) return;
                for (int slot = 0; slot < this.cauldronOutputHandler().getSlots(); slot++) {
                    ItemStack stack = this.cauldronOutputHandler().extractItem(slot, Integer.MAX_VALUE, false);
                    if (!stack.isEmpty()) this.popResourceFromOutlet(outletDirection, stack);
                }
                return;
            }

            for (IItemHandler target : targets) {
                for (int slot = 0; slot < this.cauldronOutputHandler().getSlots(); slot++) {
                    ItemStack extracted = this.cauldronOutputHandler().extractItem(slot, Integer.MAX_VALUE, true);
                    if (extracted.isEmpty()) continue;
                    ItemStack remaining = ItemHandlerUtil.insertItem(target, extracted, true);
                    if (remaining.getCount() == extracted.getCount()) continue;
                    remaining = ItemHandlerUtil.insertItem(
                        target,
                        this.cauldronOutputHandler().extractItem(slot, Integer.MAX_VALUE, false),
                        false
                    );
                    if (!remaining.isEmpty()) ItemHandlerUtil.insertItem(this.cauldronOutputHandler(), remaining, false);
                }
            }
        } finally {
            this.autoOutputting = false;
            this.onCauldronContentsChanged();
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

    public void beginRecipeProcessing() {
        boolean hasInput = !isEmpty(this.cauldronInputHandler());
        long gameTime = this.level().getGameTime();
        this.processingOutput = !hasInput
            && !isEmpty(this.cauldronOutputHandler())
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
        return ItemHandlerUtil.insertItem(this.cauldronOutputHandler(), stack, false);
    }

    protected static boolean isEmpty(IItemHandler handler) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (!handler.getStackInSlot(slot).isEmpty()) return false;
        }
        return true;
    }
}
