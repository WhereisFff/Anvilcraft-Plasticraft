package dev.anvilcraft.plasticraft.molding.blueprint;

import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.UUID;

/** 共享列表只同步渲染和权限判断所需的有界元数据。 */
public record MoldingBlueprintSummary(
    String fileId,
    long fileRevision,
    String name,
    UUID ownerId,
    String ownerName,
    String modelHash,
    long updatedAt,
    boolean pinned
) {
    public static final int MAX_FILE_ID_LENGTH = 101;

    public MoldingBlueprintSummary {
        if (!MoldingBlueprintLibrary.isSafeFileId(fileId)) throw new IllegalArgumentException("Invalid blueprint file id");
        if (fileRevision < 1L) throw new IllegalArgumentException("Invalid blueprint summary revision");
        if (name.isBlank() || name.length() > 64) throw new IllegalArgumentException("Invalid blueprint summary name");
        if (ownerName.isBlank() || ownerName.length() > MoldingBlueprint.MAX_OWNER_NAME_LENGTH) {
            throw new IllegalArgumentException("Invalid blueprint summary owner");
        }
        if (modelHash.length() != 64) throw new IllegalArgumentException("Invalid blueprint summary hash");
    }

    public static MoldingBlueprintSummary of(String fileId, MoldingBlueprint blueprint, boolean pinned) {
        return new MoldingBlueprintSummary(
            fileId,
            blueprint.fileRevision(),
            blueprint.name(),
            blueprint.ownerId(),
            blueprint.ownerName(),
            blueprint.modelHash(),
            blueprint.updatedAt(),
            pinned
        );
    }

    public void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(this.fileId, MAX_FILE_ID_LENGTH);
        buffer.writeVarLong(this.fileRevision);
        buffer.writeUtf(this.name, 64);
        buffer.writeUUID(this.ownerId);
        buffer.writeUtf(this.ownerName, MoldingBlueprint.MAX_OWNER_NAME_LENGTH);
        buffer.writeUtf(this.modelHash, 64);
        buffer.writeVarLong(this.updatedAt);
        buffer.writeBoolean(this.pinned);
    }

    public static MoldingBlueprintSummary read(RegistryFriendlyByteBuf buffer) {
        return new MoldingBlueprintSummary(
            buffer.readUtf(MAX_FILE_ID_LENGTH),
            buffer.readVarLong(),
            buffer.readUtf(64),
            buffer.readUUID(),
            buffer.readUtf(MoldingBlueprint.MAX_OWNER_NAME_LENGTH),
            buffer.readUtf(64),
            buffer.readVarLong(),
            buffer.readBoolean()
        );
    }
}
