package dev.anvilcraft.plasticraft.allay;

import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
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

    /** 当前声明的材料能力;耐热塑料尚未加入游戏,先冻结判定边界。 */
    public enum AllayMaterialTrait {
        FIRE_RESISTANT
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
}
