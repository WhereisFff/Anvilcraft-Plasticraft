package dev.anvilcraft.plasticraft.block.piston;

import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 将由高粘性树脂连接的移动方块组折算为一个活塞推动预算。 */
public final class HighViscosityPistonBudget {
    public static final int VANILLA_PUSH_BUDGET = 12;

    private HighViscosityPistonBudget() {
    }

    public static boolean withinBudget(Level level, List<BlockPos> toPush) {
        return withinBudget(level, toPush, null);
    }

    public static boolean withinBudget(Level level, List<BlockPos> toPush, @Nullable Direction pushDirection) {
        return effectivePushCount(level, toPush, pushDirection) <= VANILLA_PUSH_BUDGET;
    }

    public static int effectivePushCount(Level level, List<BlockPos> toPush) {
        return effectivePushCount(level, toPush, null);
    }

    public static int effectivePushCount(Level level, List<BlockPos> toPush, @Nullable Direction pushDirection) {
        if (toPush.isEmpty()) return 0;
        Map<BlockPos, Integer> indices = new HashMap<>();
        for (int index = 0; index < toPush.size(); index++) {
            indices.put(toPush.get(index).immutable(), index);
        }

        int[] parent = new int[toPush.size()];
        for (int index = 0; index < parent.length; index++) parent[index] = index;
        Map<UUID, Integer> plasticGroups = new HashMap<>();
        for (int index = 0; index < toPush.size(); index++) {
            AbstractPlasticEntity plastic = PlasticPistonOccupancy.plasticEntityAt(level, toPush.get(index));
            if (plastic == null) continue;
            Integer firstIndex = plasticGroups.putIfAbsent(plastic.getUUID(), index);
            if (firstIndex != null) union(parent, firstIndex, index);
        }
        for (int index = 0; index < toPush.size(); index++) {
            BlockPos resinPos = toPush.get(index);
            BlockState resinState = level.getBlockState(resinPos);
            for (Direction direction : Direction.values()) {
                BlockPos otherPos = resinPos.relative(direction);
                Integer otherIndex = indices.get(otherPos);
                if (otherIndex == null) continue;
                BlockState otherState = level.getBlockState(otherPos);
                boolean resinBond = resinState.is(PlasticraftBlocks.HIGH_VISCOSITY_RESIN_BLOCK.get())
                    && canStickTogether(resinPos, resinState, otherPos, otherState);
                if (!resinBond && !BondedPistonReactions.hasBlockBond(level, resinPos, direction, pushDirection)) {
                    continue;
                }
                union(parent, index, otherIndex);
            }
        }

        Set<Integer> groups = new HashSet<>();
        for (int index = 0; index < parent.length; index++) groups.add(find(parent, index));
        return groups.size();
    }

    public static boolean canStickTogether(
        BlockPos firstPos,
        BlockState firstState,
        BlockPos secondPos,
        BlockState secondState
    ) {
        return firstState.anvilcraft$canStickTo(firstPos, secondPos, secondState)
            && secondState.anvilcraft$canStickTo(secondPos, firstPos, firstState);
    }

    private static int find(int[] parent, int index) {
        int root = index;
        while (parent[root] != root) root = parent[root];
        while (parent[index] != index) {
            int next = parent[index];
            parent[index] = root;
            index = next;
        }
        return root;
    }

    private static void union(int[] parent, int first, int second) {
        int firstRoot = find(parent, first);
        int secondRoot = find(parent, second);
        if (firstRoot != secondRoot) parent[secondRoot] = firstRoot;
    }
}
