package dev.anvilcraft.plasticraft.item;

import dev.anvilcraft.plasticraft.block.PlasticAnvilBlock;
import dev.anvilcraft.plasticraft.entity.PlasticAnvilEntity;
import dev.anvilcraft.plasticraft.entity.PlasticAnvilOrientation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

/** Item-specific color data layered on the shared plastic-anvil placement flow. */
public class PlasticAnvilItem extends AbstractPlasticAnvilItem<PlasticAnvilEntity> {
    public PlasticAnvilItem(
        Block block,
        Properties properties,
        Supplier<? extends EntityType<? extends PlasticAnvilEntity>> entityType,
        Supplier<BlockState> displayState
    ) {
        super(block, properties, entityType, displayState);
    }

    @Override
    protected BlockState prepareDisplayState(ItemStack stack, BlockState state) {
        return PlasticAnvilBlock.withColor(state, getColor(stack));
    }

    @Override
    protected PlasticAnvilEntity createEntity(
        EntityType<? extends PlasticAnvilEntity> entityType,
        Level level,
        Vec3 position,
        BlockState displayState,
        ItemStack dropStack,
        PlasticAnvilOrientation orientation
    ) {
        return new PlasticAnvilEntity(entityType, level, position, displayState, dropStack, orientation);
    }

    /** Reads the stable color component shared by future plastic variants. */
    public static DyeColor getColor(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains("PlasticColor")) {
            return DyeColor.WHITE;
        }
        return DyeColor.byName(data.copyTag().getString("PlasticColor"), DyeColor.WHITE);
    }

    public static void setColor(ItemStack stack, DyeColor color) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString("PlasticColor", color.getName()));
    }
}
