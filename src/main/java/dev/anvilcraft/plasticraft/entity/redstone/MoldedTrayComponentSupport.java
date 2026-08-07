package dev.anvilcraft.plasticraft.entity.redstone;

import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.molding.product.MoldedTrayComponent;
import dev.dubhe.anvilcraft.block.entity.AdvancedComparatorBlockEntity;
import dev.dubhe.anvilcraft.block.entity.ItemDetectorBlockEntity;
import dev.dubhe.anvilcraft.block.entity.PulseGeneratorBlockEntity;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/** 支架元件的放入、方块实体还原和无内部状态取回。 */
public final class MoldedTrayComponentSupport {
    private MoldedTrayComponentSupport() {
    }

    public static boolean isSupportedItem(ItemStack stack) {
        return MoldedTrayComponent.isSupported(stack);
    }

    public static MoldedTrayComponent create(
        UniversalPlasticEntity host,
        ItemStack source,
        Direction localFacing
    ) {
        if (!MoldedTrayComponent.isSupported(source)
            || !(source.getItem() instanceof BlockItem blockItem)
            || localFacing.getAxis() == Direction.Axis.Y) {
            throw new IllegalArgumentException("Unsupported molded tray component item");
        }
        BlockState state = source.getOrDefault(
            DataComponents.BLOCK_STATE,
            BlockItemStateProperties.EMPTY
        ).apply(blockItem.getBlock().defaultBlockState());
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, localFacing);
        }
        if (state.hasProperty(BlockStateProperties.ATTACH_FACE)) {
            state = state.setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR);
        }
        if (state.hasProperty(BlockStateProperties.POWERED)) {
            state = state.setValue(BlockStateProperties.POWERED, false);
        }
        if (state.hasProperty(BlockStateProperties.POWER)) {
            state = state.setValue(BlockStateProperties.POWER, 0);
        }
        if (state.hasProperty(BlockStateProperties.LOCKED)) {
            state = state.setValue(BlockStateProperties.LOCKED, false);
        }
        if (state.hasProperty(BlockStateProperties.LIT)) {
            state = state.setValue(BlockStateProperties.LIT, true);
        }

        CompoundTag blockEntityData = new CompoundTag();
        BlockEntity blockEntity = createBlockEntity(
            state,
            host.plasticraft$getAnchorBlockPos(),
            host.level(),
            null
        );
        if (blockEntity != null) {
            CustomData customData = source.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY);
            if (!customData.isEmpty()) customData.loadInto(blockEntity, host.registryAccess());
            blockEntity.applyComponentsFromItemStack(source);
            blockEntity.setLevel(host.level());
            blockEntityData = blockEntity.saveWithoutMetadata(host.registryAccess());
        }
        return new MoldedTrayComponent(
            source.copyWithCount(1),
            state,
            blockEntityData,
            0,
            List.of()
        );
    }

    public static @Nullable BlockEntity createBlockEntity(
        MoldedTrayComponent component,
        BlockPos position,
        Level level
    ) {
        return createBlockEntity(component, position, level, null);
    }

    public static @Nullable BlockEntity createBlockEntity(
        MoldedTrayComponent component,
        UniversalPlasticEntity host
    ) {
        Objects.requireNonNull(host, "host");
        return createBlockEntity(
            component,
            host.plasticraft$getAnchorBlockPos(),
            host.level(),
            host
        );
    }

    private static @Nullable BlockEntity createBlockEntity(
        MoldedTrayComponent component,
        BlockPos position,
        Level level,
        @Nullable UniversalPlasticEntity host
    ) {
        BlockEntity blockEntity = createBlockEntity(component.state(), position, level, host);
        if (blockEntity == null) return null;
        blockEntity.loadWithComponents(component.blockEntityData(), level.registryAccess());
        blockEntity.setLevel(level);
        return blockEntity;
    }

    public static ItemStack extractionStack(
        MoldedTrayComponent component,
        @Nullable BlockEntity cachedBlockEntity,
        Level level
    ) {
        ItemStack result = component.originalItem().copyWithCount(1);
        BlockEntity blockEntity = cachedBlockEntity;
        if (blockEntity == null) {
            blockEntity = createBlockEntity(component, BlockPos.ZERO, level);
        }
        if (blockEntity instanceof PulseGeneratorBlockEntity
            || blockEntity instanceof ItemDetectorBlockEntity
            || blockEntity instanceof AdvancedComparatorBlockEntity) {
            blockEntity.saveToItem(result, level.registryAccess());
            CustomData.update(DataComponents.BLOCK_ENTITY_DATA, result, MoldedTrayComponentSupport::removeRuntimeData);
        }
        return result;
    }

    public static Direction localFacing(UniversalPlasticEntity host, Vec3 lookDirection) {
        Objects.requireNonNull(lookDirection, "lookDirection");
        Direction best = Direction.NORTH;
        double bestDot = Double.POSITIVE_INFINITY;
        for (Direction local : List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
            Direction world = host.getOrientation().worldDirection(local);
            double dot = lookDirection.x * world.getStepX()
                + lookDirection.y * world.getStepY()
                + lookDirection.z * world.getStepZ();
            if (dot < bestDot) {
                best = local;
                bestDot = dot;
            }
        }
        return best;
    }

    private static @Nullable BlockEntity createBlockEntity(
        BlockState state,
        BlockPos position,
        Level level,
        @Nullable UniversalPlasticEntity host
    ) {
        if (state.is(ModBlocks.PULSE_GENERATOR.get())) {
            return new VirtualPulseGeneratorBlockEntity(position, state);
        }
        if (state.is(ModBlocks.ITEM_DETECTOR.get())) {
            return new VirtualItemDetectorBlockEntity(position, state, host);
        }
        if (!(state.getBlock() instanceof EntityBlock entityBlock)) return null;
        return entityBlock.newBlockEntity(position, state);
    }

    private static void removeRuntimeData(CompoundTag tag) {
        tag.remove("OutputSignal");
        CompoundTag extraData = tag.getCompound("ExtraData");
        extraData.remove("Inputting");
        extraData.remove("State");
        extraData.remove("PhaseStartGameTime");
        extraData.remove("PhaseDuration");
        if (!extraData.isEmpty()) tag.put("ExtraData", extraData);
    }

    private static final class VirtualPulseGeneratorBlockEntity extends PulseGeneratorBlockEntity {
        private VirtualPulseGeneratorBlockEntity(BlockPos position, BlockState state) {
            super(position, state);
        }

        @Override
        public void syncToClient() {
        }
    }

    private static final class VirtualItemDetectorBlockEntity extends ItemDetectorBlockEntity {
        private final @Nullable UniversalPlasticEntity host;

        private VirtualItemDetectorBlockEntity(
            BlockPos position,
            BlockState state,
            @Nullable UniversalPlasticEntity host
        ) {
            super(position, state);
            this.host = host;
        }

        @Override
        public void recalcDetectionRange() {
            this.setChanged();
        }

        @Override
        public AABB shape() {
            if (this.host == null || !this.getBlockState().hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                return super.shape();
            }
            return MoldedTrayRedstoneNetwork.forwardRange(
                this.host,
                this.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING),
                this.getRange()
            );
        }
    }
}
