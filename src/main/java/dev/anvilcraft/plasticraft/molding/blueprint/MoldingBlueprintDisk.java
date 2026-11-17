package dev.anvilcraft.plasticraft.molding.blueprint;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.molding.bake.MoldingModelBaker;
import dev.anvilcraft.plasticraft.molding.bake.MoldingModelHasher;
import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.MoldingModelPersistence;
import dev.dubhe.anvilcraft.init.item.ModComponents;
import dev.dubhe.anvilcraft.init.item.ModItems;
import dev.dubhe.anvilcraft.item.property.component.DiskData;
import dev.dubhe.anvilcraft.item.property.component.StoredItem;
import dev.dubhe.anvilcraft.item.property.component.StructureDiskData;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
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
        // 本体扫描器会保留磁盘的其它组件；新写入的方块结构必须覆盖旧成型模型的读取与预览。
        if (!isStructureDisk(stack) || stack.has(ModComponents.STRUCTURE_DISK_DATA)) return false;
        DiskData data = stack.get(ModComponents.DISK_DATA);
        return data != null && data.tag().getCompound(TAG_BLUEPRINT).isPresent();
    }

    public static String previewToken(ItemStack stack) {
        if (!hasBlueprintData(stack)) return "";
        DiskData data = stack.get(ModComponents.DISK_DATA);
        if (data == null) return "";
        CompoundTag encoded = data.tag().getCompoundOrEmpty(TAG_BLUEPRINT);
        if (encoded.getInt(TAG_SCHEMA).isEmpty()
            || encoded.getString(TAG_MODEL_HASH).isEmpty()) {
            return "";
        }
        return encoded.getInt(TAG_SCHEMA).orElseThrow() + ":" + encoded.getString(TAG_MODEL_HASH).orElseThrow();
    }

    public static ItemStack writeCopy(ItemStack source, MoldingBlueprint blueprint) {
        if (!isStructureDisk(source)) throw new IllegalArgumentException("Item is not an AnvilCraft structure disk");
        ItemStack replacement = source.copy();
        CompoundTag data = new CompoundTag();
        writeToTag(data, blueprint);
        replacement.remove(ModComponents.STRUCTURE_DISK_DATA);
        replacement.remove(ModComponents.DISPLAY_ITEM);
        replacement.set(ModComponents.DISK_DATA, new DiskData(data));
        return replacement;
    }

    public static boolean clear(ItemStack stack) {
        if (!hasBlueprintData(stack)) return false;
        stack.remove(ModComponents.DISK_DATA);
        stack.remove(ModComponents.DISPLAY_ITEM);
        return true;
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
        encoded.store(TAG_OWNER_ID, UUIDUtil.CODEC, blueprint.ownerId());
        encoded.putString(TAG_OWNER_NAME, blueprint.ownerName());
        encoded.putLong(TAG_CREATED_AT, blueprint.createdAt());
        encoded.putLong(TAG_UPDATED_AT, blueprint.updatedAt());
        encoded.putInt(TAG_BAKE_VERSION, blueprint.bakeVersion());
        encoded.putString(TAG_MODEL_HASH, blueprint.modelHash());
        encoded.put(TAG_MODEL, MoldingModelPersistence.save(blueprint.model()));
        data.put(TAG_BLUEPRINT, encoded);
    }

    public static Optional<MoldingBlueprint> read(ItemStack stack) {
        if (!hasBlueprintData(stack)) return Optional.empty();
        DiskData data = stack.get(ModComponents.DISK_DATA);
        return data == null ? Optional.empty() : readTag(data.tag());
    }

    public static Optional<MoldingBlueprint> readTag(CompoundTag data) {
        if (data.getCompound(TAG_BLUEPRINT).isEmpty()) return Optional.empty();
        try {
            CompoundTag encoded = data.getCompound(TAG_BLUEPRINT).orElseThrow();
            if (encoded.getInt(TAG_SCHEMA).orElseThrow() != SCHEMA_VERSION) return Optional.empty();
            EditableMoldingModel model = MoldingModelPersistence.load(encoded.getCompound(TAG_MODEL).orElseThrow());
            int bakeVersion = encoded.getInt(TAG_BAKE_VERSION).orElseThrow();
            String hash = encoded.getString(TAG_MODEL_HASH).orElseThrow();
            if (bakeVersion != MoldingModelBaker.BAKE_VERSION
                || !MoldingModelHasher.hash(model, bakeVersion).equals(hash)) {
                return Optional.empty();
            }
            return Optional.of(new MoldingBlueprint(
                MoldingBlueprint.CURRENT_FORMAT_VERSION,
                encoded.getLong(TAG_FILE_REVISION).orElseThrow(),
                encoded.getString(TAG_NAME).orElseThrow(),
                encoded.read(TAG_OWNER_ID, UUIDUtil.CODEC).orElseThrow(),
                encoded.getString(TAG_OWNER_NAME).orElseThrow(),
                MoldingBlueprint.COORDINATE_SYSTEM,
                model,
                encoded.getLong(TAG_CREATED_AT).orElseThrow(),
                encoded.getLong(TAG_UPDATED_AT).orElseThrow(),
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
        StoredItem displayItem = stack.get(ModComponents.DISPLAY_ITEM);
        if (moldingData == null && structureData == null && displayItem == null) return "";
        String state = (moldingData == null ? "" : moldingData.tag().toString())
            + '|'
            + (structureData == null ? "" : structureData.toString())
            + '|'
            + (displayItem == null ? "" : BuiltInRegistries.ITEM.getKey(displayItem.stored().getItem())
                + ":" + displayItem.stored().getCount());
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
