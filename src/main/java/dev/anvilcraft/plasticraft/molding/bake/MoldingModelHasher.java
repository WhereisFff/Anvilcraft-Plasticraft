package dev.anvilcraft.plasticraft.molding.bake;

import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.MoldingElement;
import dev.anvilcraft.plasticraft.molding.model.MoldingGroup;
import dev.anvilcraft.plasticraft.molding.model.MoldingTransform;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** 生成与集合迭代顺序和 JSON/NBT 实现无关的 SHA-256 模型哈希。 */
public final class MoldingModelHasher {
    private static final double PRECISION = 1_000_000.0D;
    private static final int CUBE_ELEMENT_TYPE = 0;

    private MoldingModelHasher() {
    }

    public static String hash(EditableMoldingModel model, int bakeVersion) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(model.formatVersion());
                output.writeInt(bakeVersion);
                output.writeUTF(model.requestedType().toString());

                List<MoldingGroup> groups = model.groups().stream()
                    .sorted(Comparator.comparing(group -> group.id().toString()))
                    .toList();
                output.writeInt(groups.size());
                for (MoldingGroup group : groups) writeGroup(output, group);

                List<MoldingElement> elements = model.elements().stream()
                    .sorted(Comparator.comparing(element -> element.id().toString()))
                    .toList();
                output.writeInt(elements.size());
                for (MoldingElement element : elements) writeElement(output, element);
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes.toByteArray()));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to hash molding model", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void writeGroup(DataOutputStream output, MoldingGroup group) throws IOException {
        writeUuid(output, group.id());
        output.writeBoolean(group.parentId().isPresent());
        if (group.parentId().isPresent()) writeUuid(output, group.parentId().orElseThrow());
        writeTransform(output, group.transform());
        output.writeBoolean(group.visible());
    }

    private static void writeElement(DataOutputStream output, MoldingElement element) throws IOException {
        writeUuid(output, element.id());
        output.writeByte(CUBE_ELEMENT_TYPE);
        output.writeBoolean(element.groupId().isPresent());
        if (element.groupId().isPresent()) writeUuid(output, element.groupId().orElseThrow());
        writeVector(output, element.from());
        writeVector(output, element.to());
        writeTransform(output, element.transform());
        output.writeBoolean(element.visible());
    }

    private static void writeTransform(DataOutputStream output, MoldingTransform transform) throws IOException {
        writeVector(output, transform.translation());
        writeVector(output, transform.rotation());
        writeVector(output, transform.scale());
        writeVector(output, transform.pivot());
    }

    private static void writeVector(DataOutputStream output, MoldingVec3 value) throws IOException {
        output.writeLong(normalize(value.x()));
        output.writeLong(normalize(value.y()));
        output.writeLong(normalize(value.z()));
    }

    private static long normalize(double value) {
        return Math.round(value * PRECISION);
    }

    private static void writeUuid(DataOutputStream output, UUID id) throws IOException {
        output.writeLong(id.getMostSignificantBits());
        output.writeLong(id.getLeastSignificantBits());
    }
}
