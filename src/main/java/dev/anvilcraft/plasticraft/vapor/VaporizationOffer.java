package dev.anvilcraft.plasticraft.vapor;

import net.neoforged.neoforge.fluids.FluidStack;

import java.util.Objects;

/** 一次气化事务提议的精确输入与气体输出。 */
public record VaporizationOffer(FluidStack input, VaporStack output) {
    public VaporizationOffer {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(output, "output");
        if (input.isEmpty() || input.getAmount() <= 0) {
            throw new IllegalArgumentException("Vaporization input must not be empty");
        }
        if (output.isEmpty()) throw new IllegalArgumentException("Vaporization output must not be empty");
        input = input.copy();
    }

    @Override
    public FluidStack input() {
        return this.input.copy();
    }
}
