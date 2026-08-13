package dev.anvilcraft.plasticraft.block.entity;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.DroneStationBlock;
import dev.anvilcraft.plasticraft.blueprint.ConstructionBlueprintData;
import dev.anvilcraft.plasticraft.drone.DroneData;
import dev.anvilcraft.plasticraft.drone.DroneEnergyModel;
import dev.anvilcraft.plasticraft.entity.drone.DroneEntity;
import dev.anvilcraft.plasticraft.init.PlasticraftDataComponents;
import dev.anvilcraft.plasticraft.init.PlasticraftMenuTypes;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlockEntities;
import dev.anvilcraft.plasticraft.inventory.DroneStationMenu;
import dev.anvilcraft.plasticraft.item.DroneItem;
import dev.dubhe.anvilcraft.AnvilCraft;
import dev.dubhe.anvilcraft.api.item.IChargerDischargeable;
import dev.dubhe.anvilcraft.api.power.IPowerConsumer;
import dev.dubhe.anvilcraft.api.power.PowerGrid;
import dev.dubhe.anvilcraft.init.item.ModItems;
import dev.dubhe.anvilcraft.item.CapacitorItem;
import dev.dubhe.anvilcraft.item.SuperCapacitorItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 无人机站方块实体。站内提供 16 个无人机槽、1 个任务结构磁盘槽和 1 个电容器充能槽,
 * 不设置材料库存;下表面是唯一物流接口面,任务材料始终来自下方容器(由任务系统 TODO 使用)。
 * 顶部泊位是单通道状态机:每 20 gt 最多入库一架,入库时实体数据原子转入站内托管对象,
 * 客户端以无碰撞停泊显示对象播放下沉动画。
 */
public class DroneStationBlockEntity extends BlockEntity implements IPowerConsumer {
    public static final int DRONE_SLOT_COUNT = 16;
    public static final int DISK_SLOT = 16;
    public static final int CAPACITOR_SLOT = 17;
    public static final int SLOT_COUNT = 18;
    /** 站点内部容量与本体超级电容器一致。 */
    public static final int RATED_POWER_KW = 256;
    /** 顶部泊位单通道:一次入库动画的时长,同时也是每架之间的最小间隔。 */
    public static final int DOCKING_DURATION_TICKS = 20;
    /** 召回指令扫描站点周围无任务无人机的半径(格)。 */
    public static final double RECALL_RANGE = 16.0D;
    /** 等待方阵每层的边长;一层容纳 16 架,满层后向上叠层。 */
    public static final int FORMATION_GRID_SIZE = 4;
    /** 等待方阵首层(无人机碰撞箱底面)相对站顶方块坐标的高度。 */
    public static final double FORMATION_BASE_OFFSET_Y = 3.0D;
    private static final int FORMATION_LAYER_CAPACITY = FORMATION_GRID_SIZE * FORMATION_GRID_SIZE;
    private static final double FORMATION_SPACING = 1.0D;

    private final ItemStackHandler items = new ItemStackHandler(SLOT_COUNT) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return DroneStationBlockEntity.isValidForSlot(slot, stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            DroneStationBlockEntity.this.setChanged();
        }
    };
    private final List<Player> viewers = new ArrayList<>();
    // 入库排队瞬态状态:队首独占顶部对准点,其余按登记顺序占用方阵格位;
    // 不持久化,重启后由仍处 DOCKING 状态的无人机重新登记。
    private final List<UUID> dockingQueue = new ArrayList<>();
    private int energy;
    @Nullable
    private PowerGrid grid;
    // 顶部泊位托管对象:入库中的无人机完整数据;断电时保留进度并暂停。
    @Nullable
    private DroneData dockingData;
    @Nullable
    private Component dockingName;
    private int dockingProgress;
    private boolean dockingRunning;
    // 客户端插值基准:最近一次同步时的进度与游戏时间。
    private long dockingSyncGameTime;

    public DroneStationBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    public DroneStationBlockEntity(BlockPos pos, BlockState blockState) {
        this(PlasticraftBlockEntities.DRONE_STATION.get(), pos, blockState);
    }

    public static int capacity() {
        return SuperCapacitorItem.ENERGY;
    }

    /** 站点活动费:任一入库/出库/任务/转运活动进行时每 gt 固定扣除一份,不随活动数叠加。 */
    public static int activityCostPerTick() {
        return Math.multiplyExact(RATED_POWER_KW, AnvilCraft.CONFIG.powerConverter.powerConverterEfficiency);
    }

    /** 电网每 gt 充入站点的 FE:256 功率按当前功率转换效率换算。 */
    public static int gridChargePerTick() {
        return Math.multiplyExact(RATED_POWER_KW, AnvilCraft.CONFIG.powerConverter.powerConverterEfficiency);
    }

    public static boolean isValidForSlot(int slot, ItemStack stack) {
        if (slot < DRONE_SLOT_COUNT) return stack.getItem() instanceof DroneItem;
        // 磁盘槽只接受携带施工蓝图的结构磁盘,空盘与成型舱蓝图盘都不入槽。
        if (slot == DISK_SLOT) {
            return stack.is(ModItems.STRUCTURE_DISK.get()) && ConstructionBlueprintData.get(stack).isPresent();
        }
        if (slot == CAPACITOR_SLOT) return capacitorEnergy(stack) > 0;
        return false;
    }

    /** 与塑料成型舱一致的电容器能量判定:只认可完整充电的普通/超级电容器。 */
    public static int capacitorEnergy(ItemStack stack) {
        if (stack.getItem() instanceof SuperCapacitorItem) return SuperCapacitorItem.ENERGY;
        if (stack.getItem() instanceof CapacitorItem) return CapacitorItem.ENERGY;
        return 0;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DroneStationBlockEntity station) {
        station.tickGridCharging();
        station.tickCapacitorSlot();
        station.tickDroneCharging();
        station.tickDocking();
        station.syncPowerState(state);
    }

    private void tickGridCharging() {
        if (!this.isGridWorking() || this.energy >= capacity()) return;
        this.energy = Math.min(capacity(), this.energy + gridChargePerTick());
        this.setChanged();
    }

    /** 电容器整颗消耗:剩余容量足以完整接收才消耗,每 gt 最多一颗,空电容器按成型舱规则返还。 */
    private void tickCapacitorSlot() {
        ItemStack input = this.items.getStackInSlot(CAPACITOR_SLOT);
        int capacitorEnergy = capacitorEnergy(input);
        if (capacitorEnergy <= 0) return;
        if (capacity() - this.energy < capacitorEnergy) return;
        if (!(input.getItem() instanceof IChargerDischargeable dischargeable)) return;
        ItemStack empty = dischargeable.discharge(input.copyWithCount(1));
        this.energy += capacitorEnergy;
        input.shrink(1);
        this.items.setStackInSlot(CAPACITOR_SLOT, input);
        this.finishCapacitorReturn(empty);
        this.setChanged();
    }

    private void finishCapacitorReturn(ItemStack empty) {
        if (this.items.getStackInSlot(CAPACITOR_SLOT).isEmpty()) {
            this.items.setStackInSlot(CAPACITOR_SLOT, empty);
            return;
        }
        for (Player viewer : this.viewers) {
            if (viewer.isAlive() && !viewer.isRemoved()) {
                viewer.getInventory().placeItemBackInInventory(empty);
                return;
            }
        }
        if (this.level != null) {
            Containers.dropItemStack(
                this.level,
                this.worldPosition.getX() + 0.5D,
                this.worldPosition.getY() + 1.0D,
                this.worldPosition.getZ() + 0.5D,
                empty
            );
        }
    }

    /** 站内充电:每架未满电无人机以 8 功率对应的 FE 速率从站点内部转移能量。 */
    private void tickDroneCharging() {
        if (this.energy <= 0) return;
        int perDrone = DroneEnergyModel.chargePerTick();
        boolean changed = false;
        for (int slot = 0; slot < DRONE_SLOT_COUNT && this.energy > 0; slot++) {
            ItemStack stack = this.items.getStackInSlot(slot);
            if (!(stack.getItem() instanceof DroneItem droneItem)) continue;
            DroneData data = DroneData.get(stack)
                .orElseGet(() -> DroneData.assembled(droneItem.definition().id(), ItemStack.EMPTY, ItemStack.EMPTY));
            int missing = DroneEnergyModel.capacity() - data.energy();
            if (missing <= 0) continue;
            int transfer = Math.min(Math.min(perDrone, missing), this.energy);
            if (transfer <= 0) continue;
            DroneData.set(stack, data.withEnergy(data.energy() + transfer));
            this.items.setStackInSlot(slot, stack);
            this.energy -= transfer;
            changed = true;
        }
        if (changed) this.setChanged();
    }

    /** 顶部泊位推进:断电时保留进度并暂停,恢复供电后从相同进度继续。 */
    private void tickDocking() {
        if (this.dockingData == null) return;
        if (this.energy <= 0) {
            if (this.dockingRunning) {
                this.dockingRunning = false;
                this.sendDockingUpdate();
            }
            return;
        }
        if (!this.dockingRunning) {
            this.dockingRunning = true;
            this.sendDockingUpdate();
        }
        this.dockingProgress++;
        this.energy -= Math.min(this.energy, activityCostPerTick());
        this.setChanged();
        if (this.dockingProgress < DOCKING_DURATION_TICKS) return;
        this.finishDocking();
    }

    private void finishDocking() {
        if (this.dockingData == null || this.level == null) return;
        ItemStack stack = new ItemStack(DroneItem.byToolId(this.dockingData.toolId()));
        DroneData.set(stack, this.dockingData);
        if (this.dockingName != null) {
            stack.set(DataComponents.CUSTOM_NAME, this.dockingName);
        }
        int slot = this.findEmptyDroneSlot();
        if (slot >= 0) {
            this.items.setStackInSlot(slot, stack);
        } else {
            // 入库期间槽位被塞满的兜底:物品从顶部舱门弹出,不覆盖已有无人机。
            Containers.dropItemStack(
                this.level,
                this.worldPosition.getX() + 0.5D,
                this.worldPosition.getY() + 1.2D,
                this.worldPosition.getZ() + 0.5D,
                stack
            );
        }
        this.dockingData = null;
        this.dockingName = null;
        this.dockingProgress = 0;
        this.dockingRunning = false;
        this.level.playSound(
            null,
            this.worldPosition,
            SoundEvents.IRON_TRAPDOOR_CLOSE,
            SoundSource.BLOCKS,
            0.6F,
            1.2F
        );
        this.sendDockingUpdate();
        this.setChanged();
    }

    /** 入库飞行目标分配结果:是否为队首,以及应当前往的坐标。 */
    public record DockAssignment(boolean head, Vec3 target) {
    }

    /**
     * 为一架入库中的无人机分配当前飞行目标。队首独占顶部对准点,其余按登记顺序
     * 前往站顶上方的方阵等待格位;目标互异,多机不再向同一点互相推挤抬升。
     */
    public DockAssignment assignDockTarget(DroneEntity drone) {
        this.pruneDockingQueue();
        UUID id = drone.getUUID();
        int index = this.dockingQueue.indexOf(id);
        if (index < 0) {
            index = this.dockingQueue.size();
            this.dockingQueue.add(id);
        }
        return index == 0
            ? new DockAssignment(true, this.dockApproachPoint())
            : new DockAssignment(false, this.formationSlotPosition(index - 1));
    }

    /** 清理已消失、已入库或不再飞向本站的排队条目,让后续无人机依次前移。 */
    private void pruneDockingQueue() {
        if (!(this.level instanceof ServerLevel serverLevel)) return;
        this.dockingQueue.removeIf(id -> {
            Entity entity = serverLevel.getEntity(id);
            return !(entity instanceof DroneEntity drone)
                || drone.isRemoved()
                || !drone.isDockingTo(this.worldPosition);
        });
    }

    /** 方阵等待格位:以站顶上方为中心的 4x4 网格,1 格间距,满 16 架向上叠层。 */
    private Vec3 formationSlotPosition(int slot) {
        int layer = slot / FORMATION_LAYER_CAPACITY;
        int cell = slot % FORMATION_LAYER_CAPACITY;
        double half = (FORMATION_GRID_SIZE - 1) / 2.0D;
        double dx = (cell % FORMATION_GRID_SIZE - half) * FORMATION_SPACING;
        double dz = (cell / FORMATION_GRID_SIZE - half) * FORMATION_SPACING;
        return new Vec3(
            this.worldPosition.getX() + 0.5D + dx,
            this.worldPosition.getY() + FORMATION_BASE_OFFSET_Y + layer,
            this.worldPosition.getZ() + 0.5D + dz
        );
    }

    /**
     * 顶部泊位接收一架世界无人机:实体完整数据原子转入托管对象,调用方随后移除实体。
     * 断电、泊位占用或站内无空槽时拒绝,不覆盖已有无人机物品。
     */
    public boolean tryDock(DroneEntity drone) {
        if (this.level == null || this.level.isClientSide) return false;
        if (this.energy <= 0) return false;
        if (this.dockingData != null) return false;
        if (this.findEmptyDroneSlot() < 0) return false;
        this.dockingQueue.remove(drone.getUUID());
        this.dockingData = drone.toDroneData();
        this.dockingName = drone.getCustomName();
        this.dockingProgress = 0;
        this.dockingRunning = true;
        this.level.playSound(
            null,
            this.worldPosition,
            SoundEvents.IRON_TRAPDOOR_OPEN,
            SoundSource.BLOCKS,
            0.6F,
            1.0F
        );
        this.sendDockingUpdate();
        this.setChanged();
        return true;
    }

    /** 顶部泊位当前是否可以接收下一架;等待的无人机在站旁悬停,不挤进同一位置。 */
    public boolean canAcceptDocking() {
        return this.energy > 0 && this.dockingData == null && this.findEmptyDroneSlot() >= 0;
    }

    private int findEmptyDroneSlot() {
        for (int slot = 0; slot < DRONE_SLOT_COUNT; slot++) {
            if (this.items.getStackInSlot(slot).isEmpty()) return slot;
        }
        return -1;
    }

    /** 召回站点周围的世界无人机依次返站入库;出库接口留给任务协调器,不在此伪造任务。 */
    public int recallNearbyDrones() {
        if (this.level == null || this.level.isClientSide) return 0;
        AABB range = new AABB(this.worldPosition).inflate(RECALL_RANGE);
        List<DroneEntity> drones = this.level.getEntitiesOfClass(DroneEntity.class, range);
        int recalled = 0;
        for (DroneEntity drone : drones) {
            if (drone.startDockingTo(this.worldPosition)) recalled++;
        }
        return recalled;
    }

    /** 顶部泊位对准点:站顶面中心。 */
    public Vec3 dockApproachPoint() {
        return new Vec3(
            this.worldPosition.getX() + 0.5D,
            this.worldPosition.getY() + 1.05D,
            this.worldPosition.getZ() + 0.5D
        );
    }

    public void openMenu(ServerPlayer player) {
        player.openMenu(
            new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new DroneStationMenu(
                    PlasticraftMenuTypes.DRONE_STATION.get(),
                    containerId,
                    inventory,
                    this
                ),
                Component.translatable("container.anvilcraftplasticraft.drone_station")
            ),
            buffer -> buffer.writeBlockPos(this.worldPosition)
        );
    }

    public void addViewer(Player player) {
        if (!this.viewers.contains(player)) this.viewers.add(player);
    }

    public void removeViewer(Player player) {
        this.viewers.remove(player);
    }

    public ItemStackHandler items() {
        return this.items;
    }

    public int energy() {
        return this.energy;
    }

    public void setEnergy(int energy) {
        this.energy = Math.clamp(energy, 0, capacity());
        this.setChanged();
    }

    @Nullable
    public DroneData dockingData() {
        return this.dockingData;
    }

    public boolean isDockingRunning() {
        return this.dockingRunning;
    }

    /** 客户端按同步基准推算的停泊动画进度;断电暂停时保持在同步进度。 */
    public float clientDockingProgress(float partialTick) {
        if (this.dockingData == null || this.level == null) return 0.0F;
        if (!this.dockingRunning) return this.dockingProgress;
        long elapsed = this.level.getGameTime() - this.dockingSyncGameTime;
        return Math.min(
            DOCKING_DURATION_TICKS,
            this.dockingProgress + Math.max(0L, elapsed) + partialTick
        );
    }

    /** 站点被拆除时槽内物品与入库中的托管无人机全部掉出,不丢失数据。 */
    public void dropContents() {
        if (this.level == null) return;
        SimpleContainer spill = new SimpleContainer(SLOT_COUNT + 1);
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            spill.setItem(slot, this.items.getStackInSlot(slot));
            this.items.setStackInSlot(slot, ItemStack.EMPTY);
        }
        if (this.dockingData != null) {
            ItemStack docking = new ItemStack(DroneItem.byToolId(this.dockingData.toolId()));
            DroneData.set(docking, this.dockingData);
            if (this.dockingName != null) docking.set(DataComponents.CUSTOM_NAME, this.dockingName);
            spill.setItem(SLOT_COUNT, docking);
            this.dockingData = null;
            this.dockingName = null;
        }
        Containers.dropContents(this.level, this.worldPosition, spill);
    }

    private void syncPowerState(BlockState state) {
        boolean powered = this.energy > 0;
        if (this.level != null && state.hasProperty(DroneStationBlock.POWERED)
            && state.getValue(DroneStationBlock.POWERED) != powered) {
            this.level.setBlock(
                this.worldPosition,
                state.setValue(DroneStationBlock.POWERED, powered),
                3
            );
        }
    }

    private void sendDockingUpdate() {
        this.dockingSyncGameTime = this.level == null ? 0L : this.level.getGameTime();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    // ==================== IPowerConsumer ====================

    @Override
    public int getInputPower() {
        return this.energy < capacity() ? RATED_POWER_KW : 0;
    }

    @Override
    public void setGrid(@Nullable PowerGrid grid) {
        this.grid = grid;
    }

    @Override
    @Nullable
    public PowerGrid getGrid() {
        return this.grid;
    }

    @Override
    public BlockPos getPos() {
        return this.getBlockPos();
    }

    @Override
    @Nullable
    public Level getCurrentLevel() {
        return this.level;
    }

    // ==================== 存档与同步 ====================

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Items", this.items.serializeNBT(registries));
        tag.putInt("Energy", this.energy);
        this.saveDockingState(tag, registries);
    }

    private void saveDockingState(CompoundTag tag, HolderLookup.Provider registries) {
        if (this.dockingData == null) return;
        DroneData.CODEC
            .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), this.dockingData)
            .resultOrPartial(error -> AnvilcraftPlasticraft.LOGGER.error("Failed to save docking drone: {}", error))
            .ifPresent(encoded -> tag.put("DockingDrone", encoded));
        if (this.dockingName != null) {
            tag.putString("DockingName", Component.Serializer.toJson(this.dockingName, registries));
        }
        tag.putInt("DockingProgress", this.dockingProgress);
        tag.putBoolean("DockingRunning", this.dockingRunning);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Items")) this.items.deserializeNBT(registries, tag.getCompound("Items"));
        if (tag.contains("Energy")) this.energy = tag.getInt("Energy");
        this.dockingData = null;
        this.dockingName = null;
        this.dockingProgress = 0;
        this.dockingRunning = false;
        if (tag.contains("DockingDrone")) {
            DroneData.CODEC
                .parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("DockingDrone"))
                .resultOrPartial(error ->
                    AnvilcraftPlasticraft.LOGGER.error("Failed to load docking drone: {}", error))
                .ifPresent(data -> this.dockingData = data);
            if (tag.contains("DockingName")) {
                this.dockingName = Component.Serializer.fromJson(tag.getString("DockingName"), registries);
            }
            this.dockingProgress = tag.getInt("DockingProgress");
            this.dockingRunning = tag.getBoolean("DockingRunning");
        }
        this.dockingSyncGameTime = this.level == null ? 0L : this.level.getGameTime();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("Energy", this.energy);
        this.saveDockingState(tag, registries);
        return tag;
    }

    @Override
    @Nullable
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ==================== 物品组件往返:拆下与重放保留内部 FE ====================

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        builder.set(PlasticraftDataComponents.STATION_ENERGY.get(), this.energy);
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput input) {
        super.applyImplicitComponents(input);
        this.energy = input.getOrDefault(PlasticraftDataComponents.STATION_ENERGY.get(), 0);
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove("Energy");
    }
}
