package dev.anvilcraft.plasticraft.entity.collision;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.Objects;

/** 内置塑料实体的服务端模型 Cube 与由其派生的真实凸碰撞。 */
public final class BuiltInPlasticEntityModels {
    private static final double PIXEL_SCALE = 1.0D / 16.0D;

    public static final VoxelShape UNIVERSAL_PLASTIC_COMPATIBILITY = Block.box(
        0.0D, 0.0D, 0.0D, 16.0D, 14.0D, 16.0D
    );
    public static final VoxelShape ROYAL_ANVIL_COMPATIBILITY = Shapes.or(
        Block.box(2.0D, 0.0D, 2.0D, 14.0D, 4.0D, 14.0D),
        Block.box(5.0D, 4.0D, 4.0D, 11.0D, 10.0D, 12.0D),
        Block.box(3.0D, 10.0D, 0.0D, 13.0D, 16.0D, 16.0D)
    );
    public static final VoxelShape HARDENED_RESIN_CAULDRON_COMPATIBILITY = Blocks.CAULDRON
        .defaultBlockState()
        .getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());
    public static final VoxelShape CATALYTIC_PRESS_LID_COMPATIBILITY = Shapes.or(
        Block.box(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D),
        Block.box(1.0D, 8.0D, 1.0D, 15.0D, 13.0D, 15.0D),
        Block.box(2.0D, 13.0D, 2.0D, 14.0D, 16.0D, 14.0D)
    );

    public static final Model UNIVERSAL_PLASTIC = create(
        "universal_plastic",
        UNIVERSAL_PLASTIC_COMPATIBILITY,
        List.of(cube(0.0D, 0.0D, 0.0D, 16.0D, 14.0D, 16.0D))
    );
    public static final Model RESIN_ANVIL = create(
        "resin_anvil",
        ROYAL_ANVIL_COMPATIBILITY,
        List.of(
            cube(3.5D, 10.5D, 0.5D, 12.5D, 15.5D, 15.5D),
            cube(2.5D, 0.5D, 2.5D, 13.5D, 3.5D, 13.5D),
            cube(13.0D, 16.0D, 16.0D, 3.0D, 10.0D, 0.0D),
            cube(14.0D, 4.0D, 14.0D, 2.0D, 0.0D, 2.0D),
            cube(10.6D, 9.6D, 11.5D, 5.4D, 4.4D, 4.5D),
            cube(6.0D, 3.5D, 6.0D, 10.0D, 10.5D, 10.0D),
            cube(5.0D, 3.5D, 4.25D, 11.0D, 10.5D, 6.0D),
            cube(5.0D, 3.5D, 10.0D, 11.0D, 10.5D, 12.0D)
        )
    );
    public static final Model HARDENED_RESIN_ANVIL = create(
        "hardend_resin_anvil",
        ROYAL_ANVIL_COMPATIBILITY,
        List.of(
            cube(3.0D, 10.0D, 0.0D, 13.0D, 16.0D, 16.0D),
            cube(2.0D, 0.0D, 2.0D, 14.0D, 4.0D, 14.0D),
            cube(6.0D, 4.0D, 6.0D, 10.0D, 10.0D, 10.0D),
            cube(5.0D, 4.0D, 4.0D, 11.0D, 10.0D, 6.0D),
            cube(5.0D, 4.0D, 10.0D, 11.0D, 10.0D, 12.0D)
        )
    );

    private static final List<PlasticModelCube> HARDENED_RESIN_CAULDRON_CUBES = List.of(
        cube(14.0D, 0.0D, 12.0D, 16.0D, 3.0D, 14.0D),
        cube(0.0D, 3.0D, 0.0D, 2.0D, 16.0D, 16.0D),
        cube(2.0D, 3.0D, 2.0D, 14.0D, 4.0D, 14.0D),
        cube(14.0D, 3.0D, 0.0D, 16.0D, 16.0D, 16.0D),
        cube(2.0D, 3.0D, 0.0D, 14.0D, 16.0D, 2.0D),
        cube(2.0D, 3.0D, 14.0D, 14.0D, 16.0D, 16.0D),
        cube(0.0D, 0.0D, 0.0D, 4.0D, 3.0D, 2.0D),
        cube(0.0D, 0.0D, 2.0D, 2.0D, 3.0D, 4.0D),
        cube(12.0D, 0.0D, 0.0D, 16.0D, 3.0D, 2.0D),
        cube(14.0D, 0.0D, 2.0D, 16.0D, 3.0D, 4.0D),
        cube(0.0D, 0.0D, 14.0D, 4.0D, 3.0D, 16.0D),
        cube(0.0D, 0.0D, 12.0D, 2.0D, 3.0D, 14.0D),
        cube(12.0D, 0.0D, 14.0D, 16.0D, 3.0D, 16.0D)
    );
    public static final Model HARDENED_RESIN_CAULDRON = create(
        "hardend_resin_cauldron",
        HARDENED_RESIN_CAULDRON_COMPATIBILITY,
        HARDENED_RESIN_CAULDRON_CUBES
    );
    public static final PlasticEntityGeometry HARDENED_RESIN_CAULDRON_ENTRY = createGeometry(
        Shapes.join(
            HARDENED_RESIN_CAULDRON_COMPATIBILITY,
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 4.0D, 16.0D),
            BooleanOp.AND
        ).optimize(),
        HARDENED_RESIN_CAULDRON_CUBES.stream()
            .filter(modelCube -> Math.max(modelCube.from().y, modelCube.to().y) <= 4.0D)
            .toList(),
        HARDENED_RESIN_CAULDRON.geometry().rotationPivot(),
        HARDENED_RESIN_CAULDRON.geometry().entityOrigin()
    );

    public static final Model CATALYTIC_PRESS_LID = create(
        "catalytic_press_lid",
        CATALYTIC_PRESS_LID_COMPATIBILITY,
        List.of(
            cube(0.0D, 1.0D, 0.0D, 16.0D, 3.0D, 16.0D),
            cube(5.0D, 3.0D, 5.0D, 11.0D, 14.0D, 11.0D),
            cube(3.0D, 14.0D, 3.0D, 13.0D, 16.0D, 13.0D),
            cube(-1.0D, -1.0D, 1.0D, 1.0D, 1.0D, 17.0D),
            cube(-1.0D, -1.0D, -1.0D, 15.0D, 1.0D, 1.0D),
            cube(15.0D, -1.0D, -1.0D, 17.0D, 1.0D, 15.0D),
            cube(1.0D, -1.0D, 15.0D, 17.0D, 1.0D, 17.0D),
            rotatedCube(-0.07107D, 2.5D, -0.07107D, 8.92893D, 14.5D, 1.92893D,
                Direction.Axis.Y, -45.0D, 0.92893D, 4.5D, 0.92893D),
            rotatedCube(7.07107D, 2.5D, -0.07107D, 16.07107D, 14.5D, 1.92893D,
                Direction.Axis.Y, 45.0D, 15.07107D, 4.5D, 0.92893D),
            rotatedCube(-0.07107D, 2.5D, 14.07107D, 8.92893D, 14.5D, 16.07107D,
                Direction.Axis.Y, 45.0D, 0.92893D, 4.5D, 15.07107D),
            rotatedCube(7.07107D, 2.5D, 14.07107D, 16.07107D, 14.5D, 16.07107D,
                Direction.Axis.Y, -45.0D, 15.07107D, 4.5D, 15.07107D),
            rotatedCube(8.92893D, 14.5D, 1.92893D, -0.07107D, 2.5D, -0.07107D,
                Direction.Axis.Y, -45.0D, 0.92893D, 4.5D, 0.92893D),
            rotatedCube(16.07107D, 14.5D, 1.92893D, 7.07107D, 2.5D, -0.07107D,
                Direction.Axis.Y, 45.0D, 15.07107D, 4.5D, 0.92893D),
            rotatedCube(16.07107D, 14.5D, 16.07107D, 7.07107D, 2.5D, 14.07107D,
                Direction.Axis.Y, -45.0D, 15.07107D, 4.5D, 15.07107D),
            rotatedCube(8.92893D, 14.5D, 16.07107D, -0.07107D, 2.5D, 14.07107D,
                Direction.Axis.Y, 45.0D, 0.92893D, 4.5D, 15.07107D),
            rotatedCube(9.79617D, 3.04858D, 6.0D, 15.79617D, 6.04858D, 10.0D,
                Direction.Axis.Z, -22.5D, 8.0D, 2.56194D, 8.0D),
            rotatedCube(0.20383D, 3.04858D, 6.0D, 6.20383D, 6.04858D, 10.0D,
                Direction.Axis.Z, 22.5D, 8.0D, 2.56194D, 8.0D),
            rotatedCube(6.0D, 3.04858D, 0.20383D, 10.0D, 6.04858D, 6.20383D,
                Direction.Axis.X, -22.5D, 8.0D, 2.56194D, 8.0D),
            rotatedCube(6.0D, 3.04858D, 9.79617D, 10.0D, 6.04858D, 15.79617D,
                Direction.Axis.X, 22.5D, 8.0D, 2.56194D, 8.0D)
        )
    );

    private BuiltInPlasticEntityModels() {
    }

    private static Model create(String resourceName, VoxelShape compatibility, List<PlasticModelCube> cubes) {
        List<PlasticModelCube> sourceCubes = List.copyOf(cubes);
        return new Model(resourceName, sourceCubes, createGeometry(compatibility, sourceCubes));
    }

    private static PlasticEntityGeometry createGeometry(
        VoxelShape compatibility,
        List<PlasticModelCube> cubes
    ) {
        Vec3 pivot = compatibility.bounds().getCenter();
        Vec3 origin = new Vec3(pivot.x, compatibility.bounds().minY, pivot.z);
        return createGeometry(compatibility, cubes, pivot, origin);
    }

    private static PlasticEntityGeometry createGeometry(
        VoxelShape compatibility,
        List<PlasticModelCube> cubes,
        Vec3 rotationPivot,
        Vec3 entityOrigin
    ) {
        List<PlasticConvexShape> convexShapes = cubes.stream()
            .map(modelCube -> modelCube.convexShape(PIXEL_SCALE))
            .toList();
        VoxelShape interaction = Shapes.empty();
        for (PlasticConvexShape shape : convexShapes) {
            interaction = Shapes.or(interaction, Shapes.create(shape.bounds()));
        }
        return PlasticEntityGeometry.of(
            compatibility,
            interaction.optimize(),
            convexShapes,
            rotationPivot,
            entityOrigin
        );
    }

    private static PlasticModelCube cube(
        double fromX,
        double fromY,
        double fromZ,
        double toX,
        double toY,
        double toZ
    ) {
        return PlasticModelCube.axisAligned(
            new Vec3(fromX, fromY, fromZ),
            new Vec3(toX, toY, toZ)
        );
    }

    private static PlasticModelCube rotatedCube(
        double fromX,
        double fromY,
        double fromZ,
        double toX,
        double toY,
        double toZ,
        Direction.Axis axis,
        double degrees,
        double originX,
        double originY,
        double originZ
    ) {
        return PlasticModelCube.rotated(
            new Vec3(fromX, fromY, fromZ),
            new Vec3(toX, toY, toZ),
            axis,
            degrees,
            new Vec3(originX, originY, originZ)
        );
    }

    public record Model(String resourceName, List<PlasticModelCube> cubes, PlasticEntityGeometry geometry) {
        public Model {
            Objects.requireNonNull(resourceName, "resourceName");
            cubes = List.copyOf(cubes);
            Objects.requireNonNull(geometry, "geometry");
        }
    }
}
