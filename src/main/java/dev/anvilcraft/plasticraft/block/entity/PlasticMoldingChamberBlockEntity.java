package dev.anvilcraft.plasticraft.block.entity;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.PlasticMoldingChamberBlock;
import dev.anvilcraft.plasticraft.block.PlasticMoldingChamberStructure;
import dev.anvilcraft.plasticraft.block.PlasticMoldingMachineState;
import dev.anvilcraft.plasticraft.init.block.ModBlockEntities;
import dev.anvilcraft.plasticraft.inventory.PlasticMoldingChamberMenu;
import dev.anvilcraft.plasticraft.molding.bake.BakedMoldingModel;
import dev.anvilcraft.plasticraft.molding.bake.MoldingModelBaker;
import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.MoldingCommand;
import dev.anvilcraft.plasticraft.molding.model.MoldingModelPersistence;
import dev.anvilcraft.plasticraft.molding.session.MoldingSessionSnapshot;
import dev.anvilcraft.plasticraft.network.MoldingSessionStatusPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/** 保存成型舱草稿、编辑修订、单写者租约和界面宿主槽。 */
public class PlasticMoldingChamberBlockEntity extends BlockEntity {
    public static final int CLAY_SLOT = 0;
    public static final int DISK_SLOT = 1;
    public static final int INVENTORY_SIZE = 2;
    public static final int HISTORY_LIMIT = 10;
    public static final long LEASE_TICKS = 200L;
    private static final int REPAIR_INTERVAL = 20;
    private static final String TAG_MODEL = "EditableModel";
    private static final String TAG_REVISION = "ModelRevision";
    private static final String TAG_MACHINE_STATE = "MachineState";
    private static final String TAG_INVENTORY = "Inventory";
    private static final String TAG_STRUCTURE_COMPLETE = "StructureComplete";

    private final SimpleContainer inventory = new SimpleContainer(INVENTORY_SIZE);
    private final Deque<EditableMoldingModel> undoHistory = new ArrayDeque<>();
    private final Deque<EditableMoldingModel> redoHistory = new ArrayDeque<>();
    private EditableMoldingModel model = EditableMoldingModel.empty();
    private BakedMoldingModel bakedModel = MoldingModelBaker.bake(this.model);
    private PlasticMoldingMachineState machineState = PlasticMoldingMachineState.EDITABLE;
    private long revision;
    private boolean structureComplete;
    private int repairCountdown;
    @Nullable
    private UUID writerPlayerId;
    @Nullable
    private UUID writerSessionId;
    private String writerName = "";
    private long leaseExpiresAt;

    public PlasticMoldingChamberBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.PLASTIC_MOLDING_CHAMBER.get(), pos, state);
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
        if (chamber.repairCountdown-- > 0) return;
        chamber.repairCountdown = REPAIR_INTERVAL;
        boolean complete = PlasticMoldingChamberStructure.repair(level, pos, state.getValue(
            PlasticMoldingChamberBlock.FACING
        ));
        if (complete != chamber.structureComplete) {
            chamber.structureComplete = complete;
            chamber.setChanged();
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

    public boolean structureComplete() {
        return this.structureComplete;
    }

    public SimpleContainer inventory() {
        return this.inventory;
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
            this.model = next;
            this.bakedModel = MoldingModelBaker.bake(next);
            this.revision++;
            this.leaseExpiresAt = player.level().getGameTime() + LEASE_TICKS;
            this.syncModel();
            return EditOutcome.accepted(this);
        } catch (RuntimeException exception) {
            AnvilcraftPlasticraft.LOGGER.debug("Rejected molding edit at {}", this.worldPosition, exception);
            return EditOutcome.rejected("invalid_command", this);
        }
    }

    public void applyClientSnapshot(long newRevision, EditableMoldingModel newModel) {
        if (newRevision < this.revision) return;
        this.model = newModel;
        this.bakedModel = MoldingModelBaker.bake(newModel);
        this.revision = newRevision;
    }

    public boolean applyClientDelta(long baseRevision, long newRevision, MoldingCommand command) {
        if (this.revision != baseRevision || newRevision != baseRevision + 1L) return false;
        try {
            EditableMoldingModel next = command.apply(this.model);
            this.model = next;
            this.bakedModel = MoldingModelBaker.bake(next);
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
        tag.putString(TAG_MACHINE_STATE, this.machineState.getSerializedName());
        tag.putBoolean(TAG_STRUCTURE_COMPLETE, this.structureComplete);
        tag.put(
            TAG_INVENTORY,
            ContainerHelper.saveAllItems(new CompoundTag(), this.inventory.getItems(), provider)
        );
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
        this.revision = Math.max(0L, tag.getLong(TAG_REVISION));
        this.machineState = PlasticMoldingMachineState.fromSerializedName(tag.getString(TAG_MACHINE_STATE));
        this.structureComplete = tag.getBoolean(TAG_STRUCTURE_COMPLETE);
        this.inventory.clearContent();
        if (tag.contains(TAG_INVENTORY, CompoundTag.TAG_COMPOUND)) {
            ContainerHelper.loadAllItems(
                tag.getCompound(TAG_INVENTORY),
                this.inventory.getItems(),
                provider
            );
        }
        this.clearLease();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.put(TAG_MODEL, MoldingModelPersistence.save(this.model));
        tag.putLong(TAG_REVISION, this.revision);
        tag.putString(TAG_MACHINE_STATE, this.machineState.getSerializedName());
        tag.putBoolean(TAG_STRUCTURE_COMPLETE, this.structureComplete);
        return tag;
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
}
