package dev.anvilcraft.plasticraft.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.anvilcraft.plasticraft.init.PlasticraftDataComponents;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.Optional;

/** 成型塑料锅完整拾取时随物品保存的出料口与点燃状态。 */
public record MoldedPlasticCauldronState(Optional<Direction> outletSide, boolean ignited) {
    public static final Codec<MoldedPlasticCauldronState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.optionalFieldOf("outlet_side", -1).forGetter(state ->
            state.outletSide().map(Direction::get3DDataValue).orElse(-1)
        ),
        Codec.BOOL.optionalFieldOf("ignited", false).forGetter(MoldedPlasticCauldronState::ignited)
    ).apply(instance, MoldedPlasticCauldronState::decodePersistent));
    public static final StreamCodec<RegistryFriendlyByteBuf, MoldedPlasticCauldronState> STREAM_CODEC =
        StreamCodec.of(MoldedPlasticCauldronState::encode, MoldedPlasticCauldronState::decode);

    public MoldedPlasticCauldronState {
        Objects.requireNonNull(outletSide, "outletSide");
        outletSide = outletSide.filter(direction -> direction.getAxis().isHorizontal());
    }

    public boolean isEmpty() {
        return this.outletSide.isEmpty() && !this.ignited;
    }

    public static Optional<MoldedPlasticCauldronState> get(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        return Optional.ofNullable(stack.get(PlasticraftDataComponents.MOLDED_PLASTIC_CAULDRON_STATE.get()));
    }

    public static void set(ItemStack stack, MoldedPlasticCauldronState state) {
        if (stack.isEmpty()) return;
        if (state.isEmpty()) {
            clear(stack);
        } else {
            stack.set(PlasticraftDataComponents.MOLDED_PLASTIC_CAULDRON_STATE.get(), state);
        }
    }

    public static void clear(ItemStack stack) {
        if (!stack.isEmpty()) stack.remove(PlasticraftDataComponents.MOLDED_PLASTIC_CAULDRON_STATE.get());
    }

    public static ItemStack withoutState(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack result = stack.copy();
        clear(result);
        return result;
    }

    private static MoldedPlasticCauldronState decodePersistent(int outletSide, boolean ignited) {
        return new MoldedPlasticCauldronState(directionFor(outletSide), ignited);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MoldedPlasticCauldronState state) {
        buffer.writeByte(state.outletSide.map(Direction::get3DDataValue).orElse(-1));
        buffer.writeBoolean(state.ignited);
    }

    private static MoldedPlasticCauldronState decode(RegistryFriendlyByteBuf buffer) {
        return new MoldedPlasticCauldronState(directionFor(buffer.readByte()), buffer.readBoolean());
    }

    private static Optional<Direction> directionFor(int id) {
        if (id < 0 || id >= Direction.values().length) return Optional.empty();
        Direction direction = Direction.from3DDataValue(id);
        return direction.getAxis().isHorizontal() ? Optional.of(direction) : Optional.empty();
    }
}
