package dev.anvilcraft.plasticraft.molding.model;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 模型与编辑命令的有界、字段顺序稳定的网络格式。 */
public final class MoldingModelStreams {
    private static final int MAX_COMMAND_DEPTH = 2;
    private static final int CUBE_ELEMENT_TYPE = 0;

    private MoldingModelStreams() {
    }

    public static void writeModel(RegistryFriendlyByteBuf buffer, EditableMoldingModel model) {
        buffer.writeVarInt(model.formatVersion());
        buffer.writeUtf(model.name(), MoldingElement.MAX_NAME_LENGTH);
        ResourceLocation.STREAM_CODEC.encode(buffer, model.requestedType());
        buffer.writeVarInt(model.elements().size());
        for (MoldingElement element : model.elements()) writeElement(buffer, element);
        buffer.writeVarInt(model.groups().size());
        for (MoldingGroup group : model.groups()) writeGroup(buffer, group);
    }

    public static EditableMoldingModel readModel(RegistryFriendlyByteBuf buffer) {
        int formatVersion = buffer.readVarInt();
        String name = buffer.readUtf(MoldingElement.MAX_NAME_LENGTH);
        ResourceLocation requestedType = ResourceLocation.STREAM_CODEC.decode(buffer);
        int elementCount = readCount(buffer, EditableMoldingModel.MAX_ELEMENTS, "molding elements");
        List<MoldingElement> elements = new ArrayList<>(elementCount);
        for (int index = 0; index < elementCount; index++) elements.add(readElement(buffer));
        int groupCount = readCount(buffer, EditableMoldingModel.MAX_GROUPS, "molding groups");
        List<MoldingGroup> groups = new ArrayList<>(groupCount);
        for (int index = 0; index < groupCount; index++) groups.add(readGroup(buffer));
        return new EditableMoldingModel(formatVersion, name, requestedType, elements, groups);
    }

    public static void writeCommand(RegistryFriendlyByteBuf buffer, MoldingCommand command) {
        writeCommand(buffer, command, 0);
    }

    public static MoldingCommand readCommand(RegistryFriendlyByteBuf buffer) {
        return readCommand(buffer, 0);
    }

    public static void writeElement(RegistryFriendlyByteBuf buffer, MoldingElement element) {
        buffer.writeUUID(element.id());
        buffer.writeUtf(element.name(), MoldingElement.MAX_NAME_LENGTH);
        // 保留类型字节的位置，避免后续字段在网络载荷中整体移位。
        buffer.writeByte(CUBE_ELEMENT_TYPE);
        writeOptionalUuid(buffer, element.groupId());
        writeVector(buffer, element.from());
        writeVector(buffer, element.to());
        writeTransform(buffer, element.transform());
        buffer.writeBoolean(element.visible());
        buffer.writeBoolean(element.locked());
    }

    public static MoldingElement readElement(RegistryFriendlyByteBuf buffer) {
        UUID id = buffer.readUUID();
        String name = buffer.readUtf(MoldingElement.MAX_NAME_LENGTH);
        if (buffer.readUnsignedByte() != CUBE_ELEMENT_TYPE) {
            throw new IllegalArgumentException("Invalid molding element type");
        }
        Optional<UUID> group = readOptionalUuid(buffer);
        MoldingVec3 from = readVector(buffer);
        MoldingVec3 to = readVector(buffer);
        MoldingTransform transform = readTransform(buffer);
        boolean visible = buffer.readBoolean();
        boolean locked = buffer.readBoolean();
        return new MoldingElement(id, name, group, from, to, transform, visible, locked);
    }

    public static void writeGroup(RegistryFriendlyByteBuf buffer, MoldingGroup group) {
        buffer.writeUUID(group.id());
        buffer.writeUtf(group.name(), MoldingElement.MAX_NAME_LENGTH);
        writeOptionalUuid(buffer, group.parentId());
        writeTransform(buffer, group.transform());
        buffer.writeBoolean(group.visible());
        buffer.writeBoolean(group.locked());
    }

    public static MoldingGroup readGroup(RegistryFriendlyByteBuf buffer) {
        return new MoldingGroup(
            buffer.readUUID(),
            buffer.readUtf(MoldingElement.MAX_NAME_LENGTH),
            readOptionalUuid(buffer),
            readTransform(buffer),
            buffer.readBoolean(),
            buffer.readBoolean()
        );
    }

    private static void writeCommand(
        RegistryFriendlyByteBuf buffer,
        MoldingCommand command,
        int depth
    ) {
        if (depth >= MAX_COMMAND_DEPTH) throw new IllegalArgumentException("Molding command nesting is too deep");
        buffer.writeVarInt(MoldingCommand.SCHEMA_VERSION);
        switch (command) {
            case MoldingCommand.AddElement add -> {
                buffer.writeByte(0);
                writeElement(buffer, add.element());
            }
            case MoldingCommand.ReplaceElement replace -> {
                buffer.writeByte(1);
                writeElement(buffer, replace.element());
            }
            case MoldingCommand.RemoveElements remove -> {
                buffer.writeByte(2);
                buffer.writeVarInt(remove.ids().size());
                for (UUID id : remove.ids()) buffer.writeUUID(id);
            }
            case MoldingCommand.AddGroup add -> {
                buffer.writeByte(3);
                writeGroup(buffer, add.group());
            }
            case MoldingCommand.ReplaceGroup replace -> {
                buffer.writeByte(4);
                writeGroup(buffer, replace.group());
            }
            case MoldingCommand.RemoveGroup remove -> {
                buffer.writeByte(5);
                buffer.writeUUID(remove.id());
            }
            case MoldingCommand.RenameModel rename -> {
                buffer.writeByte(6);
                buffer.writeUtf(rename.name(), MoldingElement.MAX_NAME_LENGTH);
            }
            case MoldingCommand.Batch batch -> {
                buffer.writeByte(7);
                buffer.writeVarInt(batch.commands().size());
                for (MoldingCommand child : batch.commands()) writeCommand(buffer, child, depth + 1);
            }
            case MoldingCommand.Undo ignored -> buffer.writeByte(8);
            case MoldingCommand.Redo ignored -> buffer.writeByte(9);
            case MoldingCommand.SetRequestedType setType -> {
                buffer.writeByte(10);
                ResourceLocation.STREAM_CODEC.encode(buffer, setType.type());
            }
        }
    }

    private static MoldingCommand readCommand(RegistryFriendlyByteBuf buffer, int depth) {
        if (depth >= MAX_COMMAND_DEPTH) throw new IllegalArgumentException("Molding command nesting is too deep");
        int schema = buffer.readVarInt();
        if (schema != MoldingCommand.SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported molding command schema " + schema);
        }
        return switch (buffer.readUnsignedByte()) {
            case 0 -> new MoldingCommand.AddElement(readElement(buffer));
            case 1 -> new MoldingCommand.ReplaceElement(readElement(buffer));
            case 2 -> {
                int count = readCount(buffer, EditableMoldingModel.MAX_ELEMENTS, "removed molding elements");
                List<UUID> ids = new ArrayList<>(count);
                for (int index = 0; index < count; index++) ids.add(buffer.readUUID());
                yield new MoldingCommand.RemoveElements(ids);
            }
            case 3 -> new MoldingCommand.AddGroup(readGroup(buffer));
            case 4 -> new MoldingCommand.ReplaceGroup(readGroup(buffer));
            case 5 -> new MoldingCommand.RemoveGroup(buffer.readUUID());
            case 6 -> new MoldingCommand.RenameModel(buffer.readUtf(MoldingElement.MAX_NAME_LENGTH));
            case 7 -> {
                int count = readCount(buffer, MoldingCommand.MAX_BATCH_SIZE, "molding commands");
                List<MoldingCommand> commands = new ArrayList<>(count);
                for (int index = 0; index < count; index++) commands.add(readCommand(buffer, depth + 1));
                yield new MoldingCommand.Batch(commands);
            }
            case 8 -> new MoldingCommand.Undo();
            case 9 -> new MoldingCommand.Redo();
            case 10 -> new MoldingCommand.SetRequestedType(ResourceLocation.STREAM_CODEC.decode(buffer));
            default -> throw new IllegalArgumentException("Invalid molding command type");
        };
    }

    private static void writeTransform(RegistryFriendlyByteBuf buffer, MoldingTransform transform) {
        writeVector(buffer, transform.translation());
        writeVector(buffer, transform.rotation());
        writeVector(buffer, transform.scale());
        writeVector(buffer, transform.pivot());
    }

    private static MoldingTransform readTransform(RegistryFriendlyByteBuf buffer) {
        return new MoldingTransform(
            readVector(buffer),
            readVector(buffer),
            readVector(buffer),
            readVector(buffer)
        );
    }

    private static void writeVector(RegistryFriendlyByteBuf buffer, MoldingVec3 value) {
        buffer.writeDouble(value.x());
        buffer.writeDouble(value.y());
        buffer.writeDouble(value.z());
    }

    private static MoldingVec3 readVector(RegistryFriendlyByteBuf buffer) {
        return new MoldingVec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    private static void writeOptionalUuid(RegistryFriendlyByteBuf buffer, Optional<UUID> id) {
        buffer.writeBoolean(id.isPresent());
        id.ifPresent(buffer::writeUUID);
    }

    private static Optional<UUID> readOptionalUuid(RegistryFriendlyByteBuf buffer) {
        return buffer.readBoolean() ? Optional.of(buffer.readUUID()) : Optional.empty();
    }

    private static int readCount(RegistryFriendlyByteBuf buffer, int maximum, String description) {
        int count = buffer.readVarInt();
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException("Invalid number of " + description);
        }
        return count;
    }
}
