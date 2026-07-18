package dev.anvilcraft.plasticraft.inventory;

import dev.anvilcraft.plasticraft.entity.PlasticAnvilEntity;
import dev.dubhe.anvilcraft.inventory.RoyalAnvilMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * The normal AnvilCraft anvil workflow backed by a movable entity.
 *
 * <p>The vanilla item-combiner menu validates a block position.  A plastic
 * anvil has no permanent block position, so this menu carries the entity id
 * and validates the tracked entity instead.</p>
 */
public class PlasticAnvilMenu extends RoyalAnvilMenu {
    private static final Component TITLE = Component.translatable("container.repair");
    private static Supplier<? extends MenuType<?>> menuTypeSupplier = () -> MenuType.ANVIL;

    private final MenuType<?> menuType;
    private final int entityId;

    /**
     * Lets the registry bootstrap provide the addon menu type without making
     * this implementation depend on a particular registration framework.
     */
    public static void configureMenuType(Supplier<? extends MenuType<?>> supplier) {
        menuTypeSupplier = Objects.requireNonNull(supplier, "supplier");
    }

    public static MenuType<?> menuType() {
        MenuType<?> type = menuTypeSupplier.get();
        return type == null ? MenuType.ANVIL : type;
    }

    public PlasticAnvilMenu(MenuType<?> menuType, int containerId, Inventory playerInventory, int entityId) {
        // NULL is intentional: there is no block for AnvilMenu to damage.
        super(containerId, playerInventory, ContainerLevelAccess.NULL);
        this.menuType = menuType == null ? menuType() : menuType;
        this.entityId = entityId;
    }

    /** Client-side factory. The server writes the entity id as the only extra field. */
    public PlasticAnvilMenu(
        MenuType<?> menuType,
        int containerId,
        Inventory playerInventory,
        RegistryFriendlyByteBuf extraData
    ) {
        this(menuType, containerId, playerInventory, extraData.readVarInt());
    }

    /** Convenience constructor for a server-side provider. */
    public PlasticAnvilMenu(int containerId, Inventory playerInventory, int entityId) {
        this(menuType(), containerId, playerInventory, entityId);
    }

    /**
     * Opens this menu and sends the entity id to the client menu factory.
     * The registration layer only needs to configure the menu type once.
     */
    public static void open(ServerPlayer player, PlasticAnvilEntity entity) {
        MenuType<?> type = menuType();
        MenuProvider provider = new net.minecraft.world.SimpleMenuProvider(
            (containerId, inventory, ignored) ->
                new PlasticAnvilMenu(type, containerId, inventory, entity.getId()),
            TITLE
        );
        player.openMenu(provider, buffer -> buffer.writeVarInt(entity.getId()));
    }

    public int entityId() {
        return this.entityId;
    }

    @Override
    public MenuType<?> getType() {
        return this.menuType;
    }

    @Override
    public boolean stillValid(Player player) {
        Entity entity = player.level().getEntity(this.entityId);
        return entity instanceof PlasticAnvilEntity plastic
            && plastic.isAlive()
            && this.entityId >= 0
            && player.distanceToSqr(plastic) <= 64.0D;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        // RoyalAnvilMenu delegates to ContainerLevelAccess; NULL does not
        // clear the input slots, so do it explicitly for an entity-backed menu.
        this.clearContainer(player, this.inputSlots);
    }
}
