package dev.anvilcraft.plasticraft.entity.drone;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.blueprint.ConstructionWaitReason;
import dev.anvilcraft.plasticraft.block.entity.DroneStationBlockEntity;
import dev.anvilcraft.plasticraft.drone.DroneData;
import dev.anvilcraft.plasticraft.drone.DroneEnergyModel;
import dev.anvilcraft.plasticraft.drone.DroneFlightNavigator;
import dev.anvilcraft.plasticraft.drone.DroneFlightState;
import dev.anvilcraft.plasticraft.drone.DronePropellerTraits;
import dev.anvilcraft.plasticraft.drone.DroneShortageStrategy;
import dev.anvilcraft.plasticraft.drone.tool.DroneCapability;
import dev.anvilcraft.plasticraft.drone.tool.DroneToolDefinition;
import dev.anvilcraft.plasticraft.drone.tool.DroneToolDefinitions;
import dev.anvilcraft.plasticraft.inventory.DroneMenu;
import dev.anvilcraft.plasticraft.item.DroneItem;
import dev.dubhe.anvilcraft.api.power.DynamicPowerComponent;
import dev.dubhe.anvilcraft.api.power.PowerGrid;
import dev.dubhe.anvilcraft.item.AnvilHammerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 通用机械无人机实体。建设、拆除、收集和观察是由已安装工具定义的物品变体,
 * 世界中只注册这一个实体类型;工种差异全部经 {@link DroneToolDefinition} 表达,
 * 因此该类不出现按工种分支的行为代码。
 */
public class DroneEntity extends Entity {
    private static final EntityDataAccessor<String> DATA_TOOL_ID =
        SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<ItemStack> DATA_LEFT_PROPELLER =
        SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> DATA_RIGHT_PROPELLER =
        SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Byte> DATA_ACTION_STATE =
        SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_FLIGHT_STATE =
        SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<ItemStack> DATA_CARRIED_ITEM =
        SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.ITEM_STACK);
    /** 每架未满电无人机向所在电网申报的充电功率。 */
    private static final DynamicPowerComponent.PowerConsumption CHARGE_CONSUMPTION =
        new DynamicPowerComponent.PowerConsumption(DroneEnergyModel.CHARGE_POWER_KW);
    /** 无任务悬停高度受阻或找不到地面时向下搜索的最大格数。 */
    private static final int GROUND_SCAN_RANGE = 32;

    private final DynamicPowerComponent chargeComponent;
    private ResourceLocation cachedToolId = DroneToolDefinitions.CONSTRUCTION.id();
    private int energy;
    private UUID owner;
    private DroneShortageStrategy shortageStrategy = DroneShortageStrategy.PAUSE;
    private List<ItemStack> collectionInventory = new ArrayList<>();
    private double flightDistanceAccumulator;
    @Nullable
    private BlockPos dockStationPos;
    private Optional<UUID> assignedJobId = Optional.empty();
    private int taskOpId = -1;
    private ConstructionWaitReason waitReason = ConstructionWaitReason.NONE;
    private final DroneFlightNavigator navigator = new DroneFlightNavigator();

    public DroneEntity(EntityType<? extends DroneEntity> entityType, Level level) {
        super(entityType, level);
        this.blocksBuilding = true;
        this.chargeComponent = new DynamicPowerComponent(this, () -> this.getBoundingBox().inflate(0.5D));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_TOOL_ID, DroneToolDefinitions.CONSTRUCTION.id().toString());
        builder.define(DATA_LEFT_PROPELLER, ItemStack.EMPTY);
        builder.define(DATA_RIGHT_PROPELLER, ItemStack.EMPTY);
        builder.define(DATA_ACTION_STATE, (byte) 0);
        builder.define(DATA_FLIGHT_STATE, (byte) DroneFlightState.LANDED.ordinal());
        builder.define(DATA_CARRIED_ITEM, ItemStack.EMPTY);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (DATA_TOOL_ID.equals(accessor)) {
            this.cachedToolId = ResourceLocation.parse(this.entityData.get(DATA_TOOL_ID));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            this.serverChargeTick();
            this.toolDefinition().behavior().serverTick(this);
            this.serverFlightTick();
        }
        DroneFlightState state = this.flightState();
        if (state == DroneFlightState.LANDED && !this.isNoGravity()) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.04D, 0.0D));
        }
        Vec3 before = this.position();
        this.move(MoverType.SELF, this.getDeltaMovement());
        if (state == DroneFlightState.LANDED) {
            Vec3 motion = this.getDeltaMovement();
            if (this.onGround()) {
                this.setDeltaMovement(motion.x * 0.6D, motion.y < 0.0D ? 0.0D : motion.y * 0.98D, motion.z * 0.6D);
            } else {
                this.setDeltaMovement(motion.x * 0.91D, motion.y * 0.98D, motion.z * 0.91D);
            }
        }
        if (!this.level().isClientSide) {
            this.serverEnergyCostTick(this.position().subtract(before));
        }
        this.pushOverlappingDrones();
    }

    /** 电网充电:未满电时申报 8 功率,电网供电正常则按转换效率充入内部 FE。 */
    private void serverChargeTick() {
        PowerGrid grid = PowerGrid
            .findPowerGridContains(this.level(), this.getBoundingBox().inflate(0.5D))
            .orElse(null);
        this.chargeComponent.switchTo(grid);
        boolean wantsCharge = this.energy < DroneEnergyModel.capacity();
        Set<DynamicPowerComponent.PowerConsumption> consumptions = this.chargeComponent.getPowerConsumptions();
        if (wantsCharge) {
            consumptions.add(CHARGE_CONSUMPTION);
        } else {
            consumptions.remove(CHARGE_CONSUMPTION);
        }
        if (grid != null && grid.isWorking() && wantsCharge) {
            this.energy = Math.min(DroneEnergyModel.capacity(), this.energy + DroneEnergyModel.chargePerTick());
        }
    }

    /** 统一飞行/降落状态机;FLYING 沿路点前进,无任务时仍走悬停与降落。 */
    private void serverFlightTick() {
        switch (this.flightState()) {
            case LANDED -> {
                if (this.wantsIdleHover() && this.energy >= DroneEnergyModel.IDLE_TAKEOFF_MINIMUM) {
                    this.setFlightState(DroneFlightState.TAKING_OFF);
                }
            }
            case TAKING_OFF -> {
                if (!this.canKeepHovering()) {
                    this.setFlightState(DroneFlightState.LANDING);
                    return;
                }
                double target = this.idleHoverTargetY();
                if (Double.isNaN(target)) {
                    this.setFlightState(DroneFlightState.LANDING);
                    return;
                }
                double dy = target - this.getY();
                if (Math.abs(dy) < 0.05D) {
                    this.setDeltaMovement(Vec3.ZERO);
                    this.setFlightState(DroneFlightState.HOVERING);
                    return;
                }
                // 头顶受阻爬升不动时回到地面等待,不在障碍下面耗电死循环;
                // verticalCollisionBelow 排除起飞瞬间残留的落地碰撞标志。
                if (this.verticalCollision && !this.verticalCollisionBelow && dy > 0.1D) {
                    this.setFlightState(DroneFlightState.LANDING);
                    return;
                }
                this.setDeltaMovement(0.0D, Mth.clamp(dy * 0.3D, -0.12D, 0.12D), 0.0D);
            }
            case HOVERING -> {
                if (!this.canKeepHovering()) {
                    this.setFlightState(DroneFlightState.LANDING);
                    return;
                }
                double target = this.idleHoverTargetY();
                if (Double.isNaN(target)) {
                    this.setFlightState(DroneFlightState.LANDING);
                    return;
                }
                this.setDeltaMovement(0.0D, Mth.clamp((target - this.getY()) * 0.3D, -0.1D, 0.1D), 0.0D);
            }
            case LANDING -> {
                Vec3 motion = this.getDeltaMovement();
                this.setDeltaMovement(motion.x * 0.6D, -0.12D, motion.z * 0.6D);
                if (this.onGround()) {
                    this.setDeltaMovement(Vec3.ZERO);
                    this.setFlightState(DroneFlightState.LANDED);
                }
            }
            case DOCKING -> this.serverDockingTick();
            case FLYING -> {
                if (!this.navigator.follow(this)) {
                    this.setDeltaMovement(Vec3.ZERO);
                }
            }
        }
    }

    /**
     * 飞向目标无人机站的顶部泊位。站点按登记顺序排队:队首独占顶部对准点,
     * 其余在站顶上方的方阵等待格位悬停,轮到后再飞向对准点,不再向同一点互相推挤;
     * 站点消失则取消入库并降落。入库成功时实体数据已原子转入站内,随后移除实体。
     */
    private void serverDockingTick() {
        BlockPos stationPos = this.dockStationPos;
        DroneStationBlockEntity station = stationPos != null
            && this.level().getBlockEntity(stationPos) instanceof DroneStationBlockEntity found
            ? found
            : null;
        if (station == null) {
            this.dockStationPos = null;
            this.setFlightState(DroneFlightState.LANDING);
            return;
        }
        DroneStationBlockEntity.DockAssignment assignment = station.assignDockTarget(this);
        Vec3 delta = assignment.target().subtract(this.position());
        if (delta.length() < 0.35D) {
            if (assignment.head() && station.canAcceptDocking() && station.tryDock(this)) {
                this.dockStationPos = null;
                this.discard();
                return;
            }
            // 队首泊位忙(下沉动画或站满)在对准点保持;其余在各自方阵格位保持,
            // 目标互异,不会因互相推挤而不断抬升。
            this.setDeltaMovement(delta.scale(0.2D));
            return;
        }
        double speed = Math.min(0.25D, delta.length() * 0.25D);
        Vec3 motion = delta.normalize().scale(speed);
        if (this.horizontalCollision) {
            if (!this.hasDroneContactTowards(motion) || this.getY() < assignment.target().y + 1.0D) {
                // 无寻路的基础版本:被方块挡住时无上限爬升越障;与另一架无人机卡位时
                // 也先爬升,但只允许爬到目标上方一格,避免互相垫高不断抬升。
                motion = new Vec3(motion.x * 0.2D, 0.12D, motion.z * 0.2D);
            } else {
                // 已到爬升上限仍与同高的无人机相互顶住:向自身右侧绕行错开;
                // 双方都向右让,打破对称死锁后各自继续飞向互异目标。
                motion = new Vec3(-motion.z, 0.0D, motion.x).scale(0.6D);
            }
        }
        this.setDeltaMovement(motion);
    }

    /** 判断水平前进方向上是否贴着另一架无人机,用于区分实体卡位与方块阻挡。 */
    private boolean hasDroneContactTowards(Vec3 motion) {
        Vec3 horizontal = new Vec3(motion.x, 0.0D, motion.z);
        if (horizontal.lengthSqr() < 1.0E-8D) return false;
        Vec3 probe = horizontal.normalize().scale(0.1D);
        return !this.level().getEntities(
            this,
            this.getBoundingBox().expandTowards(probe),
            entity -> entity instanceof DroneEntity
        ).isEmpty();
    }

    /**
     * 接受站点召回,转入入库飞行。已在入库途中、正在执行入库的无人机不重复接受;
     * 返回是否真正开始返站。
     */
    public boolean startDockingTo(BlockPos stationPos) {
        if (this.level().isClientSide || this.isRemoved()) return false;
        if (this.flightState() == DroneFlightState.DOCKING) return false;
        this.dockStationPos = stationPos.immutable();
        this.setFlightState(DroneFlightState.DOCKING);
        return true;
    }

    /** 站点排队清理用:该无人机是否仍在飞向指定站点入库。 */
    public boolean isDockingTo(BlockPos stationPos) {
        return this.flightState() == DroneFlightState.DOCKING && stationPos.equals(this.dockStationPos);
    }

    /** 无任务观察无人机有电即离地悬停;其余工种落地等待。 */
    private boolean wantsIdleHover() {
        return this.toolDefinition().hasCapability(DroneCapability.CHUNK_LOADING);
    }

    /** 世界观察无人机必须始终保留安全降落电量,达到储备立即降落停耗。 */
    private boolean canKeepHovering() {
        return this.wantsIdleHover() && this.energy > DroneEnergyModel.SAFE_LANDING_RESERVE;
    }

    /**
     * 计算无任务悬停目标高度:碰撞箱底面保持在当前 X/Z 下方最近稳定、
     * 非流体碰撞顶面之上 4 格;向下有限搜索,找不到地面返回 NaN 保持落地。
     */
    private double idleHoverTargetY() {
        BlockPos.MutableBlockPos cursor = this.blockPosition().mutable();
        for (int step = 0; step <= GROUND_SCAN_RANGE; step++) {
            if (cursor.getY() < this.level().getMinBuildHeight()) return Double.NaN;
            BlockState state = this.level().getBlockState(cursor);
            VoxelShape shape = state.getFluidState().isEmpty()
                ? state.getCollisionShape(this.level(), cursor)
                : Shapes.empty();
            if (!shape.isEmpty()) {
                return cursor.getY() + shape.max(Direction.Axis.Y) + 4.0D;
            }
            cursor.move(0, -1, 0);
        }
        return Double.NaN;
    }

    /** 空中每 gt 扣悬浮费,并按实际轨迹长度累计距离费;着地不扣。 */
    private void serverEnergyCostTick(Vec3 movedDelta) {
        if (this.onGround()) return;
        this.consumeEnergy(DroneEnergyModel.HOVER_COST_PER_AIR_TICK);
        this.flightDistanceAccumulator += movedDelta.length();
        while (this.flightDistanceAccumulator >= 1.0D) {
            this.consumeEnergy(DroneEnergyModel.MOVE_COST_PER_BLOCK);
            this.flightDistanceAccumulator -= 1.0D;
        }
    }

    /** 碰撞箱真正重叠的无人机互相推开;整齐堆放的相邻碰撞箱不会触发。 */
    private void pushOverlappingDrones() {
        List<Entity> drones = this.level().getEntities(
            this,
            this.getBoundingBox(),
            entity -> entity instanceof DroneEntity && entity.isPushable()
        );
        for (Entity drone : drones) {
            this.push(drone);
        }
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean fireImmune() {
        return super.fireImmune()
            || DronePropellerTraits.isFireResistant(this.getLeftPropeller(), this.getRightPropeller());
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && stack.getItem() instanceof AnvilHammerItem) {
            return this.pickUpWithAnvilHammer(player);
        }
        if (player.isShiftKeyDown()) return InteractionResult.PASS;
        if (player instanceof ServerPlayer serverPlayer) {
            DroneMenu.openForEntity(serverPlayer, this);
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    private InteractionResult pickUpWithAnvilHammer(Player player) {
        BlockPos occupiedPos = this.blockPosition();
        if (!player.getAbilities().mayBuild || !this.level().mayInteract(player, occupiedPos)) {
            return InteractionResult.PASS;
        }
        ItemStack drop = this.getDropStack();
        if (drop.isEmpty()) return InteractionResult.FAIL;
        if (this.level().isClientSide) return InteractionResult.SUCCESS;

        player.getInventory().placeItemBackInInventory(drop);
        this.level().playSound(
            null,
            occupiedPos,
            SoundType.COPPER.getBreakSound(),
            SoundSource.BLOCKS,
            0.8F,
            1.0F
        );
        this.gameEvent(GameEvent.ENTITY_INTERACT, player);
        this.discard();
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) return false;
        if (this.level().isClientSide || this.isRemoved()) return true;
        boolean creativePlayer = source.getEntity() instanceof Player player && player.getAbilities().instabuild;
        if (!creativePlayer) {
            this.spawnAtLocation(this.getDropStack());
        }
        this.level().playSound(
            null,
            this.blockPosition(),
            SoundType.COPPER.getBreakSound(),
            SoundSource.BLOCKS,
            0.8F,
            1.0F
        );
        this.discard();
        return true;
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        this.chargeComponent.switchTo(null);
    }

    public ResourceLocation toolId() {
        return this.cachedToolId;
    }

    public DroneToolDefinition toolDefinition() {
        return DroneToolDefinitions.getOrFallback(this.toolId());
    }

    public ItemStack getLeftPropeller() {
        return this.entityData.get(DATA_LEFT_PROPELLER);
    }

    public ItemStack getRightPropeller() {
        return this.entityData.get(DATA_RIGHT_PROPELLER);
    }

    public byte getActionState() {
        return this.entityData.get(DATA_ACTION_STATE);
    }

    public DroneFlightState flightState() {
        return DroneFlightState.byId(this.entityData.get(DATA_FLIGHT_STATE));
    }

    public void setFlightState(DroneFlightState state) {
        this.entityData.set(DATA_FLIGHT_STATE, (byte) state.ordinal());
    }

    public int getEnergy() {
        return this.energy;
    }

    public void setEnergy(int energy) {
        this.energy = Mth.clamp(energy, 0, DroneEnergyModel.capacity());
    }

    public void consumeEnergy(long amount) {
        this.energy = (int) Math.max(0L, this.energy - amount);
    }

    /** 任务分配前的能量资格:报价之外还必须保留安全降落储备。 */
    public boolean canAcceptQuote(DroneEnergyModel.Quote quote) {
        long total = quote.totalCost(this.toolDefinition().instantActionEnergyCost());
        return this.energy >= total + DroneEnergyModel.SAFE_LANDING_RESERVE;
    }

    public Optional<UUID> getOwner() {
        return Optional.ofNullable(this.owner);
    }

    public void setOwner(UUID ownerId) {
        this.owner = ownerId;
    }

    public DroneShortageStrategy shortageStrategy() {
        return this.shortageStrategy;
    }

    public void setShortageStrategy(DroneShortageStrategy strategy) {
        this.shortageStrategy = strategy;
    }

    public Optional<UUID> assignedJobId() {
        return this.assignedJobId;
    }

    public int taskOpId() {
        return this.taskOpId;
    }

    public void assign(UUID jobId, int opId) {
        this.assignedJobId = Optional.of(jobId);
        this.taskOpId = opId;
    }

    /** 解除任务租约;clearCarry 只清空同步携带物,不在世界里再生成一份物品。 */
    public void clearAssignment(boolean clearCarry) {
        this.assignedJobId = Optional.empty();
        this.taskOpId = -1;
        this.navigator.clear();
        if (clearCarry) {
            this.setHostedCarry(ItemStack.EMPTY);
        }
    }

    public ItemStack hostedCarry() {
        return this.entityData.get(DATA_CARRIED_ITEM);
    }

    public void setHostedCarry(ItemStack stack) {
        this.entityData.set(DATA_CARRIED_ITEM, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
    }

    public void setActionState(byte state) {
        this.entityData.set(DATA_ACTION_STATE, state);
    }

    public ConstructionWaitReason waitReason() {
        return this.waitReason;
    }

    public void setWaitReason(ConstructionWaitReason reason) {
        this.waitReason = reason;
    }

    public DroneFlightNavigator navigator() {
        return this.navigator;
    }

    public DroneData toDroneData() {
        return new DroneData(
            this.toolId(),
            this.getLeftPropeller().copy(),
            this.getRightPropeller().copy(),
            this.energy,
            Optional.ofNullable(this.owner),
            this.shortageStrategy,
            List.copyOf(this.collectionInventory),
            this.assignedJobId,
            this.hostedCarry().copy()
        );
    }

    public void applyDroneData(DroneData data) {
        this.entityData.set(DATA_TOOL_ID, data.toolId().toString());
        this.cachedToolId = data.toolId();
        this.entityData.set(DATA_LEFT_PROPELLER, data.leftPropeller().copy());
        this.entityData.set(DATA_RIGHT_PROPELLER, data.rightPropeller().copy());
        this.energy = Mth.clamp(data.energy(), 0, DroneEnergyModel.capacity());
        this.owner = data.owner().orElse(null);
        this.shortageStrategy = data.shortageStrategy();
        this.collectionInventory = new ArrayList<>(data.collectionInventory());
        this.assignedJobId = data.assignedJobId();
        this.setHostedCarry(data.hostedCarry());
    }

    /** 铁砧锤回收与摧毁掉落共用的完整数据物品;自定义名称随物品往返。 */
    public ItemStack getDropStack() {
        ItemStack stack = new ItemStack(DroneItem.byToolId(this.toolId()));
        DroneData.set(stack, this.toDroneData());
        if (this.hasCustomName()) {
            stack.set(DataComponents.CUSTOM_NAME, this.getCustomName());
        }
        return stack;
    }

    @Override
    public ItemStack getPickResult() {
        return this.getDropStack();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (!tag.contains("DroneData")) return;
        DroneData.CODEC
            .parse(this.registryAccess().createSerializationContext(NbtOps.INSTANCE), tag.get("DroneData"))
            .resultOrPartial(error -> AnvilcraftPlasticraft.LOGGER.error("Failed to load drone data: {}", error))
            .ifPresent(this::applyDroneData);
        if (tag.contains("FlightState")) {
            this.setFlightState(DroneFlightState.byId(tag.getByte("FlightState")));
        }
        this.dockStationPos = tag.contains("DockStation")
            ? BlockPos.of(tag.getLong("DockStation"))
            : null;
        this.taskOpId = tag.contains("TaskOpId") ? tag.getInt("TaskOpId") : -1;
        if (tag.contains("WaitReason")) {
            this.waitReason = ConstructionWaitReason.byId(tag.getByte("WaitReason"));
        }
        if (tag.contains("Navigator")) {
            this.navigator.load(tag.getCompound("Navigator"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        DroneData.CODEC
            .encodeStart(this.registryAccess().createSerializationContext(NbtOps.INSTANCE), this.toDroneData())
            .resultOrPartial(error -> AnvilcraftPlasticraft.LOGGER.error("Failed to save drone data: {}", error))
            .ifPresent(encoded -> tag.put("DroneData", encoded));
        tag.putByte("FlightState", (byte) this.flightState().ordinal());
        if (this.dockStationPos != null) {
            tag.putLong("DockStation", this.dockStationPos.asLong());
        }
        tag.putInt("TaskOpId", this.taskOpId);
        tag.putByte("WaitReason", (byte) this.waitReason.ordinal());
        tag.put("Navigator", this.navigator.save());
    }
}
