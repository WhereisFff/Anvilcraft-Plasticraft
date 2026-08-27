package dev.anvilcraft.plasticraft.block;

import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.entity.HardenedResinCauldronEntity;
import dev.anvilcraft.plasticraft.entity.collision.BuiltInPlasticEntityModels;
import dev.anvilcraft.plasticraft.init.entity.PlasticraftEntities;
import dev.anvilcraft.plasticraft.item.PlasticItemData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 六向硬化树脂釜使用的兼容展示方块。 */
public class HardenedResinCauldronBlock extends AbstractPlasticEntityBlock<HardenedResinCauldronEntity> {
    public static final VoxelShape COLLISION_SHAPE = BuiltInPlasticEntityModels
        .HARDENED_RESIN_CAULDRON_COMPATIBILITY;
    /**
     * The orientation is stored in the bonded block entity rather than the
     * block state, so all state shapes must be evaluated against the live level.
     * dynamicShape() disables BlockStateBase's empty-level shape cache.
     */
    private static final VoxelShape UPWARD_ANVIL_SUPPORT = Shapes.box(
        0.0D, 15.0D / 16.0D, 0.0D, 1.0D, 1.0D, 1.0D
    );
    private static final Map<PlasticEntityOrientation, VoxelShape> BONDED_CAULDRON_SHAPES =
        new ConcurrentHashMap<>();
    private static final Map<PlasticEntityOrientation, VoxelShape> BONDED_SUPPORT_SHAPES =
        new ConcurrentHashMap<>();

    public HardenedResinCauldronBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
            .setValue(FACING, Direction.NORTH)
            .setValue(MAGNETIZED, false)
            .setValue(BONDED, false));
    }

    @Override
    protected EntityType<? extends HardenedResinCauldronEntity> getPlasticEntityType() {
        return PlasticraftEntities.HARDEND_RESIN_CAULDRON.get();
    }

    @Override
    protected ItemStack createDropStack(BlockState state) {
        ItemStack stack = new ItemStack(this);
        PlasticItemData.setMaterial(stack, "hardened_resin");
        if (state.hasProperty(MAGNETIZED) && state.getValue(MAGNETIZED)) {
            PlasticItemData.setMagnetized(stack, true);
        }
        return stack;
    }

    @Override
    protected HardenedResinCauldronEntity createPlasticEntity(
        EntityType<? extends HardenedResinCauldronEntity> entityType,
        ServerLevel level,
        Vec3 position,
        BlockState displayState,
        ItemStack dropStack,
        PlasticEntityOrientation orientation
    ) {
        return new HardenedResinCauldronEntity(entityType, level, position, displayState, dropStack, orientation);
    }

    @Override
    public VoxelShape getShape(
        BlockState state,
        BlockGetter level,
        BlockPos pos,
        CollisionContext context
    ) {
        if (state.getValue(BONDED)) return this.bondedCauldronShape(level, pos);
        return COLLISION_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(
        BlockState state,
        BlockGetter level,
        BlockPos pos,
        CollisionContext context
    ) {
        if (state.getValue(BONDED)) return this.bondedCauldronShape(level, pos);
        return COLLISION_SHAPE;
    }

    @Override
    protected VoxelShape getBlockSupportShape(
        BlockState state,
        BlockGetter level,
        BlockPos pos
    ) {
        if (state.getValue(BONDED)
            && level.getBlockEntity(pos) instanceof BondedEntityBlockEntity bonded
            && bonded.isInitialized()
            && bonded.isPlastic()) {
            // The opening is the landing face; keep the rest of the support
            // shape empty so side-attached pots do not become solid blocks.
            PlasticEntityOrientation orientation = bonded.getPlasticOrientation();
            return BONDED_SUPPORT_SHAPES.computeIfAbsent(
                orientation,
                ignored -> AbstractPlasticEntityBlock.rotateShape(UPWARD_ANVIL_SUPPORT, orientation)
            );
        }
        return super.getBlockSupportShape(state, level, pos);
    }

    private VoxelShape bondedCauldronShape(BlockGetter level, BlockPos pos) {
        PlasticEntityOrientation orientation = level.getBlockEntity(pos) instanceof BondedEntityBlockEntity bonded
            && bonded.isInitialized()
            && bonded.isPlastic()
            ? bonded.getPlasticOrientation()
            : PlasticEntityOrientation.DEFAULT;
        return BONDED_CAULDRON_SHAPES.computeIfAbsent(orientation, ignored -> AbstractPlasticEntityBlock.rotateShape(
            COLLISION_SHAPE,
            orientation
        ));
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
        if (state.getValue(BONDED)) return super.use(state, level, pos, player, hand, hit);
        return InteractionResult.PASS;
    }
}
