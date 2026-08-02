package dev.anvilcraft.plasticraft.molding.blueprint;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.anvilcraft.plasticraft.molding.bake.MoldingModelBaker;
import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.MoldingCoordinateSystem;
import dev.anvilcraft.plasticraft.molding.model.MoldingElement;
import dev.anvilcraft.plasticraft.molding.model.MoldingGroup;
import dev.anvilcraft.plasticraft.molding.model.MoldingTransform;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** 将规范 JSON、Minecraft Java 模型和 Blockbench cube 域转换为同一源模型。 */
public final class MoldingBlueprintImporter {
    public static final int MAX_UPLOAD_NAME_LENGTH = 128;
    private static final double EPSILON = 1.0E-7D;
    private static final Set<String> NO_SHAPE_PARENTS = Set.of(
        "minecraft:block/block",
        "block/block"
    );
    private static final Set<String> FULL_CUBE_PARENTS = Set.of(
        "minecraft:block/cube",
        "minecraft:block/cube_all",
        "minecraft:block/cube_column",
        "minecraft:block/cube_bottom_top",
        "minecraft:block/orientable",
        "block/cube",
        "block/cube_all",
        "block/cube_column",
        "block/cube_bottom_top",
        "block/orientable"
    );

    private MoldingBlueprintImporter() {
    }

    public static ImportPreview inspect(String filename, String text) throws BlueprintException {
        validateUploadFilename(filename);
        JsonObject root = MoldingBlueprintCodec.parseObject(text);
        EditableMoldingModel model;
        String sourceFormat;
        if (root.has("blueprint_format")) {
            model = MoldingBlueprintCodec.decode(text).model();
            sourceFormat = "plasticraft";
        } else if (filename.toLowerCase(Locale.ROOT).endsWith(".bbmodel") || looksLikeBlockbench(root)) {
            model = importBlockbench(filename, root);
            sourceFormat = "blockbench";
        } else {
            model = importMinecraft(filename, root);
            sourceFormat = "minecraft_java";
        }
        return inspectBounds(model, sourceFormat);
    }

    public static EditableMoldingModel finish(
        ImportPreview preview,
        boolean confirmTranslation,
        MoldingVec3 translation
    ) throws BlueprintException {
        if (preview.translationRequired() && !confirmTranslation) {
            MoldingVec3 suggestion = preview.suggestedTranslation();
            throw new BlueprintException(
                "import_translation_required",
                "Suggested translation: " + format(suggestion.x()) + ", "
                    + format(suggestion.y()) + ", " + format(suggestion.z())
            );
        }
        MoldingVec3 applied = preview.translationRequired() ? translation : MoldingVec3.ZERO;
        EditableMoldingModel result = applied.equals(MoldingVec3.ZERO)
            ? preview.model()
            : translate(preview.model(), applied);
        try {
            MoldingModelBaker.bake(result);
            return result;
        } catch (IllegalArgumentException exception) {
            throw new BlueprintException("import_translation_invalid", exception.getMessage(), exception);
        }
    }

    public static void validateUploadFilename(String filename) throws BlueprintException {
        if (filename == null
            || filename.isBlank()
            || filename.length() > MAX_UPLOAD_NAME_LENGTH
            || filename.indexOf('/') >= 0
            || filename.indexOf('\\') >= 0
            || filename.equals(".")
            || filename.equals("..")) {
            throw new BlueprintException("unsafe_filename", "Upload filename is not safe");
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".json") && !lower.endsWith(".bbmodel")) {
            throw new BlueprintException("unsupported_extension", "Only .json and .bbmodel uploads are accepted");
        }
        for (int index = 0; index < filename.length(); index++) {
            if (Character.isISOControl(filename.charAt(index))) {
                throw new BlueprintException("unsafe_filename", "Upload filename contains a control character");
            }
        }
    }

    private static EditableMoldingModel importMinecraft(String filename, JsonObject root)
        throws BlueprintException {
        List<MoldingElement> elements = new ArrayList<>();
        String parent = optionalString(root, "parent", "");
        boolean hasLocalElements = root.has("elements");
        if (!hasLocalElements
            && !parent.isEmpty()
            && !NO_SHAPE_PARENTS.contains(parent)
            && !FULL_CUBE_PARENTS.contains(parent)) {
            throw new BlueprintException("unsupported_parent", "Unsupported external parent: " + parent);
        }

        if (hasLocalElements) {
            JsonArray values = array(root, "elements");
            if (values.size() > EditableMoldingModel.MAX_ELEMENTS) {
                throw new BlueprintException("too_many_elements", "Minecraft model exceeds 256 elements");
            }
            for (int index = 0; index < values.size(); index++) {
                JsonObject value = object(values.get(index), "Minecraft element " + index);
                MoldingVec3 from = importedVector(value, "from");
                MoldingVec3 to = importedVector(value, "to");
                MoldingTransform transform = MoldingTransform.IDENTITY;
                if (value.has("rotation")) {
                    JsonObject rotation = object(value.get("rotation"), "Minecraft rotation " + index);
                    if (optionalBoolean(rotation, "rescale", false)) {
                        throw new BlueprintException(
                            "unsupported_element",
                            "Element " + index + " uses rotation rescale, which cannot be converted losslessly"
                        );
                    }
                    String axis = requiredString(rotation, "axis");
                    double angle = requiredNumber(rotation, "angle");
                    MoldingVec3 angles = switch (axis) {
                        case "x" -> new MoldingVec3(angle, 0.0D, 0.0D);
                        case "y" -> new MoldingVec3(0.0D, angle, 0.0D);
                        case "z" -> new MoldingVec3(0.0D, 0.0D, angle);
                        default -> throw new BlueprintException("unsupported_element", "Invalid rotation axis: " + axis);
                    };
                    MoldingVec3 origin = rotation.has("origin")
                        ? importedVector(rotation, "origin")
                        : MoldingCoordinateSystem.toModel(new MoldingVec3(8.0D, 8.0D, 8.0D));
                    transform = new MoldingTransform(MoldingVec3.ZERO, angles, MoldingVec3.ONE, origin);
                }
                String name = cleanName(optionalString(value, "name", "Cube " + (index + 1)));
                elements.add(new MoldingElement(
                    deterministicUuid("minecraft", index, value),
                    name,
                    Optional.empty(),
                    from,
                    to,
                    transform,
                    true,
                    false
                ));
            }
        } else if (FULL_CUBE_PARENTS.contains(parent)) {
            elements.add(new MoldingElement(
                UUID.nameUUIDFromBytes("minecraft:inherited-full-cube".getBytes(StandardCharsets.UTF_8)),
                "Cube",
                Optional.empty(),
                MoldingCoordinateSystem.ORIGIN,
                MoldingCoordinateSystem.toModel(new MoldingVec3(16.0D, 16.0D, 16.0D)),
                new MoldingTransform(
                    MoldingVec3.ZERO,
                    MoldingVec3.ZERO,
                    MoldingVec3.ONE,
                    MoldingCoordinateSystem.toModel(new MoldingVec3(8.0D, 8.0D, 8.0D))
                ),
                true,
                false
            ));
        }
        if (elements.isEmpty()) {
            throw new BlueprintException("empty_import", "Minecraft model contains no supported shape elements");
        }
        return new EditableMoldingModel(
            EditableMoldingModel.CURRENT_FORMAT_VERSION,
            modelName(filename, root),
            EditableMoldingModel.NORMAL_TYPE,
            elements,
            List.of()
        );
    }

    private static EditableMoldingModel importBlockbench(String filename, JsonObject root)
        throws BlueprintException {
        rejectNonEmpty(root, "locators", "Blockbench locators");
        JsonArray values = array(root, "elements");
        if (values.size() > EditableMoldingModel.MAX_ELEMENTS) {
            throw new BlueprintException("too_many_elements", "Blockbench model exceeds 256 elements");
        }

        List<MoldingElement> elements = new ArrayList<>();
        Map<String, Integer> rawElementIds = new HashMap<>();
        List<String> unsupported = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            JsonObject value = object(values.get(index), "Blockbench element " + index);
            String type = optionalString(value, "type", value.has("from") && value.has("to") ? "cube" : "unknown");
            if (!type.equals("cube")) {
                unsupported.add("element " + (index + 1) + " (" + type + ")");
                continue;
            }
            if (!value.has("from") || !value.has("to")) {
                unsupported.add("element " + (index + 1) + " (cube without from/to)");
                continue;
            }
            MoldingVec3 from = importedVector(value, "from");
            MoldingVec3 to = importedVector(value, "to");
            MoldingVec3 origin = value.has("origin")
                ? importedVector(value, "origin")
                : from.add(to).scale(0.5D);
            MoldingVec3 rotation = value.has("rotation") ? vector(value, "rotation") : MoldingVec3.ZERO;
            String rawId = optionalString(value, "uuid", "element-" + index);
            UUID id = parseOrDeriveUuid(rawId, "blockbench-element", index, value);
            if (rawElementIds.put(rawId, elements.size()) != null) {
                throw new BlueprintException("invalid_model", "Duplicate Blockbench element UUID: " + rawId);
            }
            elements.add(new MoldingElement(
                id,
                cleanName(optionalString(value, "name", "Cube " + (index + 1))),
                Optional.empty(),
                from,
                to,
                new MoldingTransform(MoldingVec3.ZERO, rotation, MoldingVec3.ONE, origin),
                optionalBoolean(value, "visibility", true),
                optionalBoolean(value, "locked", false)
            ));
        }
        if (!unsupported.isEmpty()) {
            throw new BlueprintException("unsupported_element", "Unsupported Blockbench items: " + String.join(", ", unsupported));
        }
        if (elements.isEmpty()) throw new BlueprintException("empty_import", "Blockbench model contains no cubes");

        List<MoldingGroup> groups = new ArrayList<>();
        Map<String, UUID> rawGroupIds = new HashMap<>();
        Map<String, UUID> elementGroups = new HashMap<>();
        if (root.has("outliner")) {
            parseOutliner(
                array(root, "outliner"),
                Optional.empty(),
                groups,
                rawGroupIds,
                rawElementIds,
                elementGroups,
                0
            );
        }
        List<MoldingElement> grouped = new ArrayList<>(elements.size());
        for (Map.Entry<String, Integer> entry : rawElementIds.entrySet()) {
            MoldingElement element = elements.get(entry.getValue());
            UUID groupId = elementGroups.get(entry.getKey());
            grouped.add(groupId == null ? element : element.withGroup(Optional.of(groupId)));
        }
        grouped.sort((first, second) -> Integer.compare(indexOf(elements, first.id()), indexOf(elements, second.id())));
        return fitFullBlockbenchSpan(new EditableMoldingModel(
            EditableMoldingModel.CURRENT_FORMAT_VERSION,
            modelName(filename, root),
            EditableMoldingModel.NORMAL_TYPE,
            grouped,
            groups
        ));
    }

    private static void parseOutliner(
        JsonArray children,
        Optional<UUID> parentId,
        List<MoldingGroup> groups,
        Map<String, UUID> rawGroupIds,
        Map<String, Integer> rawElementIds,
        Map<String, UUID> elementGroups,
        int depth
    ) throws BlueprintException {
        if (depth > 32) throw new BlueprintException("json_too_deep", "Blockbench group nesting exceeds 32");
        for (JsonElement child : children) {
            if (child.isJsonPrimitive() && child.getAsJsonPrimitive().isString()) {
                String rawElementId = child.getAsString();
                if (!rawElementIds.containsKey(rawElementId)) {
                    throw new BlueprintException("invalid_model", "Outliner references missing element " + rawElementId);
                }
                if (parentId.isPresent() && elementGroups.put(rawElementId, parentId.orElseThrow()) != null) {
                    throw new BlueprintException("invalid_model", "Element occurs in more than one Blockbench group");
                }
                continue;
            }
            JsonObject group = object(child, "Blockbench outliner child");
            if (!group.has("children")) {
                throw new BlueprintException("unsupported_element", "Unsupported object embedded in Blockbench outliner");
            }
            if (groups.size() >= EditableMoldingModel.MAX_GROUPS) {
                throw new BlueprintException("too_many_groups", "Blockbench model exceeds 128 groups");
            }
            String rawId = optionalString(group, "uuid", "group-" + groups.size());
            UUID id = parseOrDeriveUuid(rawId, "blockbench-group", groups.size(), group);
            if (rawGroupIds.put(rawId, id) != null) {
                throw new BlueprintException("invalid_model", "Duplicate Blockbench group UUID: " + rawId);
            }
            MoldingVec3 origin = group.has("origin")
                ? importedVector(group, "origin")
                : MoldingCoordinateSystem.ORIGIN;
            MoldingVec3 rotation = group.has("rotation") ? vector(group, "rotation") : MoldingVec3.ZERO;
            groups.add(new MoldingGroup(
                id,
                cleanName(optionalString(group, "name", "Group " + groups.size())),
                parentId,
                new MoldingTransform(MoldingVec3.ZERO, rotation, MoldingVec3.ONE, origin),
                optionalBoolean(group, "visibility", true),
                optionalBoolean(group, "locked", false)
            ));
            parseOutliner(
                array(group, "children"),
                Optional.of(id),
                groups,
                rawGroupIds,
                rawElementIds,
                elementGroups,
                depth + 1
            );
        }
    }

    private static MoldingVec3 importedVector(JsonObject object, String key) throws BlueprintException {
        return MoldingCoordinateSystem.toModel(vector(object, key));
    }

    private static EditableMoldingModel fitFullBlockbenchSpan(EditableMoldingModel model) {
        MoldingVec3 minimum = null;
        MoldingVec3 maximum = null;
        for (MoldingElement element : model.elements()) {
            for (MoldingVec3 vertex : MoldingModelBaker.transformedVertices(model, element)) {
                minimum = minimum == null ? vertex : minimum.min(vertex);
                maximum = maximum == null ? vertex : maximum.max(vertex);
            }
        }
        if (minimum == null || maximum == null) return model;
        MoldingVec3 offset = new MoldingVec3(
            fullSpanOffset(minimum.x(), maximum.x()),
            fullSpanOffset(minimum.y(), maximum.y()),
            fullSpanOffset(minimum.z(), maximum.z())
        );
        return offset.equals(MoldingVec3.ZERO) ? model : translate(model, offset);
    }

    private static double fullSpanOffset(double minimum, double maximum) {
        if (Math.abs(maximum - minimum - 48.0D) > EPSILON) return 0.0D;
        return minimum < -EPSILON || maximum > 48.0D + EPSILON ? -minimum : 0.0D;
    }

    private static ImportPreview inspectBounds(EditableMoldingModel model, String sourceFormat)
        throws BlueprintException {
        MoldingVec3 minimum = null;
        MoldingVec3 maximum = null;
        for (MoldingElement element : model.elements()) {
            for (MoldingVec3 vertex : MoldingModelBaker.transformedVertices(model, element)) {
                minimum = minimum == null ? vertex : minimum.min(vertex);
                maximum = maximum == null ? vertex : maximum.max(vertex);
            }
        }
        if (minimum == null || maximum == null) {
            throw new BlueprintException("empty_import", "Imported model contains no geometry");
        }
        MoldingVec3 span = maximum.subtract(minimum);
        if (span.x() > 48.0D + EPSILON || span.y() > 48.0D + EPSILON || span.z() > 48.0D + EPSILON) {
            throw new BlueprintException(
                "import_too_large",
                "Imported bounds are " + format(span.x()) + " x " + format(span.y()) + " x " + format(span.z())
            );
        }
        MoldingVec3 suggestion = new MoldingVec3(
            suggestedAxis(minimum.x(), maximum.x()),
            suggestedAxis(minimum.y(), maximum.y()),
            suggestedAxis(minimum.z(), maximum.z())
        );
        boolean required = !suggestion.equals(MoldingVec3.ZERO);
        return new ImportPreview(model, sourceFormat, minimum, maximum, required, suggestion);
    }

    private static EditableMoldingModel translate(EditableMoldingModel model, MoldingVec3 offset) {
        List<MoldingGroup> groups = new ArrayList<>(model.groups().size());
        for (MoldingGroup group : model.groups()) {
            MoldingTransform transform = group.transform();
            groups.add(group.parentId().isPresent() ? group : new MoldingGroup(
                group.id(),
                group.name(),
                group.parentId(),
                withTranslation(transform, transform.translation().add(offset)),
                group.visible(),
                group.locked()
            ));
        }
        List<MoldingElement> elements = new ArrayList<>(model.elements().size());
        for (MoldingElement element : model.elements()) {
            if (element.groupId().isPresent()) {
                elements.add(element);
                continue;
            }
            MoldingTransform transform = element.transform();
            elements.add(new MoldingElement(
                element.id(),
                element.name(),
                element.groupId(),
                element.from(),
                element.to(),
                withTranslation(transform, transform.translation().add(offset)),
                element.visible(),
                element.locked()
            ));
        }
        return new EditableMoldingModel(
            model.formatVersion(),
            model.name(),
            model.requestedType(),
            elements,
            groups
        );
    }

    private static MoldingTransform withTranslation(MoldingTransform transform, MoldingVec3 translation) {
        return new MoldingTransform(translation, transform.rotation(), transform.scale(), transform.pivot());
    }

    private static boolean looksLikeBlockbench(JsonObject root) {
        return root.has("outliner") || root.has("meta") && root.has("elements");
    }

    private static void rejectNonEmpty(JsonObject root, String key, String description) throws BlueprintException {
        if (!root.has(key) || root.get(key).isJsonNull()) return;
        JsonElement value = root.get(key);
        boolean empty = value.isJsonArray() && value.getAsJsonArray().isEmpty()
            || value.isJsonObject() && value.getAsJsonObject().isEmpty();
        if (!empty) throw new BlueprintException("unsupported_element", description + " are not supported");
    }

    private static int indexOf(List<MoldingElement> elements, UUID id) {
        for (int index = 0; index < elements.size(); index++) {
            if (elements.get(index).id().equals(id)) return index;
        }
        return Integer.MAX_VALUE;
    }

    private static UUID parseOrDeriveUuid(String raw, String namespace, int index, JsonObject value) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return deterministicUuid(namespace + ':' + raw, index, value);
        }
    }

    private static UUID deterministicUuid(String namespace, int index, JsonObject value) {
        return UUID.nameUUIDFromBytes(
            (namespace + ':' + index + ':' + value).getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String modelName(String filename, JsonObject root) throws BlueprintException {
        String fallback = filename.substring(0, filename.lastIndexOf('.'));
        return cleanName(optionalString(root, "name", fallback));
    }

    private static String cleanName(String value) throws BlueprintException {
        String cleaned = value.strip();
        if (cleaned.isEmpty()) throw new BlueprintException("invalid_name", "Imported model name is empty");
        return cleaned.length() <= MoldingElement.MAX_NAME_LENGTH
            ? cleaned
            : cleaned.substring(0, MoldingElement.MAX_NAME_LENGTH);
    }

    private static JsonArray array(JsonObject object, String key) throws BlueprintException {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonArray()) {
            throw new BlueprintException("invalid_json", "Expected array field " + key);
        }
        return value.getAsJsonArray();
    }

    private static JsonObject object(JsonElement value, String description) throws BlueprintException {
        if (value == null || !value.isJsonObject()) {
            throw new BlueprintException("invalid_json", description + " must be an object");
        }
        return value.getAsJsonObject();
    }

    private static MoldingVec3 vector(JsonObject object, String key) throws BlueprintException {
        JsonArray value = array(object, key);
        if (value.size() != 3) throw new BlueprintException("invalid_json", key + " must contain three numbers");
        try {
            return new MoldingVec3(value.get(0).getAsDouble(), value.get(1).getAsDouble(), value.get(2).getAsDouble());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new BlueprintException("invalid_json", "Invalid vector field " + key, exception);
        }
    }

    private static String requiredString(JsonObject object, String key) throws BlueprintException {
        if (!object.has(key)) throw new BlueprintException("invalid_json", "Missing field " + key);
        try {
            return object.get(key).getAsString();
        } catch (IllegalStateException exception) {
            throw new BlueprintException("invalid_json", "Invalid string field " + key, exception);
        }
    }

    private static String optionalString(JsonObject object, String key, String fallback) throws BlueprintException {
        if (!object.has(key) || object.get(key).isJsonNull()) return fallback;
        return requiredString(object, key);
    }

    private static double requiredNumber(JsonObject object, String key) throws BlueprintException {
        if (!object.has(key)) throw new BlueprintException("invalid_json", "Missing field " + key);
        try {
            double value = object.get(key).getAsDouble();
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite number");
            return value;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new BlueprintException("invalid_json", "Invalid number field " + key, exception);
        }
    }

    private static boolean optionalBoolean(JsonObject object, String key, boolean fallback)
        throws BlueprintException {
        if (!object.has(key) || object.get(key).isJsonNull()) return fallback;
        try {
            return object.get(key).getAsBoolean();
        } catch (IllegalStateException exception) {
            throw new BlueprintException("invalid_json", "Invalid boolean field " + key, exception);
        }
    }

    private static double suggestedAxis(double minimum, double maximum) {
        if (minimum < -EPSILON) return -minimum;
        if (maximum > 48.0D + EPSILON) return 48.0D - maximum;
        return 0.0D;
    }

    private static String format(double value) {
        if (Math.abs(value - Math.rint(value)) < EPSILON) return Long.toString(Math.round(value));
        return String.format(Locale.ROOT, "%.3f", value);
    }

    public record ImportPreview(
        EditableMoldingModel model,
        String sourceFormat,
        MoldingVec3 minimum,
        MoldingVec3 maximum,
        boolean translationRequired,
        MoldingVec3 suggestedTranslation
    ) {
    }
}
