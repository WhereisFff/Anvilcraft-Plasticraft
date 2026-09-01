package dev.anvilcraft.plasticraft.blueprint;

import dev.dubhe.anvilcraft.block.RedstoneWireBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 用本体公开的端口编辑把蓝图四向写入覆盖表。
 * 拓扑重建会按邻居自动连线;随后按蓝图关掉多余端口、补回缺失端口。
 */
final class AnvilCraftRedstoneWirePorts {
    private AnvilCraftRedstoneWirePorts() {
    }

    static boolean isWire(BlockState state) {
        return state.getBlock() instanceof RedstoneWireBlock;
    }

    static void reconcile(ServerLevel level, BlockPos pos, BlockState blueprint) {
        BlockState current = level.getBlockState(pos);
        if (!(current.getBlock() instanceof RedstoneWireBlock wire) || !isWire(blueprint)) {
            return;
        }
        for (int pass = 0; pass < RedstoneWireBlock.CONNECTION_PROPERTIES.size(); pass++) {
            current = level.getBlockState(pos);
            if (!(current.getBlock() instanceof RedstoneWireBlock)) {
                return;
            }
            boolean changed = false;
            for (int index = 0; index < RedstoneWireBlock.CONNECTION_PROPERTIES.size(); index++) {
                if (isConnected(current, index) && !isConnected(blueprint, index)
                    && clickPort(wire, level, pos, current, index)) {
                    changed = true;
                    current = level.getBlockState(pos);
                    if (!(current.getBlock() instanceof RedstoneWireBlock)) {
                        return;
                    }
                }
            }
            if (!changed) {
                break;
            }
        }
        current = level.getBlockState(pos);
        for (int index = 0; index < RedstoneWireBlock.CONNECTION_PROPERTIES.size(); index++) {
            if (!(current.getBlock() instanceof RedstoneWireBlock)) {
                return;
            }
            if (!isConnected(current, index) && isConnected(blueprint, index)
                && clickPort(wire, level, pos, current, index)) {
                current = level.getBlockState(pos);
            }
        }
    }

    private static boolean isConnected(BlockState state, int index) {
        return state.getValue(RedstoneWireBlock.CONNECTION_PROPERTIES.get(index)).isConnected();
    }

    private static boolean clickPort(
        RedstoneWireBlock wire,
        ServerLevel level,
        BlockPos pos,
        BlockState state,
        int index
    ) {
        Direction attachment = state.getValue(RedstoneWireBlock.ATTACHMENT);
        Direction tangent = RedstoneWireBlock.getLocalDirection(attachment, index);
        Vec3 hit = Vec3.atCenterOf(pos).add(
            tangent.getStepX() * 0.3D,
            tangent.getStepY() * 0.3D,
            tangent.getStepZ() * 0.3D
        );
        return wire.editConnection(level, pos, state, hit, true);
    }
}
