package dev.anvilcraft.plasticraft.inventory;

import dev.anvilcraft.plasticraft.block.PlasticMoldingMachineState;
import dev.anvilcraft.plasticraft.block.entity.PlasticMoldingChamberBlockEntity;
import dev.anvilcraft.plasticraft.init.PlasticraftMenuTypes;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprintSummary;
import dev.anvilcraft.plasticraft.molding.machine.MoldingProductionMode;
import dev.anvilcraft.plasticraft.molding.machine.MoldingWaitReason;
import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.MoldingCommand;
import dev.anvilcraft.plasticraft.molding.model.MoldingModelStreams;
import dev.anvilcraft.plasticraft.molding.session.MoldingSessionSnapshot;
import dev.anvilcraft.plasticraft.network.MoldingSessionStatusPacket;
import dev.dubhe.anvilcraft.item.DiskItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.function.IntSupplier;

/** 成型舱界面菜单；模型编辑本身通过带修订号的语义命令同步。 */
public class PlasticMoldingChamberMenu extends AbstractContainerMenu {
    public static final int MACHINE_SLOT_COUNT = 2;
    public static final int PLAYER_SLOT_START = MACHINE_SLOT_COUNT;
    public static final int PLAYER_SLOT_END = PLAYER_SLOT_START + 36;
    private final BlockPos chamberPos;
    private final Player player;
    private final ContainerData machineData;
    @Nullable
    private final PlasticMoldingChamberBlockEntity chamber;
    private UUID sessionId;
    private boolean writable;
    private String writerName;
    private long revision;
    private EditableMoldingModel model;
    private List<MoldingBlueprintSummary> blueprints = List.of();
    private String selectedBlueprintFileId = "";
    private long blueprintListGeneration;
    private Component titleMessage = Component.empty();
    private long titleMessageGeneration;

    public PlasticMoldingChamberMenu(
        MenuType<?> type,
        int containerId,
        Inventory playerInventory,
        PlasticMoldingChamberBlockEntity chamber,
        MoldingSessionSnapshot snapshot
    ) {
        super(type, containerId);
        this.player = playerInventory.player;
        this.chamber = chamber;
        this.chamberPos = chamber.getBlockPos();
        this.sessionId = snapshot.sessionId();
        this.writable = snapshot.writable();
        this.writerName = snapshot.writerName();
        this.revision = snapshot.revision();
        this.model = snapshot.model();
        this.machineData = chamber.menuData();
        this.addSlots(chamber.inventory(), playerInventory);
        this.addDataSlots(this.machineData);
    }

    public PlasticMoldingChamberMenu(
        MenuType<?> type,
        int containerId,
        Inventory playerInventory,
        RegistryFriendlyByteBuf buffer
    ) {
        super(type, containerId);
        this.player = playerInventory.player;
        this.chamber = null;
        this.chamberPos = buffer.readBlockPos();
        this.sessionId = buffer.readUUID();
        this.writable = buffer.readBoolean();
        this.writerName = buffer.readUtf(64);
        this.revision = buffer.readVarLong();
        this.model = MoldingModelStreams.readModel(buffer);
        this.machineData = new SimpleContainerData(PlasticMoldingChamberBlockEntity.MENU_DATA_COUNT);
        this.addSlots(PlasticMoldingChamberBlockEntity.createInventory(), playerInventory);
        this.addDataSlots(this.machineData);
    }

    public static void open(
        ServerPlayer player,
        PlasticMoldingChamberBlockEntity chamber,
        MoldingSessionSnapshot snapshot
    ) {
        player.openMenu(
            new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new PlasticMoldingChamberMenu(
                    PlasticraftMenuTypes.PLASTIC_MOLDING_CHAMBER.get(),
                    containerId,
                    inventory,
                    chamber,
                    snapshot
                ),
                Component.translatable("container.anvilcraftplasticraft.plastic_molding_chamber")
            ),
            buffer -> {
                buffer.writeBlockPos(chamber.getBlockPos());
                buffer.writeUUID(snapshot.sessionId());
                buffer.writeBoolean(snapshot.writable());
                buffer.writeUtf(snapshot.writerName(), 64);
                buffer.writeVarLong(snapshot.revision());
                MoldingModelStreams.writeModel(buffer, snapshot.model());
            }
        );
    }

    public BlockPos chamberPos() {
        return this.chamberPos;
    }

    public UUID sessionId() {
        return this.sessionId;
    }

    public boolean writable() {
        return this.writable;
    }

    public String writerName() {
        return this.writerName;
    }

    public long revision() {
        return this.revision;
    }

    public EditableMoldingModel model() {
        return this.model;
    }

    public List<MoldingBlueprintSummary> blueprints() {
        return this.blueprints;
    }

    public String selectedBlueprintFileId() {
        return this.selectedBlueprintFileId;
    }

    public long blueprintListGeneration() {
        return this.blueprintListGeneration;
    }

    public Component titleMessage() {
        return this.titleMessage;
    }

    public long titleMessageGeneration() {
        return this.titleMessageGeneration;
    }

    public void showTitleMessage(Component message) {
        this.titleMessage = message.copy();
        this.titleMessageGeneration++;
    }

    public static boolean showTitleMessage(Player player, BlockPos chamberPos, Component message) {
        if (!(player.containerMenu instanceof PlasticMoldingChamberMenu menu)
            || !menu.chamberPos.equals(chamberPos)) {
            return false;
        }
        menu.showTitleMessage(message);
        return true;
    }

    public void applyBlueprintList(List<MoldingBlueprintSummary> summaries, String selectedFileId) {
        this.blueprints = List.copyOf(summaries);
        this.selectedBlueprintFileId = selectedFileId;
        this.blueprintListGeneration++;
    }

    public PlasticMoldingMachineState machineState() {
        PlasticMoldingMachineState[] values = PlasticMoldingMachineState.values();
        return values[Math.clamp(this.machineData.get(0), 0, values.length - 1)];
    }

    public MoldingProductionMode productionMode() {
        MoldingProductionMode[] values = MoldingProductionMode.values();
        return values[Math.clamp(this.machineData.get(1), 0, values.length - 1)];
    }

    public MoldingWaitReason waitReason() {
        MoldingWaitReason[] values = MoldingWaitReason.values();
        return values[Math.clamp(this.machineData.get(2), 0, values.length - 1)];
    }

    public int clayLimit() {
        return Math.clamp(
            this.machineData.get(3),
            PlasticMoldingChamberBlockEntity.CLAY_LIMIT_MIN,
            PlasticMoldingChamberBlockEntity.CLAY_LIMIT_MAX
        );
    }

    public int moldedClayBalls() {
        return this.machineData.get(4);
    }

    public int requiredClayBalls() {
        return this.machineData.get(5);
    }

    public int moldFillProgress() {
        return this.machineData.get(6);
    }

    public int stagingFluidAmount() {
        return this.machineData.get(7);
    }

    public int batchFluidAmount() {
        return this.machineData.get(8);
    }

    public int batchFluidCapacity() {
        return this.machineData.get(9);
    }

    public int energyStored() {
        return this.machineData.get(10);
    }

    public boolean structureComplete() {
        return this.machineData.get(11) != 0;
    }

    public boolean canUndo() {
        return this.machineData.get(12) != 0;
    }

    public boolean modelEditable() {
        return this.writable && this.machineState() == PlasticMoldingMachineState.EDITABLE;
    }

    public void applySnapshot(MoldingSessionSnapshot snapshot) {
        this.sessionId = snapshot.sessionId();
        this.writable = snapshot.writable();
        this.writerName = snapshot.writerName();
        this.revision = snapshot.revision();
        this.model = snapshot.model();
    }

    public void applyWriterStatus(@Nullable UUID writerId, String currentWriterName) {
        this.writerName = currentWriterName;
        this.writable = writerId != null && writerId.equals(this.player.getUUID());
    }

    public boolean applyDelta(long baseRevision, long newRevision, MoldingCommand command) {
        if (this.revision != baseRevision || newRevision != baseRevision + 1L) return false;
        try {
            this.model = command.apply(this.model);
            this.revision = newRevision;
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(this.chamberPos).is(PlasticraftBlocks.PLASTIC_MOLDING_CHAMBER.get())
            && player.distanceToSqr(this.chamberPos.getCenter()) <= 64.0D;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < MACHINE_SLOT_COUNT) {
            if (!this.moveItemStackTo(stack, PLAYER_SLOT_START, PLAYER_SLOT_END, true)) return ItemStack.EMPTY;
        } else if (stack.is(Items.CLAY_BALL)) {
            if (!this.moveItemStackTo(stack, PlasticMoldingChamberBlockEntity.CLAY_SLOT, 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.getItem() instanceof DiskItem) {
            if (!this.moveItemStackTo(stack, PlasticMoldingChamberBlockEntity.DISK_SLOT, 2, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player instanceof ServerPlayer serverPlayer && this.chamber != null) {
            if (this.chamber.releaseSession(serverPlayer, this.sessionId)) {
                MoldingSessionStatusPacket.broadcast(serverPlayer.serverLevel(), this.chamber);
            }
        }
    }

    private void addSlots(Container machine, Inventory inventory) {
        this.addSlot(new FilteredSlot(
            machine,
            PlasticMoldingChamberBlockEntity.CLAY_SLOT,
            9,
            143,
            Filter.CLAY,
            this::clayLimit
        ));
        this.addSlot(new FilteredSlot(
            machine,
            PlasticMoldingChamberBlockEntity.DISK_SLOT,
            288,
            171,
            Filter.DISK,
            () -> 1
        ));
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(inventory, column + row * 9 + 9, 94 + column * 18, 144 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(inventory, column, 94 + column * 18, 202));
        }
    }

    private enum Filter {
        CLAY,
        DISK
    }

    private static final class FilteredSlot extends Slot {
        private final Filter filter;
        private final IntSupplier limit;

        private FilteredSlot(
            Container container,
            int slot,
            int x,
            int y,
            Filter filter,
            IntSupplier limit
        ) {
            super(container, slot, x, y);
            this.filter = filter;
            this.limit = limit;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return switch (this.filter) {
                case CLAY -> stack.is(Items.CLAY_BALL);
                case DISK -> stack.getItem() instanceof DiskItem;
            };
        }

        @Override
        public int getMaxStackSize() {
            return this.limit.getAsInt();
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return this.limit.getAsInt();
        }
    }
}
