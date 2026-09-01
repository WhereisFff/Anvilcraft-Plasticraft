package dev.anvilcraft.plasticraft.entity.redstone;

import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.anvilcraft.plasticraft.molding.product.MoldedTrayCell;
import dev.anvilcraft.plasticraft.molding.product.MoldedTrayComponent;
import dev.anvilcraft.plasticraft.molding.product.MoldedTrayComponentPlacement;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** 管理同一支架九个承载格的独立红石运行时。 */
public final class MoldedTrayRedstoneRuntimeManager {
    private final UniversalPlasticEntity host;
    private final Map<MoldedTrayCell, MoldedTrayRedstoneRuntime> runtimes = new HashMap<>();

    public MoldedTrayRedstoneRuntimeManager(UniversalPlasticEntity host) {
        this.host = Objects.requireNonNull(host, "host");
    }

    public MoldedTrayRedstoneRuntime runtime(MoldedTrayCell cell) {
        return this.runtimes.computeIfAbsent(cell, key -> new MoldedTrayRedstoneRuntime(this.host, this, key));
    }

    public void tick() {
        List<MoldedTrayComponentPlacement> placements = this.placements();
        this.removeMissing(placements);
        for (MoldedTrayComponentPlacement placement : placements) {
            this.runtime(placement.cell()).tick();
        }
    }

    public void flush() {
        List<MoldedTrayComponentPlacement> placements = this.placements();
        this.removeMissing(placements);
        for (MoldedTrayComponentPlacement placement : placements) {
            this.runtime(placement.cell()).flush();
        }
    }

    public void persist() {
        List<MoldedTrayComponentPlacement> placements = this.placements();
        this.removeMissing(placements);
        for (MoldedTrayComponentPlacement placement : placements) {
            this.runtime(placement.cell()).persist();
        }
    }

    public void applyTo(ItemStack stack) {
        for (MoldedTrayComponentPlacement placement : this.placements(stack)) {
            this.runtime(placement.cell()).applyTo(stack);
        }
    }

    public void publish() {
        List<MoldedTrayComponentPlacement> placements = this.placements();
        this.removeMissing(placements);
        for (MoldedTrayComponentPlacement placement : placements) {
            MoldedTrayComponent component = this.runtime(placement.cell()).signalComponent();
            if (component != null) MoldedTrayRedstoneNetwork.update(this.host, placement.cell(), component);
        }
    }

    public void remove() {
        for (MoldedTrayRedstoneRuntime runtime : List.copyOf(this.runtimes.values())) runtime.remove();
        this.runtimes.clear();
        MoldedTrayRedstoneNetwork.remove(this.host);
    }

    public Map<MoldedTrayCell, BlockEntity> blockEntities() {
        Map<MoldedTrayCell, BlockEntity> result = new LinkedHashMap<>();
        for (MoldedTrayComponentPlacement placement : this.placements()) {
            BlockEntity blockEntity = this.runtime(placement.cell()).blockEntity();
            if (blockEntity != null) result.put(placement.cell(), blockEntity);
        }
        return Map.copyOf(result);
    }

    OptionalInt internalSignal(MoldedTrayCell receiver, Direction inputDirection, boolean diodesOnly) {
        Optional<MoldedTrayCell> sourceCell = receiver.relative(inputDirection);
        if (sourceCell.isEmpty()) return OptionalInt.empty();
        MoldedTrayComponent source = this.component(sourceCell.orElseThrow());
        if (source == null) return OptionalInt.empty();
        MoldedTrayRedstoneBehavior behavior = MoldedTrayRedstoneBehaviors.find(source.state());
        if (diodesOnly && !behavior.isDiode()) return OptionalInt.of(0);
        return OptionalInt.of(Math.clamp(behavior.weakSignal(source, inputDirection), 0, 15));
    }

    Optional<Boolean> internalDiodePriority(MoldedTrayCell source, Direction localOutputFace) {
        Optional<MoldedTrayCell> receiverCell = source.relative(localOutputFace);
        if (receiverCell.isEmpty()) return Optional.empty();
        MoldedTrayComponent receiver = this.component(receiverCell.orElseThrow());
        if (receiver == null) return Optional.empty();
        BlockState state = receiver.state();
        boolean prioritized = MoldedTrayRedstoneBehaviors.find(state).isDiode()
            && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
            && state.getValue(BlockStateProperties.HORIZONTAL_FACING) != localOutputFace;
        return Optional.of(prioritized);
    }

    private MoldedTrayComponent component(MoldedTrayCell cell) {
        MoldedTrayRedstoneRuntime runtime = this.runtimes.get(cell);
        if (runtime != null) {
            MoldedTrayComponent snapshot = runtime.signalComponent();
            if (snapshot != null) return snapshot;
        }
        return this.host.getMoldedData()
            .filter(data -> MoldingProductTypes.isTray(data.finalType()))
            .flatMap(data -> data.contents().trayComponent(cell))
            .orElse(null);
    }

    private void removeMissing(List<MoldedTrayComponentPlacement> placements) {
        int occupied = 0;
        for (MoldedTrayComponentPlacement placement : placements) occupied |= placement.cell().bit();
        for (MoldedTrayCell cell : List.copyOf(this.runtimes.keySet())) {
            if ((occupied & cell.bit()) != 0) continue;
            this.runtimes.remove(cell).remove();
        }
    }

    private List<MoldedTrayComponentPlacement> placements() {
        return this.host.getMoldedData()
            .filter(data -> MoldingProductTypes.isTray(data.finalType()))
            .map(data -> data.contents().trayComponents())
            .orElse(List.of());
    }

    private List<MoldedTrayComponentPlacement> placements(ItemStack stack) {
        return MoldedPlasticData.get(stack)
            .filter(data -> MoldingProductTypes.isTray(data.finalType()))
            .map(data -> data.contents().trayComponents())
            .orElse(List.of());
    }
}
