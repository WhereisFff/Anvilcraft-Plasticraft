package dev.anvilcraft.plasticraft.item;

import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.material.PlasticMaterial;
import dev.anvilcraft.plasticraft.molding.bake.BakedMoldingModel;
import dev.anvilcraft.plasticraft.molding.bake.MoldingModelBaker;
import dev.anvilcraft.plasticraft.molding.blueprint.BlueprintException;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprint;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprintCodec;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/** 从模组内置蓝图创建各材料、各功能类型的创造模式演示制品。 */
public final class MoldedPlasticDemoItemStacks {
    private static final String RESOURCE_ROOT = "/data/anvilcraftplasticraft/molding/demo/";
    private static final List<DemoDefinition> DEFINITIONS = List.of(
        new DemoDefinition("chest.json", MoldingProductTypes.CHEST_ID),
        new DemoDefinition("fluid-tank.json", MoldingProductTypes.TANK_ID),
        new DemoDefinition("anvil.json", MoldingProductTypes.ANVIL_ID),
        new DemoDefinition("cauldron.json", MoldingProductTypes.CAULDRON_ID),
        new DemoDefinition("bracket.json", MoldingProductTypes.TRAY_ID),
        new DemoDefinition("alloy-hard-ham.json", MoldingProductTypes.ALLAY_HARD_HAT_ID)
    );
    // 大型塑料炼药锅只能由玩家自制大开口模型升级得到，故不列入创造栏演示制品
    public static final List<ResourceLocation> PRODUCT_TYPES = List.of(
        MoldingProductTypes.NORMAL_ID,
        MoldingProductTypes.CHEST_ID,
        MoldingProductTypes.TANK_ID,
        MoldingProductTypes.ANVIL_ID,
        MoldingProductTypes.CAULDRON_ID,
        MoldingProductTypes.TRAY_ID,
        MoldingProductTypes.ALLAY_HARD_HAT_ID
    );

    private MoldedPlasticDemoItemStacks() {
    }

    public static List<ItemStack> all() {
        return all(PlasticMaterial.UNIVERSAL);
    }

    public static List<ItemStack> all(PlasticMaterial material) {
        return DemoDataHolder.DATA.stream().map(data -> createStack(data, material)).toList();
    }

    public static List<ItemStack> creativeProducts(PlasticMaterial material) {
        return PRODUCT_TYPES.stream()
            .map(type -> product(material, type).orElseThrow())
            .toList();
    }

    public static Optional<ItemStack> product(PlasticMaterial material, ResourceLocation type) {
        if (MoldingProductTypes.NORMAL_ID.equals(type)) {
            return Optional.of(material.productStack(DyeColor.WHITE));
        }
        for (int index = 0; index < DEFINITIONS.size(); index++) {
            if (DEFINITIONS.get(index).type().equals(type)) {
                return Optional.of(createStack(DemoDataHolder.DATA.get(index), material));
            }
        }
        return Optional.empty();
    }

    public static boolean isProductType(ResourceLocation type) {
        return PRODUCT_TYPES.contains(type);
    }

    public static Optional<PlasticMaterial> materialOfProduct(ItemStack stack) {
        return PlasticMaterial.fromKey(PlasticItemData.getMaterial(stack))
            .filter(material -> stack.is(material.productBlock().asItem()));
    }

    private static ItemStack createStack(MoldedPlasticData data, PlasticMaterial material) {
        FluidStack moldedMaterial = new FluidStack(material.melt(), data.material().getAmount());
        if (material.supportsDyeing()) PlasticMeltColor.set(moldedMaterial, DyeColor.WHITE);
        ItemStack stack = material.productStack(DyeColor.WHITE);
        MoldedPlasticData.set(stack, data.withMaterial(moldedMaterial));
        PlasticItemData.markDemonstrationModel(stack);
        return stack;
    }

    private static MoldedPlasticData load(DemoDefinition definition) {
        String path = RESOURCE_ROOT + definition.fileName();
        try (InputStream input = MoldedPlasticDemoItemStacks.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Missing molded plastic demonstration blueprint " + path);
            }
            String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            MoldingBlueprint blueprint = MoldingBlueprintCodec.decode(text);
            if (!definition.type().equals(blueprint.model().requestedType())) {
                throw new IllegalStateException("Unexpected molded plastic demonstration type in " + path);
            }
            BakedMoldingModel baked = MoldingModelBaker.bake(blueprint.model());
            int meltMillibuckets = baked.analysis().minimumMeltMillibuckets();
            MoldedPlasticData data = MoldedPlasticData.manufacture(
                blueprint.model(),
                baked,
                new FluidStack(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get(), meltMillibuckets),
                meltMillibuckets
            );
            if (!definition.type().equals(data.finalType())) {
                throw new IllegalStateException("Invalid molded plastic demonstration blueprint " + path);
            }
            return data;
        } catch (IOException | BlueprintException exception) {
            throw new IllegalStateException("Unable to load molded plastic demonstration blueprint " + path, exception);
        }
    }

    private record DemoDefinition(String fileName, ResourceLocation type) {
    }

    private static final class DemoDataHolder {
        private static final List<MoldedPlasticData> DATA = DEFINITIONS.stream()
            .map(MoldedPlasticDemoItemStacks::load)
            .toList();
    }
}
