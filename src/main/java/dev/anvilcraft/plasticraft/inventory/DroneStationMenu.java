package dev.anvilcraft.plasticraft.inventory;

import dev.anvilcraft.plasticraft.block.entity.DroneStationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/** 无人机站菜单:16 个无人机槽、1 个结构磁盘槽、1 个电容器充能槽与玩家背包。 */
public class DroneStationMenu extends AbstractContainerMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_COUNT = 1;
    public static final int STATION_SLOT_COUNT = DroneStationBlockEntity.SLOT_COUNT;
    public static final int DRONE_GRID_X = 44;
    public static final int DRONE_GRID_Y = 18;
    public static final int DISK_SLOT_X = 134;
    public static final int DISK_SLOT_Y = 27;
    public static final int CAPACITOR_SLOT_X = 134;
    public static final int CAPACITOR_SLOT_Y = 63;
    public static final int PLAYER_INVENTORY_Y = 104;
    public static final int PLAYER_HOTBAR_Y = 162;

    private final Player player;
    @Nullable
    private final DroneStationBlockEntity station;
    private final BlockPos stationPos;
    private final ContainerData data;

    /** 服务端构造。 */
    public DroneStationMenu(
        MenuType<?> type,
        int containerId,
        Inventory playerInventory,
        DroneStationBlockEntity station
    ) {
        this(
            type,
            containerId,
            playerInventory,
            station,
            station.getBlockPos(),
            station.items(),
            new ContainerData() {
                @Override
                public int get(int index) {
                    return index == DATA_ENERGY ? station.energy() : 0;
                }

                @Override
                public void set(int index, int value) {
                }

                @Override
                public int getCount() {
                    return DATA_COUNT;
                }
            }
        );
        station.addViewer(playerInventory.player);
    }

    /** 客户端构造:槽位内容与能量经原版菜单同步机制填充。 */
    public DroneStationMenu(
        MenuType<?> type,
        int containerId,
        Inventory playerInventory,
        RegistryFriendlyByteBuf buffer
    ) {
        this(
            type,
            containerId,
            playerInventory,
            null,
            buffer.readBlockPos(),
            new ItemStackHandler(STATION_SLOT_COUNT),
            new SimpleContainerData(DATA_COUNT)
        );
    }

    private DroneStationMenu(
        MenuType<?> type,
        int containerId,
        Inventory playerInventory,
        @Nullable DroneStationBlockEntity station,
        BlockPos stationPos,
        ItemStackHandler items,
        ContainerData data
    ) {
        super(type, containerId);
        this.player = playerInventory.player;
        this.station = station;
        this.stationPos = stationPos;
        this.data = data;
        for (int slot = 0; slot < DroneStationBlockEntity.DRONE_SLOT_COUNT; slot++) {
            int column = slot % 4;
            int row = slot / 4;
            this.addSlot(new StationSlot(items, slot, DRONE_GRID_X + column * 18, DRONE_GRID_Y + row * 18));
        }
        this.addSlot(new StationSlot(items, DroneStationBlockEntity.DISK_SLOT, DISK_SLOT_X, DISK_SLOT_Y));
        this.addSlot(new StationSlot(items, DroneStationBlockEntity.CAPACITOR_SLOT, CAPACITOR_SLOT_X, CAPACITOR_SLOT_Y));
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(
                    playerInventory,
                    9 + row * 9 + column,
                    8 + column * 18,
                    PLAYER_INVENTORY_Y + row * 18
                ));
            }
        }
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(playerInventory, column, 8 + column * 18, PLAYER_HOTBAR_Y));
        }
        this.addDataSlots(data);
    }

    public BlockPos stationPos() {
        return this.stationPos;
    }

    public int energy() {
        return this.data.get(DATA_ENERGY);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < STATION_SLOT_COUNT) {
            if (!this.moveItemStackTo(stack, STATION_SLOT_COUNT, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean moved = false;
            if (DroneStationBlockEntity.isValidForSlot(0, stack)) {
                moved = this.moveItemStackTo(stack, 0, DroneStationBlockEntity.DRONE_SLOT_COUNT, false);
            } else if (DroneStationBlockEntity.isValidForSlot(DroneStationBlockEntity.DISK_SLOT, stack)) {
                moved = this.moveItemStackTo(
                    stack,
                    DroneStationBlockEntity.DISK_SLOT,
                    DroneStationBlockEntity.DISK_SLOT + 1,
                    false
                );
            } else if (DroneStationBlockEntity.isValidForSlot(DroneStationBlockEntity.CAPACITOR_SLOT, stack)) {
                moved = this.moveItemStackTo(
                    stack,
                    DroneStationBlockEntity.CAPACITOR_SLOT,
                    DroneStationBlockEntity.CAPACITOR_SLOT + 1,
                    false
                );
            }
            if (!moved) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(
            this.stationPos.getX() + 0.5D,
            this.stationPos.getY() + 0.5D,
            this.stationPos.getZ() + 0.5D
        ) <= 64.0D;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (this.station != null) {
            this.station.removeViewer(player);
        }
    }

    /** 站内槽位按槽位语义过滤:无人机、结构磁盘或已充电电容器。 */
    private static class StationSlot extends SlotItemHandler {
        StationSlot(ItemStackHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return DroneStationBlockEntity.isValidForSlot(this.getSlotIndex(), stack);
        }
    }
}
