package dev.anvilcraft.plasticraft.allay;

import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.dubhe.anvilcraft.util.BlockMiningEffect;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 悦灵安全帽材料能力。一顶帽子具备某种材料特性,整只戴帽悦灵就获得该特性;
 * 后续新材料只在此注册能力映射,不在实体类内逐种硬编码。
 */
public final class AllayHardHatTraits {
    private static final Map<ResourceLocation, Set<AllayMaterialTrait>> BY_MATERIAL = new HashMap<>();

    /** 耐热能力为后续材料预留，工程塑料当前只注册精准采集。 */
    public enum AllayMaterialTrait {
        FIRE_RESISTANT,
        SILK_TOUCH
    }

    private AllayHardHatTraits() {
    }

    public static void registerMaterial(ResourceLocation materialFluidId, Set<AllayMaterialTrait> traits) {
        Objects.requireNonNull(materialFluidId, "materialFluidId");
        synchronized (BY_MATERIAL) {
            BY_MATERIAL.merge(materialFluidId, EnumSet.copyOf(traits), (existing, added) -> {
                existing.addAll(added);
                return existing;
            });
        }
    }

    public static Set<AllayMaterialTrait> traitsOf(ItemStack hat) {
        return MoldedPlasticData.get(hat)
            .map(data -> BuiltInRegistries.FLUID.getKey(data.material().getFluid()))
            .map(fluidId -> {
                synchronized (BY_MATERIAL) {
                    Set<AllayMaterialTrait> traits = BY_MATERIAL.get(fluidId);
                    return traits == null
                        ? EnumSet.noneOf(AllayMaterialTrait.class)
                        : EnumSet.copyOf(traits);
                }
            })
            .orElseGet(() -> EnumSet.noneOf(AllayMaterialTrait.class));
    }

    public static boolean isFireResistant(ItemStack hat) {
        return traitsOf(hat).contains(AllayMaterialTrait.FIRE_RESISTANT);
    }

    public static boolean hasSilkTouch(ItemStack hat) {
        return traitsOf(hat).contains(AllayMaterialTrait.SILK_TOUCH);
    }

    public static BlockMiningEffect miningEffectOf(ItemStack hat) {
        Set<AllayMaterialTrait> traits = traitsOf(hat);
        if (traits.contains(AllayMaterialTrait.FIRE_RESISTANT)) return BlockMiningEffect.SMELTING;
        if (traits.contains(AllayMaterialTrait.SILK_TOUCH)) return BlockMiningEffect.SILK_TOUCH;
        return BlockMiningEffect.NORMAL;
    }
}
