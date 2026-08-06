package dev.anvilcraft.plasticraft.molding.product;

import dev.anvilcraft.plasticraft.api.texture.PlasticSurface;
import dev.anvilcraft.plasticraft.api.texture.PlasticTextureGenerator;
import dev.anvilcraft.plasticraft.api.texture.PlasticTextureLayout;
import dev.anvilcraft.plasticraft.molding.bake.MoldingQuad;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;

import java.util.ArrayList;
import java.util.List;

/** 把制造网格表面转换为公共程序化贴图 API 的输入。 */
public final class MoldedPlasticSurfaceAdapter {
    public static final int MAX_SURFACES = 16_384;

    private MoldedPlasticSurfaceAdapter() {
    }

    public static List<PlasticSurface> adapt(List<MoldingQuad> quads) {
        return adaptWithLayout(quads).surfaces();
    }

    public static AdaptedSurfaces adaptWithLayout(List<MoldingQuad> quads) {
        if (quads.isEmpty() || quads.size() > MAX_SURFACES) {
            throw new IllegalArgumentException("Invalid manufactured surface count: " + quads.size());
        }
        List<PlasticSurface> surfaces = new ArrayList<>(quads.size());
        for (int index = 0; index < quads.size(); index++) {
            MoldingQuad quad = quads.get(index);
            surfaces.add(new PlasticSurface(
                "surface_" + index,
                List.of(
                    point(quad.first()),
                    point(quad.second()),
                    point(quad.third()),
                    point(quad.fourth())
                ),
                point(quad.normal()),
                pixelLength(quad.first(), quad.second()),
                pixelLength(quad.second(), quad.third()),
                quad.doubleSided(),
                0,
                0
            ));
        }
        List<PlasticSurface> result = List.copyOf(surfaces);
        return new AdaptedSurfaces(result, PlasticTextureGenerator.layout(result));
    }

    private static int pixelLength(MoldingVec3 first, MoldingVec3 second) {
        MoldingVec3 difference = second.subtract(first);
        return Math.max(1, (int) Math.ceil(Math.sqrt(difference.lengthSquared())));
    }

    private static PlasticSurface.Point point(MoldingVec3 value) {
        return new PlasticSurface.Point(value.x(), value.y(), value.z());
    }

    public record AdaptedSurfaces(List<PlasticSurface> surfaces, PlasticTextureLayout textureLayout) {
        public AdaptedSurfaces {
            surfaces = List.copyOf(surfaces);
        }
    }
}
