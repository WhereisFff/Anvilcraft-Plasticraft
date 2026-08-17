package dev.anvilcraft.plasticraft.inventory;

import dev.anvilcraft.plasticraft.allay.AllayShortageStrategy;
import dev.anvilcraft.plasticraft.block.entity.AllayLoungeBlockEntity;
import dev.anvilcraft.plasticraft.blueprint.ConstructionBlueprintData;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJob;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobIndex;
import dev.anvilcraft.plasticraft.blueprint.ConstructionPermission;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/** 悦灵休息室菜单:磁盘槽、玩家背包;托管卡片由界面按方块实体同步数据绘制。 */
public class AllayLoungeMenu extends AbstractContainerMenu {
    public static final int DATA_HOSTED_COUNT = 0;
    public static final int DATA_STRATEGY = 1;
    public static final int DATA_COUNT = 2;
    public static final int DISK_SLOT_X = 134;
    public static final int DISK_SLOT_Y = 27;
    public static final int PLAYER_INVENTORY_Y = 104;
    public static final int PLAYER_HOTBAR_Y = 162;
    public static final int CARD_GRID_X = 44;
    public static final int CARD_GRID_Y = 18;

    private final Player player;
    @Nullable
    private final AllayLoungeBlockEntity lounge;
    private final BlockPos loungePos;
    private final ContainerData data;

    public AllayLoungeMenu(
        MenuType<?> type,
        int containerId,
        Inventory playerInventory,
        AllayLoungeBlockEntity lounge
    ) {
        this(
            type,
            containerId,
            playerInventory,
            lounge,
            lounge.getBlockPos(),
            lounge.items(),
            new ContainerData() {
                @Override
                public int get(int index) {
                    return switch (index) {
                        case DATA_HOSTED_COUNT -> lounge.hosted().size();
                        case DATA_STRATEGY -> lounge.shortageStrategy().ordinal();
                        default -> 0;
                    };
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
        lounge.addViewer(playerInventory.player);
    }

    public AllayLoungeMenu(
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
            new ItemStackHandler(1),
            new SimpleContainerData(DATA_COUNT)
        );
    }

    private AllayLoungeMenu(
        MenuType<?> type,
        int containerId,
        Inventory playerInventory,
        @Nullable AllayLoungeBlockEntity lounge,
        BlockPos loungePos,
        ItemStackHandler items,
        ContainerData data
    ) {
        super(type, containerId);
        this.player = playerInventory.player;
        this.lounge = lounge;
        this.loungePos = loungePos;
        this.data = data;
        this.addSlot(new DiskSlot(
            items,
            AllayLoungeBlockEntity.DISK_SLOT,
            DISK_SLOT_X,
            DISK_SLOT_Y,
            playerInventory.player
        ));
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

    public BlockPos loungePos() {
        return this.loungePos;
    }

    public int hostedCount() {
        return this.data.get(DATA_HOSTED_COUNT);
    }

    public AllayShortageStrategy shortageStrategy() {
        int id = this.data.get(DATA_STRATEGY);
        AllayShortageStrategy[] values = AllayShortageStrategy.values();
        return id >= 0 && id < values.length ? values[id] : AllayShortageStrategy.PAUSE;
    }

    @Nullable
    public AllayLoungeBlockEntity lounge() {
        if (this.lounge != null) return this.lounge;
        if (this.player.level() != null
            && this.player.level().getBlockEntity(this.loungePos) instanceof AllayLoungeBlockEntity found) {
            return found;
        }
        return null;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        AllayLoungeBlockEntity lounge = this.lounge();
        if (lounge == null || !(player instanceof ServerPlayer serverPlayer)
            || !ConstructionPermission.canUseLounge(serverPlayer, lounge)) {
            return ItemStack.EMPTY;
        }
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index == 0) {
            if (!canManageDisk(serverPlayer, stack)) return ItemStack.EMPTY;
            if (!this.moveItemStackTo(stack, 1, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (AllayLoungeBlockEntity.isValidDisk(stack) && canManageDisk(serverPlayer, stack)) {
            if (!this.moveItemStackTo(stack, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        AllayLoungeBlockEntity lounge = this.lounge();
        if (player instanceof ServerPlayer serverPlayer) {
            if (lounge == null || !ConstructionPermission.canUseLounge(serverPlayer, lounge)) return;
            if (slotId == 0
                && (!canManageDisk(serverPlayer, this.slots.get(0).getItem())
                    || !canManageDisk(serverPlayer, this.getCarried()))) {
                return;
            }
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public boolean stillValid(Player player) {
        AllayLoungeBlockEntity lounge = this.lounge();
        if (lounge == null) return false;
        if (player instanceof ServerPlayer serverPlayer
            && !ConstructionPermission.canUseLounge(serverPlayer, lounge)) {
            return false;
        }
        return player.distanceToSqr(
            this.loungePos.getX() + 0.5D,
            this.loungePos.getY() + 0.5D,
            this.loungePos.getZ() + 0.5D
        ) <= 64.0D;
    }

    private static boolean canManageDisk(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return true;
        ConstructionJob job = ConstructionBlueprintData.get(stack)
            .flatMap(ConstructionBlueprintData::jobId)
            .map(id -> ConstructionJobIndex.get(player.server).job(id))
            .orElse(null);
        return job == null || ConstructionPermission.canManageJob(player, job);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (this.lounge != null) {
            this.lounge.removeViewer(player);
        }
    }

    private static class DiskSlot extends SlotItemHandler {
        private final Player player;

        DiskSlot(ItemStackHandler handler, int index, int x, int y, Player player) {
            super(handler, index, x, y);
            this.player = player;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return AllayLoungeBlockEntity.isValidDisk(stack)
                && (!(this.player instanceof ServerPlayer serverPlayer) || canManageDisk(serverPlayer, stack));
        }
    }
}
