package dev.anvilcraft.plasticraft.block.entity;

import com.mojang.serialization.Codec;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.allay.AllayShortageStrategy;
import dev.anvilcraft.plasticraft.allay.AllayWorkRecord;
import dev.anvilcraft.plasticraft.block.AllayLoungeBlock;
import dev.anvilcraft.plasticraft.blueprint.ConstructionBlueprintData;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobController;
import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;
import dev.anvilcraft.plasticraft.init.PlasticraftMenuTypes;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlockEntities;
import dev.anvilcraft.plasticraft.inventory.AllayLoungeMenu;
import dev.dubhe.anvilcraft.api.power.IPowerConsumer;
import dev.dubhe.anvilcraft.api.power.PowerGrid;
import dev.dubhe.anvilcraft.init.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 悦灵休息室。最多托管 16 只戴帽悦灵,另有 1 个结构磁盘槽。
 * 固定向电网请求 16 功率;有电才能召回与入库。破坏时把托管悦灵生成回世界。
 */
public class AllayLoungeBlockEntity extends BlockEntity implements IPowerConsumer {
    public static final int HOST_CAPACITY = 16;
    public static final int DISK_SLOT = 0;
    public static final int RATED_POWER_KW = 16;
    public static final int DOCKING_DURATION_TICKS = 20;
    public static final double RECALL_RANGE = 16.0D;
    public static final int FORMATION_GRID_SIZE = 4;
    public static final double FORMATION_BASE_OFFSET_Y = 3.0D;
    private static final int FORMATION_LAYER_CAPACITY = FORMATION_GRID_SIZE * FORMATION_GRID_SIZE;
    private static final double FORMATION_SPACING = 1.0D;
    private static final Codec<List<AllayWorkRecord>> HOSTS_CODEC = AllayWorkRecord.CODEC.listOf();

    private final ItemStackHandler items = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return AllayLoungeBlockEntity.isValidDisk(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            AllayLoungeBlockEntity.this.onDiskChanged();
            AllayLoungeBlockEntity.this.setChanged();
        }
    };
    private final List<AllayWorkRecord> hosted = new ArrayList<>();
    private final List<Player> viewers = new ArrayList<>();
    private final List<UUID> dockingQueue = new ArrayList<>();
    private final EnumMap<Direction, ItemStack> pickupDisplays = new EnumMap<>(Direction.class);
    @Nullable
    private PowerGrid grid;
    @Nullable
    private AllayWorkRecord dockingRecord;
    private int dockingProgress;
    private boolean dockingRunning;
    private long dockingSyncGameTime;
    private AllayShortageStrategy shortageStrategy = AllayShortageStrategy.PAUSE;
    private boolean loading;
    @Nullable
    private UUID lastDiskJobId;

    public AllayLoungeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    public AllayLoungeBlockEntity(BlockPos pos, BlockState blockState) {
        this(PlasticraftBlockEntities.ALLAY_LOUNGE.get(), pos, blockState);
    }

    public static boolean isValidDisk(ItemStack stack) {
        return stack.is(ModItems.STRUCTURE_DISK.get()) && ConstructionBlueprintData.get(stack).isPresent();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AllayLoungeBlockEntity lounge) {
        lounge.tickDocking();
        lounge.syncPowerState(state);
    }

    private void tickDocking() {
        if (this.dockingRecord == null && !this.dockingRunning) return;
        if (!this.isPowered()) {
            if (this.dockingRunning && this.dockingRecord != null) {
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
        this.setChanged();
        if (this.dockingProgress < DOCKING_DURATION_TICKS) return;
        if (this.dockingRecord == null) {
            this.dockingProgress = 0;
            this.dockingRunning = false;
            this.sendDockingUpdate();
            this.setChanged();
            return;
        }
        this.finishDocking();
    }

    private void finishDocking() {
        if (this.dockingRecord == null) return;
        if (this.hosted.size() < HOST_CAPACITY) {
            this.hosted.add(this.dockingRecord);
        } else if (this.level instanceof ServerLevel serverLevel) {
            this.spawnBound(serverLevel, this.dockApproachPoint().add(0.0D, 0.2D, 0.0D), this.dockingRecord);
        }
        this.dockingRecord = null;
        this.dockingProgress = 0;
        this.dockingRunning = false;
        if (this.level != null) {
            this.level.playSound(
                null,
                this.worldPosition,
                SoundEvents.IRON_TRAPDOOR_CLOSE,
                SoundSource.BLOCKS,
                0.6F,
                1.2F
            );
        }
        this.sendDockingUpdate();
        this.setChanged();
    }

    public record DockAssignment(boolean head, Vec3 target) {
    }

    public DockAssignment assignDockTarget(WorkingAllayEntity worker) {
        this.pruneDockingQueue();
        UUID id = worker.getUUID();
        int index = this.dockingQueue.indexOf(id);
        if (index < 0) {
            index = this.dockingQueue.size();
            this.dockingQueue.add(id);
        }
        return index == 0
            ? new DockAssignment(true, this.dockApproachPoint())
            : new DockAssignment(false, this.formationSlotPosition(index - 1));
    }

    private void pruneDockingQueue() {
        if (!(this.level instanceof ServerLevel serverLevel)) return;
        this.dockingQueue.removeIf(id -> {
            Entity entity = serverLevel.getEntity(id);
            return !(entity instanceof WorkingAllayEntity worker)
                || worker.isRemoved()
                || !worker.isDockingTo(this.worldPosition);
        });
    }

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

    public boolean tryDock(WorkingAllayEntity worker) {
        if (this.level == null || this.level.isClientSide) return false;
        if (!this.isPowered()) return false;
        if (this.isBayBusy()) return false;
        if (this.hosted.size() >= HOST_CAPACITY) return false;
        this.dockingQueue.remove(worker.getUUID());
        this.dockingRecord = worker.toWorkRecord();
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

    public boolean canAcceptDocking() {
        return this.isPowered() && !this.isBayBusy() && this.hosted.size() < HOST_CAPACITY;
    }

    public boolean isBayBusy() {
        return this.dockingRecord != null || this.dockingRunning;
    }

    public int recallNearbyWorkers() {
        if (this.level == null || this.level.isClientSide || !this.isPowered()) return 0;
        AABB range = new AABB(this.worldPosition).inflate(RECALL_RANGE);
        List<WorkingAllayEntity> workers = this.level.getEntitiesOfClass(WorkingAllayEntity.class, range);
        int recalled = 0;
        for (WorkingAllayEntity worker : workers) {
            if (worker.startDockingTo(this.worldPosition)) recalled++;
        }
        return recalled;
    }

    public boolean releaseHosted(int index) {
        if (!(this.level instanceof ServerLevel serverLevel) || !this.isPowered()) return false;
        if (this.isBayBusy()) return false;
        if (index < 0 || index >= this.hosted.size()) return false;
        AllayWorkRecord record = this.hosted.remove(index);
        this.spawnBound(serverLevel, this.releasePoint(), record);
        this.occupyOutboundBay();
        return true;
    }

    public boolean tryLaunch(Predicate<AllayWorkRecord> match) {
        if (!(this.level instanceof ServerLevel serverLevel) || !this.isPowered()) return false;
        if (this.isBayBusy()) return false;
        for (int index = 0; index < this.hosted.size(); index++) {
            AllayWorkRecord record = this.hosted.get(index);
            if (!match.test(record)) continue;
            this.hosted.remove(index);
            this.spawnBound(serverLevel, this.releasePoint(), record);
            this.occupyOutboundBay();
            return true;
        }
        return false;
    }

    private void occupyOutboundBay() {
        this.dockingRecord = null;
        this.dockingProgress = 0;
        this.dockingRunning = true;
        this.setChanged();
        this.sendDockingUpdate();
    }

    public void releaseAllToWorld() {
        if (this.level instanceof ServerLevel serverLevel) {
            ConstructionJobController.unclaimLounge(serverLevel, this.worldPosition, this.diskJobId());
        }
        this.lastDiskJobId = null;
        if (!(this.level instanceof ServerLevel serverLevel)) {
            this.dropDisk();
            return;
        }
        Vec3 spawn = this.releasePoint();
        if (this.dockingRecord != null) {
            this.spawnBound(serverLevel, spawn, this.dockingRecord);
            this.dockingRecord = null;
        }
        for (AllayWorkRecord record : this.hosted) {
            this.spawnBound(serverLevel, spawn, record);
        }
        this.hosted.clear();
        this.dropDisk();
    }

    private void dropDisk() {
        ItemStack disk = this.items.getStackInSlot(DISK_SLOT);
        if (disk.isEmpty() || this.level == null) return;
        Vec3 pos = Vec3.atCenterOf(this.worldPosition);
        this.level.addFreshEntity(new ItemEntity(this.level, pos.x, pos.y, pos.z, disk.copy()));
        this.items.setStackInSlot(DISK_SLOT, ItemStack.EMPTY);
    }

    private WorkingAllayEntity spawnBound(ServerLevel level, Vec3 pos, AllayWorkRecord record) {
        WorkingAllayEntity worker = WorkingAllayEntity.spawnFromRecord(level, pos, record);
        worker.setHomeLounge(this.worldPosition);
        return worker;
    }

    public AllayShortageStrategy shortageStrategy() {
        return this.shortageStrategy;
    }

    public void setShortageStrategy(AllayShortageStrategy strategy) {
        this.shortageStrategy = strategy == null ? AllayShortageStrategy.PAUSE : strategy;
        this.setChanged();
        this.sendDockingUpdate();
        if (this.level instanceof ServerLevel serverLevel) {
            ConstructionJobController.onLoungeShortageStrategyChanged(
                serverLevel,
                this.worldPosition,
                this.shortageStrategy
            );
        }
    }

    public void setPickupDisplay(Direction side, ItemStack stack) {
        if (!side.getAxis().isHorizontal()) return;
        this.pickupDisplays.put(side, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        this.sendDockingUpdate();
        this.setChanged();
    }

    public void clearPickupDisplays() {
        this.pickupDisplays.clear();
        this.sendDockingUpdate();
        this.setChanged();
    }

    public ItemStack pickupDisplay(Direction side) {
        if (!this.shouldShowPickup()) return ItemStack.EMPTY;
        return this.pickupDisplays.getOrDefault(side, ItemStack.EMPTY);
    }

    private boolean shouldShowPickup() {
        if (this.level == null) return false;
        BlockState state = this.getBlockState();
        if (state.hasProperty(AllayLoungeBlock.POWERED)) {
            return state.getValue(AllayLoungeBlock.POWERED);
        }
        return this.isPowered();
    }

    public Map<Direction, ItemStack> pickupDisplays() {
        return Map.copyOf(this.pickupDisplays);
    }

    @Nullable
    public UUID diskJobId() {
        return ConstructionBlueprintData.get(this.items.getStackInSlot(DISK_SLOT))
            .flatMap(ConstructionBlueprintData::jobId)
            .orElse(null);
    }

    public void clearDiskJobId(UUID jobId) {
        ItemStack disk = this.items.getStackInSlot(DISK_SLOT);
        ConstructionBlueprintData data = ConstructionBlueprintData.get(disk).orElse(null);
        if (data == null || data.jobId().filter(jobId::equals).isEmpty()) return;
        this.lastDiskJobId = null;
        ConstructionBlueprintData.set(disk, data.withoutJobId());
        this.setChanged();
        this.sendDockingUpdate();
    }

    private void onDiskChanged() {
        if (this.loading || this.level == null || this.level.isClientSide) return;
        if (!(this.level instanceof ServerLevel serverLevel)) return;
        UUID now = this.diskJobId();
        if (Objects.equals(now, this.lastDiskJobId)) return;
        UUID previous = this.lastDiskJobId;
        this.lastDiskJobId = now;
        if (previous != null) {
            ConstructionJobController.unclaimLounge(serverLevel, this.worldPosition, previous);
        }
        if (now != null) {
            ConstructionJobController.claimLounge(serverLevel, this.worldPosition, now);
        }
    }

    public Vec3 dockApproachPoint() {
        return new Vec3(
            this.worldPosition.getX() + 0.5D,
            this.worldPosition.getY() + 1.05D,
            this.worldPosition.getZ() + 0.5D
        );
    }

    public Vec3 releasePoint() {
        return new Vec3(
            this.worldPosition.getX() + 0.5D,
            this.worldPosition.getY() + 1.2D,
            this.worldPosition.getZ() + 0.5D
        );
    }

    public void openMenu(ServerPlayer player) {
        player.openMenu(
            new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new AllayLoungeMenu(
                    PlasticraftMenuTypes.ALLAY_LOUNGE.get(),
                    containerId,
                    inventory,
                    this
                ),
                Component.translatable("container.anvilcraftplasticraft.allay_lounge")
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

    public List<AllayWorkRecord> hosted() {
        return List.copyOf(this.hosted);
    }

    public boolean addHosted(AllayWorkRecord record) {
        if (this.hosted.size() >= HOST_CAPACITY) return false;
        this.hosted.add(record);
        this.setChanged();
        return true;
    }

    @Nullable
    public AllayWorkRecord dockingRecord() {
        return this.dockingRecord;
    }

    public int dockingProgress() {
        return this.dockingProgress;
    }

    public boolean isDockingRunning() {
        return this.dockingRunning;
    }

    public float clientDockingProgress(float partialTick) {
        if (!this.dockingRunning || this.level == null) return this.dockingProgress;
        long elapsed = this.level.getGameTime() - this.dockingSyncGameTime;
        return this.dockingProgress + elapsed + partialTick;
    }

    public boolean isPowered() {
        return this.grid != null && this.grid.isWorking();
    }

    private void syncPowerState(BlockState state) {
        if (this.level == null || this.level.isClientSide) return;
        boolean powered = this.isPowered();
        if (state.getValue(AllayLoungeBlock.POWERED) != powered) {
            this.level.setBlock(this.worldPosition, state.setValue(AllayLoungeBlock.POWERED, powered), 3);
        }
    }

    private void sendDockingUpdate() {
        this.dockingSyncGameTime = this.level == null ? 0L : this.level.getGameTime();
        if (this.level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    @Override
    public int getInputPower() {
        return RATED_POWER_KW;
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

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Items", this.items.serializeNBT(registries));
        tag.putString("ShortageStrategy", this.shortageStrategy.getSerializedName());
        HOSTS_CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), this.hosted)
            .resultOrPartial(error -> AnvilcraftPlasticraft.LOGGER.error("Failed to save lounge hosts: {}", error))
            .ifPresent(encoded -> tag.put("Hosted", encoded));
        this.saveDockingState(tag, registries);
        this.savePickupDisplays(tag, registries);
    }

    private void saveDockingState(CompoundTag tag, HolderLookup.Provider registries) {
        if (this.dockingRecord != null) {
            AllayWorkRecord.CODEC
                .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), this.dockingRecord)
                .resultOrPartial(error -> AnvilcraftPlasticraft.LOGGER.error("Failed to save docking allay: {}", error))
                .ifPresent(encoded -> tag.put("DockingAllay", encoded));
        }
        if (this.dockingRecord != null || this.dockingRunning) {
            tag.putInt("DockingProgress", this.dockingProgress);
            tag.putBoolean("DockingRunning", this.dockingRunning);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.loading = true;
        if (tag.contains("Items")) this.items.deserializeNBT(registries, tag.getCompound("Items"));
        this.lastDiskJobId = this.diskJobId();
        this.loadPickupDisplays(tag, registries);
        this.loading = false;
        this.shortageStrategy = AllayShortageStrategy.SKIP.getSerializedName().equals(tag.getString("ShortageStrategy"))
            ? AllayShortageStrategy.SKIP
            : AllayShortageStrategy.PAUSE;
        this.hosted.clear();
        if (tag.contains("Hosted")) {
            HOSTS_CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("Hosted"))
                .resultOrPartial(error -> AnvilcraftPlasticraft.LOGGER.error("Failed to load lounge hosts: {}", error))
                .ifPresent(this.hosted::addAll);
        }
        this.dockingRecord = null;
        this.dockingProgress = 0;
        this.dockingRunning = false;
        if (tag.contains("DockingAllay")) {
            AllayWorkRecord.CODEC
                .parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("DockingAllay"))
                .resultOrPartial(error -> AnvilcraftPlasticraft.LOGGER.error("Failed to load docking allay: {}", error))
                .ifPresent(record -> this.dockingRecord = record);
        }
        if (tag.contains("DockingProgress") || tag.contains("DockingRunning")) {
            this.dockingProgress = tag.getInt("DockingProgress");
            this.dockingRunning = tag.getBoolean("DockingRunning");
        }
        this.dockingSyncGameTime = this.level == null ? 0L : this.level.getGameTime();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        this.saveDockingState(tag, registries);
        HOSTS_CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), this.hosted)
            .resultOrPartial(error -> AnvilcraftPlasticraft.LOGGER.error("Failed to sync lounge hosts: {}", error))
            .ifPresent(encoded -> tag.put("Hosted", encoded));
        this.savePickupDisplays(tag, registries);
        return tag;
    }

    private void savePickupDisplays(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag displays = new CompoundTag();
        for (Map.Entry<Direction, ItemStack> entry : this.pickupDisplays.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            displays.put(entry.getKey().getSerializedName(), entry.getValue().save(registries));
        }
        if (!displays.isEmpty()) {
            tag.put("PickupDisplays", displays);
        }
    }

    private void loadPickupDisplays(CompoundTag tag, HolderLookup.Provider registries) {
        this.pickupDisplays.clear();
        if (!tag.contains("PickupDisplays", Tag.TAG_COMPOUND)) return;
        CompoundTag displays = tag.getCompound("PickupDisplays");
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (!displays.contains(side.getSerializedName())) continue;
            ItemStack.parse(registries, displays.getCompound(side.getSerializedName()))
                .ifPresent(stack -> this.pickupDisplays.put(side, stack));
        }
    }

    @Override
    @Nullable
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
