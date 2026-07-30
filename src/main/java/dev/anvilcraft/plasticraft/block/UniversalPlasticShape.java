package dev.anvilcraft.plasticraft.block;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.api.texture.PlasticSurface;
import dev.anvilcraft.plasticraft.api.texture.PlasticTextureGenerator;
import dev.anvilcraft.plasticraft.api.texture.PlasticTextureInput;
import dev.anvilcraft.plasticraft.api.texture.PlasticTextureLayout;
import dev.anvilcraft.plasticraft.entity.collision.PlasticEntityGeometry;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/** 通用塑料块的固定几何，以及送入公共贴图生成器的表面描述。 */
public final class UniversalPlasticShape {
    public static final VoxelShape COLLISION = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 14.0D, 16.0D);
    public static final PlasticEntityGeometry GEOMETRY = PlasticEntityGeometry.of(COLLISION);
    public static final List<PlasticSurface> SURFACES = List.of(
        surface(
            Direction.DOWN,
            point(0, 0, 16), point(16, 0, 16), point(16, 0, 0), point(0, 0, 0),
            16, 16, 0, 2
        ),
        surface(
            Direction.UP,
            point(0, 14, 0), point(16, 14, 0), point(16, 14, 16), point(0, 14, 16),
            16, 16, 0, 0
        ),
        surface(
            Direction.NORTH,
            point(16, 0, 0), point(16, 14, 0), point(0, 14, 0), point(0, 0, 0),
            16, 14, 0, 1
        ),
        surface(
            Direction.SOUTH,
            point(0, 0, 16), point(0, 14, 16), point(16, 14, 16), point(16, 0, 16),
            16, 14, 0, 1
        ),
        surface(
            Direction.WEST,
            point(0, 0, 0), point(0, 14, 0), point(0, 14, 16), point(0, 0, 16),
            16, 14, 0, 1
        ),
        surface(
            Direction.EAST,
            point(16, 0, 16), point(16, 14, 16), point(16, 14, 0), point(16, 0, 0),
            16, 14, 0, 1
        )
    );
    public static final String SHAPE_HASH = PlasticTextureInput.computeShapeHash(SURFACES);
    public static final PlasticTextureLayout TEXTURE_LAYOUT = PlasticTextureGenerator.layout(SURFACES);

    private UniversalPlasticShape() {
    }

    public static PlasticTextureLayout.UvRegion uv(Direction direction) {
        PlasticTextureLayout.UvRegion region = TEXTURE_LAYOUT.regions().get(direction.getName());
        if (region == null) {
            throw new IllegalArgumentException("Universal plastic surface is unavailable: " + direction.getName());
        }
        return region;
    }

    /** 所有调用方共用同一精灵命名，防止模型与图集源各自拼接资源路径。 */
    public static ResourceLocation sprite(DyeColor color) {
        return AnvilcraftPlasticraft.of("block/universal_plastic/" + color.getName());
    }

    private static PlasticSurface surface(
        Direction direction,
        PlasticSurface.Point first,
        PlasticSurface.Point second,
        PlasticSurface.Point third,
        PlasticSurface.Point fourth,
        int width,
        int height,
        int grooveShade,
        int ambientOcclusionShade
    ) {
        return new PlasticSurface(
            direction.getName(),
            List.of(first, second, third, fourth),
            point(direction.getStepX(), direction.getStepY(), direction.getStepZ()),
            width,
            height,
            false,
            grooveShade,
            ambientOcclusionShade
        );
    }

    private static PlasticSurface.Point point(int x, int y, int z) {
        return new PlasticSurface.Point(x, y, z);
    }
}
