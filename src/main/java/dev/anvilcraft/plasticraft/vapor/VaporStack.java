package dev.anvilcraft.plasticraft.vapor;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/** 未注册的虚拟气体，数量以毫桶计。 */
public record VaporStack(Identifier type, int amount) {
    public VaporStack {
        Objects.requireNonNull(type, "type");
        if (amount < 0) throw new IllegalArgumentException("Vapor amount must not be negative");
    }

    public boolean isEmpty() {
        return this.amount == 0;
    }

    public VaporStack withAmount(int amount) {
        return new VaporStack(this.type, amount);
    }
}
