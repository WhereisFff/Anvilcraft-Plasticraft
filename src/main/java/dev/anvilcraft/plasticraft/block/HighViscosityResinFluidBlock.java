package dev.anvilcraft.plasticraft.block;

import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.entity.HardenedResinCauldronEntity;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.material.PlasticMaterial;
import dev.dubhe.anvilcraft.block.FishTankBlock;
import dev.dubhe.anvilcraft.block.LargeCauldronBlock;
import dev.dubhe.anvilcraft.block.entity.FishTankBlockEntity;
import dev.dubhe.anvilcraft.block.entity.LargeCauldronBlockEntity;
import dev.dubhe.anvilcraft.util.CauldronUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** 接触后限制实体行动的高粘性树脂流体方块。 */
public class HighViscosityResinFluidBlock extends LiquidBlock {
    private static final Vec3 PLAYER_SPEED = new Vec3(0.25D, 0.05D, 0.25D);
    private static final double FISH_TANK_CONTENT_MIN = 1.0D / 16.0D;
    private static final double FISH_TANK_CONTENT_MAX = 15.0D / 16.0D;
    private static final double FISH_TANK_CONTENT_HEIGHT = 7.0D / 8.0D;

    public HighViscosityResinFluidBlock(FlowingFluid fluid, Properties properties) {
        super(fluid, properties);
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        stickEntity(state, entity);
    }

    public static void stickEntity(BlockState state, Entity entity) {
        if (entity instanceof Player) {
            entity.makeStuckInBlock(state, PLAYER_SPEED);
            return;
        }
        entity.makeStuckInBlock(state, Vec3.ZERO);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.hasImpulse = true;
    }

    /** 在本体容器的真实液面内应用树脂的粘滞效果。 */
    public static void stickEntityInContainer(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (isEntityInsideContainer(state, level, pos, entity)) {
            if (containsPlasticMelt(level, state, pos)) {
                entity.makeStuckInBlock(state, PLAYER_SPEED);
            } else {
                stickEntity(state, entity);
            }
        }
    }

    /** 判断实体是否接触鱼缸或大型炼药锅中的树脂液层。 */
    public static boolean isEntityInsideContainer(
        BlockState state,
        Level level,
        BlockPos pos,
        Entity entity
    ) {
        if (state.getBlock() instanceof FishTankBlock) {
            return isEntityInsideFishTank(level, pos, entity);
        }
        if (state.getBlock() instanceof LargeCauldronBlock) {
            return isEntityInsideLargeCauldron(level, pos, state, entity);
        }
        if (state.getBlock() instanceof UniversalPlasticMeltCauldronBlock cauldron) {
            return cauldron.containsEntity(state, pos, entity);
        }
        return false;
    }

    private static boolean containsPlasticMelt(Level level, BlockState state, BlockPos pos) {
        if (state.getBlock() instanceof FishTankBlock
            && level.getBlockEntity(pos) instanceof FishTankBlockEntity tank) {
            return PlasticMaterial.isMelt(tank.getFluidHandler().getFluid());
        }
        if (state.getBlock() instanceof LargeCauldronBlock) {
            LargeCauldronBlockEntity cauldron = LargeCauldronBlockEntity.getMain(level, pos, state);
            if (cauldron == null) return false;
            IFluidHandler fluids = cauldron.getFluidHandler();
            for (int tank = 0; tank < fluids.getTanks(); tank++) {
                if (PlasticMaterial.isMelt(fluids.getFluidInTank(tank))) return true;
            }
        }
        if (state.getBlock() instanceof HardenedResinCauldronBlock
            && level.getBlockEntity(pos) instanceof BondedEntityBlockEntity bonded
            && bonded.getOrCreateRenderEntity() instanceof HardenedResinCauldronEntity cauldron) {
            return PlasticMaterial.isMelt(cauldron.getFluidHandler().getFluid());
        }
        if (state.getBlock() instanceof UniversalPlasticMeltCauldronBlock) {
            return state.getValue(UniversalPlasticMeltCauldronBlock.LEVEL) > 0;
        }
        return false;
    }

    private static boolean isEntityInsideFishTank(Level level, BlockPos pos, Entity entity) {
        if (!(level.getBlockEntity(pos) instanceof FishTankBlockEntity tank)) return false;
        FluidStack fluid = tank.getFluidHandler().getFluid();
        if (!isHighViscosityResin(fluid)) return false;

        double fill = Mth.clamp(
            (double) fluid.getAmount() / tank.getFluidHandler().getCapacity(),
            0.0D,
            1.0D
        );
        double minX = pos.getX() + FISH_TANK_CONTENT_MIN;
        double minY = pos.getY() + FISH_TANK_CONTENT_MIN;
        double minZ = pos.getZ() + FISH_TANK_CONTENT_MIN;
        double maxX = pos.getX() + FISH_TANK_CONTENT_MAX;
        double maxY = minY + FISH_TANK_CONTENT_HEIGHT * fill;
        double maxZ = pos.getZ() + FISH_TANK_CONTENT_MAX;
        return maxY > minY && new AABB(minX, minY, minZ, maxX, maxY, maxZ)
            .intersects(entity.getBoundingBox());
    }

    private static boolean isEntityInsideLargeCauldron(
        Level level,
        BlockPos pos,
        BlockState state,
        Entity entity
    ) {
        LargeCauldronBlockEntity cauldron = LargeCauldronBlockEntity.getMain(level, pos, state);
        if (cauldron == null) return false;
        IFluidHandler fluids = cauldron.getFluidHandler();
        int totalCapacity = 0;
        for (int tank = 0; tank < fluids.getTanks(); tank++) {
            totalCapacity += fluids.getTankCapacity(tank);
        }
        if (totalCapacity <= 0) return false;

        AABB contentArea = CauldronUtil.getInnerArea(pos, state);
        double layerMinY = contentArea.minY;
        for (int tank = 0; tank < fluids.getTanks(); tank++) {
            FluidStack fluid = fluids.getFluidInTank(tank);
            if (fluid.isEmpty()) continue;
            double layerMaxY = layerMinY
                + contentArea.getYsize() * fluid.getAmount() / totalCapacity;
            if (isHighViscosityResin(fluid)) {
                AABB layerArea = new AABB(
                    contentArea.minX,
                    layerMinY,
                    contentArea.minZ,
                    contentArea.maxX,
                    layerMaxY,
                    contentArea.maxZ
                );
                if (layerArea.intersects(entity.getBoundingBox())) return true;
            }
            layerMinY = layerMaxY;
        }
        return false;
    }

    private static boolean isHighViscosityResin(FluidStack fluid) {
        return !fluid.isEmpty() && (fluid.is(PlasticraftFluids.LIQUID_HIGH_VISCOSITY_RESIN.get())
            || PlasticMaterial.isMelt(fluid));
    }

    public static boolean isEntityTouching(Entity entity) {
        AABB bounds = entity.getBoundingBox().deflate(1.0E-7D);
        int minX = Mth.floor(bounds.minX);
        int minY = Mth.floor(bounds.minY);
        int minZ = Mth.floor(bounds.minZ);
        int maxX = Mth.floor(bounds.maxX);
        int maxY = Mth.floor(bounds.maxY);
        int maxZ = Mth.floor(bounds.maxZ);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.set(x, y, z);
                    BlockState state = entity.level().getBlockState(pos);
                    if (state.is(PlasticraftBlocks.LIQUID_HIGH_VISCOSITY_RESIN.get())) return true;
                    if (state.getBlock() instanceof HighViscosityResinCauldronBlock cauldron
                        && cauldron.containsEntity(state, pos, entity)) {
                        return true;
                    }
                    if (isEntityInsideContainer(state, entity.level(), pos, entity)) return true;
                }
            }
        }
        return false;
    }

    /** 判断实体是否接触塑料熔体；熔体容器中的实体仍由各容器入口施加减速。 */
    public static boolean isPlasticMeltTouching(Entity entity) {
        AABB bounds = entity.getBoundingBox().deflate(1.0E-7D);
        int minX = Mth.floor(bounds.minX);
        int minY = Mth.floor(bounds.minY);
        int minZ = Mth.floor(bounds.minZ);
        int maxX = Mth.floor(bounds.maxX);
        int maxY = Mth.floor(bounds.maxY);
        int maxZ = Mth.floor(bounds.maxZ);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.set(x, y, z);
                    BlockState state = entity.level().getBlockState(pos);
                    if (PlasticMaterial.fromMeltBlock(state).isPresent()
                        || (PlasticMaterial.fromMeltCauldron(state).isPresent()
                            && isEntityInsideContainer(state, entity.level(), pos, entity))) {
                        return true;
                    }
                    if (state.getBlock() instanceof HardenedResinCauldronBlock
                        && entity.level().getBlockEntity(pos) instanceof BondedEntityBlockEntity bonded
                        && bonded.getOrCreateRenderEntity() instanceof HardenedResinCauldronEntity cauldron
                        && cauldron.plasticraft$isEntityInsidePlasticMelt(entity)) return true;
                    if (containsPlasticMelt(entity.level(), state, pos)
                        && isEntityInsideContainer(state, entity.level(), pos, entity)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
