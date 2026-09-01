package dev.anvilcraft.plasticraft.molding.model;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

/** 在稳定 Codec 与方块实体 NBT 之间转换模型。 */
public final class MoldingModelPersistence {
    private MoldingModelPersistence() {
    }

    public static CompoundTag save(EditableMoldingModel model) {
        Tag encoded = EditableMoldingModel.CODEC.encodeStart(NbtOps.INSTANCE, model)
            .getOrThrow(message -> new IllegalArgumentException("Unable to encode molding model: " + message));
        if (!(encoded instanceof CompoundTag compound)) {
            throw new IllegalArgumentException("Molding model codec did not produce a compound tag");
        }
        return compound;
    }

    public static EditableMoldingModel load(CompoundTag tag) {
        return EditableMoldingModel.CODEC.parse(NbtOps.INSTANCE, tag)
            .getOrThrow(message -> new IllegalArgumentException("Unable to decode molding model: " + message));
    }
}
