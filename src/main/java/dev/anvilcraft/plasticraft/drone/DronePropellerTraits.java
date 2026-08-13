package dev.anvilcraft.plasticraft.drone;

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
 * 螺旋桨材料能力集合。任意一个螺旋桨具备某种材料特性,整架无人机就获得该特性;
 * 后续新材料只在此注册能力映射,不在无人机类内逐种硬编码。
 */
public final class DronePropellerTraits {
    private static final Map<ResourceLocation, Set<DroneMaterialTrait>> BY_MATERIAL = new HashMap<>();

    /** 当前声明的材料能力;耐热塑料尚未加入游戏,先冻结判定边界。 */
    public enum DroneMaterialTrait {
        FIRE_RESISTANT
    }

    private DronePropellerTraits() {
    }

    /** 供未来耐热等塑料材料注册自身能力;materialFluidId 为成型材料的流体注册名。 */
    public static void registerMaterial(ResourceLocation materialFluidId, Set<DroneMaterialTrait> traits) {
        Objects.requireNonNull(materialFluidId, "materialFluidId");
        synchronized (BY_MATERIAL) {
            BY_MATERIAL.merge(materialFluidId, EnumSet.copyOf(traits), (existing, added) -> {
                existing.addAll(added);
                return existing;
            });
        }
    }

    public static Set<DroneMaterialTrait> traitsOf(ItemStack propeller) {
        return MoldedPlasticData.get(propeller)
            .map(data -> BuiltInRegistries.FLUID.getKey(data.material().getFluid()))
            .map(fluidId -> {
                synchronized (BY_MATERIAL) {
                    Set<DroneMaterialTrait> traits = BY_MATERIAL.get(fluidId);
                    return traits == null
                        ? EnumSet.noneOf(DroneMaterialTrait.class)
                        : EnumSet.copyOf(traits);
                }
            })
            .orElseGet(() -> EnumSet.noneOf(DroneMaterialTrait.class));
    }

    public static Set<DroneMaterialTrait> union(ItemStack leftPropeller, ItemStack rightPropeller) {
        Set<DroneMaterialTrait> traits = EnumSet.noneOf(DroneMaterialTrait.class);
        traits.addAll(traitsOf(leftPropeller));
        traits.addAll(traitsOf(rightPropeller));
        return traits;
    }

    public static boolean isFireResistant(ItemStack leftPropeller, ItemStack rightPropeller) {
        return union(leftPropeller, rightPropeller).contains(DroneMaterialTrait.FIRE_RESISTANT);
    }
}
