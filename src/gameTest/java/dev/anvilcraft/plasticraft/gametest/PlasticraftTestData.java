package dev.anvilcraft.plasticraft.gametest;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

final class PlasticraftTestData {
    private PlasticraftTestData() {
    }

    static CompoundTag save(Entity entity) {
        ProblemReporter.Collector errors = new ProblemReporter.Collector();
        TagValueOutput output = TagValueOutput.createWithContext(errors, entity.registryAccess());
        entity.saveWithoutId(output);
        check(errors);
        return output.buildResult();
    }

    static void read(HolderLookup.Provider registries, CompoundTag tag, Consumer<ValueInput> load) {
        ProblemReporter.Collector errors = new ProblemReporter.Collector();
        load.accept(TagValueInput.create(errors, registries, tag));
        check(errors);
    }

    private static void check(ProblemReporter.Collector errors) {
        if (!errors.isEmpty()) {
            throw new PlasticraftTestFailure(errors.getTreeReport());
        }
    }

    static @Nullable IItemHandler items(@Nullable ResourceHandler<ItemResource> handler) {
        return handler == null ? null : IItemHandler.of(handler);
    }

    static @Nullable IFluidHandler fluids(@Nullable ResourceHandler<FluidResource> handler) {
        return handler == null ? null : IFluidHandler.of(handler);
    }
    static IEnergyStorage energy(EnergyHandler handler) {
        return IEnergyStorage.of(handler);
    }
}
