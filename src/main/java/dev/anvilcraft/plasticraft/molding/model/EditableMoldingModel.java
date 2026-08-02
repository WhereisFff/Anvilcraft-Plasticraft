package dev.anvilcraft.plasticraft.molding.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 服务端保存的可编辑蓝图，不包含客户端选择和 GPU 状态。 */
public record EditableMoldingModel(
    int formatVersion,
    String name,
    ResourceLocation requestedType,
    List<MoldingElement> elements,
    List<MoldingGroup> groups
) {
    public static final int CURRENT_FORMAT_VERSION = 3;
    public static final int MAX_ELEMENTS = 256;
    public static final int MAX_GROUPS = 128;
    public static final ResourceLocation NORMAL_TYPE = AnvilcraftPlasticraft.of("normal");
    private static final Codec<List<MoldingElement>> ELEMENTS_CODEC = MoldingElement.CODEC.listOf()
        .validate(elements -> elements.size() <= MAX_ELEMENTS
            ? DataResult.success(elements)
            : DataResult.error(() -> "Too many molding elements"));
    private static final Codec<List<MoldingGroup>> GROUPS_CODEC = MoldingGroup.CODEC.listOf()
        .validate(groups -> groups.size() <= MAX_GROUPS
            ? DataResult.success(groups)
            : DataResult.error(() -> "Too many molding groups"));
    public static final Codec<EditableMoldingModel> CODEC = RecordCodecBuilder
        .<EditableMoldingModel>create(instance -> instance.group(
        Codec.INT.fieldOf("format_version").forGetter(model -> model.formatVersion),
        Codec.STRING.optionalFieldOf("name", "Untitled").forGetter(model -> model.name),
        ResourceLocation.CODEC.optionalFieldOf("requested_type", NORMAL_TYPE)
            .forGetter(model -> model.requestedType),
        ELEMENTS_CODEC.optionalFieldOf("elements", List.of()).forGetter(model -> model.elements),
        GROUPS_CODEC.optionalFieldOf("groups", List.of()).forGetter(model -> model.groups)
    ).apply(instance, EditableMoldingModel::new)).validate(EditableMoldingModel::validateCodec);

    public EditableMoldingModel {
        elements = List.copyOf(elements);
        groups = List.copyOf(groups);
        validate(formatVersion, name, elements, groups);
    }

    public static EditableMoldingModel empty() {
        return new EditableMoldingModel(CURRENT_FORMAT_VERSION, "Untitled", NORMAL_TYPE, List.of(), List.of());
    }

    public EditableMoldingModel withElements(List<MoldingElement> replacements) {
        return new EditableMoldingModel(
            this.formatVersion,
            this.name,
            this.requestedType,
            replacements,
            this.groups
        );
    }

    public EditableMoldingModel withGroups(List<MoldingGroup> replacements) {
        return new EditableMoldingModel(
            this.formatVersion,
            this.name,
            this.requestedType,
            this.elements,
            replacements
        );
    }

    public EditableMoldingModel withName(String replacement) {
        return new EditableMoldingModel(
            this.formatVersion,
            replacement,
            this.requestedType,
            this.elements,
            this.groups
        );
    }

    public Map<UUID, MoldingGroup> groupMap() {
        Map<UUID, MoldingGroup> result = new HashMap<>();
        for (MoldingGroup group : this.groups) result.put(group.id(), group);
        return Map.copyOf(result);
    }

    public boolean isEmpty() {
        return this.elements.stream().noneMatch(MoldingElement::visible);
    }

    private static DataResult<EditableMoldingModel> validateCodec(EditableMoldingModel model) {
        try {
            validate(model.formatVersion, model.name, model.elements, model.groups);
            return DataResult.success(model);
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private static void validate(
        int formatVersion,
        String name,
        List<MoldingElement> elements,
        List<MoldingGroup> groups
    ) {
        if (formatVersion != CURRENT_FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported molding model format " + formatVersion);
        }
        if (name.isBlank() || name.length() > MoldingElement.MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Invalid molding model name");
        }
        if (elements.size() > MAX_ELEMENTS || groups.size() > MAX_GROUPS) {
            throw new IllegalArgumentException("Molding model exceeds element or group limit");
        }

        Set<UUID> ids = new HashSet<>();
        for (MoldingGroup group : groups) {
            if (!ids.add(group.id())) throw new IllegalArgumentException("Duplicate molding object id");
        }
        for (MoldingElement element : elements) {
            if (!ids.add(element.id())) throw new IllegalArgumentException("Duplicate molding object id");
        }

        Map<UUID, MoldingGroup> groupMap = new HashMap<>();
        for (MoldingGroup group : groups) groupMap.put(group.id(), group);
        for (MoldingGroup group : groups) {
            group.parentId().ifPresent(parent -> {
                if (!groupMap.containsKey(parent)) throw new IllegalArgumentException("Missing parent group");
            });
            ensureAcyclic(group, groupMap);
        }
        for (MoldingElement element : elements) {
            element.groupId().ifPresent(group -> {
                if (!groupMap.containsKey(group)) throw new IllegalArgumentException("Missing element group");
            });
        }
    }

    private static void ensureAcyclic(MoldingGroup start, Map<UUID, MoldingGroup> groups) {
        Set<UUID> visited = new HashSet<>();
        MoldingGroup current = start;
        while (current.parentId().isPresent()) {
            if (!visited.add(current.id())) throw new IllegalArgumentException("Cyclic molding group hierarchy");
            current = groups.get(current.parentId().orElseThrow());
        }
    }

    public List<MoldingElement> mutableElementCopy() {
        return new ArrayList<>(this.elements);
    }

    public List<MoldingGroup> mutableGroupCopy() {
        return new ArrayList<>(this.groups);
    }
}
