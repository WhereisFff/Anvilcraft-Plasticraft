package dev.anvilcraft.plasticraft.molding.blueprint;

import dev.anvilcraft.plasticraft.molding.bake.MoldingModelBaker;
import dev.anvilcraft.plasticraft.molding.bake.MoldingModelHasher;
import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;

import java.util.UUID;

/** 磁盘与共享文件共同使用的版本化蓝图记录。 */
public record MoldingBlueprint(
    int formatVersion,
    long fileRevision,
    String name,
    UUID ownerId,
    String ownerName,
    String coordinateSystem,
    EditableMoldingModel model,
    long createdAt,
    long updatedAt,
    int bakeVersion,
    String modelHash
) {
    public static final int CURRENT_FORMAT_VERSION = 1;
    public static final String COORDINATE_SYSTEM = "world_xyz_px";
    public static final int MAX_OWNER_NAME_LENGTH = 64;

    public MoldingBlueprint {
        if (formatVersion != CURRENT_FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported blueprint format " + formatVersion);
        }
        if (fileRevision < 1L) throw new IllegalArgumentException("Invalid blueprint file revision");
        if (name.isBlank() || name.length() > 64) throw new IllegalArgumentException("Invalid blueprint name");
        if (!name.equals(model.name())) throw new IllegalArgumentException("Blueprint name does not match its model");
        if (ownerName.isBlank() || ownerName.length() > MAX_OWNER_NAME_LENGTH) {
            throw new IllegalArgumentException("Invalid blueprint owner name");
        }
        if (!COORDINATE_SYSTEM.equals(coordinateSystem)) {
            throw new IllegalArgumentException("Unsupported blueprint coordinate system");
        }
        if (createdAt < 0L || updatedAt < createdAt) throw new IllegalArgumentException("Invalid blueprint time");
        if (bakeVersion != MoldingModelBaker.BAKE_VERSION) {
            throw new IllegalArgumentException("Unsupported blueprint bake version " + bakeVersion);
        }
        String expectedHash = MoldingModelHasher.hash(model, bakeVersion);
        if (!expectedHash.equals(modelHash)) throw new IllegalArgumentException("Blueprint model hash mismatch");
        MoldingModelBaker.bake(model);
    }

    public static MoldingBlueprint create(
        EditableMoldingModel model,
        UUID ownerId,
        String ownerName,
        long time
    ) {
        return create(model.name(), model, ownerId, ownerName, time);
    }

    public static MoldingBlueprint create(
        String name,
        EditableMoldingModel model,
        UUID ownerId,
        String ownerName,
        long time
    ) {
        EditableMoldingModel namedModel = model.name().equals(name) ? model : model.withName(name);
        return new MoldingBlueprint(
            CURRENT_FORMAT_VERSION,
            1L,
            name,
            ownerId,
            ownerName,
            COORDINATE_SYSTEM,
            namedModel,
            time,
            time,
            MoldingModelBaker.BAKE_VERSION,
            MoldingModelHasher.hash(namedModel, MoldingModelBaker.BAKE_VERSION)
        );
    }

    public MoldingBlueprint copyFor(String newName, UUID newOwnerId, String newOwnerName, long time) {
        return create(newName, this.model, newOwnerId, newOwnerName, time);
    }

    public MoldingBlueprint revise(String newName, EditableMoldingModel newModel, long time) {
        EditableMoldingModel namedModel = newModel.name().equals(newName) ? newModel : newModel.withName(newName);
        return new MoldingBlueprint(
            this.formatVersion,
            this.fileRevision + 1L,
            newName,
            this.ownerId,
            this.ownerName,
            this.coordinateSystem,
            namedModel,
            this.createdAt,
            Math.max(time, this.createdAt),
            this.bakeVersion,
            MoldingModelHasher.hash(namedModel, this.bakeVersion)
        );
    }
}
