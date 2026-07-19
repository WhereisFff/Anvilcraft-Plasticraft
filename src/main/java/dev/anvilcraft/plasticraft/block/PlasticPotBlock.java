package dev.anvilcraft.plasticraft.block;

import dev.anvilcraft.plasticraft.entity.PlasticAnvilOrientation;
import dev.anvilcraft.plasticraft.entity.PlasticPotEntity;
import dev.anvilcraft.plasticraft.init.PlasticEntities;
import dev.anvilcraft.plasticraft.item.PlasticItemData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Compatibility display block for the six-direction plastic pot entity. */
public class PlasticPotBlock extends AbstractPlasticAnvilBlock<PlasticPotEntity> {
    public static final EnumProperty<DyeColor> COLOR = EnumProperty.create("color", DyeColor.class);

    public PlasticPotBlock(Properties properties) {
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

    public static int tint(BlockState state) {
        DyeColor color = state.hasProperty(COLOR) ? state.getValue(COLOR) : DyeColor.WHITE;
        return PlasticAnvilBlock.tint(color);
    }

    public static int tint(DyeColor color) {
        return PlasticAnvilBlock.tint(color);
    }

    @Override
    protected EntityType<? extends PlasticPotEntity> getPlasticAnvilEntityType() {
        return PlasticEntities.PLASTIC_POT.get();
    }

    @Override
    protected ItemStack createDropStack(BlockState state) {
        ItemStack stack = new ItemStack(this);
        if (state.hasProperty(COLOR)) PlasticItemData.setColor(stack, state.getValue(COLOR));
        return stack;
    }

    @Override
    protected PlasticPotEntity createPlasticAnvilEntity(
        EntityType<? extends PlasticPotEntity> entityType,
        ServerLevel level,
        Vec3 position,
        BlockState displayState,
        ItemStack dropStack,
        PlasticAnvilOrientation orientation
    ) {
        return new PlasticPotEntity(entityType, level, position, displayState, dropStack, orientation);
    }

    @Override
    public VoxelShape getShape(
        BlockState state,
        BlockGetter level,
        BlockPos pos,
        CollisionContext context
    ) {
        return Blocks.CAULDRON.defaultBlockState().getShape(level, pos, context);
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
        return InteractionResult.PASS;
    }
}
