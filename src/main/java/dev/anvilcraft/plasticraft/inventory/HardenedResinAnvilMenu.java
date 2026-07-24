package dev.anvilcraft.plasticraft.inventory;

import dev.anvilcraft.plasticraft.entity.HardenedResinAnvilEntity;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.item.ResinAnvilHammerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.function.Supplier;

/** 仅供硬化树脂使用的实体化原版铁砧菜单。 */
public class HardenedResinAnvilMenu extends AnvilMenu {
    private static final Component TITLE = Component.translatable("container.repair");
    private static Supplier<? extends MenuType<?>> menuTypeSupplier = () -> MenuType.ANVIL;

    private final MenuType<?> menuType;
    private final int entityId;
    private final BlockPos bondedBlockPos;
    private String requestedItemName;
    private boolean calculatingVanillaResult;
    private boolean freeRename;
    private boolean freeResinHammerRepair;

    private record Target(int entityId, BlockPos bondedBlockPos) {
    }

    public static void configureMenuType(Supplier<? extends MenuType<?>> supplier) {
        menuTypeSupplier = Objects.requireNonNull(supplier, "supplier");
    }

    public static MenuType<?> menuType() {
        MenuType<?> type = menuTypeSupplier.get();
        return type == null ? MenuType.ANVIL : type;
    }

    public HardenedResinAnvilMenu(MenuType<?> menuType, int containerId, Inventory playerInventory, int entityId) {
        this(menuType, containerId, playerInventory, new Target(entityId, null));
    }

    public HardenedResinAnvilMenu(
        MenuType<?> menuType,
        int containerId,
        Inventory playerInventory,
        BlockPos bondedBlockPos
    ) {
        this(menuType, containerId, playerInventory, new Target(-1, bondedBlockPos.immutable()));
    }

    private HardenedResinAnvilMenu(
        MenuType<?> menuType,
        int containerId,
        Inventory playerInventory,
        Target target
    ) {
        // 实体砧没有永久方块位置，固定砧则由方块实体负责有效性检查。
        super(containerId, playerInventory, ContainerLevelAccess.NULL);
        this.menuType = menuType == null ? menuType() : menuType;
        this.entityId = target.entityId();
        this.bondedBlockPos = target.bondedBlockPos();
    }

    public HardenedResinAnvilMenu(
        MenuType<?> menuType,
        int containerId,
        Inventory playerInventory,
        RegistryFriendlyByteBuf extraData
    ) {
        this(menuType, containerId, playerInventory, readTarget(extraData));
    }

    private static Target readTarget(RegistryFriendlyByteBuf buffer) {
        return buffer.readBoolean()
            ? new Target(-1, buffer.readBlockPos())
            : new Target(buffer.readVarInt(), null);
    }

    public HardenedResinAnvilMenu(int containerId, Inventory playerInventory, int entityId) {
        this(menuType(), containerId, playerInventory, entityId);
    }

    public static void open(ServerPlayer player, HardenedResinAnvilEntity entity) {
        MenuType<?> type = menuType();
        MenuProvider provider = new net.minecraft.world.SimpleMenuProvider(
            (containerId, inventory, ignored) ->
                new HardenedResinAnvilMenu(type, containerId, inventory, entity.getId()),
            TITLE
        );
        player.openMenu(provider, buffer -> {
            buffer.writeBoolean(false);
            buffer.writeVarInt(entity.getId());
        });
    }

    public static void open(ServerPlayer player, BondedEntityBlockEntity bonded) {
        BlockPos pos = bonded.getBlockPos();
        MenuType<?> type = menuType();
        MenuProvider provider = new net.minecraft.world.SimpleMenuProvider(
            (containerId, inventory, ignored) ->
                new HardenedResinAnvilMenu(type, containerId, inventory, pos),
            TITLE
        );
        player.openMenu(provider, buffer -> {
            buffer.writeBoolean(true);
            buffer.writeBlockPos(pos);
        });
    }

    public int entityId() {
        return this.entityId;
    }

    public boolean onlyRenaming() {
        return this.freeRename;
    }

    @Override
    public MenuType<?> getType() {
        return this.menuType;
    }

    @Override
    protected boolean mayPickup(Player player, boolean hasStack) {
        return super.mayPickup(player, hasStack)
            || hasStack && (this.freeRename || this.freeResinHammerRepair);
    }

    @Override
    public void createResult() {
        if (this.calculatingVanillaResult) {
            super.createResult();
            return;
        }

        this.freeResinHammerRepair = false;

        ItemStack inputLeft = this.getSlot(0).getItem();
        ItemStack inputRight = this.getSlot(1).getItem();
        boolean freeResinHammerRepair = inputLeft.getItem() instanceof ResinAnvilHammerItem
            && inputLeft.isDamaged()
            && inputRight.is(dev.dubhe.anvilcraft.init.item.ModItems.RESIN.get());
        Integer previousRepairCost = inputLeft.get(DataComponents.REPAIR_COST);

        // 计算时临时忽略既有惩罚，避免高 RepairCost 让免费树脂修复显示“过于昂贵”。
        if (freeResinHammerRepair && previousRepairCost != null) {
            inputLeft.remove(DataComponents.REPAIR_COST);
        }
        try {
            this.createResultInternal(inputLeft, inputRight);
        } finally {
            if (freeResinHammerRepair && previousRepairCost != null) {
                inputLeft.set(DataComponents.REPAIR_COST, previousRepairCost);
            }
        }

        if (!freeResinHammerRepair) return;
        ItemStack output = this.getSlot(2).getItem();
        if (output.isEmpty() || output.getDamageValue() >= inputLeft.getDamageValue()) return;

        if (previousRepairCost == null) {
            output.remove(DataComponents.REPAIR_COST);
        } else {
            output.set(DataComponents.REPAIR_COST, previousRepairCost);
        }
        this.freeResinHammerRepair = true;
        this.setMaximumCost(0L);
        this.broadcastChanges();
    }

    private void createResultInternal(ItemStack inputLeft, ItemStack inputRight) {
        this.freeRename = false;

        // 让原版使用输入物品的当前名称计算操作。
        // 上方已防护 setItemName 的虚方法回调，因而这里只进行一次原版计算，
        // 不会递归计算重命名。
        String unchangedName = inputLeft.isEmpty() ? "" : inputLeft.getHoverName().getString();
        this.calculatingVanillaResult = true;
        try {
            if (!super.setItemName(unchangedName)) {
                super.createResult();
            }
        } finally {
            this.calculatingVanillaResult = false;
        }

        if (!this.changesName(inputLeft)) return;

        ItemStack output = this.getSlot(2).getItem();
        if (output.isEmpty()) {
            // 没有第二项输入时，原版不会产出操作结果。
            // 因此副本就是完整的纯重命名结果，并保留 RepairCost。
            if (!inputRight.isEmpty() || inputLeft.isEmpty()) return;
            output = inputLeft.copy();
            this.resultSlots.setItem(0, output);
            this.freeRename = true;
            // 原版的临时计算会包含先前工作惩罚。
            // 纯重命名明确免费，此规则也适用于曾经修复过的物品。
            this.setMaximumCost(0L);
        }

        if (StringUtil.isBlank(this.requestedItemName)) {
            output.remove(DataComponents.CUSTOM_NAME);
        } else {
            output.set(DataComponents.CUSTOM_NAME, Component.literal(this.requestedItemName));
        }
        this.broadcastChanges();
    }

    @Override
    public boolean setItemName(String value) {
        String filtered = StringUtil.filterText(value);
        if (filtered.length() > MAX_NAME_LENGTH || filtered.equals(this.requestedItemName)) return false;
        this.requestedItemName = filtered;
        this.createResult();
        return true;
    }

    private boolean changesName(ItemStack input) {
        if (input.isEmpty() || this.requestedItemName == null) return false;
        if (StringUtil.isBlank(this.requestedItemName)) {
            return input.has(DataComponents.CUSTOM_NAME);
        }
        return !this.requestedItemName.equals(input.getHoverName().getString());
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.bondedBlockPos != null) {
            return player.level().getBlockEntity(this.bondedBlockPos) instanceof BondedEntityBlockEntity bonded
                && bonded.isInitialized()
                && bonded.isPlastic()
                && bonded.isHardenedResinAnvil()
                && player.distanceToSqr(this.bondedBlockPos.getCenter()) <= 64.0D;
        }
        Entity entity = player.level().getEntity(this.entityId);
        return entity instanceof HardenedResinAnvilEntity anvil
            && anvil.isAlive()
            && player.distanceToSqr(anvil) <= 64.0D;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.clearContainer(player, this.inputSlots);
    }
}
