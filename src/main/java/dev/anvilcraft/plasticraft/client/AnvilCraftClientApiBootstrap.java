package dev.anvilcraft.plasticraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.lib.v2.multiblock.dynamic.definition.MultiblockDefinition;
import dev.anvilcraft.lib.v2.util.predicate.BlockStatePredicate;
import dev.anvilcraft.plasticraft.block.IgnitedFluidEffects;
import dev.anvilcraft.plasticraft.client.renderer.IgnitedFluidFlameRenderer;
import dev.anvilcraft.plasticraft.client.renderer.MoldingBlueprintDiskPreviewRenderer;
import dev.anvilcraft.plasticraft.client.renderer.PlasticOilCatalysisRenderer;
import dev.anvilcraft.plasticraft.client.renderer.StructureDiskPreviewRenderer;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprintDisk;
import dev.dubhe.anvilcraft.api.fluid.LargeCauldronFluidHandler;
import dev.dubhe.anvilcraft.block.entity.FishTankBlockEntity;
import dev.dubhe.anvilcraft.block.entity.LargeCauldronBlockEntity;
import dev.dubhe.anvilcraft.block.entity.fluid.PipeCheckValveBlockEntity;
import dev.dubhe.anvilcraft.block.fluid.PipeBlock;
import dev.dubhe.anvilcraft.client.renderer.blockentity.FishTankRenderHooks;
import dev.dubhe.anvilcraft.client.renderer.blockentity.LargeCauldronRenderHooks;
import dev.dubhe.anvilcraft.client.support.StructureDiskPreviewSupport;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import dev.dubhe.anvilcraft.util.LevelLike;
import dev.dubhe.anvilcraft.util.RecipeUtil;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.neoforged.neoforge.fluids.FluidStack;

/** 客户端扩展点注册。不得在此初始化 JEI/Jade 客户端 API。 */
public final class AnvilCraftClientApiBootstrap {
    private static final float CAULDRON_MIN_XZ = -0.75F + 0.001F;
    private static final float CAULDRON_MAX_XZ = 1.75F - 0.001F;
    private static final float CAULDRON_MIN_Y = -0.5F + 0.001F;
    private static final float CAULDRON_CONTENT_HEIGHT = 2.25F;
    private static final float CAULDRON_FLAME_SCALE = 3.0F;
    private static final float TANK_WALL = 1.0F / 16.0F + 0.001F;

    private AnvilCraftClientApiBootstrap() {
    }

    public static void register() {
        RecipeUtil.registerLevelLikeCustomizer(AnvilCraftClientApiBootstrap::seedCondenserValves);
        StructureDiskPreviewSupport.registerPreviewHandler((graphics, stack, mouseX, mouseY) -> {
            if (MoldingBlueprintDisk.hasBlueprintData(stack)) {
                return MoldingBlueprintDiskPreviewRenderer.renderPreviewAt(graphics, stack, mouseX, mouseY);
            }
            return StructureDiskPreviewRenderer.renderPreviewAt(graphics, stack, mouseX, mouseY);
        });
        LargeCauldronRenderHooks.register(new LargeCauldronRenderHooks.Handler() {
            @Override
            public boolean showVanillaFire(LargeCauldronBlockEntity cauldron) {
                return !IgnitedFluidEffects.isHighHeatFuel(cauldron.getTopFluid());
            }

            @Override
            public void afterRender(
                LargeCauldronBlockEntity cauldron,
                float partialTick,
                PoseStack poseStack,
                MultiBufferSource buffers,
                int packedLight,
                int packedOverlay
            ) {
                renderLargeCauldronOverlay(cauldron, partialTick, poseStack, buffers, packedLight, packedOverlay);
            }
        });
        FishTankRenderHooks.register(new FishTankRenderHooks.Handler() {
            @Override
            public boolean showVanillaFire(FishTankBlockEntity tank) {
                return !tank.getFluidHandler().getFluid().is(PlasticraftFluids.HIGH_HEAT_FUEL.get());
            }

            @Override
            public void afterRender(
                FishTankBlockEntity tank,
                float partialTick,
                PoseStack poseStack,
                MultiBufferSource buffers,
                int packedLight,
                int packedOverlay
            ) {
                renderFishTankOverlay(tank, partialTick, poseStack, buffers, packedLight, packedOverlay);
            }
        });
    }

    private static void seedCondenserValves(MultiblockDefinition definition, LevelLike level) {
        if (!isCondenserInput(definition)) return;
        seedValve(level, new BlockPos(0, 2, 1), Direction.WEST);
        seedValve(level, new BlockPos(2, 2, 1), Direction.EAST);
        seedValve(level, new BlockPos(1, 2, 0), Direction.NORTH);
        seedValve(level, new BlockPos(1, 2, 2), Direction.SOUTH);
    }

    private static boolean isCondenserInput(MultiblockDefinition definition) {
        return definition.definition().size() == 18
            && isTrapdoor(predicateAt(definition, 1, 2, 1), Half.TOP)
            && isTrapdoor(predicateAt(definition, 1, 0, 1), Half.BOTTOM)
            && isValvePipe(predicateAt(definition, 0, 2, 1), Direction.Axis.X)
            && isValvePipe(predicateAt(definition, 2, 2, 1), Direction.Axis.X)
            && isValvePipe(predicateAt(definition, 1, 2, 0), Direction.Axis.Z)
            && isValvePipe(predicateAt(definition, 1, 2, 2), Direction.Axis.Z);
    }

    private static BlockStatePredicate predicateAt(MultiblockDefinition definition, int x, int y, int z) {
        return definition.definition().get(new Vec3i(x, y, z));
    }

    private static boolean isValvePipe(BlockStatePredicate predicate, Direction.Axis axis) {
        return predicate != null && predicate.testWithoutEntity(pipeState(axis));
    }

    private static boolean isTrapdoor(BlockStatePredicate predicate, Half half) {
        return predicate != null && predicate.testWithoutEntity(trapdoorState(half));
    }

    private static BlockState pipeState(Direction.Axis axis) {
        return ModBlocks.PIPE_STRAIGHT.get().defaultBlockState()
            .setValue(PipeBlock.AXIS, axis)
            .setValue(PipeBlock.HAS_END_START, true)
            .setValue(PipeBlock.HAS_END_END, true)
            .setValue(PipeBlock.HAS_CHECK_VALVE, true)
            .setValue(PipeBlock.WATERLOGGED, false);
    }

    private static BlockState trapdoorState(Half half) {
        return Blocks.COPPER_TRAPDOOR.defaultBlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
            .setValue(BlockStateProperties.OPEN, false)
            .setValue(TrapDoorBlock.HALF, half)
            .setValue(BlockStateProperties.WATERLOGGED, false);
    }

    private static void seedValve(LevelLike level, BlockPos pos, Direction outward) {
        if (level.getBlockEntity(pos) instanceof PipeCheckValveBlockEntity valve) {
            var parent = valve.getLevel();
            valve.setLevel(null);
            valve.setValve(outward, outward);
            valve.setLevel(parent);
        }
    }

    private static void renderLargeCauldronOverlay(
        LargeCauldronBlockEntity cauldron,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource buffers,
        int packedLight,
        int packedOverlay
    ) {
        if (!cauldron.isMainPart() || cauldron.getLevel() == null) return;
        float layerMinY = CAULDRON_MIN_Y;
        for (int tank = 0; tank < cauldron.getFluids().getTanks(); tank++) {
            FluidStack fluid = cauldron.getFluids().getFluidInTank(tank);
            if (fluid.isEmpty()) continue;
            float layerMaxY = layerMinY
                + CAULDRON_CONTENT_HEIGHT * fluid.getAmount() / LargeCauldronFluidHandler.TOTAL_CAPACITY;
            if (isCatalyzingFluid(fluid)) {
                PlasticOilCatalysisRenderer.renderContainerOverlay(
                    cauldron.getLevel(),
                    cauldron.getBlockPos(),
                    partialTick,
                    fluid,
                    CAULDRON_MIN_XZ,
                    layerMinY,
                    CAULDRON_MIN_XZ,
                    CAULDRON_MAX_XZ,
                    layerMaxY,
                    CAULDRON_MAX_XZ,
                    buffers,
                    poseStack,
                    packedLight,
                    true
                );
            }
            layerMinY = layerMaxY;
        }
        if (!cauldron.isIgnited() || !IgnitedFluidEffects.isHighHeatFuel(cauldron.getTopFluid())) return;
        float fill = Mth.clamp(
            (float) cauldron.getFluids().getTotalAmount() / LargeCauldronFluidHandler.TOTAL_CAPACITY,
            0.0F,
            1.0F
        );
        IgnitedFluidFlameRenderer.renderBlue(
            poseStack,
            buffers,
            CAULDRON_MIN_Y + CAULDRON_CONTENT_HEIGHT * fill,
            CAULDRON_FLAME_SCALE,
            packedOverlay
        );
    }

    private static void renderFishTankOverlay(
        FishTankBlockEntity tank,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource buffers,
        int packedLight,
        int packedOverlay
    ) {
        FluidStack fluid = tank.getFluidHandler().getFluid();
        if (tank.getLevel() != null && isCatalyzingFluid(fluid)) {
            float fill = Math.min((float) fluid.getAmount() / tank.getFluidHandler().getCapacity(), 1.0F);
            float surfaceY = TANK_WALL + (1.0F - 2.0F * TANK_WALL) * fill;
            PlasticOilCatalysisRenderer.renderContainerOverlay(
                tank.getLevel(),
                tank.getBlockPos(),
                partialTick,
                fluid,
                TANK_WALL,
                TANK_WALL,
                TANK_WALL,
                1.0F - TANK_WALL,
                surfaceY,
                1.0F - TANK_WALL,
                buffers,
                poseStack,
                packedLight,
                true
            );
        }
        if (!tank.isIgnited() || !fluid.is(PlasticraftFluids.HIGH_HEAT_FUEL.get())) return;
        float fill = Math.min((float) fluid.getAmount() / tank.getFluidHandler().getCapacity(), 1.0F);
        float surfaceY = TANK_WALL + (1.0F - 2.0F * TANK_WALL) * fill;
        IgnitedFluidFlameRenderer.renderBlue(poseStack, buffers, surfaceY, 1.0F, packedOverlay);
    }

    private static boolean isCatalyzingFluid(FluidStack fluid) {
        return fluid.is(PlasticraftFluids.PLASTIC_OIL.get())
            || fluid.is(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get());
    }
}
