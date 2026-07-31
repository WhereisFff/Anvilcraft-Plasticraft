package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.api.FluidPipeNetworkExtension;
import dev.anvilcraft.plasticraft.recipe.CatalyticPressProcess;
import dev.dubhe.anvilcraft.api.fluid.network.FluidContainerLookup;
import dev.dubhe.anvilcraft.api.fluid.network.FluidEndpoint;
import dev.dubhe.anvilcraft.api.fluid.network.FluidPipeNetwork;
import dev.dubhe.anvilcraft.api.fluid.network.ValveState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 将压盖的整量挤出限制在 AnvilCraft 管网的真实可达端点内。 */
@Mixin(FluidPipeNetwork.class)
abstract class FluidPipeNetworkMixin implements FluidPipeNetworkExtension {
    @Shadow
    @Final
    private Level level;

    @Shadow
    @Final
    private Set<BlockPos> parts;

    @Shadow
    @Final
    private Map<BlockPos, List<BlockPos>> adjacency;

    @Shadow
    @Final
    private Map<BlockPos, ValveState> valves;

    @Shadow
    @Final
    private Map<BlockPos, Direction> diodes;

    @Shadow
    @Final
    private Map<BlockPos, Map<Direction, Direction>> faceFlow;

    @Shadow
    @Final
    private List<FluidEndpoint> endpoints;

    @Override
    public boolean plasticraft$pushAll(
        IFluidHandler source,
        BlockPos sourcePos,
        BlockPos entryPipePos,
        int sourceEffectiveHeight,
        FluidStack fluid
    ) {
        if (fluid.isEmpty() || !this.parts.contains(entryPipePos)) return false;
        FluidStack simulated = source.drain(fluid, IFluidHandler.FluidAction.SIMULATE);
        if (simulated.getAmount() != fluid.getAmount()
            || !FluidStack.isSameFluidSameComponents(simulated, fluid)) return false;

        Reachable reachable = this.plasticraft$findReachable(entryPipePos, fluid);
        FluidEndpoint target = this.endpoints.stream()
            .filter(endpoint -> endpoint.handler() != source)
            .filter(endpoint -> endpoint.effectiveHeight() < sourceEffectiveHeight)
            .filter(endpoint -> this.plasticraft$isConnected(endpoint))
            .filter(endpoint -> this.plasticraft$isReachable(reachable, endpoint))
            .filter(endpoint -> endpoint.handler().fill(fluid, IFluidHandler.FluidAction.SIMULATE)
                == fluid.getAmount())
            .min(Comparator
                .comparingInt(FluidEndpoint::effectiveHeight)
                .thenComparingInt(endpoint -> endpoint.containerPos().distManhattan(sourcePos)))
            .orElse(null);
        if (target == null) return false;

        FluidStack drained = source.drain(fluid, IFluidHandler.FluidAction.EXECUTE);
        if (drained.getAmount() != fluid.getAmount()
            || !FluidStack.isSameFluidSameComponents(drained, fluid)) {
            if (!drained.isEmpty()) source.fill(drained, IFluidHandler.FluidAction.EXECUTE);
            return false;
        }
        int filled = target.handler().fill(drained, IFluidHandler.FluidAction.EXECUTE);
        if (filled == drained.getAmount()) {
            if (this.level instanceof ServerLevel serverLevel) {
                CatalyticPressProcess.syncMeltContainerColor(serverLevel, target.containerPos(), drained);
            }
            return true;
        }

        if (filled > 0) {
            target.handler().drain(drained.copyWithAmount(filled), IFluidHandler.FluidAction.EXECUTE);
        }
        source.fill(drained, IFluidHandler.FluidAction.EXECUTE);
        return false;
    }

    private boolean plasticraft$isConnected(FluidEndpoint endpoint) {
        return endpoint.entity() == null || FluidContainerLookup.isEntityConnectedToPipe(
            this.level,
            endpoint.containerPos(),
            endpoint.sideToPipe(),
            endpoint.entity()
        );
    }

    private Reachable plasticraft$findReachable(BlockPos start, FluidStack fluid) {
        Set<BlockPos> reached = new HashSet<>();
        Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        if (!this.plasticraft$valveAllows(start, fluid)) return new Reachable(reached, cameFrom);
        reached.add(start);
        cameFrom.put(start, null);
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            for (BlockPos next : this.adjacency.getOrDefault(current, List.of())) {
                if (reached.contains(next)
                    || !this.plasticraft$canLeaveDiode(current, cameFrom.get(current), next)
                    || !this.plasticraft$canPassFaceValve(current, next)
                    || !this.plasticraft$valveAllows(next, fluid)) continue;
                reached.add(next);
                cameFrom.put(next, current);
                queue.addLast(next);
            }
        }
        return new Reachable(reached, cameFrom);
    }

    private boolean plasticraft$isReachable(Reachable reachable, FluidEndpoint endpoint) {
        BlockPos pipe = endpoint.fromPipePos();
        if (!reachable.parts().contains(pipe)
            || !this.plasticraft$canLeaveDiode(pipe, reachable.cameFrom().get(pipe), endpoint.containerPos())) {
            return false;
        }
        if (endpoint.sideToPipe() == null) return true;
        Direction toContainer = endpoint.sideToPipe().getOpposite();
        Map<Direction, Direction> faces = this.faceFlow.get(pipe);
        if (faces == null) return true;
        Direction allowed = faces.get(toContainer);
        return allowed == null || allowed == toContainer;
    }

    private boolean plasticraft$valveAllows(BlockPos pos, FluidStack fluid) {
        ValveState valve = this.valves.get(pos);
        return valve == null || valve.allows(fluid);
    }

    private boolean plasticraft$canPassFaceValve(BlockPos current, BlockPos next) {
        Direction direction = Direction.fromDelta(
            next.getX() - current.getX(),
            next.getY() - current.getY(),
            next.getZ() - current.getZ()
        );
        if (direction == null) return true;
        Map<Direction, Direction> currentFaces = this.faceFlow.get(current);
        if (currentFaces != null) {
            Direction allowed = currentFaces.get(direction);
            if (allowed != null && allowed != direction) return false;
        }
        Map<Direction, Direction> nextFaces = this.faceFlow.get(next);
        if (nextFaces == null) return true;
        Direction allowed = nextFaces.get(direction.getOpposite());
        return allowed == null || allowed == direction;
    }

    private boolean plasticraft$canLeaveDiode(BlockPos current, BlockPos from, BlockPos to) {
        Direction inflow = this.diodes.get(current);
        if (inflow == null) return true;
        if (!to.equals(current.relative(inflow.getOpposite()))) return false;
        return from == null || from.equals(current.relative(inflow));
    }

    private record Reachable(Set<BlockPos> parts, Map<BlockPos, BlockPos> cameFrom) {
    }
}
