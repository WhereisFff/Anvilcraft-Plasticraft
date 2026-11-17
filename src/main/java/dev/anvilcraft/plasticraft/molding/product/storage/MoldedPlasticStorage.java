package dev.anvilcraft.plasticraft.molding.product.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticContents;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** 塑料锅与储罐的内容快照，保留原料槽位置及流体层序。 */
public record MoldedPlasticStorage(UUID id, List<MoldedPlasticContents.StoredItem> items, List<FluidStack> fluids) {
    public static final Codec<MoldedPlasticStorage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.CODEC.fieldOf("id").forGetter(MoldedPlasticStorage::id),
        MoldedPlasticContents.StoredItem.CODEC.listOf().fieldOf("items").forGetter(MoldedPlasticStorage::items),
        FluidStack.CODEC.listOf().fieldOf("fluids").forGetter(MoldedPlasticStorage::fluids)
    ).apply(instance, MoldedPlasticStorage::new));

    public MoldedPlasticStorage {
        items = items.stream().sorted(Comparator.comparingInt(MoldedPlasticContents.StoredItem::slot)).toList();
        fluids = fluids.stream().map(FluidStack::copy).toList();
    }

    @Override
    public List<FluidStack> fluids() {
        return this.fluids.stream().map(FluidStack::copy).toList();
    }

    public ItemStack itemInSlot(int slot) {
        return this.items.stream().filter(item -> item.slot() == slot).findFirst()
            .map(MoldedPlasticContents.StoredItem::stack).orElse(ItemStack.EMPTY);
    }
}
