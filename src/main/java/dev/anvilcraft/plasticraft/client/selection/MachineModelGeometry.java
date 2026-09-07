package dev.anvilcraft.plasticraft.client.selection;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.anvilcraft.lib.v2.cube.geometry.ConvexShape;
import dev.anvilcraft.lib.v2.cube.geometry.SelectionGeometry;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.geometry.UnbakedGeometryHelper;
import org.joml.Matrix4d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** 补齐 520 尚未自动处理的复合模型与零厚度面，仅在资源重载阶段读取模型。 */
public final class MachineModelGeometry {
    private final Function<ResourceLocation, JsonObject> resources;
    private final Map<ResourceLocation, JsonObject> json = new HashMap<>();
    private final Map<ResourceLocation, BlockModel> parents = new HashMap<>();
    private final Map<ResourceLocation, List<ConvexShape>> decoded = new HashMap<>();

    public MachineModelGeometry(Function<ResourceLocation, JsonObject> resources) {
        this.resources = resources;
    }

    public List<ConvexShape> load(ResourceLocation id) {
        return this.decoded.computeIfAbsent(id, key -> this.decode(this.json(key), 0));
    }

    private List<ConvexShape> decode(JsonObject object, int depth) {
        if (depth > 32) throw new IllegalArgumentException("Model nesting exceeds 32 levels");
        BlockModel owner = parse(object);
        owner.resolveParents(this::parent);
        JsonObject definition = this.definition(object);
        String loader = definition.has("loader") ? definition.get("loader").getAsString() : "";
        List<ConvexShape> shapes = new ArrayList<>();
        if ("neoforge:composite".equals(loader)) {
            for (Map.Entry<String, JsonElement> child : definition.getAsJsonObject("children").entrySet()) {
                if (owner.customData.isComponentVisible(child.getKey(), true)) {
                    shapes.addAll(this.decode(child.getValue().getAsJsonObject(), depth + 1));
                }
                checkCount(shapes.size());
            }
        } else {
            if (!loader.isEmpty() && !"neoforge:elements".equals(loader)) {
                throw new IllegalArgumentException("Unsupported machine model loader: " + loader);
            }
            checkCount(owner.getElements().size());
            shapes.addAll(PlasticSelectionGeometry.modelElements(owner.getElements()));
        }
        if (!owner.customData.getRootTransform().isIdentity()) {
            ModelState state = UnbakedGeometryHelper.composeRootTransformIntoModelState(
                BlockModelRotation.X0_Y0, owner.customData.getRootTransform()
            );
            double scale = PlasticSelectionGeometry.GEOMETRY_SCALE;
            Matrix4d transform = new Matrix4d().scaling(scale).translate(0.5, 0.5, 0.5)
                .mul(new Matrix4d(state.getRotation().getMatrix())).translate(-0.5, -0.5, -0.5).scale(1 / scale);
            shapes = shapes.stream().map(shape -> shape.transform(transform)).toList();
        }
        return List.copyOf(shapes);
    }

    private JsonObject definition(JsonObject start) {
        JsonObject current = start;
        Set<ResourceLocation> seen = new HashSet<>();
        while (!current.has("elements") && !current.has("loader") && current.has("parent")) {
            ResourceLocation parent = ResourceLocation.parse(current.get("parent").getAsString());
            if (!seen.add(parent) || seen.size() > 32) throw new IllegalArgumentException("Invalid model parent chain");
            current = this.json(parent);
        }
        return current;
    }

    private BlockModel parent(ResourceLocation id) {
        return this.parents.computeIfAbsent(id, key -> parse(this.json(key)));
    }

    private JsonObject json(ResourceLocation id) {
        if (this.json.size() >= 256 && !this.json.containsKey(id)) throw new IllegalArgumentException("Too many model dependencies");
        return this.json.computeIfAbsent(id, this.resources);
    }

    private static BlockModel parse(JsonObject object) {
        JsonObject copy = object.deepCopy();
        // 父模型、元素旋转及根变换仍交给原版解析，复合子模型由上层逐项展开。
        copy.remove("loader");
        copy.remove("children");
        copy.remove("item_render_order");
        return BlockModel.fromString(copy.toString());
    }

    private static void checkCount(int count) {
        if (count > SelectionGeometry.MAX_SHAPES) throw new IllegalArgumentException("Too many machine model cubes");
    }
}
