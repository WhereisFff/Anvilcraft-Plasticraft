package dev.anvilcraft.plasticraft.block.entity;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.PlasticMoldingChamberBlock;
import dev.anvilcraft.plasticraft.block.PlasticMoldingChamberStructure;
import dev.anvilcraft.plasticraft.block.PlasticMoldingMachineState;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlockEntities;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.inventory.PlasticMoldingChamberMenu;
import dev.anvilcraft.plasticraft.molding.bake.BakedMoldingModel;
import dev.anvilcraft.plasticraft.molding.bake.ManufacturedMoldingGeometry;
import dev.anvilcraft.plasticraft.molding.bake.MoldingModelBaker;
import dev.anvilcraft.plasticraft.molding.bake.MoldingVolumeMask;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprintDisk;
import dev.anvilcraft.plasticraft.molding.machine.MoldingMachineAction;
import dev.anvilcraft.plasticraft.molding.machine.MoldingFormingMode;
import dev.anvilcraft.plasticraft.molding.machine.MoldingPowerBridge;
import dev.anvilcraft.plasticraft.molding.machine.MoldingProcessSnapshot;
import dev.anvilcraft.plasticraft.molding.machine.MoldingPrintingPlan;
import dev.anvilcraft.plasticraft.molding.machine.MoldingPrintingVoxel;
import dev.anvilcraft.plasticraft.molding.machine.MoldingProductionMode;
import dev.anvilcraft.plasticraft.molding.machine.MoldingWaitReason;
import dev.anvilcraft.plasticraft.molding.machine.PlasticMoldingAnvilProcessor;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticSurfaceAdapter;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductPreview;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import dev.anvilcraft.plasticraft.molding.type.MoldingTypeValidation;
import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.MoldingCommand;
import dev.anvilcraft.plasticraft.molding.model.MoldingModelBounds;
import dev.anvilcraft.plasticraft.molding.model.MoldingModelPersistence;
import dev.anvilcraft.plasticraft.molding.session.MoldingSessionSnapshot;
import dev.anvilcraft.plasticraft.network.MoldingSessionStatusPacket;
import dev.dubhe.anvilcraft.api.injection.tooltip.ITooltipProviderExtension;
import dev.dubhe.anvilcraft.api.item.IChargerDischargeable;
import dev.dubhe.anvilcraft.api.power.IPowerConsumer;
import dev.dubhe.anvilcraft.api.power.PowerGrid;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import dev.dubhe.anvilcraft.init.item.ModItems;
import dev.dubhe.anvilcraft.item.CapacitorItem;
import dev.dubhe.anvilcraft.item.SuperCapacitorItem;
import dev.dubhe.anvilcraft.util.UnitUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 保存成型舱模型、生产资源、自动化状态和单写者编辑租约。 */
public class PlasticMoldingChamberBlockEntity extends BlockEntity
    implements IPowerConsumer, ITooltipProviderExtension {
    public static final int CLAY_SLOT = 0;
    public static final int DISK_SLOT = 1;
    public static final int RESOURCE_SLOT = 2;
    public static final int INVENTORY_SIZE = 3;
    public static final int HISTORY_LIMIT = 10;
    public static final long LEASE_TICKS = 200L;
    public static final int CLAY_LIMIT_MIN = 1;
    public static final int CLAY_LIMIT_MAX = 256;
    public static final int DEFAULT_CLAY_LIMIT = 256;
    public static final int CLAY_COLLECTION_INTERVAL = 40;
    public static final int MOLD_FILL_LAYERS = 3;
    public static final int MOLD_FILL_LAYER_TICKS = 4;
    public static final int MOLD_FILL_TICKS = MOLD_FILL_LAYERS * MOLD_FILL_LAYER_TICKS;
    public static final int STAGING_TANK_CAPACITY = 8 * FluidType.BUCKET_VOLUME;
    public static final int MOLDING_PUMP_RATE = FluidType.BUCKET_VOLUME / 4;
    public static final int PRINTING_PUMP_RATE = MOLDING_PUMP_RATE;
    public static final int PRINTING_CONSUMPTION_RATE = 1;
    public static final int MINIMUM_PROCESS_MELT = 250;
    public static final int MENU_DATA_COUNT = 20;
    private static final int REPAIR_INTERVAL = 20;
    private static final int CLIENT_SYNC_INTERVAL = 4;
    private static final String TAG_MODEL = "EditableModel";
    private static final String TAG_REVISION = "ModelRevision";
    private static final String TAG_MACHINE_STATE = "MachineState";
    private static final String TAG_INVENTORY = "Inventory";
    private static final String TAG_CLAY_COUNT = "ClayCount";
    private static final String TAG_STRUCTURE_COMPLETE = "StructureComplete";
    private static final String TAG_CLAY_LIMIT = "ClayLimit";
    private static final String TAG_MOLDED_CLAY = "MoldedClay";
    private static final String TAG_REQUIRED_CLAY = "RequiredClay";
    private static final String TAG_FILL_PROGRESS = "MoldFillProgress";
    private static final String TAG_STAGING_TANK = "StagingTank";
    private static final String TAG_BATCH_TANK = "BatchTank";
    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_SELECTED_MODE = "SelectedMode";
    private static final String TAG_CYCLE_MODE = "CycleMode";
    private static final String TAG_REDSTONE_POWERED = "RedstonePowered";
    private static final String TAG_WAIT_REASON = "WaitReason";
    private static final String TAG_TYPE_OVERRIDE = "TypeOverrideCommitted";
    private static final String TAG_CREATIVE_OVERRIDE = "CreativeOverrideLocked";
    private static final String TAG_CYCLE_FORMING_MODE = "CycleFormingMode";
    private static final String TAG_PRINTING_PROGRESS = "PrintingProgress";
    private static final String TAG_PRINTING_TOTAL = "PrintingTotal";
    private static final String TAG_PRINTING_MATERIAL = "PrintingMaterial";
    private static final String TAG_PRINTING_MELT_CONSUMED = "PrintingMeltConsumed";

    private final SimpleContainer inventory = createInventory();
    private final Deque<EditableMoldingModel> undoHistory = new ArrayDeque<>();
    private final Deque<EditableMoldingModel> redoHistory = new ArrayDeque<>();
    private final FluidTank stagingTank = new FluidTank(STAGING_TANK_CAPACITY) {
        @Override
        public boolean isFluidValid(FluidStack stack) {
            return PlasticMoldingChamberBlockEntity.this.isCompatibleStagingFluid(stack);
        }

        @Override
        protected void onContentsChanged() {
            PlasticMoldingChamberBlockEntity.this.onFluidChanged();
        }
    };
    private final FluidTank batchTank = new FluidTank(0) {
        @Override
        public boolean isFluidValid(FluidStack stack) {
            return isPlasticMelt(stack);
        }

        @Override
        protected void onContentsChanged() {
            PlasticMoldingChamberBlockEntity.this.onFluidChanged();
        }
    };
    private final IFluidHandler fluidHandler = new ChamberFluidHandler();
    private final IItemHandler clayItemHandler = new ClayItemHandler();
    private final IEnergyStorage energyStorage = new ChamberEnergyStorage();
    private final ContainerData menuData = new ChamberMenuData();
    private EditableMoldingModel model = EditableMoldingModel.empty();
    private BakedMoldingModel bakedModel = MoldingModelBaker.bake(this.model);
    private PlasticMoldingMachineState machineState = PlasticMoldingMachineState.EDITABLE;
    private MoldingProductionMode selectedMode = MoldingProductionMode.REDSTONE;
    private MoldingProductionMode cycleMode = MoldingProductionMode.REDSTONE;
    private MoldingFormingMode cycleFormingMode = MoldingFormingMode.CASTING;
    private MoldingWaitReason waitReason = MoldingWaitReason.NONE;
    private long revision;
    private boolean structureComplete;
    private boolean redstonePowered;
    private int repairCountdown;
    private int clayCollectionCountdown = CLAY_COLLECTION_INTERVAL;
    private long lastClientSyncGameTime = Long.MIN_VALUE;
    private boolean clientSyncPending;
    private int clayLimit = DEFAULT_CLAY_LIMIT;
    private int moldedClayBalls;
    private int requiredClayBalls;
    private int moldFillProgress;
    private int energy;
    private boolean typeOverrideCommitted;
    private boolean creativeOverrideLocked;
    private int printingProgress;
    private int printingTotal;
    private FluidStack printingMaterial = FluidStack.EMPTY;
    private int printingMeltConsumed;
    @Nullable
    private MoldingPrintingPlan printingPlan;
    @Nullable
    private MoldingProcessSnapshot processingSnapshot;
    @Nullable
    private PowerGrid grid;
    @Nullable
    private UUID writerPlayerId;
    @Nullable
    private UUID writerSessionId;
    private String writerName = "";
    private long leaseExpiresAt;

    public PlasticMoldingChamberBlockEntity(BlockPos pos, BlockState state) {
        this(PlasticraftBlockEntities.PLASTIC_MOLDING_CHAMBER.get(), pos, state);
    }

    public PlasticMoldingChamberBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.inventory.addListener(ignored -> this.setChanged());
    }

    public static void serverTick(
        Level level,
        BlockPos pos,
        BlockState state,
        PlasticMoldingChamberBlockEntity chamber
    ) {
        if (chamber.expireLease(level.getGameTime()) && level instanceof ServerLevel serverLevel) {
            MoldingSessionStatusPacket.broadcast(serverLevel, chamber);
        }
        if (chamber.repairCountdown-- <= 0) {
            chamber.repairCountdown = REPAIR_INTERVAL;
            boolean complete = PlasticMoldingChamberStructure.repair(
                level,
                pos,
                state.getValue(PlasticMoldingChamberBlock.FACING)
            );
            if (complete != chamber.structureComplete) {
                chamber.structureComplete = complete;
                chamber.setChanged();
            }
        }
        chamber.tickClayCollection();
        chamber.tickRedstone(level);
        chamber.tickResourceSlot();
        chamber.tickGridCharging();
        chamber.tickMachine();
        chamber.flushClientSync();
        chamber.syncPowerState();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null && !this.level.isClientSide) {
            if (this.machineState == PlasticMoldingMachineState.PROCESSING) {
                if (this.isPrintingProcess()
                    && this.processingSnapshot != null) {
                    this.syncProductionState(false);
                    return;
                }
                this.energy = Math.min(
                    MoldingPowerBridge.capacity(),
                    this.energy + MoldingPowerBridge.energyPerWorkingTick()
                );
                this.updateReadyState();
                return;
            }
            this.syncProductionState(this.hasMoldCollision());
        }
    }

    public EditableMoldingModel model() {
        return this.model;
    }

    public BakedMoldingModel bakedModel() {
        return this.bakedModel;
    }

    public long revision() {
        return this.revision;
    }

    public PlasticMoldingMachineState machineState() {
        return this.machineState;
    }

    public MoldingProductionMode selectedMode() {
        return this.selectedMode;
    }

    public MoldingProductionMode cycleMode() {
        return this.cycleMode;
    }

    public MoldingFormingMode formingMode() {
        return this.printingComponentPresent()
            ? MoldingFormingMode.PRINTING
            : MoldingFormingMode.CASTING;
    }

    public MoldingFormingMode cycleFormingMode() {
        return this.cycleFormingMode;
    }

    public boolean printingComponentPresent() {
        return this.printingComponent() != null;
    }

    private @Nullable Plastic3DPrintingComponentBlockEntity printingComponent() {
        if (this.level == null
            || !this.level.getBlockState(this.worldPosition.above())
                .is(PlasticraftBlocks.PLASTIC_3D_PRINTING_COMPONENT.get())) {
            return null;
        }
        return this.level.getBlockEntity(this.worldPosition.above())
            instanceof Plastic3DPrintingComponentBlockEntity component ? component : null;
    }

    public boolean isPrintingProcess() {
        return this.machineState == PlasticMoldingMachineState.PROCESSING
            && this.cycleFormingMode == MoldingFormingMode.PRINTING;
    }

    public int printingProgress() {
        return this.printingProgress;
    }

    public int printingTotal() {
        return this.printingTotal;
    }

    public MoldingPrintingPlan printingPlan() {
        if (this.printingPlan == null) {
            this.printingPlan = MoldingPrintingPlan.create(this.bakedModel);
        }
        return this.printingPlan;
    }

    public MoldingVolumeMask printedMask() {
        return this.printingPlan().prefix(this.printingProgress);
    }

    public Optional<MoldingPrintingVoxel> currentPrintingVoxel() {
        if (!this.isPrintingProcess() || this.printingProgress >= this.printingTotal) return Optional.empty();
        return Optional.of(this.printingPlan().voxelAt(this.printingProgress));
    }

    public MoldingWaitReason waitReason() {
        return this.waitReason;
    }

    public boolean structureComplete() {
        return this.structureComplete;
    }

    public boolean isLocked() {
        return this.machineState != PlasticMoldingMachineState.EDITABLE;
    }

    public boolean hasMoldCollision() {
        return this.cycleFormingMode == MoldingFormingMode.CASTING && hasMoldCollision(this.machineState);
    }

    public float moldFillFraction(float partialTick) {
        if (this.cycleFormingMode == MoldingFormingMode.PRINTING) return 0.0F;
        if (this.machineState == PlasticMoldingMachineState.MOLD_FILLING) {
            int completedLayers = this.moldFillProgress / MOLD_FILL_LAYER_TICKS;
            return Math.clamp((float) completedLayers / MOLD_FILL_LAYERS, 0.0F, 1.0F);
        }
        return hasMoldCollision() ? 1.0F : 0.0F;
    }

    public static SimpleContainer createInventory() {
        return new MoldingInventory();
    }

    public static boolean isResourceInput(ItemStack stack) {
        return capacitorEnergy(stack) > 0
            || isPlasticMeltContainer(stack)
            || isCreativeGenerator(stack)
            || isMultiphaseTranscendium(stack);
    }

    public int clayLimit() {
        return this.clayLimit;
    }

    public int moldedClayBalls() {
        return this.moldedClayBalls;
    }

    public int requiredClayBalls() {
        return this.requiredClayBalls;
    }

    public int moldFillProgress() {
        return this.moldFillProgress;
    }

    public int energyStored() {
        return this.energy;
    }

    public int stagingFluidAmount() {
        return this.stagingTank.getFluidAmount();
    }

    public int batchFluidAmount() {
        return this.formingFluidAmount();
    }

    public int batchFluidCapacity() {
        return this.bakedModel.analysis().minimumMeltMillibuckets();
    }

    public int requiredProcessMelt() {
        return this.activeFormingMode() == MoldingFormingMode.PRINTING
            ? this.batchFluidCapacity()
            : MINIMUM_PROCESS_MELT;
    }

    public boolean typeOverrideCommitted() {
        return this.typeOverrideCommitted;
    }

    public boolean creativeOverrideLocked() {
        return this.creativeOverrideLocked;
    }

    public boolean modelFitsWorkspace() {
        return MoldingModelBounds.all(this.model)
            .map(MoldingModelBounds::fitsWorkspaceSize)
            .orElse(true);
    }

    public boolean willCurrentResultDowngrade() {
        if (this.activeFormingMode() == MoldingFormingMode.PRINTING
            || this.batchTank.getFluidAmount() < MINIMUM_PROCESS_MELT
            || MoldingProductTypes.NORMAL_ID.equals(this.model.requestedType())
            || this.typeOverrideCommitted || this.creativeOverrideLocked) {
            return false;
        }
        return MoldingProductPreview.evaluate(
            this.model,
            this.bakedModel,
            this.batchTank.getFluidAmount()
        ).downgraded();
    }

    public FluidStack stagingFluid() {
        return this.stagingTank.getFluid().copy();
    }

    public FluidStack batchFluid() {
        return this.formingFluid();
    }

    public SimpleContainer inventory() {
        return this.inventory;
    }

    public IItemHandler clayItemHandler() {
        return this.clayItemHandler;
    }

    public IFluidHandler fluidHandler() {
        return this.fluidHandler;
    }

    public IEnergyStorage energyStorage() {
        return this.energyStorage;
    }

    public ContainerData menuData() {
        return this.menuData;
    }

    public void openMenu(ServerPlayer player) {
        MoldingSessionSnapshot snapshot = this.openSession(player, false);
        PlasticMoldingChamberMenu.open(player, this, snapshot);
        MoldingSessionStatusPacket.broadcast(player.serverLevel(), this);
    }

    public MoldingSessionSnapshot openSession(ServerPlayer player, boolean forceTakeover) {
        long now = player.level().getGameTime();
        this.expireLease(now);
        boolean available = this.writerPlayerId == null || this.writerPlayerId.equals(player.getUUID());
        boolean writable = available || forceTakeover;
        UUID session = UUID.randomUUID();
        if (writable) {
            this.writerPlayerId = player.getUUID();
            this.writerSessionId = session;
            this.writerName = player.getGameProfile().getName();
            this.leaseExpiresAt = now + LEASE_TICKS;
            this.undoHistory.clear();
            this.redoHistory.clear();
        }
        return new MoldingSessionSnapshot(
            session,
            writable,
            this.writerName,
            this.revision,
            this.model
        );
    }

    public boolean heartbeat(ServerPlayer player, UUID sessionId) {
        if (!this.isWriter(player, sessionId)) return false;
        this.leaseExpiresAt = player.level().getGameTime() + LEASE_TICKS;
        return true;
    }

    public MoldingSessionSnapshot sessionSnapshot(ServerPlayer player, UUID sessionId) {
        return new MoldingSessionSnapshot(
            sessionId,
            this.isWriter(player, sessionId),
            this.writerName,
            this.revision,
            this.model
        );
    }

    public String writerName() {
        return this.writerName;
    }

    @Nullable
    public UUID writerPlayerId() {
        return this.writerPlayerId;
    }

    public boolean releaseSession(ServerPlayer player, UUID sessionId) {
        if (!this.isWriter(player, sessionId)) return false;
        this.clearLease();
        return true;
    }

    public EditOutcome applyEditorCommand(
        ServerPlayer player,
        UUID sessionId,
        long baseRevision,
        MoldingCommand command
    ) {
        if (!this.isWriter(player, sessionId)) return EditOutcome.rejected("invalid_session", this);
        if (baseRevision != this.revision) return EditOutcome.rejected("stale_revision", this);
        if (this.machineState != PlasticMoldingMachineState.EDITABLE) {
            return EditOutcome.rejected("not_editable", this);
        }

        try {
            EditableMoldingModel next;
            if (command instanceof MoldingCommand.Undo) {
                if (this.undoHistory.isEmpty()) return EditOutcome.rejected("nothing_to_undo", this);
                next = this.undoHistory.removeFirst();
                pushHistory(this.redoHistory, this.model);
            } else if (command instanceof MoldingCommand.Redo) {
                if (this.redoHistory.isEmpty()) return EditOutcome.rejected("nothing_to_redo", this);
                next = this.redoHistory.removeFirst();
                pushHistory(this.undoHistory, this.model);
            } else {
                next = command.apply(this.model);
                MoldingModelBaker.bake(next);
                pushHistory(this.undoHistory, this.model);
                this.redoHistory.clear();
            }
            this.applyModel(next, true);
            this.leaseExpiresAt = player.level().getGameTime() + LEASE_TICKS;
            return EditOutcome.accepted(this);
        } catch (RuntimeException exception) {
            AnvilcraftPlasticraft.LOGGER.debug("Rejected molding edit at {}", this.worldPosition, exception);
            return EditOutcome.rejected("invalid_command", this);
        }
    }

    public MachineOutcome toggleLock(long baseRevision) {
        if (baseRevision != this.revision) return MachineOutcome.rejected("stale_revision");
        return this.machineState == PlasticMoldingMachineState.EDITABLE
            ? this.requestLock()
            : this.unlock();
    }

    public MachineOutcome requestLock() {
        if (this.machineState != PlasticMoldingMachineState.EDITABLE) {
            return MachineOutcome.rejected("already_locked");
        }
        return this.prepareCurrentModelForCycle();
    }

    private MachineOutcome prepareCurrentModelForCycle() {
        if (this.bakedModel.analysis().empty()) return MachineOutcome.rejected("empty_model");
        ItemStack resource = this.inventory.getItem(RESOURCE_SLOT);
        boolean creativeOverride = isCreativeGenerator(resource);
        boolean alloyOverride = isMultiphaseTranscendium(resource);
        if (!this.modelFitsWorkspace() && !creativeOverride) {
            return MachineOutcome.rejected("model_too_large");
        }
        MoldingTypeValidation typeValidation = MoldingProductTypes.validate(
            this.model.requestedType(),
            this.model,
            this.bakedModel
        );
        MoldingFormingMode formingMode = this.formingMode();
        boolean printing = formingMode == MoldingFormingMode.PRINTING;
        if (MoldingProductTypes.requiresPrinting(this.model.requestedType()) && !printing) {
            return MachineOutcome.rejected("missing_printing_component");
        }
        boolean typeOverride = !typeValidation.valid() && (creativeOverride || alloyOverride);
        if (!typeValidation.valid() && !typeOverride) {
            return MachineOutcome.rejected("type_" + typeValidation.reason());
        }
        try {
            ManufacturedMoldingGeometry fullShape = MoldingModelBaker.createManufacturedGeometry(
                this.model,
                1.0D
            );
            MoldedPlasticSurfaceAdapter.adapt(fullShape.surfaceMesh());
        } catch (RuntimeException exception) {
            AnvilcraftPlasticraft.LOGGER.debug(
                "Rejected over-complex molding model at {}",
                this.worldPosition,
                exception
            );
            return MachineOutcome.rejected("model_too_complex");
        }
        if (typeOverride && !creativeOverride) {
            ItemStack current = this.inventory.getItem(RESOURCE_SLOT);
            if (!isMultiphaseTranscendium(current)) {
                return MachineOutcome.rejected("missing_type_override");
            }
            current.shrink(1);
            if (current.isEmpty()) this.inventory.setItem(RESOURCE_SLOT, ItemStack.EMPTY);
            else this.inventory.setChanged();
        }
        this.typeOverrideCommitted = typeOverride;
        this.creativeOverrideLocked = creativeOverride;
        this.requiredClayBalls = printing ? 0 : this.bakedModel.analysis().clayBallRequirement();
        this.moldedClayBalls = 0;
        this.moldFillProgress = 0;
        this.cycleMode = this.selectedMode;
        this.cycleFormingMode = formingMode;
        this.printingPlan = printing ? MoldingPrintingPlan.create(this.bakedModel) : null;
        this.printingProgress = 0;
        this.printingTotal = printing ? this.printingPlan().size() : 0;
        this.printingMaterial = FluidStack.EMPTY;
        this.printingMeltConsumed = 0;
        this.setMachineState(PlasticMoldingMachineState.WAITING_TO_LOCK);
        this.setWaitReason(MoldingWaitReason.NONE);
        return MachineOutcome.success();
    }

    public MachineOutcome unlock() {
        if (this.machineState == PlasticMoldingMachineState.EDITABLE) {
            return MachineOutcome.rejected("not_locked");
        }
        if (this.machineState == PlasticMoldingMachineState.PROCESSING) {
            return MachineOutcome.rejected("processing");
        }
        if (!this.returnBatchMelt()) return MachineOutcome.rejected("drain_batch_first");
        this.returnMoldedClay();
        this.requiredClayBalls = 0;
        this.moldFillProgress = 0;
        this.printingProgress = 0;
        this.printingTotal = 0;
        this.printingMaterial = FluidStack.EMPTY;
        this.printingMeltConsumed = 0;
        this.typeOverrideCommitted = false;
        this.creativeOverrideLocked = false;
        this.setMachineState(PlasticMoldingMachineState.EDITABLE);
        this.setWaitReason(MoldingWaitReason.NONE);
        return MachineOutcome.success();
    }

    public MachineOutcome setProductionMode(MoldingProductionMode mode, long baseRevision) {
        if (baseRevision != this.revision) return MachineOutcome.rejected("stale_revision");
        if (this.selectedMode == mode) return MachineOutcome.success();
        this.selectedMode = mode;
        if (this.machineState == PlasticMoldingMachineState.EDITABLE) this.cycleMode = mode;
        this.setChanged();
        return MachineOutcome.success();
    }

    public MachineOutcome setClayLimit(int limit, long baseRevision) {
        if (baseRevision != this.revision) return MachineOutcome.rejected("stale_revision");
        if (limit < CLAY_LIMIT_MIN || limit > CLAY_LIMIT_MAX) {
            return MachineOutcome.rejected("invalid_clay_limit");
        }
        if (this.clayLimit == limit) return MachineOutcome.success();
        this.clayLimit = limit;
        this.setChanged();
        return MachineOutcome.success();
    }

    public MachineOutcome applyMachineAction(
        ServerPlayer player,
        UUID sessionId,
        long baseRevision,
        MoldingMachineAction action,
        int value
    ) {
        if (!this.isWriter(player, sessionId)) return MachineOutcome.rejected("invalid_session");
        if (baseRevision != this.revision) return MachineOutcome.rejected("stale_revision");
        this.leaseExpiresAt = player.level().getGameTime() + LEASE_TICKS;
        try {
            return switch (action) {
                case TOGGLE_LOCK -> this.toggleLock(baseRevision);
                case SET_MODE -> this.setProductionMode(MoldingProductionMode.fromProtocolId(value), baseRevision);
                case SET_CLAY_LIMIT -> this.setClayLimit(value, baseRevision);
                case INTERACT_STAGING_FLUID -> this.interactWithCarriedFluid(player)
                    ? MachineOutcome.success()
                    : MachineOutcome.rejected("fluid_interaction_failed");
                case SET_FORMING_MODE -> MachineOutcome.success();
                default -> MachineOutcome.rejected("unsupported_machine_action");
            };
        } catch (IllegalArgumentException exception) {
            return MachineOutcome.rejected("invalid_machine_action");
        }
    }

    private boolean interactWithCarriedFluid(ServerPlayer player) {
        ItemStack carried = player.containerMenu.getCarried();
        if (carried.isEmpty()) return false;
        IItemHandler playerInventory = player.getCapability(Capabilities.ItemHandler.ENTITY);
        if (playerInventory == null) return false;
        FluidActionResult result = FluidUtil.tryFillContainerAndStow(
            carried,
            this.stagingTank,
            playerInventory,
            Integer.MAX_VALUE,
            player,
            true
        );
        if (!result.isSuccess()) {
            result = FluidUtil.tryEmptyContainerAndStow(
                carried,
                this.stagingTank,
                playerInventory,
                Integer.MAX_VALUE,
                player,
                true
            );
        }
        if (!result.isSuccess()) return false;
        player.containerMenu.setCarried(result.getResult());
        player.containerMenu.broadcastChanges();
        return true;
    }

    public MachineOutcome validateBlueprintSession(
        ServerPlayer player,
        UUID sessionId,
        long baseRevision
    ) {
        if (!this.isWriter(player, sessionId)) return MachineOutcome.rejected("invalid_session");
        if (baseRevision != this.revision) return MachineOutcome.rejected("stale_revision");
        this.leaseExpiresAt = player.level().getGameTime() + LEASE_TICKS;
        return MachineOutcome.success();
    }

    public MachineOutcome replaceEditableModel(EditableMoldingModel replacement, long baseRevision) {
        if (baseRevision != this.revision) return MachineOutcome.rejected("stale_revision");
        if (this.machineState != PlasticMoldingMachineState.EDITABLE) {
            return MachineOutcome.rejected("not_editable");
        }
        if (this.moldedClayBalls != 0) return MachineOutcome.rejected("reserved_clay");
        if (this.formingFluidAmount() != 0) return MachineOutcome.rejected("drain_batch_first");
        try {
            MoldingModelBaker.bake(replacement);
            this.undoHistory.clear();
            this.redoHistory.clear();
            this.applyModel(replacement, true);
            return MachineOutcome.success();
        } catch (RuntimeException exception) {
            AnvilcraftPlasticraft.LOGGER.debug("Rejected molding model replacement at {}", this.worldPosition, exception);
            return MachineOutcome.rejected("invalid_model");
        }
    }

    public Optional<MoldingProcessSnapshot> beginProcessing() {
        if (this.cycleFormingMode == MoldingFormingMode.PRINTING) return Optional.empty();
        return this.beginProcessingTransaction();
    }

    private Optional<MoldingProcessSnapshot> beginProcessingTransaction() {
        if (this.machineState != PlasticMoldingMachineState.PROCESS_READY
            || !this.structureComplete
            || !this.creativeOverrideLocked && this.formingFluidAmount() < this.requiredProcessMelt()
            || this.cycleFormingMode == MoldingFormingMode.PRINTING && !this.printingComponentPresent()
            || !this.hasWorkingEnergy()) {
            return Optional.empty();
        }
        ItemStack creativeGenerator = this.inventory.getItem(RESOURCE_SLOT);
        if (this.creativeOverrideLocked && !isCreativeGenerator(creativeGenerator)) return Optional.empty();
        MoldingProcessSnapshot snapshot = new MoldingProcessSnapshot(
            this.revision,
            this.model,
            this.bakedModel,
            this.formingFluid(),
            this.moldedClayBalls,
            this.cycleMode,
            this.cycleFormingMode,
            this.typeOverrideCommitted,
            this.creativeOverrideLocked,
            this.printingProgress,
            this.printingTotal
        );
        if (this.creativeOverrideLocked) {
            if (!this.consumeWorkingEnergy()) return Optional.empty();
            creativeGenerator.shrink(1);
            if (creativeGenerator.isEmpty()) this.inventory.setItem(RESOURCE_SLOT, ItemStack.EMPTY);
            else this.inventory.setChanged();
        } else if (!this.consumeWorkingEnergy()) {
            return Optional.empty();
        }
        this.processingSnapshot = snapshot;
        this.printingProgress = snapshot.printingProgress();
        this.printingTotal = snapshot.printingTotal();
        if (snapshot.formingMode() == MoldingFormingMode.PRINTING) {
            this.printingMaterial = snapshot.batchFluid().copy();
            this.printingMeltConsumed = 0;
        }
        this.setMachineState(PlasticMoldingMachineState.PROCESSING);
        this.setWaitReason(MoldingWaitReason.PROCESSING);
        return Optional.of(snapshot);
    }

    public Optional<MoldingProcessSnapshot> activeProcessingSnapshot() {
        return Optional.ofNullable(this.processingSnapshot);
    }

    public void waitForPrintingOutput() {
        if (this.isPrintingProcess() && this.printingProgress >= this.printingTotal) {
            this.setWaitReason(MoldingWaitReason.REGION_BLOCKED);
        }
    }

    public boolean advancePrintingTick() {
        if (!this.isPrintingProcess()
            || this.processingSnapshot == null) {
            return false;
        }
        Plastic3DPrintingComponentBlockEntity component = this.printingComponent();
        if (component == null) {
            this.setWaitReason(MoldingWaitReason.MISSING_PRINTING_COMPONENT);
            return false;
        }
        if (this.printingProgress >= this.printingTotal) return true;
        int requiredMelt = this.requiredProcessMelt();
        FluidStack simulated = FluidStack.EMPTY;
        if (!this.creativeOverrideLocked) {
            simulated = component.consumeForPrinting(
                PRINTING_CONSUMPTION_RATE,
                IFluidHandler.FluidAction.SIMULATE
            );
            if (simulated.getAmount() != PRINTING_CONSUMPTION_RATE
                || !FluidStack.isSameFluidSameComponents(simulated, this.printingMaterial)) {
                this.setWaitReason(MoldingWaitReason.MOLD_READY);
                return false;
            }
        }
        if (!this.consumeWorkingEnergy()) {
            this.setWaitReason(MoldingWaitReason.MISSING_POWER);
            return false;
        }
        if (!this.creativeOverrideLocked) {
            FluidStack consumed = component.consumeForPrinting(
                PRINTING_CONSUMPTION_RATE,
                IFluidHandler.FluidAction.EXECUTE
            );
            if (consumed.getAmount() != PRINTING_CONSUMPTION_RATE
                || !FluidStack.isSameFluidSameComponents(simulated, consumed)) {
                this.energy = Math.min(
                    MoldingPowerBridge.capacity(),
                    this.energy + MoldingPowerBridge.energyPerWorkingTick()
                );
                this.setWaitReason(MoldingWaitReason.MOLD_READY);
                return false;
            }
        }
        this.printingMeltConsumed = Math.min(
            requiredMelt,
            this.printingMeltConsumed + PRINTING_CONSUMPTION_RATE
        );
        this.printingProgress = Math.clamp(
            (int) ((long) this.printingMeltConsumed * this.printingTotal / requiredMelt),
            this.printingProgress,
            this.printingTotal
        );
        this.setWaitReason(MoldingWaitReason.PROCESSING);
        this.requestClientSync();
        return true;
    }

    public boolean finishProcessing(MoldingProcessSnapshot snapshot, boolean success) {
        if (!success) return this.abortProcessing(snapshot);
        boolean printing = snapshot.formingMode() == MoldingFormingMode.PRINTING;
        if (!this.isActiveProcessingSnapshot(snapshot)
            || printing && (this.printingProgress < this.printingTotal
                || this.printingMeltConsumed < this.requiredProcessMelt())
            || snapshot.modelRevision() != this.revision
            || !snapshot.bakedModel().modelHash().equals(this.bakedModel.modelHash())
            || snapshot.moldedClayBalls() != this.moldedClayBalls
            || snapshot.cycleMode() != this.cycleMode
            || snapshot.formingMode() != this.cycleFormingMode
            || snapshot.typeOverride() != this.typeOverrideCommitted
            || snapshot.creativeOverride() != this.creativeOverrideLocked
            || printing && !sameFluidAndAmount(snapshot.batchFluid(), this.printingMaterial)
            || !printing && !sameFluidAndAmount(snapshot.batchFluid(), this.batchTank.getFluid())) {
            return false;
        }
        this.processingSnapshot = null;
        if (!printing && !snapshot.creativeOverride()) this.batchTank.setFluid(FluidStack.EMPTY);
        this.moldedClayBalls = 0;
        this.requiredClayBalls = 0;
        this.moldFillProgress = 0;
        this.printingProgress = 0;
        this.printingTotal = 0;
        this.printingMaterial = FluidStack.EMPTY;
        this.printingMeltConsumed = 0;
        this.typeOverrideCommitted = false;
        this.creativeOverrideLocked = false;
        MoldingProductionMode completedMode = this.cycleMode;
        this.cycleMode = this.selectedMode;
        this.cycleFormingMode = this.formingMode();
        this.setMachineState(completedMode == MoldingProductionMode.CONTINUOUS
            ? PlasticMoldingMachineState.WAITING_NEXT_CYCLE
            : PlasticMoldingMachineState.EDITABLE);
        this.setWaitReason(completedMode == MoldingProductionMode.CONTINUOUS
            ? MoldingWaitReason.WAITING_FOR_CLEAR_REGION
            : MoldingWaitReason.NONE);
        return true;
    }

    /** 事务提交失败时恢复冻结批次；暂存罐和加工期间的外部输入不受影响。 */
    public boolean abortProcessing(MoldingProcessSnapshot snapshot) {
        if (!this.isActiveProcessingSnapshot(snapshot)) return false;
        if (!snapshot.creativeOverride()) {
            if (snapshot.formingMode() == MoldingFormingMode.PRINTING) {
                Plastic3DPrintingComponentBlockEntity component = this.printingComponent();
                if (component == null) return false;
                component.setFluidFromChamber(snapshot.batchFluid());
            } else {
                this.batchTank.setFluid(snapshot.batchFluid());
            }
        }
        this.moldedClayBalls = snapshot.moldedClayBalls();
        this.requiredClayBalls = Math.max(this.requiredClayBalls, this.moldedClayBalls);
        this.cycleMode = snapshot.cycleMode();
        this.cycleFormingMode = snapshot.formingMode();
        this.printingProgress = snapshot.printingProgress();
        this.printingTotal = snapshot.printingTotal();
        this.printingMaterial = FluidStack.EMPTY;
        this.printingMeltConsumed = 0;
        this.energy = Math.min(
            MoldingPowerBridge.capacity(),
            this.energy + MoldingPowerBridge.energyPerWorkingTick()
        );
        this.processingSnapshot = null;
        this.updateReadyState();
        return true;
    }

    private boolean isActiveProcessingSnapshot(MoldingProcessSnapshot snapshot) {
        MoldingProcessSnapshot active = this.processingSnapshot;
        return this.machineState == PlasticMoldingMachineState.PROCESSING
            && active != null
            && snapshot.modelRevision() == active.modelRevision()
            && snapshot.bakedModel().modelHash().equals(active.bakedModel().modelHash())
            && snapshot.moldedClayBalls() == active.moldedClayBalls()
            && snapshot.cycleMode() == active.cycleMode()
            && snapshot.formingMode() == active.formingMode()
            && snapshot.typeOverride() == active.typeOverride()
            && snapshot.creativeOverride() == active.creativeOverride()
            && sameFluidAndAmount(snapshot.batchFluid(), active.batchFluid());
    }

    public void applyClientSnapshot(long newRevision, EditableMoldingModel newModel) {
        if (newRevision < this.revision) return;
        this.model = newModel;
        this.bakedModel = MoldingModelBaker.bake(newModel);
        this.printingPlan = null;
        this.batchTank.setCapacity(this.bakedModel.analysis().minimumMeltMillibuckets());
        this.revision = newRevision;
    }

    public boolean applyClientDelta(long baseRevision, long newRevision, MoldingCommand command) {
        if (this.revision != baseRevision || newRevision != baseRevision + 1L) return false;
        try {
            EditableMoldingModel next = command.apply(this.model);
            this.model = next;
            this.bakedModel = MoldingModelBaker.bake(next);
            this.printingPlan = null;
            this.batchTank.setCapacity(this.bakedModel.analysis().minimumMeltMillibuckets());
            this.revision = newRevision;
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public void dropContents() {
        if (this.level == null || this.level.isClientSide) return;
        Containers.dropContents(this.level, this.worldPosition, this.inventory);
        this.inventory.clearContent();
        this.dropClayInRegion(this.moldedClayBalls);
        this.moldedClayBalls = 0;
        this.requiredClayBalls = 0;
        this.stagingTank.setFluid(FluidStack.EMPTY);
        this.batchTank.setFluid(FluidStack.EMPTY);
        this.energy = 0;
        this.printingProgress = 0;
        this.printingTotal = 0;
        this.printingMaterial = FluidStack.EMPTY;
        this.printingMeltConsumed = 0;
        this.processingSnapshot = null;
        this.typeOverrideCommitted = false;
        this.creativeOverrideLocked = false;
    }

    @Override
    public int getInputPower() {
        return this.energy < MoldingPowerBridge.capacity() ? MoldingPowerBridge.RATED_POWER_KW : 0;
    }

    @Override
    public @Nullable Level getCurrentLevel() {
        return this.level;
    }

    @Override
    public BlockPos getPos() {
        return this.worldPosition;
    }

    @Override
    public void setGrid(@Nullable PowerGrid grid) {
        this.grid = grid;
    }

    @Override
    public @Nullable PowerGrid getGrid() {
        return this.grid;
    }

    @Override
    public List<Component> anvilcraft$getTooltip() {
        List<Component> lines = new ArrayList<>();
        MoldingFormingMode displayedMode = this.isLocked()
            ? this.cycleFormingMode
            : this.formingMode();
        lines.add(Component.translatable(
            "tooltip.anvilcraftplasticraft.molding.state",
            Component.translatable("screen.anvilcraftplasticraft.molding.state." + this.machineState.getSerializedName())
        ).withStyle(ChatFormatting.BLUE));
        lines.add(Component.translatable(
            "tooltip.anvilcraftplasticraft.molding.energy",
            this.energy,
            MoldingPowerBridge.capacity(),
            MoldingPowerBridge.RATED_POWER_KW
        ).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(
            displayedMode == MoldingFormingMode.PRINTING
                ? "tooltip.anvilcraftplasticraft.molding.printing_fluids"
                : "tooltip.anvilcraftplasticraft.molding.fluids",
            UnitUtil.fluidUnit(this.stagingTank.getFluidAmount(), false),
            UnitUtil.fluidUnit(STAGING_TANK_CAPACITY, false),
            UnitUtil.fluidUnit(this.formingFluidAmount(), false),
            UnitUtil.fluidUnit(this.batchFluidCapacity(), false)
        ).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(
            "screen.anvilcraftplasticraft.molding.forming",
            Component.translatable(
                "screen.anvilcraftplasticraft.molding.forming." + displayedMode.getSerializedName()
            )
        ).withStyle(ChatFormatting.GRAY));
        if (displayedMode == MoldingFormingMode.PRINTING) {
            lines.add(Component.translatable(
                "tooltip.anvilcraftplasticraft.molding.printing_rates",
                PRINTING_PUMP_RATE,
                PRINTING_CONSUMPTION_RATE
            ).withStyle(ChatFormatting.GRAY));
        }
        if (displayedMode == MoldingFormingMode.CASTING) {
            lines.add(Component.translatable(
                "tooltip.anvilcraftplasticraft.molding.clay",
                this.inventory.getItem(CLAY_SLOT).getCount(),
                this.moldedClayBalls,
                this.requiredClayBalls
            ).withStyle(ChatFormatting.GRAY));
        } else if (this.printingTotal > 0) {
            lines.add(Component.translatable(
                "screen.anvilcraftplasticraft.molding.printing_progress",
                this.printingProgress,
                this.printingTotal
            ).withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    private void tickRedstone(Level level) {
        boolean powered = level.hasNeighborSignal(this.worldPosition);
        boolean rising = powered && !this.redstonePowered;
        if (powered != this.redstonePowered) {
            this.redstonePowered = powered;
            this.setChanged();
        }
        if (rising
            && this.selectedMode == MoldingProductionMode.REDSTONE
            && this.machineState == PlasticMoldingMachineState.EDITABLE) {
            this.requestLock();
        }
    }

    private void tickGridCharging() {
        if (!this.isGridWorking() || this.getInputPower() == 0) return;
        int accepted = Math.min(
            MoldingPowerBridge.energyPerWorkingTick(),
            MoldingPowerBridge.capacity() - this.energy
        );
        if (accepted <= 0) return;
        this.energy += accepted;
        this.setChanged();
    }

    private void tickResourceSlot() {
        ItemStack input = this.inventory.getItem(RESOURCE_SLOT);
        if (input.isEmpty()) return;
        if (isCreativeGenerator(input) || isMultiphaseTranscendium(input)) return;
        if (this.tryEmptyResourceFluidContainer(input)) return;

        int capacitorEnergy = capacitorEnergy(input);
        if (capacitorEnergy <= 0 || MoldingPowerBridge.capacity() - this.energy < capacitorEnergy) return;
        if (!(input.getItem() instanceof IChargerDischargeable dischargeable)) return;
        ItemStack emptyCapacitor = dischargeable.discharge(input.copyWithCount(1));
        this.energy += capacitorEnergy;
        this.finishResourceInput(emptyCapacitor);
    }

    private boolean tryEmptyResourceFluidContainer(ItemStack input) {
        IFluidHandlerItem container = FluidUtil.getFluidHandler(input.copyWithCount(1)).orElse(null);
        if (container == null) return false;
        FluidStack contained = container.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
        if (!isPlasticMelt(contained)
            || this.stagingTank.fill(contained, IFluidHandler.FluidAction.SIMULATE) != contained.getAmount()) {
            return false;
        }
        FluidStack stagingBefore = this.stagingTank.getFluid().copy();
        FluidStack drained = container.drain(contained, IFluidHandler.FluidAction.EXECUTE);
        if (drained.getAmount() != contained.getAmount()
            || this.stagingTank.fill(drained, IFluidHandler.FluidAction.EXECUTE) != drained.getAmount()) {
            this.stagingTank.setFluid(stagingBefore);
            this.inventory.setItem(RESOURCE_SLOT, input.copy());
            AnvilcraftPlasticraft.LOGGER.error("Failed molding resource-slot fluid transfer at {}", this.worldPosition);
            return false;
        }
        this.finishResourceInput(container.getContainer());
        return true;
    }

    private void finishResourceInput(ItemStack output) {
        ItemStack remainingInput = this.inventory.getItem(RESOURCE_SLOT);
        remainingInput.shrink(1);
        if (remainingInput.isEmpty()) {
            this.inventory.setItem(RESOURCE_SLOT, output);
            return;
        }
        this.inventory.setChanged();
        if (output.isEmpty()) return;
        ServerPlayer player = this.findResourceReturnPlayer();
        if (player != null) {
            player.getInventory().placeItemBackInInventory(output);
        } else if (this.level != null) {
            Containers.dropItemStack(
                this.level,
                this.worldPosition.getX() + 0.5D,
                this.worldPosition.getY() + 0.5D,
                this.worldPosition.getZ() + 0.5D,
                output
            );
        }
    }

    @Nullable
    private ServerPlayer findResourceReturnPlayer() {
        if (!(this.level instanceof ServerLevel serverLevel)) return null;
        ServerPlayer fallback = null;
        for (ServerPlayer player : serverLevel.players()) {
            if (!(player.containerMenu instanceof PlasticMoldingChamberMenu menu)
                || !menu.isForChamber(this.worldPosition)) {
                continue;
            }
            if (player.getUUID().equals(this.writerPlayerId)) return player;
            fallback = player;
        }
        return fallback;
    }

    private void tickMachine() {
        switch (this.machineState) {
            case EDITABLE -> {
            }
            case PROCESSING -> {
                if (this.level instanceof ServerLevel serverLevel && this.isPrintingProcess()) {
                    PlasticMoldingAnvilProcessor.tickPrinting(serverLevel, this);
                }
            }
            case WAITING_TO_LOCK -> this.tickWaitingToLock();
            case MOLD_FILLING -> this.tickMoldFilling();
            case MOLD_READY -> this.tickMeltPump();
            case PROCESS_READY -> {
                if (this.cycleFormingMode == MoldingFormingMode.PRINTING) this.tickPrintingStart();
                else this.tickMeltPump();
            }
            case WAITING_NEXT_CYCLE -> this.tickWaitingNextCycle();
        }
    }

    private void tickPrintingStart() {
        if (!this.structureComplete) {
            this.setWaitReason(MoldingWaitReason.STRUCTURE_INCOMPLETE);
            return;
        }
        if (!this.printingComponentPresent()) {
            this.setMachineState(PlasticMoldingMachineState.MOLD_READY);
            this.setWaitReason(MoldingWaitReason.MISSING_PRINTING_COMPONENT);
            return;
        }
        if (!this.regionIsClear()) {
            this.setWaitReason(MoldingWaitReason.REGION_BLOCKED);
            return;
        }
        if (this.beginProcessingTransaction().isEmpty() && !this.hasWorkingEnergy()) {
            this.setWaitReason(MoldingWaitReason.MISSING_POWER);
        }
    }

    private void tickWaitingToLock() {
        if (!this.structureComplete) {
            this.setWaitReason(MoldingWaitReason.STRUCTURE_INCOMPLETE);
            return;
        }
        if (this.bakedModel.analysis().empty()) {
            this.setWaitReason(MoldingWaitReason.INVALID_MODEL);
            return;
        }
        if (!this.regionIsClear()) {
            this.setWaitReason(MoldingWaitReason.REGION_BLOCKED);
            return;
        }
        if (this.cycleFormingMode == MoldingFormingMode.PRINTING) {
            if (!this.printingComponentPresent()) {
                this.setWaitReason(MoldingWaitReason.MISSING_PRINTING_COMPONENT);
                return;
            }
            if (!this.hasWorkingEnergy()) {
                this.setWaitReason(MoldingWaitReason.MISSING_POWER);
                return;
            }
            this.setMachineState(PlasticMoldingMachineState.MOLD_READY);
            this.tickMeltPump();
            return;
        }
        int firstClayTarget = clayTargetForLayer(1, this.requiredClayBalls);
        if (this.inventory.getItem(CLAY_SLOT).getCount() < firstClayTarget) {
            this.setWaitReason(MoldingWaitReason.MISSING_CLAY);
            return;
        }
        if (!this.hasWorkingEnergy()) {
            this.setWaitReason(MoldingWaitReason.MISSING_POWER);
            return;
        }
        this.setMachineState(PlasticMoldingMachineState.MOLD_FILLING);
        this.tickMoldFilling();
    }

    private void tickMoldFilling() {
        if (!this.structureComplete) {
            this.setWaitReason(MoldingWaitReason.STRUCTURE_INCOMPLETE);
            return;
        }
        if (this.moldFillProgress >= MOLD_FILL_TICKS) {
            this.setMachineState(PlasticMoldingMachineState.MOLD_READY);
            this.setWaitReason(MoldingWaitReason.MOLD_READY);
            return;
        }
        int nextProgress = this.moldFillProgress + 1;
        int completedLayers = nextProgress / MOLD_FILL_LAYER_TICKS;
        int nextClayTarget = nextProgress % MOLD_FILL_LAYER_TICKS == 0
            ? clayTargetForLayer(completedLayers, this.requiredClayBalls)
            : this.moldedClayBalls;
        int requiredNow = Math.max(0, nextClayTarget - this.moldedClayBalls);
        if (this.inventory.getItem(CLAY_SLOT).getCount() < requiredNow) {
            this.setWaitReason(MoldingWaitReason.MISSING_CLAY);
            return;
        }
        if (!this.hasWorkingEnergy()) {
            this.setWaitReason(MoldingWaitReason.MISSING_POWER);
            return;
        }
        this.advanceMoldFill(nextProgress, nextClayTarget);
    }

    private void advanceMoldFill(int nextProgress, int clayTarget) {
        if (!this.consumeWorkingEnergy()) {
            this.setWaitReason(MoldingWaitReason.MISSING_POWER);
            return;
        }
        int toConsume = Math.max(0, clayTarget - this.moldedClayBalls);
        if (toConsume > 0) {
            ItemStack previous = this.inventory.getItem(CLAY_SLOT).copy();
            ItemStack removed = this.inventory.removeItem(CLAY_SLOT, toConsume);
            if (removed.getCount() != toConsume) {
                this.inventory.setItem(CLAY_SLOT, previous);
                this.energy = Math.min(
                    MoldingPowerBridge.capacity(),
                    this.energy + MoldingPowerBridge.energyPerWorkingTick()
                );
                this.setWaitReason(MoldingWaitReason.MISSING_CLAY);
                return;
            }
            this.moldedClayBalls += toConsume;
        }
        this.moldFillProgress = nextProgress;
        if (this.moldFillProgress >= MOLD_FILL_TICKS && this.moldedClayBalls >= this.requiredClayBalls) {
            this.setMachineState(PlasticMoldingMachineState.MOLD_READY);
            this.setWaitReason(MoldingWaitReason.MOLD_READY);
        } else {
            this.setWaitReason(MoldingWaitReason.MOLD_FILLING);
        }
        this.syncProductionState(false);
    }

    private void tickMeltPump() {
        this.updateReadyState();
        if (!this.structureComplete) {
            this.setWaitReason(MoldingWaitReason.STRUCTURE_INCOMPLETE);
            return;
        }
        if (this.creativeOverrideLocked) {
            this.setWaitReason(MoldingWaitReason.PROCESS_READY);
            return;
        }
        if (this.cycleFormingMode == MoldingFormingMode.PRINTING && !this.printingComponentPresent()) {
            this.setWaitReason(MoldingWaitReason.MISSING_PRINTING_COMPONENT);
            return;
        }
        int remaining = this.batchFluidCapacity() - this.formingFluidAmount();
        if (remaining <= 0) {
            this.setWaitReason(this.machineState == PlasticMoldingMachineState.PROCESS_READY
                ? MoldingWaitReason.PROCESS_READY
                : MoldingWaitReason.BATCH_FULL);
            return;
        }
        if (this.stagingTank.isEmpty()) {
            this.setWaitReason(this.machineState == PlasticMoldingMachineState.PROCESS_READY
                ? MoldingWaitReason.PROCESS_READY
                : MoldingWaitReason.MOLD_READY);
            return;
        }
        int pumpRate = this.cycleFormingMode == MoldingFormingMode.PRINTING
            ? PRINTING_PUMP_RATE
            : MOLDING_PUMP_RATE;
        int transfer = Math.min(pumpRate, Math.min(remaining, this.stagingTank.getFluidAmount()));
        FluidStack candidate = this.stagingTank.getFluid().copyWithAmount(transfer);
        if (this.fillFormingFluid(candidate, IFluidHandler.FluidAction.SIMULATE) != transfer) {
            this.setWaitReason(MoldingWaitReason.BATCH_FULL);
            return;
        }
        if (!this.consumeWorkingEnergy()) {
            this.setWaitReason(MoldingWaitReason.MISSING_POWER);
            return;
        }
        FluidStack stagingBefore = this.stagingTank.getFluid().copy();
        FluidStack batchBefore = this.formingFluid();
        int energyBefore = this.energy;
        FluidStack drained = this.stagingTank.drain(candidate, IFluidHandler.FluidAction.EXECUTE);
        if (drained.getAmount() != transfer
            || this.fillFormingFluid(drained, IFluidHandler.FluidAction.EXECUTE) != transfer) {
            this.stagingTank.setFluid(stagingBefore);
            this.setFormingFluid(batchBefore);
            this.energy = energyBefore;
            this.setChanged();
            AnvilcraftPlasticraft.LOGGER.error("Failed atomic molding melt transfer at {}", this.worldPosition);
            return;
        }
        this.updateReadyState();
        this.setWaitReason(this.machineState == PlasticMoldingMachineState.PROCESS_READY
            ? MoldingWaitReason.PROCESS_READY
            : MoldingWaitReason.PUMPING);
    }

    private void tickWaitingNextCycle() {
        if (!this.structureComplete) {
            this.setWaitReason(MoldingWaitReason.STRUCTURE_INCOMPLETE);
            return;
        }
        if (!this.regionIsClear()) {
            this.setWaitReason(MoldingWaitReason.WAITING_FOR_CLEAR_REGION);
            return;
        }
        MachineOutcome outcome = this.prepareCurrentModelForCycle();
        if (!outcome.accepted()) this.setWaitReason(MoldingWaitReason.INVALID_MODEL);
    }

    private void updateReadyState() {
        if (this.cycleFormingMode == MoldingFormingMode.PRINTING && !this.printingComponentPresent()) {
            this.setMachineState(PlasticMoldingMachineState.MOLD_READY);
            this.setWaitReason(MoldingWaitReason.MISSING_PRINTING_COMPONENT);
            return;
        }
        PlasticMoldingMachineState next = this.creativeOverrideLocked
            || this.formingFluidAmount() >= this.requiredProcessMelt()
            ? PlasticMoldingMachineState.PROCESS_READY
            : PlasticMoldingMachineState.MOLD_READY;
        this.setMachineState(next);
        this.setWaitReason(next == PlasticMoldingMachineState.PROCESS_READY
            ? MoldingWaitReason.PROCESS_READY
            : MoldingWaitReason.MOLD_READY);
    }

    private boolean regionIsClear() {
        if (!(this.level instanceof ServerLevel serverLevel)) return false;
        AABB bounds = PlasticMoldingChamberStructure.regionBounds(
            this.worldPosition,
            this.getBlockState().getValue(PlasticMoldingChamberBlock.FACING)
        );
        return serverLevel.getEntities(
            (Entity) null,
            bounds,
            entity -> entity.isAlive() && !entity.isSpectator()
        ).isEmpty();
    }

    private void tickClayCollection() {
        if (--this.clayCollectionCountdown > 0) return;
        this.clayCollectionCountdown = CLAY_COLLECTION_INTERVAL;
        if (this.level == null || this.level.isClientSide
            || this.inventory.getItem(CLAY_SLOT).getCount() >= this.clayLimit) {
            return;
        }
        Direction front = this.getBlockState().getValue(PlasticMoldingChamberBlock.FACING);
        AABB bounds = PlasticMoldingChamberStructure.clayCollectionBounds(this.worldPosition, front);
        List<ItemEntity> clayEntities = this.level.getEntitiesOfClass(
            ItemEntity.class,
            bounds,
            entity -> entity.isAlive() && entity.getItem().is(Items.CLAY_BALL)
        );
        for (ItemEntity entity : clayEntities) {
            ItemStack remainder = this.clayItemHandler.insertItem(0, entity.getItem(), false);
            if (remainder.isEmpty()) {
                entity.remove(Entity.RemovalReason.DISCARDED);
            } else {
                entity.setItem(remainder);
            }
            if (this.inventory.getItem(CLAY_SLOT).getCount() >= this.clayLimit) return;
        }
    }

    private boolean hasWorkingEnergy() {
        return this.energy >= MoldingPowerBridge.energyPerWorkingTick();
    }

    private boolean consumeWorkingEnergy() {
        int required = MoldingPowerBridge.energyPerWorkingTick();
        if (this.energy < required) return false;
        this.energy -= required;
        this.setChanged();
        this.syncPowerState();
        return true;
    }

    private void returnMoldedClay() {
        if (this.moldedClayBalls <= 0) return;
        ItemStack current = this.inventory.getItem(CLAY_SLOT);
        int room = Math.max(0, this.clayLimit - current.getCount());
        int returned = Math.min(room, this.moldedClayBalls);
        if (returned > 0) {
            if (current.isEmpty()) this.inventory.setItem(CLAY_SLOT, new ItemStack(Items.CLAY_BALL, returned));
            else current.grow(returned);
        }
        this.dropClayInRegion(this.moldedClayBalls - returned);
        this.moldedClayBalls = 0;
    }

    private boolean returnBatchMelt() {
        FluidStack batch = this.formingFluid();
        if (batch.isEmpty()) return true;
        if (this.stagingTank.fill(batch, IFluidHandler.FluidAction.SIMULATE) != batch.getAmount()) return false;
        FluidStack stagingBefore = this.stagingTank.getFluid().copy();
        if (this.stagingTank.fill(batch, IFluidHandler.FluidAction.EXECUTE) != batch.getAmount()) {
            this.stagingTank.setFluid(stagingBefore);
            return false;
        }
        this.setFormingFluid(FluidStack.EMPTY);
        return true;
    }

    private void dropClayInRegion(int amount) {
        if (amount <= 0 || this.level == null || this.level.isClientSide) return;
        Direction front = this.getBlockState().getValue(PlasticMoldingChamberBlock.FACING);
        List<BlockPos> positions = PlasticMoldingChamberStructure.regionPositions(this.worldPosition, front);
        int dropped = 0;
        int index = 0;
        while (dropped < amount) {
            int count = Math.min(Items.CLAY_BALL.getDefaultMaxStackSize(), amount - dropped);
            BlockPos pos = positions.get(index++ % positions.size());
            Containers.dropItemStack(
                this.level,
                pos.getX() + 0.5D,
                pos.getY() + 0.5D,
                pos.getZ() + 0.5D,
                new ItemStack(Items.CLAY_BALL, count)
            );
            dropped += count;
        }
    }

    private void applyModel(EditableMoldingModel next, boolean incrementRevision) {
        this.model = next;
        this.bakedModel = MoldingModelBaker.bake(next);
        this.printingPlan = null;
        this.batchTank.setCapacity(this.bakedModel.analysis().minimumMeltMillibuckets());
        if (incrementRevision) this.revision++;
        this.syncModel();
    }

    private void setMachineState(PlasticMoldingMachineState next) {
        if (this.machineState == next) return;
        boolean oldCollision = this.hasMoldCollision();
        this.machineState = next;
        boolean newCollision = this.hasMoldCollision();
        this.syncProductionState(oldCollision != newCollision);
    }

    private void setWaitReason(MoldingWaitReason reason) {
        if (this.waitReason == reason) return;
        this.waitReason = reason;
        this.setChanged();
    }

    private void syncProductionState(boolean collisionChanged) {
        this.setChanged();
        if (this.level == null) return;
        BlockState state = this.getBlockState();
        boolean locked = this.isLocked();
        boolean powered = this.energy > 0;
        BlockState nextState = state;
        if (state.hasProperty(PlasticMoldingChamberBlock.LOCKED)
            && state.getValue(PlasticMoldingChamberBlock.LOCKED) != locked) {
            nextState = nextState.setValue(PlasticMoldingChamberBlock.LOCKED, locked);
        }
        if (state.hasProperty(PlasticMoldingChamberBlock.POWERED)
            && state.getValue(PlasticMoldingChamberBlock.POWERED) != powered) {
            nextState = nextState.setValue(PlasticMoldingChamberBlock.POWERED, powered);
        }
        if (nextState != state) {
            this.level.setBlock(this.worldPosition, nextState, Block.UPDATE_ALL);
        } else {
            this.level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
        this.clientSyncPending = false;
        this.lastClientSyncGameTime = this.level.getGameTime();
        if (collisionChanged) {
            Direction front = state.getValue(PlasticMoldingChamberBlock.FACING);
            for (BlockPos region : PlasticMoldingChamberStructure.regionPositions(this.worldPosition, front)) {
                BlockState regionState = this.level.getBlockState(region);
                this.level.sendBlockUpdated(region, regionState, regionState, Block.UPDATE_ALL);
            }
        }
    }

    private void syncPowerState() {
        if (this.level == null || this.level.isClientSide) return;
        BlockState state = this.getBlockState();
        if (!state.hasProperty(PlasticMoldingChamberBlock.POWERED)) return;
        boolean powered = this.energy > 0;
        if (state.getValue(PlasticMoldingChamberBlock.POWERED) != powered) {
            this.level.setBlock(
                this.worldPosition,
                state.setValue(PlasticMoldingChamberBlock.POWERED, powered),
                Block.UPDATE_ALL
            );
        }
    }

    private void onFluidChanged() {
        this.requestClientSync();
    }

    private void requestClientSync() {
        this.setChanged();
        if (this.level == null || this.level.isClientSide) return;
        this.clientSyncPending = true;
        this.flushClientSync();
    }

    private void flushClientSync() {
        if (!this.clientSyncPending || this.level == null || this.level.isClientSide) return;
        long gameTime = this.level.getGameTime();
        if (this.lastClientSyncGameTime != Long.MIN_VALUE
            && gameTime - this.lastClientSyncGameTime < CLIENT_SYNC_INTERVAL) {
            return;
        }
        this.syncProductionState(false);
    }

    private FluidStack formingFluid() {
        if (this.activeFormingMode() != MoldingFormingMode.PRINTING) return this.batchTank.getFluid().copy();
        Plastic3DPrintingComponentBlockEntity component = this.printingComponent();
        return component == null ? FluidStack.EMPTY : component.fluid();
    }

    private int formingFluidAmount() {
        if (this.activeFormingMode() != MoldingFormingMode.PRINTING) return this.batchTank.getFluidAmount();
        Plastic3DPrintingComponentBlockEntity component = this.printingComponent();
        return component == null ? 0 : component.fluidAmount();
    }

    private int fillFormingFluid(FluidStack resource, IFluidHandler.FluidAction action) {
        if (this.activeFormingMode() != MoldingFormingMode.PRINTING) return this.batchTank.fill(resource, action);
        Plastic3DPrintingComponentBlockEntity component = this.printingComponent();
        return component == null ? 0 : component.fillFromChamber(resource, action);
    }

    private FluidStack drainFormingFluid(FluidStack resource, IFluidHandler.FluidAction action) {
        if (this.activeFormingMode() != MoldingFormingMode.PRINTING) return this.batchTank.drain(resource, action);
        Plastic3DPrintingComponentBlockEntity component = this.printingComponent();
        return component == null ? FluidStack.EMPTY : component.consumeForPrinting(resource.getAmount(), action);
    }

    private FluidStack drainFormingFluid(int amount, IFluidHandler.FluidAction action) {
        if (this.activeFormingMode() != MoldingFormingMode.PRINTING) return this.batchTank.drain(amount, action);
        Plastic3DPrintingComponentBlockEntity component = this.printingComponent();
        return component == null ? FluidStack.EMPTY : component.consumeForPrinting(amount, action);
    }

    private void setFormingFluid(FluidStack fluid) {
        if (this.activeFormingMode() != MoldingFormingMode.PRINTING) {
            this.batchTank.setFluid(fluid.copy());
            return;
        }
        Plastic3DPrintingComponentBlockEntity component = this.printingComponent();
        if (component != null) component.setFluidFromChamber(fluid);
    }

    private MoldingFormingMode activeFormingMode() {
        return this.machineState == PlasticMoldingMachineState.EDITABLE
            ? this.formingMode()
            : this.cycleFormingMode;
    }

    private boolean isCompatibleStagingFluid(FluidStack stack) {
        if (!isPlasticMelt(stack)) return false;
        FluidStack batch = this.formingFluid();
        return batch.isEmpty() || FluidStack.isSameFluidSameComponents(batch, stack);
    }

    private static boolean isPlasticMelt(FluidStack stack) {
        return !stack.isEmpty() && stack.getFluid().getFluidType() == PlasticraftFluids.UNIVERSAL_PLASTIC_MELT_TYPE.get();
    }

    private static boolean isPlasticMeltContainer(ItemStack stack) {
        IFluidHandlerItem container = FluidUtil.getFluidHandler(stack.copyWithCount(1)).orElse(null);
        if (container == null) return false;
        return isPlasticMelt(container.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE));
    }

    private static int capacitorEnergy(ItemStack stack) {
        if (stack.getItem() instanceof SuperCapacitorItem) return SuperCapacitorItem.ENERGY;
        if (stack.getItem() instanceof CapacitorItem) return CapacitorItem.ENERGY;
        return 0;
    }

    public static boolean isCreativeGenerator(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ModBlocks.CREATIVE_GENERATOR.asItem());
    }

    public static boolean isMultiphaseTranscendium(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ModItems.MULTIPHASE_TRANSCENDIUM.get());
    }

    private static boolean sameFluidAndAmount(FluidStack first, FluidStack second) {
        return first.getAmount() == second.getAmount()
            && (first.isEmpty() && second.isEmpty() || FluidStack.isSameFluidSameComponents(first, second));
    }

    private static int clayTargetForLayer(int layer, int total) {
        return (total * Math.clamp(layer, 0, MOLD_FILL_LAYERS) + MOLD_FILL_LAYERS - 1) / MOLD_FILL_LAYERS;
    }

    private static boolean hasMoldCollision(PlasticMoldingMachineState state) {
        return switch (state) {
            case MOLD_FILLING, MOLD_READY, PROCESS_READY -> true;
            case EDITABLE, WAITING_TO_LOCK, PROCESSING, WAITING_NEXT_CYCLE -> false;
        };
    }

    private boolean isWriter(ServerPlayer player, UUID sessionId) {
        this.expireLease(player.level().getGameTime());
        return this.writerPlayerId != null
            && this.writerSessionId != null
            && this.writerPlayerId.equals(player.getUUID())
            && this.writerSessionId.equals(sessionId);
    }

    private boolean expireLease(long gameTime) {
        if (this.writerPlayerId == null || gameTime <= this.leaseExpiresAt) return false;
        this.clearLease();
        return true;
    }

    private void clearLease() {
        this.writerPlayerId = null;
        this.writerSessionId = null;
        this.writerName = "";
        this.leaseExpiresAt = 0L;
        this.undoHistory.clear();
        this.redoHistory.clear();
    }

    private static void pushHistory(Deque<EditableMoldingModel> history, EditableMoldingModel model) {
        history.addFirst(model);
        while (history.size() > HISTORY_LIMIT) history.removeLast();
    }

    private void syncModel() {
        this.setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.put(TAG_MODEL, MoldingModelPersistence.save(this.model));
        tag.putLong(TAG_REVISION, this.revision);
        tag.put(TAG_INVENTORY, this.saveInventory(provider));
        tag.putInt(TAG_CLAY_COUNT, this.inventory.getItem(CLAY_SLOT).getCount());
        this.saveProduction(tag, provider);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        try {
            this.model = tag.contains(TAG_MODEL, CompoundTag.TAG_COMPOUND)
                ? MoldingModelPersistence.load(tag.getCompound(TAG_MODEL))
                : EditableMoldingModel.empty();
            this.bakedModel = MoldingModelBaker.bake(this.model);
        } catch (RuntimeException exception) {
            AnvilcraftPlasticraft.LOGGER.error("Invalid molding model at {}; resetting draft", this.worldPosition, exception);
            this.model = EditableMoldingModel.empty();
            this.bakedModel = MoldingModelBaker.bake(this.model);
        }
        this.batchTank.setCapacity(this.bakedModel.analysis().minimumMeltMillibuckets());
        this.revision = Math.max(0L, tag.getLong(TAG_REVISION));
        this.loadInventory(tag, provider);
        this.loadProduction(tag, provider);
        this.clearLease();
    }

    private CompoundTag saveInventory(HolderLookup.Provider provider) {
        NonNullList<ItemStack> savedItems = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            savedItems.set(slot, this.inventory.getItem(slot).copy());
        }
        ItemStack clay = savedItems.get(CLAY_SLOT);
        // 1.21.1 的 ItemStack 编码不接受三位数数量，槽位保留物品组件，数量单独持久化。
        if (!clay.isEmpty()) clay.setCount(1);
        return ContainerHelper.saveAllItems(new CompoundTag(), savedItems, provider);
    }

    private void loadInventory(CompoundTag tag, HolderLookup.Provider provider) {
        this.inventory.clearContent();
        if (tag.contains(TAG_INVENTORY, CompoundTag.TAG_COMPOUND)) {
            ContainerHelper.loadAllItems(
                tag.getCompound(TAG_INVENTORY),
                this.inventory.getItems(),
                provider
            );
        }
        ItemStack clay = this.inventory.getItem(CLAY_SLOT);
        int clayCount = Math.clamp(tag.getInt(TAG_CLAY_COUNT), 0, CLAY_LIMIT_MAX);
        if (clayCount == 0 || !clay.is(Items.CLAY_BALL)) {
            this.inventory.getItems().set(CLAY_SLOT, ItemStack.EMPTY);
        } else {
            clay.setCount(clayCount);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.put(TAG_MODEL, MoldingModelPersistence.save(this.model));
        tag.putLong(TAG_REVISION, this.revision);
        this.saveProduction(tag, provider);
        return tag;
    }

    private void saveProduction(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putString(TAG_MACHINE_STATE, this.machineState.getSerializedName());
        tag.putBoolean(TAG_STRUCTURE_COMPLETE, this.structureComplete);
        tag.putInt(TAG_CLAY_LIMIT, this.clayLimit);
        tag.putInt(TAG_MOLDED_CLAY, this.moldedClayBalls);
        tag.putInt(TAG_REQUIRED_CLAY, this.requiredClayBalls);
        tag.putInt(TAG_FILL_PROGRESS, this.moldFillProgress);
        tag.put(TAG_STAGING_TANK, this.stagingTank.writeToNBT(provider, new CompoundTag()));
        tag.put(TAG_BATCH_TANK, this.batchTank.writeToNBT(provider, new CompoundTag()));
        tag.putInt(TAG_ENERGY, this.energy);
        tag.putString(TAG_SELECTED_MODE, this.selectedMode.getSerializedName());
        tag.putString(TAG_CYCLE_MODE, this.cycleMode.getSerializedName());
        tag.putString(TAG_CYCLE_FORMING_MODE, this.cycleFormingMode.getSerializedName());
        tag.putInt(TAG_PRINTING_PROGRESS, this.printingProgress);
        tag.putInt(TAG_PRINTING_TOTAL, this.printingTotal);
        tag.put(TAG_PRINTING_MATERIAL, this.printingMaterial.saveOptional(provider));
        tag.putInt(TAG_PRINTING_MELT_CONSUMED, this.printingMeltConsumed);
        tag.putBoolean(TAG_REDSTONE_POWERED, this.redstonePowered);
        tag.putString(TAG_WAIT_REASON, this.waitReason.name());
        tag.putBoolean(TAG_TYPE_OVERRIDE, this.typeOverrideCommitted);
        tag.putBoolean(TAG_CREATIVE_OVERRIDE, this.creativeOverrideLocked);
    }

    private void loadProduction(CompoundTag tag, HolderLookup.Provider provider) {
        this.processingSnapshot = null;
        this.machineState = PlasticMoldingMachineState.fromSerializedName(tag.getString(TAG_MACHINE_STATE));
        this.structureComplete = tag.getBoolean(TAG_STRUCTURE_COMPLETE);
        this.clayLimit = tag.contains(TAG_CLAY_LIMIT)
            ? Math.clamp(tag.getInt(TAG_CLAY_LIMIT), CLAY_LIMIT_MIN, CLAY_LIMIT_MAX)
            : DEFAULT_CLAY_LIMIT;
        this.moldedClayBalls = Math.max(0, tag.getInt(TAG_MOLDED_CLAY));
        this.requiredClayBalls = Math.max(this.moldedClayBalls, tag.getInt(TAG_REQUIRED_CLAY));
        this.moldFillProgress = Math.clamp(tag.getInt(TAG_FILL_PROGRESS), 0, MOLD_FILL_TICKS);
        this.stagingTank.readFromNBT(provider, tag.getCompound(TAG_STAGING_TANK));
        this.batchTank.readFromNBT(provider, tag.getCompound(TAG_BATCH_TANK));
        this.energy = Math.clamp(tag.getInt(TAG_ENERGY), 0, MoldingPowerBridge.capacity());
        this.selectedMode = MoldingProductionMode.fromSerializedName(tag.getString(TAG_SELECTED_MODE));
        this.cycleMode = MoldingProductionMode.fromSerializedName(tag.getString(TAG_CYCLE_MODE));
        this.cycleFormingMode = MoldingFormingMode.fromSerializedName(tag.getString(TAG_CYCLE_FORMING_MODE));
        this.printingPlan = null;
        int printingPlanSize = this.cycleFormingMode == MoldingFormingMode.PRINTING
            ? this.printingPlan().size()
            : 0;
        this.printingTotal = Math.clamp(
            tag.contains(TAG_PRINTING_TOTAL) ? tag.getInt(TAG_PRINTING_TOTAL) : 0,
            0,
            printingPlanSize
        );
        this.printingProgress = Math.clamp(
            tag.contains(TAG_PRINTING_PROGRESS) ? tag.getInt(TAG_PRINTING_PROGRESS) : 0,
            0,
            this.printingTotal
        );
        this.printingMaterial = FluidStack.parseOptional(
            provider,
            tag.getCompound(TAG_PRINTING_MATERIAL)
        );
        this.printingMeltConsumed = Math.clamp(
            tag.getInt(TAG_PRINTING_MELT_CONSUMED),
            0,
            this.batchFluidCapacity()
        );
        this.redstonePowered = tag.getBoolean(TAG_REDSTONE_POWERED);
        this.typeOverrideCommitted = tag.getBoolean(TAG_TYPE_OVERRIDE);
        this.creativeOverrideLocked = tag.getBoolean(TAG_CREATIVE_OVERRIDE);
        try {
            this.waitReason = MoldingWaitReason.valueOf(tag.getString(TAG_WAIT_REASON));
        } catch (IllegalArgumentException exception) {
            this.waitReason = MoldingWaitReason.NONE;
        }
        if (this.machineState == PlasticMoldingMachineState.EDITABLE) {
            this.moldedClayBalls = 0;
            this.requiredClayBalls = 0;
            this.moldFillProgress = 0;
            this.printingProgress = 0;
            this.printingTotal = 0;
            this.printingMaterial = FluidStack.EMPTY;
            this.printingMeltConsumed = 0;
            this.cycleFormingMode = MoldingFormingMode.CASTING;
            this.typeOverrideCommitted = false;
            this.creativeOverrideLocked = false;
        } else if (this.machineState == PlasticMoldingMachineState.PROCESSING
            && this.cycleFormingMode == MoldingFormingMode.PRINTING
            && this.printingTotal == this.printingPlan().size()) {
            this.processingSnapshot = new MoldingProcessSnapshot(
                this.revision,
                this.model,
                this.bakedModel,
                this.printingMaterial,
                this.moldedClayBalls,
                this.cycleMode,
                this.cycleFormingMode,
                this.typeOverrideCommitted,
                this.creativeOverrideLocked,
                this.printingProgress,
                this.printingTotal
            );
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public record EditOutcome(
        boolean accepted,
        String reason,
        long revision,
        EditableMoldingModel model
    ) {
        private static EditOutcome accepted(PlasticMoldingChamberBlockEntity chamber) {
            return new EditOutcome(true, "", chamber.revision, chamber.model);
        }

        private static EditOutcome rejected(String reason, PlasticMoldingChamberBlockEntity chamber) {
            return new EditOutcome(false, reason, chamber.revision, chamber.model);
        }
    }

    public record MachineOutcome(boolean accepted, String reason) {
        public static MachineOutcome success() {
            return new MachineOutcome(true, "");
        }

        public static MachineOutcome rejected(String reason) {
            return new MachineOutcome(false, reason);
        }
    }

    private static final class MoldingInventory extends SimpleContainer {
        private MoldingInventory() {
            super(INVENTORY_SIZE);
        }

        @Override
        public int getMaxStackSize() {
            return CLAY_LIMIT_MAX;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return stack.is(Items.CLAY_BALL) ? CLAY_LIMIT_MAX : stack.getMaxStackSize();
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return switch (slot) {
                case CLAY_SLOT -> stack.is(Items.CLAY_BALL);
                case DISK_SLOT -> MoldingBlueprintDisk.isStructureDisk(stack);
                case RESOURCE_SLOT -> isResourceInput(stack);
                default -> false;
            };
        }
    }

    private final class ClayItemHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            validateSlot(slot);
            return PlasticMoldingChamberBlockEntity.this.inventory.getItem(CLAY_SLOT);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            validateSlot(slot);
            if (stack.isEmpty() || !stack.is(Items.CLAY_BALL)) return stack;
            ItemStack current = getStackInSlot(slot);
            if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, stack)) return stack;
            int accepted = Math.min(
                stack.getCount(),
                Math.max(0, getSlotLimit(slot) - current.getCount())
            );
            if (accepted <= 0) return stack;
            if (!simulate) {
                if (current.isEmpty()) {
                    PlasticMoldingChamberBlockEntity.this.inventory.setItem(
                        CLAY_SLOT,
                        stack.copyWithCount(accepted)
                    );
                } else {
                    current.grow(accepted);
                    PlasticMoldingChamberBlockEntity.this.inventory.setChanged();
                }
            }
            return stack.copyWithCount(stack.getCount() - accepted);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            validateSlot(slot);
            if (amount <= 0) return ItemStack.EMPTY;
            ItemStack current = getStackInSlot(slot);
            int extracted = Math.min(amount, current.getCount());
            if (extracted <= 0) return ItemStack.EMPTY;
            ItemStack result = current.copyWithCount(extracted);
            if (!simulate) PlasticMoldingChamberBlockEntity.this.inventory.removeItem(CLAY_SLOT, extracted);
            return result;
        }

        @Override
        public int getSlotLimit(int slot) {
            validateSlot(slot);
            return PlasticMoldingChamberBlockEntity.this.clayLimit;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            validateSlot(slot);
            return stack.is(Items.CLAY_BALL);
        }

        private static void validateSlot(int slot) {
            if (slot != 0) throw new RuntimeException("Slot " + slot + " not in valid range - [0,1)");
        }
    }

    private final class ChamberFluidHandler implements IFluidHandler {
        @Override
        public int getTanks() {
            return 2;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return switch (tank) {
                case 0 -> PlasticMoldingChamberBlockEntity.this.stagingTank.getFluid().copy();
                case 1 -> PlasticMoldingChamberBlockEntity.this.formingFluid();
                default -> FluidStack.EMPTY;
            };
        }

        @Override
        public int getTankCapacity(int tank) {
            return switch (tank) {
                case 0 -> STAGING_TANK_CAPACITY;
                case 1 -> PlasticMoldingChamberBlockEntity.this.batchFluidCapacity();
                default -> 0;
            };
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return tank == 0 && PlasticMoldingChamberBlockEntity.this.isCompatibleStagingFluid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return PlasticMoldingChamberBlockEntity.this.stagingTank.fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) return FluidStack.EMPTY;
            FluidStack batch = PlasticMoldingChamberBlockEntity.this.formingFluid();
            if (PlasticMoldingChamberBlockEntity.this.machineState != PlasticMoldingMachineState.PROCESSING
                && !batch.isEmpty()
                && FluidStack.isSameFluidSameComponents(batch, resource)) {
                return PlasticMoldingChamberBlockEntity.this.drainFormingFluid(resource, action);
            }
            return PlasticMoldingChamberBlockEntity.this.stagingTank.drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (maxDrain <= 0) return FluidStack.EMPTY;
            if (PlasticMoldingChamberBlockEntity.this.machineState != PlasticMoldingMachineState.PROCESSING
                && PlasticMoldingChamberBlockEntity.this.formingFluidAmount() > 0) {
                return PlasticMoldingChamberBlockEntity.this.drainFormingFluid(maxDrain, action);
            }
            return PlasticMoldingChamberBlockEntity.this.stagingTank.drain(maxDrain, action);
        }
    }

    private final class ChamberEnergyStorage implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int accepted = Math.min(
                Math.max(0, maxReceive),
                MoldingPowerBridge.capacity() - PlasticMoldingChamberBlockEntity.this.energy
            );
            if (!simulate && accepted > 0) {
                PlasticMoldingChamberBlockEntity.this.energy += accepted;
                PlasticMoldingChamberBlockEntity.this.setChanged();
                PlasticMoldingChamberBlockEntity.this.syncPowerState();
            }
            return accepted;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return PlasticMoldingChamberBlockEntity.this.energy;
        }

        @Override
        public int getMaxEnergyStored() {
            return MoldingPowerBridge.capacity();
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return PlasticMoldingChamberBlockEntity.this.energy < MoldingPowerBridge.capacity();
        }
    }

    private final class ChamberMenuData implements ContainerData {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> PlasticMoldingChamberBlockEntity.this.machineState.ordinal();
                case 1 -> PlasticMoldingChamberBlockEntity.this.selectedMode.ordinal();
                case 2 -> PlasticMoldingChamberBlockEntity.this.waitReason.ordinal();
                case 3 -> PlasticMoldingChamberBlockEntity.this.clayLimit;
                case 4 -> PlasticMoldingChamberBlockEntity.this.moldedClayBalls;
                case 5 -> PlasticMoldingChamberBlockEntity.this.requiredClayBalls;
                case 6 -> PlasticMoldingChamberBlockEntity.this.moldFillProgress;
                case 7 -> PlasticMoldingChamberBlockEntity.this.stagingTank.getFluidAmount();
                case 8 -> PlasticMoldingChamberBlockEntity.this.formingFluidAmount();
                case 9 -> PlasticMoldingChamberBlockEntity.this.batchFluidCapacity();
                case 10 -> PlasticMoldingChamberBlockEntity.this.energy;
                case 11 -> PlasticMoldingChamberBlockEntity.this.structureComplete ? 1 : 0;
                case 12 -> PlasticMoldingChamberBlockEntity.this.undoHistory.isEmpty() ? 0 : 1;
                case 13 -> PlasticMoldingChamberBlockEntity.this.typeOverrideCommitted ? 1 : 0;
                case 14 -> PlasticMoldingChamberBlockEntity.this.creativeOverrideLocked ? 1 : 0;
                case 15 -> PlasticMoldingChamberBlockEntity.this.formingMode().ordinal();
                case 16 -> PlasticMoldingChamberBlockEntity.this.cycleFormingMode.ordinal();
                case 17 -> PlasticMoldingChamberBlockEntity.this.printingComponentPresent() ? 1 : 0;
                case 18 -> PlasticMoldingChamberBlockEntity.this.printingProgress;
                case 19 -> PlasticMoldingChamberBlockEntity.this.printingTotal;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return MENU_DATA_COUNT;
        }
    }
}
