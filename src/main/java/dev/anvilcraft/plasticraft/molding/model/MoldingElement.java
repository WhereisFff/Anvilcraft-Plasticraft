package dev.anvilcraft.plasticraft.molding.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;
import java.util.UUID;

/** 一个可编辑 cube；尺寸保留方向，任一尺寸为 0 时表示零厚度 cube。 */
public record MoldingElement(
    UUID id,
    String name,
    Optional<UUID> groupId,
    MoldingVec3 from,
    MoldingVec3 to,
    MoldingTransform transform,
    boolean visible,
    boolean locked
) {
    public static final int MAX_NAME_LENGTH = 64;
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);
    public static final Codec<MoldingElement> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUID_CODEC.fieldOf("id").forGetter(MoldingElement::id),
        Codec.STRING.fieldOf("name").forGetter(MoldingElement::name),
        UUID_CODEC.optionalFieldOf("group").forGetter(MoldingElement::groupId),
        MoldingVec3.CODEC.fieldOf("from").forGetter(MoldingElement::from),
        MoldingVec3.CODEC.fieldOf("to").forGetter(MoldingElement::to),
        MoldingTransform.CODEC.optionalFieldOf("transform", MoldingTransform.IDENTITY)
            .forGetter(MoldingElement::transform),
        Codec.BOOL.optionalFieldOf("visible", true).forGetter(MoldingElement::visible),
        Codec.BOOL.optionalFieldOf("locked", false).forGetter(MoldingElement::locked)
    ).apply(instance, MoldingElement::new));

    public MoldingElement {
        if (name.isBlank() || name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Invalid molding element name");
        }
        double sizeX = to.x() - from.x();
        double sizeY = to.y() - from.y();
        double sizeZ = to.z() - from.z();
        int zeroAxes = (sizeX == 0.0D ? 1 : 0) + (sizeY == 0.0D ? 1 : 0) + (sizeZ == 0.0D ? 1 : 0);
        if (zeroAxes > 1) {
            throw new IllegalArgumentException("Molding cube cannot collapse to a line or point");
        }
    }

    public static MoldingElement cube(String name, MoldingVec3 from, MoldingVec3 to) {
        MoldingVec3 pivot = from.add(to).scale(0.5D);
        return new MoldingElement(
            UUID.randomUUID(),
            name,
            Optional.empty(),
            from,
            to,
            new MoldingTransform(MoldingVec3.ZERO, MoldingVec3.ZERO, MoldingVec3.ONE, pivot),
            true,
            false
        );
    }

    public boolean hasVolume() {
        return this.from.x() != this.to.x()
            && this.from.y() != this.to.y()
            && this.from.z() != this.to.z();
    }

    public MoldingElement withGroup(Optional<UUID> group) {
        return new MoldingElement(
            this.id,
            this.name,
            group,
            this.from,
            this.to,
            this.transform,
            this.visible,
            this.locked
        );
    }

}
