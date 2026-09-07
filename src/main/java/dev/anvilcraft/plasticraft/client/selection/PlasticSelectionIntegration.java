package dev.anvilcraft.plasticraft.client.selection;

import dev.anvilcraft.lib.v2.cube.client.CubeSelection;
import dev.anvilcraft.lib.v2.cube.client.SelectionPart;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.AbstractPlasticEntityBlock;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.collision.PlasticEntityGeometry;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.event.ModelEvent;

import java.util.List;

public final class PlasticSelectionIntegration {
    private static final AABB PLACED_BOUNDS = new AABB(-2, -2, -2, 3, 3, 3);

    private PlasticSelectionIntegration() {
    }

    public static void register() {
        // 成型区域是无模型的交互占位，继续由其当前生产阶段决定选取范围。
        CubeSelection.exclude(PlasticraftBlocks.PLASTIC_MOLDING_REGION.get());
        MachineBlockSelection.register();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block instanceof AbstractPlasticEntityBlock<?>
                && AnvilcraftPlasticraft.MOD_ID.equals(BuiltInRegistries.BLOCK.getKey(block).getNamespace())) {
                CubeSelection.registerDynamic(block, PLACED_BOUNDS, false, PlasticSelectionIntegration::parts);
            }
        }
    }

    public static void reload(ModelEvent.BakingCompleted event) {
        PlasticSelectionGeometry.clear();
        MachineBlockSelection.reload();
    }

    public static boolean supported(SelectionPart part) {
        AABB allowed = PLACED_BOUNDS.inflate(1.0E-5D);
        AABB bounds = part.bounds();
        return allowed.contains(bounds.minX, bounds.minY, bounds.minZ)
            && allowed.contains(bounds.maxX, bounds.maxY, bounds.maxZ);
    }

    private static List<SelectionPart> parts(ClientLevel level, BlockPos pos, BlockState state, float partialTick) {
        if (state.getValue(AbstractPlasticEntityBlock.BONDED)
            && level.getBlockEntity(pos) instanceof BondedEntityBlockEntity bonded
            && bonded.isInitialized() && bonded.isPlastic()
            && bonded.getOrCreateRenderEntity() instanceof AbstractPlasticEntity plastic) {
            PlasticEntityGeometry geometry = plastic.plasticraft$getGeometry();
            return PlasticSelectionGeometry.get(geometry).placed(geometry, bonded.getPlasticOrientation(), bonded.getAdhesiveLocalFace());
        }
        // 未完成实体化或同步的方块仍保留可选取区域，不能把缺少数据解释为空模型。
        List<SelectionPart> model = CubeSelection.modelParts(state, pos);
        if (model != null) return model;
        VoxelShape shape = state.getShape(level, pos);
        return shape.isEmpty() ? List.of() : List.of(PlasticSelectionGeometry.fromShape(shape));
    }
}
