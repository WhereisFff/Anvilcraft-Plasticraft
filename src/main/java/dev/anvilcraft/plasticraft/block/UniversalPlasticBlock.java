package dev.anvilcraft.plasticraft.block;

import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.init.entity.PlasticraftEntities;
import dev.anvilcraft.plasticraft.item.DyeableMaterial;
import dev.anvilcraft.plasticraft.item.PlasticItemData;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 由世界熔体凝固得到的无专用功能通用塑料制品。 */
public class UniversalPlasticBlock extends AbstractPlasticEntityBlock<UniversalPlasticEntity> {
    private static final Map<PlasticEntityOrientation, VoxelShape> BONDED_INTERACTION_SHAPES = new ConcurrentHashMap<>();
    private static final Map<PlasticEntityOrientation, VoxelShape> BONDED_COLLISION_SHAPES = new ConcurrentHashMap<>();

    public UniversalPlasticBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
            .setValue(FACING, Direction.NORTH)
            .setValue(MAGNETIZED, false)
            .setValue(BONDED, false)
            .setValue(DyeableMaterial.COLOR, DyeColor.WHITE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(DyeableMaterial.COLOR);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!state.getValue(BONDED)) return UniversalPlasticShape.COLLISION;
        if (level.getBlockEntity(pos) instanceof BondedEntityBlockEntity bonded
            && bonded.isInitialized()
            && bonded.isPlastic()) {
            VoxelShape bondedShape = bonded.getBondedInteractionShape();
            if (bondedShape != null) return bondedShape;
        }
        PlasticEntityOrientation orientation = PlasticEntityOrientation.fromLegacyState(state);
        return BONDED_INTERACTION_SHAPES.computeIfAbsent(
            orientation,
            UniversalPlasticShape.GEOMETRY::placedInteractionShape
        );
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!state.getValue(BONDED)) return UniversalPlasticShape.COLLISION;
        if (level.getBlockEntity(pos) instanceof BondedEntityBlockEntity bonded
            && bonded.isInitialized()
            && bonded.isPlastic()) {
            VoxelShape bondedShape = bonded.getBondedCollisionShape();
            if (bondedShape != null) return bondedShape;
        }
        PlasticEntityOrientation orientation = PlasticEntityOrientation.fromLegacyState(state);
        return BONDED_COLLISION_SHAPES.computeIfAbsent(
            orientation,
            UniversalPlasticShape.GEOMETRY::placedCollisionShape
        );
    }

    @Override
    protected EntityType<? extends UniversalPlasticEntity> getPlasticEntityType() {
        return PlasticraftEntities.UNIVERSAL_PLASTIC.get();
    }

    @Override
    protected ItemStack createDropStack(BlockState state) {
        ItemStack stack = new ItemStack(this);
        PlasticItemData.setMaterial(stack, "universal_plastic");
        PlasticMeltColor.set(stack, state.getValue(DyeableMaterial.COLOR));
        if (state.getValue(MAGNETIZED)) {
            PlasticItemData.setMagnetized(stack, true);
        }
        return stack;
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        BlockEntity blockEntity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        ItemStack bondedDrop = this.getBondedDrop(blockEntity);
        if (!bondedDrop.isEmpty()) return List.of(bondedDrop);
        return List.of(this.createDropStack(state));
    }

    @Override
    public ItemStack getCloneItemStack(
        BlockState state,
        HitResult target,
        LevelReader level,
        BlockPos pos,
        Player player
    ) {
        ItemStack bondedDrop = this.getBondedDrop(level.getBlockEntity(pos));
        if (!bondedDrop.isEmpty()) return bondedDrop;
        return this.createDropStack(state);
    }

    private ItemStack getBondedDrop(BlockEntity blockEntity) {
        if (!(blockEntity instanceof BondedEntityBlockEntity bonded)
            || !bonded.isInitialized()
            || !bonded.isPlastic()) {
            return ItemStack.EMPTY;
        }
        return bonded.getStoredDropStack();
    }

    @Override
    protected UniversalPlasticEntity createPlasticEntity(
        EntityType<? extends UniversalPlasticEntity> entityType,
        ServerLevel level,
        Vec3 position,
        BlockState displayState,
        ItemStack dropStack,
        PlasticEntityOrientation orientation
    ) {
        return new UniversalPlasticEntity(entityType, level, position, displayState, dropStack, orientation);
    }

}
