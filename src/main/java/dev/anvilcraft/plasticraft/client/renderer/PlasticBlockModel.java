package dev.anvilcraft.plasticraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;

import java.util.ArrayList;
import java.util.List;

/** 机器零件以独立模型参与资源重载，实体、物品和编辑器共用烘焙后的四边形。 */
public record PlasticBlockModel(Identifier id, StandaloneModelKey<BlockStateModel> key) {
    public PlasticBlockModel(Identifier id) {
        this(id, new StandaloneModelKey<>(id::toString));
    }

    public void register(ModelEvent.RegisterStandalone event) {
        event.register(this.key, SimpleUnbakedStandaloneModel.blockStateModel(this.id));
    }

    public BlockStateModel baked() {
        return Minecraft.getInstance().getModelManager().getStandaloneModel(this.key);
    }

    public static BlockStateModel of(BlockState state) {
        return Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
    }

    private static List<BakedQuad> quads(BlockStateModel model) {
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(RandomSource.create(42), parts);
        List<BakedQuad> quads = new ArrayList<>();
        for (BlockStateModelPart part : parts) {
            for (Direction face : Direction.values()) {
                quads.addAll(part.getQuads(face));
            }
            quads.addAll(part.getQuads(null));
        }
        return quads;
    }

    public static void render(PoseStack.Pose pose, VertexConsumer vertices, BlockStateModel model,
                              float red, float green, float blue, int light, int overlay) {
        QuadInstance instance = new QuadInstance();
        instance.setLightCoords(light);
        instance.setOverlayCoords(overlay);
        int tint = ARGB.colorFromFloat(1, red, green, blue);
        for (BakedQuad quad : quads(model)) {
            instance.setColor(quad.materialInfo().isTinted() ? tint : -1);
            vertices.putBakedQuad(pose, quad, instance);
        }
    }

    public static void render(BlockState state, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        QuadInstance instance = new QuadInstance();
        instance.setLightCoords(light);
        instance.setOverlayCoords(overlay);
        var colors = Minecraft.getInstance().getBlockColors();
        for (BakedQuad quad : quads(of(state))) {
            var source = quad.materialInfo().isTinted() ? colors.getTintSource(state, quad.materialInfo().tintIndex()) : null;
            instance.setColor(source == null ? -1 : source.color(state));
            buffers.getBuffer(quad.materialInfo().itemRenderType()).putBakedQuad(pose.last(), quad, instance);
        }
    }
}
