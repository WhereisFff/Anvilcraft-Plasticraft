package dev.anvilcraft.plasticraft.molding.product;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import net.minecraft.tags.BlockTags;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 支架承载的单个红石元件及其独立运行状态。 */
public record MoldedTrayComponent(
    ItemStack originalItem,
    BlockState state,
    CompoundTag blockEntityData,
    int scheduledTicks,
    List<Long> torchToggleTimes
) {
    public static final int MAX_SCHEDULED_TICKS = 24000;
    public static final int MAX_TORCH_TOGGLES = 8;
    private static final Codec<List<Long>> TOGGLE_TIMES_CODEC = Codec.LONG.listOf().validate(times ->
        times.size() <= MAX_TORCH_TOGGLES
            ? DataResult.success(times)
            : DataResult.error(() -> "Too many molded tray torch toggles")
    );
    public static final Codec<MoldedTrayComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ItemStack.CODEC.fieldOf("item").forGetter(MoldedTrayComponent::originalItem),
        BlockState.CODEC.fieldOf("state").forGetter(MoldedTrayComponent::state),
        CompoundTag.CODEC.optionalFieldOf("block_entity", new CompoundTag())
            .forGetter(MoldedTrayComponent::blockEntityData),
        Codec.INT.optionalFieldOf("scheduled_ticks", 0).forGetter(MoldedTrayComponent::scheduledTicks),
        TOGGLE_TIMES_CODEC.optionalFieldOf("torch_toggle_times", List.of())
            .forGetter(MoldedTrayComponent::torchToggleTimes)
    ).apply(instance, MoldedTrayComponent::new));
    private static final StreamCodec<RegistryFriendlyByteBuf, BlockState> BLOCK_STATE_STREAM_CODEC =
        ByteBufCodecs.fromCodecWithRegistries(BlockState.CODEC);
    public static final StreamCodec<RegistryFriendlyByteBuf, MoldedTrayComponent> STREAM_CODEC = StreamCodec.of(
        MoldedTrayComponent::encode,
        MoldedTrayComponent::decode
    );

    public MoldedTrayComponent {
        Objects.requireNonNull(originalItem, "originalItem");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(blockEntityData, "blockEntityData");
        Objects.requireNonNull(torchToggleTimes, "torchToggleTimes");
        if (originalItem.isEmpty() || !(originalItem.getItem() instanceof BlockItem item)) {
            throw new IllegalArgumentException("Molded tray component requires a block item");
        }
        if (!isSupported(state) || item.getBlock() != state.getBlock()) {
            throw new IllegalArgumentException("Unsupported molded tray component");
        }
        if (scheduledTicks < 0 || scheduledTicks > MAX_SCHEDULED_TICKS) {
            throw new IllegalArgumentException("Invalid molded tray scheduled tick count");
        }
        if (torchToggleTimes.size() > MAX_TORCH_TOGGLES) {
            throw new IllegalArgumentException("Too many molded tray torch toggles");
        }
        originalItem = originalItem.copyWithCount(1);
        blockEntityData = sanitizeBlockEntityData(blockEntityData);
        torchToggleTimes = List.copyOf(torchToggleTimes);
    }

    @Override
    public ItemStack originalItem() {
        return this.originalItem.copy();
    }

    @Override
    public CompoundTag blockEntityData() {
        return this.blockEntityData.copy();
    }

    public MoldedTrayComponent withState(BlockState replacement) {
        if (this.state.equals(replacement)) return this;
        return new MoldedTrayComponent(
            this.originalItem,
            replacement,
            this.blockEntityData,
            this.scheduledTicks,
            this.torchToggleTimes
        );
    }

    public MoldedTrayComponent withBlockEntityData(CompoundTag replacement) {
        CompoundTag sanitized = sanitizeBlockEntityData(replacement);
        if (this.blockEntityData.equals(sanitized)) return this;
        return new MoldedTrayComponent(
            this.originalItem,
            this.state,
            sanitized,
            this.scheduledTicks,
            this.torchToggleTimes
        );
    }

    public MoldedTrayComponent withRuntime(int replacementTicks, List<Long> replacementToggleTimes) {
        if (this.scheduledTicks == replacementTicks && this.torchToggleTimes.equals(replacementToggleTimes)) {
            return this;
        }
        return new MoldedTrayComponent(
            this.originalItem,
            this.state,
            this.blockEntityData,
            replacementTicks,
            replacementToggleTimes
        );
    }

    public MoldedTrayComponent replace(
        BlockState replacementState,
        CompoundTag replacementBlockEntityData,
        int replacementTicks,
        List<Long> replacementToggleTimes
    ) {
        return new MoldedTrayComponent(
            this.originalItem,
            replacementState,
            replacementBlockEntityData,
            replacementTicks,
            replacementToggleTimes
        );
    }

    public boolean hasSameSignalState(MoldedTrayComponent other) {
        return other != null
            && this.state.equals(other.state)
            && this.blockEntityData.equals(other.blockEntityData);
    }

    public boolean hasSameConfiguration(MoldedTrayComponent other) {
        return other != null
            && ItemStack.matches(this.originalItem, other.originalItem)
            && this.hasSameSignalState(other);
    }

    public static boolean isSupported(ItemStack stack) {
        return !stack.isEmpty()
            && stack.getItem() instanceof BlockItem item
            && isSupported(item.getBlock().defaultBlockState());
    }

    public static boolean isSupported(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.REPEATER
            || block == Blocks.COMPARATOR
            || block == Blocks.REDSTONE_TORCH
            || block == Blocks.LEVER
            || block == Blocks.DAYLIGHT_DETECTOR
            || block == ModBlocks.ADVANCED_COMPARATOR.get()
            || block instanceof ButtonBlock
            || block instanceof BasePressurePlateBlock
            || state.is(BlockTags.BUTTONS)
            || state.is(BlockTags.PRESSURE_PLATES)
            || block == ModBlocks.PULSE_GENERATOR.get()
            || block == ModBlocks.ITEM_DETECTOR.get();
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof MoldedTrayComponent component
            && ItemStack.matches(this.originalItem, component.originalItem)
            && this.state.equals(component.state)
            && this.blockEntityData.equals(component.blockEntityData)
            && this.scheduledTicks == component.scheduledTicks
            && this.torchToggleTimes.equals(component.torchToggleTimes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            this.originalItem.getItemHolder().unwrapKey().orElse(null),
            this.originalItem.getComponentsPatch(),
            this.state,
            this.blockEntityData,
            this.scheduledTicks,
            this.torchToggleTimes
        );
    }

    private static CompoundTag sanitizeBlockEntityData(CompoundTag data) {
        CompoundTag result = data.copy();
        result.remove("id");
        result.remove("x");
        result.remove("y");
        result.remove("z");
        return result;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MoldedTrayComponent component) {
        ItemStack.STREAM_CODEC.encode(buffer, component.originalItem);
        BLOCK_STATE_STREAM_CODEC.encode(buffer, component.state);
        buffer.writeNbt(component.blockEntityData);
        buffer.writeVarInt(component.scheduledTicks);
        buffer.writeVarInt(component.torchToggleTimes.size());
        for (long time : component.torchToggleTimes) buffer.writeVarLong(time);
    }

    private static MoldedTrayComponent decode(RegistryFriendlyByteBuf buffer) {
        ItemStack item = ItemStack.STREAM_CODEC.decode(buffer);
        BlockState state = BLOCK_STATE_STREAM_CODEC.decode(buffer);
        CompoundTag blockEntityData = buffer.readNbt();
        if (blockEntityData == null) blockEntityData = new CompoundTag();
        int scheduledTicks = buffer.readVarInt();
        int toggleCount = buffer.readVarInt();
        if (toggleCount < 0 || toggleCount > MAX_TORCH_TOGGLES) {
            throw new IllegalArgumentException("Invalid molded tray torch toggle count");
        }
        ArrayList<Long> toggleTimes = new ArrayList<>(toggleCount);
        for (int index = 0; index < toggleCount; index++) toggleTimes.add(buffer.readVarLong());
        return new MoldedTrayComponent(item, state, blockEntityData, scheduledTicks, toggleTimes);
    }
}
