package dev.anvilcraft.plasticraft.molding.blueprint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** 规范蓝图 JSON 的稳定字段顺序与输入边界。 */
public final class MoldingBlueprintCodec {
    public static final int MAX_TEXT_BYTES = 1024 * 1024;
    public static final int MAX_JSON_DEPTH = 64;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private MoldingBlueprintCodec() {
    }

    public static String encode(MoldingBlueprint blueprint) {
        JsonObject root = new JsonObject();
        root.addProperty("blueprint_format", blueprint.formatVersion());
        root.addProperty("file_revision", blueprint.fileRevision());
        root.addProperty("name", blueprint.name());

        JsonObject owner = new JsonObject();
        owner.addProperty("uuid", blueprint.ownerId().toString());
        owner.addProperty("name", blueprint.ownerName());
        root.add("owner", owner);

        JsonObject workspace = new JsonObject();
        workspace.addProperty("coordinate_system", blueprint.coordinateSystem());
        workspace.addProperty("units_per_block", 16);
        JsonArray size = new JsonArray();
        size.add(48);
        size.add(48);
        size.add(48);
        workspace.add("size", size);
        root.add("workspace", workspace);

        root.addProperty("type_suggestion", blueprint.model().requestedType().toString());
        root.addProperty("created_at", blueprint.createdAt());
        root.addProperty("updated_at", blueprint.updatedAt());
        root.addProperty("bake_version", blueprint.bakeVersion());
        root.addProperty("model_hash", blueprint.modelHash());
        root.add("model", EditableMoldingModel.CODEC.encodeStart(JsonOps.INSTANCE, blueprint.model())
            .getOrThrow(message -> new IllegalArgumentException("Unable to encode blueprint model: " + message)));
        return GSON.toJson(root) + "\n";
    }

    public static MoldingBlueprint decode(String text) throws BlueprintException {
        JsonObject root = parseObject(text);
        try {
            int format = required(root, "blueprint_format").getAsInt();
            long revision = required(root, "file_revision").getAsLong();
            String name = required(root, "name").getAsString();
            JsonObject owner = required(root, "owner").getAsJsonObject();
            UUID ownerId = UUID.fromString(required(owner, "uuid").getAsString());
            String ownerName = required(owner, "name").getAsString();
            JsonObject workspace = required(root, "workspace").getAsJsonObject();
            String coordinates = required(workspace, "coordinate_system").getAsString();
            if (required(workspace, "units_per_block").getAsInt() != 16) {
                throw new IllegalArgumentException("Unsupported blueprint unit scale");
            }
            validateWorkspaceSize(required(workspace, "size").getAsJsonArray());
            long created = required(root, "created_at").getAsLong();
            long updated = required(root, "updated_at").getAsLong();
            int bakeVersion = required(root, "bake_version").getAsInt();
            String hash = required(root, "model_hash").getAsString();
            EditableMoldingModel model = decodeModel(required(root, "model"));
            String typeSuggestion = required(root, "type_suggestion").getAsString();
            if (!model.requestedType().toString().equals(typeSuggestion)) {
                throw new IllegalArgumentException("Blueprint type suggestion does not match its model");
            }
            return new MoldingBlueprint(
                format,
                revision,
                name,
                ownerId,
                ownerName,
                coordinates,
                model,
                created,
                updated,
                bakeVersion,
                hash
            );
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new BlueprintException("invalid_blueprint", exception.getMessage(), exception);
        }
    }

    public static JsonObject parseObject(String text) throws BlueprintException {
        validateText(text);
        try {
            JsonElement parsed = JsonParser.parseString(text);
            if (!parsed.isJsonObject()) throw new JsonParseException("Blueprint root must be an object");
            return parsed.getAsJsonObject();
        } catch (JsonParseException exception) {
            throw new BlueprintException("invalid_json", exception.getMessage(), exception);
        }
    }

    public static EditableMoldingModel decodeModel(JsonElement element) throws BlueprintException {
        try {
            return EditableMoldingModel.CODEC.parse(JsonOps.INSTANCE, element)
                .getOrThrow(message -> new IllegalArgumentException("Unable to decode blueprint model: " + message));
        } catch (IllegalArgumentException exception) {
            throw new BlueprintException("invalid_model", exception.getMessage(), exception);
        }
    }

    public static void validateText(String text) throws BlueprintException {
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_TEXT_BYTES) {
            throw new BlueprintException("file_too_large", "Blueprint text exceeds 1 MiB");
        }
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (quoted) {
                if (escaped) escaped = false;
                else if (character == '\\') escaped = true;
                else if (character == '"') quoted = false;
                continue;
            }
            if (character == '"') quoted = true;
            else if (character == '{' || character == '[') {
                if (++depth > MAX_JSON_DEPTH) {
                    throw new BlueprintException("json_too_deep", "Blueprint JSON exceeds depth 64");
                }
            } else if (character == '}' || character == ']') {
                if (--depth < 0) throw new BlueprintException("invalid_json", "Unbalanced JSON delimiters");
            } else if (character == 0) {
                throw new BlueprintException("invalid_json", "Blueprint JSON contains a NUL character");
            }
        }
        if (quoted || depth != 0) throw new BlueprintException("invalid_json", "Incomplete JSON text");
    }

    private static JsonElement required(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull()) throw new IllegalArgumentException("Missing blueprint field " + key);
        return value;
    }

    private static void validateWorkspaceSize(JsonArray size) {
        if (size.size() != 3
            || size.get(0).getAsInt() != 48
            || size.get(1).getAsInt() != 48
            || size.get(2).getAsInt() != 48) {
            throw new IllegalArgumentException("Unsupported blueprint workspace size");
        }
    }
}
