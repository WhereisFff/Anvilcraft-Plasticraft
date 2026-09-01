package dev.anvilcraft.plasticraft.vapor;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.jetbrains.annotations.Nullable;

public final class VaporCapabilities {
    public static final BlockCapability<IVaporConsumer, @Nullable Direction> VAPOR_CONSUMER =
        BlockCapability.createSided(AnvilcraftPlasticraft.of("vapor_consumer"), IVaporConsumer.class);

    private VaporCapabilities() {
    }
}
