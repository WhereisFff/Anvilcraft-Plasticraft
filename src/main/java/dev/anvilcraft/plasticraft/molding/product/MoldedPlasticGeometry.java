package dev.anvilcraft.plasticraft.molding.product;

import dev.anvilcraft.plasticraft.entity.collision.PlasticConvexShape;
import dev.anvilcraft.plasticraft.entity.collision.PlasticEntityGeometry;
import dev.anvilcraft.plasticraft.molding.bake.MoldingConvexHull;
import dev.anvilcraft.plasticraft.molding.bake.MoldingQuad;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** 从持久化制造形状创建真实物理轮廓和独立交互轮廓。 */
public final class MoldedPlasticGeometry {
    private static final double PIXELS_PER_BLOCK = 16.0D;
    private static final double INTERACTION_EPSILON_PIXELS = 1.0D / 64.0D;
    private static final int CACHE_LIMIT = 128;
    private static final Map<MoldedPlasticData.ShapeKey, PlasticEntityGeometry> CACHE = new LinkedHashMap<>(
        CACHE_LIMIT,
        0.75F,
        true
    ) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<MoldedPlasticData.ShapeKey, PlasticEntityGeometry> eldest) {
            return this.size() > CACHE_LIMIT;
        }
    };

    private MoldedPlasticGeometry() {
    }

    public static PlasticEntityGeometry get(MoldedPlasticData data) {
        synchronized (CACHE) {
            return CACHE.computeIfAbsent(data.shapeKey(), ignored -> create(data));
        }
    }

    public static void clear() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    private static PlasticEntityGeometry create(MoldedPlasticData data) {
        VoxelShape collision = Shapes.empty();
        for (MoldingConvexHull hull : data.collisionHulls()) {
            MoldingConvexHull.Bounds box = hull.bounds();
            collision = Shapes.joinUnoptimized(collision, Shapes.box(
                box.minimum().x() / PIXELS_PER_BLOCK,
                box.minimum().y() / PIXELS_PER_BLOCK,
                box.minimum().z() / PIXELS_PER_BLOCK,
                box.maximum().x() / PIXELS_PER_BLOCK,
                box.maximum().y() / PIXELS_PER_BLOCK,
                box.maximum().z() / PIXELS_PER_BLOCK
            ), BooleanOp.OR);
        }
        VoxelShape additionalSelection = Shapes.empty();
        for (MoldingQuad quad : data.zeroThicknessQuads()) {
            additionalSelection = Shapes.joinUnoptimized(additionalSelection, interactionShape(quad), BooleanOp.OR);
        }
        VoxelShape interaction = Shapes.joinUnoptimized(collision, additionalSelection, BooleanOp.OR);
        List<PlasticConvexShape> convexShapes = data.collisionHulls().stream()
            .map(hull -> PlasticConvexShape.fromMolding(hull, 1.0D / PIXELS_PER_BLOCK))
            .toList();
        return PlasticEntityGeometry.of(
            collision.optimize(),
            interaction.optimize(),
            convexShapes,
            toBlocks(data.rotationPivot()),
            toBlocks(data.entityOrigin()),
            additionalSelection
        );
    }

    private static Vec3 toBlocks(MoldingVec3 point) {
        return new Vec3(
            point.x() / PIXELS_PER_BLOCK,
            point.y() / PIXELS_PER_BLOCK,
            point.z() / PIXELS_PER_BLOCK
        );
    }

    private static VoxelShape interactionShape(MoldingQuad quad) {
        MoldingVec3 minimum = quad.first().min(quad.second()).min(quad.third()).min(quad.fourth());
        MoldingVec3 maximum = quad.first().max(quad.second()).max(quad.third()).max(quad.fourth());
        double minX = interactionMinimum(minimum.x(), maximum.x());
        double minY = interactionMinimum(minimum.y(), maximum.y());
        double minZ = interactionMinimum(minimum.z(), maximum.z());
        double maxX = interactionMaximum(minimum.x(), maximum.x());
        double maxY = interactionMaximum(minimum.y(), maximum.y());
        double maxZ = interactionMaximum(minimum.z(), maximum.z());
        return Shapes.box(
            minX / PIXELS_PER_BLOCK,
            minY / PIXELS_PER_BLOCK,
            minZ / PIXELS_PER_BLOCK,
            maxX / PIXELS_PER_BLOCK,
            maxY / PIXELS_PER_BLOCK,
            maxZ / PIXELS_PER_BLOCK
        );
    }

    private static double interactionMinimum(double minimum, double maximum) {
        return maximum - minimum < INTERACTION_EPSILON_PIXELS
            ? minimum - INTERACTION_EPSILON_PIXELS * 0.5D
            : minimum;
    }

    private static double interactionMaximum(double minimum, double maximum) {
        return maximum - minimum < INTERACTION_EPSILON_PIXELS
            ? maximum + INTERACTION_EPSILON_PIXELS * 0.5D
            : maximum;
    }
}
