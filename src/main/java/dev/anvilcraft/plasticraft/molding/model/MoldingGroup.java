package dev.anvilcraft.plasticraft.molding.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;
import java.util.UUID;

/** 可嵌套且可独立变换的元素分组。 */
public record MoldingGroup(
    UUID id,
    String name,
    Optional<UUID> parentId,
    MoldingTransform transform,
    boolean visible,
    boolean locked
) {
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);
    public static final Codec<MoldingGroup> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUID_CODEC.fieldOf("id").forGetter(MoldingGroup::id),
        Codec.STRING.fieldOf("name").forGetter(MoldingGroup::name),
        UUID_CODEC.optionalFieldOf("parent").forGetter(MoldingGroup::parentId),
        MoldingTransform.CODEC.optionalFieldOf("transform", MoldingTransform.IDENTITY)
            .forGetter(MoldingGroup::transform),
        Codec.BOOL.optionalFieldOf("visible", true).forGetter(MoldingGroup::visible),
        Codec.BOOL.optionalFieldOf("locked", false).forGetter(MoldingGroup::locked)
    ).apply(instance, MoldingGroup::new));

    public MoldingGroup {
        if (name.isBlank() || name.length() > MoldingElement.MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Invalid molding group name");
        }
        if (parentId.filter(id::equals).isPresent()) {
            throw new IllegalArgumentException("Molding group cannot parent itself");
        }
    }
}
