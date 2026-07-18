package dev.anvilcraft.plasticraft.entity;

import dev.anvilcraft.plasticraft.block.PlasticAnvilBlock;
import dev.anvilcraft.plasticraft.init.PlasticBlocks;
import dev.anvilcraft.plasticraft.inventory.PlasticAnvilMenu;
import dev.anvilcraft.plasticraft.item.PlasticAnvilItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.function.Supplier;

/** The first concrete plastic-anvil type; shared movement lives in the abstract base. */
public class PlasticAnvilEntity extends AbstractPlasticAnvilEntity {
    private static Supplier<ItemStack> defaultDropSupplier = () -> ItemStack.EMPTY;

    /** Configure the item returned when an old/save-loaded entity has no explicit stack. */
    public static void configureDefaultDrop(Supplier<ItemStack> supplier) {
        defaultDropSupplier = Objects.requireNonNull(supplier, "supplier");
    }

    public PlasticAnvilEntity(EntityType<? extends PlasticAnvilEntity> entityType, Level level) {
        super(entityType, level);
        this.setDisplayState(PlasticBlocks.PLASTIC_ANVIL.get().defaultBlockState());
    }

    public PlasticAnvilEntity(
        EntityType<? extends PlasticAnvilEntity> entityType,
        Level level,
        Vec3 position,
        BlockState displayState,
        ItemStack dropStack,
        PlasticAnvilOrientation orientation
    ) {
        super(entityType, level, position, displayState, dropStack, orientation);
    }

    @Override
    protected ItemStack createDefaultDropStack() {
        ItemStack fallback = defaultDropSupplier.get();
        if (fallback == null || fallback.isEmpty()) {
            return ItemStack.EMPTY;
        }
        fallback = fallback.copy();
        BlockState state = this.getDisplayState();
        if (state.hasProperty(PlasticAnvilBlock.COLOR)) {
            DyeColor color = state.getValue(PlasticAnvilBlock.COLOR);
            if (color != DyeColor.WHITE) {
                PlasticAnvilItem.setColor(fallback, color);
            }
        }
        return fallback;
    }

    @Override
    protected void openAnvilMenu(ServerPlayer player) {
        PlasticAnvilMenu.open(player, this);
    }
}
