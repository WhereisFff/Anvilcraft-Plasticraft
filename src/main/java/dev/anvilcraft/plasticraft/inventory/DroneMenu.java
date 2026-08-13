package dev.anvilcraft.plasticraft.inventory;

import dev.anvilcraft.plasticraft.drone.DroneData;
import dev.anvilcraft.plasticraft.drone.DroneFlightState;
import dev.anvilcraft.plasticraft.drone.DroneShortageStrategy;
import dev.anvilcraft.plasticraft.drone.tool.DroneToolDefinition;
import dev.anvilcraft.plasticraft.drone.tool.DroneToolDefinitions;
import dev.anvilcraft.plasticraft.entity.drone.DroneEntity;
import dev.anvilcraft.plasticraft.init.PlasticraftMenuTypes;
import dev.anvilcraft.plasticraft.item.DroneItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 无人机单机设置菜单。实体右击与物品潜行右击打开同一界面:
 * 目标是实体时直接读写实体,目标是手持物品时读写物品数据组件,
 * 设置随铁砧锤回收在两种形态之间保持。
 */
public class DroneMenu extends AbstractContainerMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_FLIGHT_STATE = 1;
    public static final int DATA_STRATEGY = 2;
    public static final int DATA_COUNT = 3;
    /** 收集库存展示区的九格槽位布局左上角。 */
    public static final int COLLECTION_SLOT_X = 106;
    public static final int COLLECTION_SLOT_Y = 78;

    private final Player player;
    @Nullable
    private final DroneEntity drone;
    @Nullable
    private final InteractionHand hand;
    private final ResourceLocation toolId;
    private final String ownerName;
    private final ContainerData data;

    private DroneMenu(
        MenuType<?> type,
        int containerId,
        Inventory playerInventory,
        @Nullable DroneEntity drone,
        @Nullable InteractionHand hand,
        ResourceLocation toolId,
        String ownerName,
        List<ItemStack> collectionInventory,
        ContainerData data
    ) {
        super(type, containerId);
        this.player = playerInventory.player;
        this.drone = drone;
        this.hand = hand;
        this.toolId = toolId;
        this.ownerName = ownerName;
        this.data = data;
        if (this.toolDefinition().inventorySize() > 0) {
            this.addCollectionSlots(collectionInventory);
        }
        this.addDataSlots(data);
    }

    /** 服务端实体目标。 */
    public DroneMenu(MenuType<?> type, int containerId, Inventory playerInventory, DroneEntity drone) {
        this(
            type,
            containerId,
            playerInventory,
            drone,
            null,
            drone.toolId(),
            resolveOwnerName(playerInventory.player, drone.getOwner()),
            drone.toDroneData().collectionInventory(),
            new ContainerData() {
                @Override
                public int get(int index) {
                    return switch (index) {
                        case DATA_ENERGY -> drone.getEnergy();
                        case DATA_FLIGHT_STATE -> drone.flightState().ordinal();
                        case DATA_STRATEGY -> drone.shortageStrategy().ordinal();
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
    }

    /** 服务端手持物品目标。 */
    public DroneMenu(MenuType<?> type, int containerId, Inventory playerInventory, InteractionHand hand) {
        this(
            type,
            containerId,
            playerInventory,
            null,
            hand,
            itemData(playerInventory.player, hand).toolId(),
            resolveOwnerName(playerInventory.player, itemData(playerInventory.player, hand).owner()),
            itemData(playerInventory.player, hand).collectionInventory(),
            new ContainerData() {
                @Override
                public int get(int index) {
                    DroneData data = itemData(playerInventory.player, hand);
                    return switch (index) {
                        case DATA_ENERGY -> data.energy();
                        case DATA_FLIGHT_STATE -> DroneFlightState.LANDED.ordinal();
                        case DATA_STRATEGY -> data.shortageStrategy().ordinal();
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
    }

    /** 客户端构造:静态信息经打开缓冲传输,动态值走容器数据同步。 */
    public DroneMenu(MenuType<?> type, int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(
            type,
            containerId,
            playerInventory,
            null,
            buffer.readBoolean() ? InteractionHand.values()[buffer.readVarInt()] : null,
            ResourceLocation.STREAM_CODEC.decode(buffer),
            buffer.readUtf(64),
            ItemStack.OPTIONAL_LIST_STREAM_CODEC.decode(buffer),
            new SimpleContainerData(DATA_COUNT)
        );
    }

    public static void openForEntity(ServerPlayer player, DroneEntity drone) {
        DroneData data = drone.toDroneData();
        player.openMenu(
            new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new DroneMenu(
                    PlasticraftMenuTypes.DRONE.get(),
                    containerId,
                    inventory,
                    drone
                ),
                drone.getDisplayName()
            ),
            buffer -> writeOpenData(
                buffer,
                null,
                drone.toolId(),
                resolveOwnerName(player, drone.getOwner()),
                data.collectionInventory()
            )
        );
    }

    public static void openForItem(ServerPlayer player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        DroneData data = itemData(player, hand);
        player.openMenu(
            new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new DroneMenu(
                    PlasticraftMenuTypes.DRONE.get(),
                    containerId,
                    inventory,
                    hand
                ),
                stack.getHoverName()
            ),
            buffer -> writeOpenData(
                buffer,
                hand,
                data.toolId(),
                resolveOwnerName(player, data.owner()),
                data.collectionInventory()
            )
        );
    }

    private static void writeOpenData(
        RegistryFriendlyByteBuf buffer,
        @Nullable InteractionHand hand,
        ResourceLocation toolId,
        String ownerName,
        List<ItemStack> collectionInventory
    ) {
        buffer.writeBoolean(hand != null);
        if (hand != null) buffer.writeVarInt(hand.ordinal());
        ResourceLocation.STREAM_CODEC.encode(buffer, toolId);
        buffer.writeUtf(ownerName, 64);
        ItemStack.OPTIONAL_LIST_STREAM_CODEC.encode(buffer, collectionInventory);
    }

    private static DroneData itemData(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() instanceof DroneItem droneItem) {
            return DroneData.get(stack)
                .orElseGet(() -> DroneData.assembled(droneItem.definition().id(), ItemStack.EMPTY, ItemStack.EMPTY));
        }
        return DroneData.assembled(DroneToolDefinitions.NONE.id(), ItemStack.EMPTY, ItemStack.EMPTY);
    }

    private static String resolveOwnerName(Player viewer, Optional<UUID> owner) {
        if (owner.isEmpty()) return "";
        UUID ownerId = owner.get();
        if (ownerId.equals(viewer.getUUID())) return viewer.getGameProfile().getName();
        if (viewer.getServer() != null) {
            return viewer.getServer().getProfileCache() == null
                ? ownerId.toString().substring(0, 8)
                : viewer.getServer().getProfileCache().get(ownerId)
                    .map(profile -> profile.getName())
                    .orElse(ownerId.toString().substring(0, 8));
        }
        return ownerId.toString().substring(0, 8);
    }

    /** 收集无人机的九格库存只读展示;取出与装载语义由收集任务 TODO 决定。 */
    private void addCollectionSlots(List<ItemStack> items) {
        SimpleContainer view = new SimpleContainer(this.toolDefinition().inventorySize());
        for (int index = 0; index < items.size() && index < view.getContainerSize(); index++) {
            view.setItem(index, items.get(index).copy());
        }
        for (int index = 0; index < view.getContainerSize(); index++) {
            int column = index % 3;
            int row = index / 3;
            this.addSlot(new Slot(view, index, COLLECTION_SLOT_X + column * 18, COLLECTION_SLOT_Y + row * 18) {
                @Override
                public boolean mayPickup(Player player) {
                    return false;
                }

                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }
    }

    /** 缺料与缺拆除能力策略切换;实体与物品两种目标写入同一份数据。 */
    public void applyStrategy(DroneShortageStrategy strategy) {
        if (this.drone != null) {
            this.drone.setShortageStrategy(strategy);
            return;
        }
        if (this.hand == null) return;
        ItemStack stack = this.player.getItemInHand(this.hand);
        if (!(stack.getItem() instanceof DroneItem droneItem)) return;
        DroneData data = DroneData.get(stack)
            .orElseGet(() -> DroneData.assembled(droneItem.definition().id(), ItemStack.EMPTY, ItemStack.EMPTY));
        DroneData.set(stack, new DroneData(
            data.toolId(),
            data.leftPropeller(),
            data.rightPropeller(),
            data.energy(),
            data.owner(),
            strategy,
            data.collectionInventory()
        ));
    }

    public ResourceLocation toolId() {
        return this.toolId;
    }

    public DroneToolDefinition toolDefinition() {
        return DroneToolDefinitions.getOrFallback(this.toolId);
    }

    public String ownerName() {
        return this.ownerName;
    }

    public int energy() {
        return this.data.get(DATA_ENERGY);
    }

    public DroneFlightState flightState() {
        return DroneFlightState.byId(this.data.get(DATA_FLIGHT_STATE));
    }

    public DroneShortageStrategy strategy() {
        int ordinal = this.data.get(DATA_STRATEGY);
        DroneShortageStrategy[] values = DroneShortageStrategy.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : DroneShortageStrategy.PAUSE;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.drone != null) {
            return this.drone.isAlive() && this.drone.distanceToSqr(player) <= 64.0D;
        }
        if (this.hand != null) {
            return player.getItemInHand(this.hand).getItem() instanceof DroneItem;
        }
        return true;
    }
}
