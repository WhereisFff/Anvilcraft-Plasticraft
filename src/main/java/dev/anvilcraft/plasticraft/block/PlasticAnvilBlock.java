package dev.anvilcraft.plasticraft.block;

import dev.anvilcraft.plasticraft.entity.PlasticAnvilEntity;
import dev.anvilcraft.plasticraft.entity.PlasticAnvilOrientation;
import dev.anvilcraft.plasticraft.init.PlasticEntities;
import dev.anvilcraft.plasticraft.item.PlasticAnvilItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Royal-anvil-shaped display block backing the movable plastic anvil entity.
 * The normal item path creates an entity instead of placing this block.
 */
public class PlasticAnvilBlock extends AbstractPlasticAnvilBlock<PlasticAnvilEntity> {
    public static final EnumProperty<DyeColor> COLOR = EnumProperty.create("color", DyeColor.class);

    public PlasticAnvilBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
            .setValue(FACING, Direction.NORTH)
            .setValue(COLOR, DyeColor.WHITE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(COLOR);
    }

    public static BlockState withColor(BlockState state, DyeColor color) {
        return state.hasProperty(COLOR) ? state.setValue(COLOR, color) : state;
    }

    /** A light tint keeps the white plastic texture readable for every future dye. */
    public static int tint(DyeColor color) {
        int rgb = color.getTextureDiffuseColor();
        int red = 192 + ((rgb >> 16) & 0xFF) / 4;
        int green = 192 + ((rgb >> 8) & 0xFF) / 4;
        int blue = 192 + (rgb & 0xFF) / 4;
        return (red << 16) | (green << 8) | blue;
    }

    public static int tint(BlockState state) {
        return tint(state.hasProperty(COLOR) ? state.getValue(COLOR) : DyeColor.WHITE);
    }

    @Override
    protected EntityType<? extends PlasticAnvilEntity> getPlasticAnvilEntityType() {
        return PlasticEntities.PLASTIC_ANVIL.get();
    }

    @Override
    protected ItemStack createDropStack(BlockState state) {
        ItemStack stack = new ItemStack(this);
        if (state.hasProperty(COLOR)) {
            PlasticAnvilItem.setColor(stack, state.getValue(COLOR));
        }
        return stack;
    }

    @Override
    protected PlasticAnvilEntity createPlasticAnvilEntity(
        EntityType<? extends PlasticAnvilEntity> entityType,
        ServerLevel level,
        Vec3 position,
        BlockState displayState,
        ItemStack dropStack,
        PlasticAnvilOrientation orientation
    ) {
        return new PlasticAnvilEntity(entityType, level, position, displayState, dropStack, orientation);
    }

    @Override
    public InteractionResult use(
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        InteractionHand hand,
        BlockHitResult hit
    ) {
        // A command-placed display block should not accidentally open the Royal Anvil menu.
        return InteractionResult.PASS;
    }
}
