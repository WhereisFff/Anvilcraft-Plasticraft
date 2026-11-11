package dev.anvilcraft.plasticraft.client.selection;

import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.anvilcraft.lib.v2.cube.client.CubeSelection;
import dev.anvilcraft.lib.v2.cube.client.SelectionPart;
import dev.anvilcraft.lib.v2.cube.geometry.ConvexShape;
import dev.anvilcraft.lib.v2.cube.geometry.SelectionGeometry;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.CondenserTowerBlock;
import dev.anvilcraft.plasticraft.block.Plastic3DPrintingComponentBlock;
import dev.anvilcraft.plasticraft.block.PlasticMoldingChamberBlock;
import dev.anvilcraft.plasticraft.client.renderer.blockentity.MachineModelTransforms;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.dubhe.anvilcraft.block.state.Cube3x3PartHalf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MachineBlockSelection {
    private static final AABB MACHINE_BOUNDS = new AABB(-1, -1, -1, 2, 2, 2);
    private static final long MAX_GEOMETRY_BYTES = 16L * 1024 * 1024 - 64 * 1024;
    private static Snapshot models = new Snapshot(Map.of(), Map.of(), null);

    private MachineBlockSelection() {
    }

    public static void register() {
        CubeSelection.registerDynamic(PlasticraftBlocks.PLASTIC_MOLDING_CHAMBER.get(), MACHINE_BOUNDS, false, (level, pos, state, tick) ->
            staticParts(level, pos, state, state.getValue(PlasticMoldingChamberBlock.POWERED) ? Variant.CHAMBER_ON : Variant.CHAMBER_OFF,
                state.getValue(PlasticMoldingChamberBlock.FACING)));
        CubeSelection.registerDynamic(PlasticraftBlocks.PLASTIC_3D_PRINTING_COMPONENT.get(), MACHINE_BOUNDS, false, (level, pos, state, tick) ->
            staticParts(level, pos, state, state.getValue(Plastic3DPrintingComponentBlock.POWERED) ? Variant.PRINTER_ON : Variant.PRINTER_OFF,
                state.getValue(Plastic3DPrintingComponentBlock.FACING)));
        CubeSelection.registerDynamic(PlasticraftBlocks.CONDENSER_TOWER.get(), new AABB(0, 0, 0, 1, 1, 1), false,
            (level, pos, state, tick) -> {
                List<SelectionPart> parts = models.tower().get(state.getValue(CondenserTowerBlock.HALF));
                return parts == null ? fallback(level, pos, state) : parts;
            });
    }

    public static void reload() {
        ResourceManager resources = Minecraft.getInstance().getResourceManager();
        MachineModelGeometry reader = new MachineModelGeometry(id -> {
            ResourceLocation file = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "models/" + id.getPath() + ".json");
            try (Reader input = resources.getResourceOrThrow(file).openAsReader()) {
                return JsonParser.parseReader(input).getAsJsonObject();
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
        models = bake(reader);
    }

    public static @Nullable SelectionPart condenserOutline() {
        return models.towerOutline();
    }

    static Snapshot bake(MachineModelGeometry reader) {
        Map<Variant, Map<Direction, List<SelectionPart>>> parts = new EnumMap<>(Variant.class);
        Map<List<ConvexShape>, SelectionGeometry> shared = new HashMap<>();
        for (Variant variant : Variant.values()) {
            try {
                List<ConvexShape> shapes = new ArrayList<>();
                for (String name : variant.models) shapes.addAll(reader.load(modelId(name)));
                SelectionGeometry geometry = geometry(shared, shapes);
                Map<Direction, List<SelectionPart>> directions = new EnumMap<>(Direction.class);
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    directions.put(facing, geometry == null ? List.of() : List.of(new SelectionPart(geometry,
                        MachineModelTransforms.facing(facing).scale(1 / PlasticSelectionGeometry.GEOMETRY_SCALE))));
                }
                parts.put(variant, Map.copyOf(directions));
            } catch (RuntimeException exception) {
                LogUtils.getLogger().warn("Unable to prepare machine selection for {}; keeping block shape", variant, exception);
            }
        }
        Map<Cube3x3PartHalf, List<SelectionPart>> tower = Map.of();
        SelectionPart towerOutline = null;
        try {
            List<ConvexShape> shapes = new ArrayList<>(reader.load(modelId("condenser_tower")));
            shapes.addAll(reader.load(modelId("condenser_tower_resin")));
            SelectionGeometry whole = geometry(shared, shapes);
            long budget = MAX_GEOMETRY_BYTES - shared.values().stream().mapToLong(SelectionGeometry::estimatedBytes).sum();
            tower = CondenserSelectionGeometry.bake(shapes, budget);
            if (whole != null) {
                towerOutline = new SelectionPart(whole, new Matrix4f().scaling(1 / PlasticSelectionGeometry.GEOMETRY_SCALE));
            }
        } catch (RuntimeException exception) {
            LogUtils.getLogger().warn("Unable to prepare condenser selection; keeping block shapes", exception);
        }
        return new Snapshot(Map.copyOf(parts), tower, towerOutline);
    }

    private static @Nullable SelectionGeometry geometry(Map<List<ConvexShape>, SelectionGeometry> shared, List<ConvexShape> shapes) {
        if (shapes.isEmpty()) return null;
        SelectionGeometry existing = shared.get(shapes);
        if (existing != null) return existing;
        SelectionGeometry built = new SelectionGeometry(shapes, PlasticSelectionGeometry.GEOMETRY_SCALE);
        long bytes = built.estimatedBytes() + shared.values().stream().mapToLong(SelectionGeometry::estimatedBytes).sum();
        if (bytes > MAX_GEOMETRY_BYTES) throw new IllegalArgumentException("Machine selection geometry exceeds memory budget");
        shared.put(built.shapes(), built);
        return built;
    }

    private static List<SelectionPart> staticParts(ClientLevel level, BlockPos pos, BlockState state, Variant variant, Direction facing) {
        Map<Direction, List<SelectionPart>> directions = models.parts().get(variant);
        return directions == null ? fallback(level, pos, state) : directions.get(facing);
    }

    private static List<SelectionPart> fallback(ClientLevel level, BlockPos pos, BlockState state) {
        VoxelShape shape = state.getShape(level, pos);
        return shape.isEmpty() ? List.of() : List.of(PlasticSelectionGeometry.fromShape(shape));
    }

    private static ResourceLocation modelId(String name) {
        return ResourceLocation.fromNamespaceAndPath(AnvilcraftPlasticraft.MOD_ID, "block/" + name);
    }

    enum Variant {
        CHAMBER_ON("plastic_molding_chamber"),
        CHAMBER_OFF("plastic_molding_chamber_off"),
        PRINTER_ON("print_component_body", "print_component_screen", "print_component_eye"),
        PRINTER_OFF("print_component_off");

        private final List<String> models;

        Variant(String... models) {
            this.models = List.of(models);
        }
    }

    record Snapshot(Map<Variant, Map<Direction, List<SelectionPart>>> parts,
                    Map<Cube3x3PartHalf, List<SelectionPart>> tower,
                    @Nullable SelectionPart towerOutline) {
    }
}
