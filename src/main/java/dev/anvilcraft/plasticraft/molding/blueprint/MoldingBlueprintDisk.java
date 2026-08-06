package dev.anvilcraft.plasticraft.molding.blueprint;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.molding.bake.MoldingModelBaker;
import dev.anvilcraft.plasticraft.molding.bake.MoldingModelHasher;
import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.MoldingModelPersistence;
import dev.dubhe.anvilcraft.init.item.ModComponents;
import dev.dubhe.anvilcraft.init.item.ModItems;
import dev.dubhe.anvilcraft.item.property.component.DiskData;
import dev.dubhe.anvilcraft.item.property.component.StructureDiskData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/** AnvilCraft DISK_DATA 中的自包含蓝图格式。 */
public final class MoldingBlueprintDisk {
    public static final int SCHEMA_VERSION = 1;
    private static final String STORED_FROM = AnvilcraftPlasticraft.of("plastic_molding_chamber").toString();
    private static final String TAG_BLUEPRINT = "AnvilcraftPlasticraftBlueprint";
    private static final String TAG_SCHEMA = "SchemaVersion";
    private static final String TAG_FILE_REVISION = "FileRevision";
    private static final String TAG_NAME = "Name";
    private static final String TAG_OWNER_ID = "OwnerId";
    private static final String TAG_OWNER_NAME = "OwnerName";
    private static final String TAG_CREATED_AT = "CreatedAt";
    private static final String TAG_UPDATED_AT = "UpdatedAt";
    private static final String TAG_BAKE_VERSION = "BakeVersion";
    private static final String TAG_MODEL_HASH = "ModelHash";
    private static final String TAG_MODEL = "Model";

    private MoldingBlueprintDisk() {
    }

    public static boolean isStructureDisk(ItemStack stack) {
        return stack.is(ModItems.STRUCTURE_DISK.get());
    }

    public static boolean hasBlueprintData(ItemStack stack) {
        if (!isStructureDisk(stack)) return false;
        DiskData data = stack.get(ModComponents.DISK_DATA);
        return data != null && data.tag().contains(TAG_BLUEPRINT, CompoundTag.TAG_COMPOUND);
    }

    public static String previewToken(ItemStack stack) {
        if (!isStructureDisk(stack)) return "";
        DiskData data = stack.get(ModComponents.DISK_DATA);
        if (data == null || !data.tag().contains(TAG_BLUEPRINT, CompoundTag.TAG_COMPOUND)) return "";
        CompoundTag encoded = data.tag().getCompound(TAG_BLUEPRINT);
        if (!encoded.contains(TAG_SCHEMA, CompoundTag.TAG_INT)
            || !encoded.contains(TAG_MODEL_HASH, CompoundTag.TAG_STRING)) {
            return "";
        }
        return encoded.getInt(TAG_SCHEMA) + ":" + encoded.getString(TAG_MODEL_HASH);
    }

    public static ItemStack writeCopy(ItemStack source, MoldingBlueprint blueprint) {
        if (!isStructureDisk(source)) throw new IllegalArgumentException("Item is not an AnvilCraft structure disk");
        ItemStack replacement = source.copy();
        CompoundTag data = new CompoundTag();
        writeToTag(data, blueprint);
        replacement.remove(ModComponents.STRUCTURE_DISK_DATA);
        replacement.set(ModComponents.DISK_DATA, new DiskData(data));
        return replacement;
    }

    public static void writeToTag(CompoundTag data, MoldingBlueprint blueprint) {
        data.putString("StoredFrom", STORED_FROM);
        CompoundTag groups = new CompoundTag();
        groups.putString("0", STORED_FROM);
        data.put("CompatibleGroups", groups);

        CompoundTag encoded = new CompoundTag();
        encoded.putInt(TAG_SCHEMA, SCHEMA_VERSION);
        encoded.putLong(TAG_FILE_REVISION, blueprint.fileRevision());
        encoded.putString(TAG_NAME, blueprint.name());
        encoded.putUUID(TAG_OWNER_ID, blueprint.ownerId());
        encoded.putString(TAG_OWNER_NAME, blueprint.ownerName());
        encoded.putLong(TAG_CREATED_AT, blueprint.createdAt());
        encoded.putLong(TAG_UPDATED_AT, blueprint.updatedAt());
        encoded.putInt(TAG_BAKE_VERSION, blueprint.bakeVersion());
        encoded.putString(TAG_MODEL_HASH, blueprint.modelHash());
        encoded.put(TAG_MODEL, MoldingModelPersistence.save(blueprint.model()));
        data.put(TAG_BLUEPRINT, encoded);
    }

    public static Optional<MoldingBlueprint> read(ItemStack stack) {
        if (!isStructureDisk(stack)) return Optional.empty();
        DiskData data = stack.get(ModComponents.DISK_DATA);
        return data == null ? Optional.empty() : readTag(data.tag());
    }

    public static Optional<MoldingBlueprint> readTag(CompoundTag data) {
        if (!data.contains(TAG_BLUEPRINT, CompoundTag.TAG_COMPOUND)) return Optional.empty();
        try {
            CompoundTag encoded = data.getCompound(TAG_BLUEPRINT);
            if (encoded.getInt(TAG_SCHEMA) != SCHEMA_VERSION) return Optional.empty();
            if (!encoded.contains(TAG_FILE_REVISION, CompoundTag.TAG_LONG)
                || !encoded.contains(TAG_NAME, CompoundTag.TAG_STRING)
                || !encoded.hasUUID(TAG_OWNER_ID)
                || !encoded.contains(TAG_OWNER_NAME, CompoundTag.TAG_STRING)
                || !encoded.contains(TAG_CREATED_AT, CompoundTag.TAG_LONG)
                || !encoded.contains(TAG_UPDATED_AT, CompoundTag.TAG_LONG)
                || !encoded.contains(TAG_BAKE_VERSION, CompoundTag.TAG_INT)
                || !encoded.contains(TAG_MODEL_HASH, CompoundTag.TAG_STRING)
                || !encoded.contains(TAG_MODEL, CompoundTag.TAG_COMPOUND)) {
                return Optional.empty();
            }
            EditableMoldingModel model = MoldingModelPersistence.load(encoded.getCompound(TAG_MODEL));
            int bakeVersion = encoded.getInt(TAG_BAKE_VERSION);
            String hash = encoded.getString(TAG_MODEL_HASH);
            if (bakeVersion != MoldingModelBaker.BAKE_VERSION
                || !MoldingModelHasher.hash(model, bakeVersion).equals(hash)) {
                return Optional.empty();
            }
            return Optional.of(new MoldingBlueprint(
                MoldingBlueprint.CURRENT_FORMAT_VERSION,
                encoded.getLong(TAG_FILE_REVISION),
                encoded.getString(TAG_NAME),
                encoded.getUUID(TAG_OWNER_ID),
                encoded.getString(TAG_OWNER_NAME),
                MoldingBlueprint.COORDINATE_SYSTEM,
                model,
                encoded.getLong(TAG_CREATED_AT),
                encoded.getLong(TAG_UPDATED_AT),
                bakeVersion,
                hash
            ));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    public static String stateToken(ItemStack stack) {
        if (!isStructureDisk(stack)) return "";
        DiskData moldingData = stack.get(ModComponents.DISK_DATA);
        StructureDiskData structureData = stack.get(ModComponents.STRUCTURE_DISK_DATA);
        if (moldingData == null && structureData == null) return "";
        String state = (moldingData == null ? "" : moldingData.tag().toString())
            + '|'
            + (structureData == null ? "" : structureData.toString());
        return "data:" + sha256(state);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
