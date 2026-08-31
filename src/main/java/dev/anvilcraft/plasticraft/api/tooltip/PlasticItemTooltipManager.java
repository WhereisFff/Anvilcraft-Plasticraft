package dev.anvilcraft.plasticraft.api.tooltip;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.entity.collision.BuiltInPlasticEntityModels;
import dev.anvilcraft.plasticraft.item.AbstractPlasticEntityItem;
import dev.anvilcraft.plasticraft.item.PlasticItemData;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.anvilcraft.plasticraft.item.ResinAnvilItem;
import dev.anvilcraft.plasticraft.item.UniversalPlasticBlockItem;
import dev.anvilcraft.plasticraft.item.UniversalPlasticGranuleItem;
import dev.anvilcraft.plasticraft.item.UniversalPlasticMeltBucketItem;
import dev.anvilcraft.plasticraft.material.PlasticMaterial;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprintDisk;
import dev.anvilcraft.plasticraft.molding.model.MoldingModelBounds;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import dev.dubhe.anvilcraft.init.item.ModComponents;
import dev.dubhe.anvilcraft.item.property.component.SavedEntity;
import dev.dubhe.anvilcraft.util.ResentmentUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 集中声明并追加 Plasticraft 物品工具提示，同时为语言数据生成提供原文。 */
public final class PlasticItemTooltipManager {
    public static final String DEMONSTRATION_TOOLTIP_KEY =
        "tooltip.anvilcraftplasticraft.molded_demonstration.shift";
    private static final Map<ResourceLocation, String> NORMAL = new LinkedHashMap<>();
    private static final Map<ResourceLocation, String> SHIFT = new LinkedHashMap<>();
    private static boolean initialized;

    private PlasticItemTooltipManager() {
    }

    public static void init() {
        if (initialized) return;
        initialized = true;
        register(
            AnvilcraftPlasticraft.of("liquid_high_viscosity_resin_bucket"),
            "Highly adhesive and non-volatile; it appears to need thousands of years to solidify",
            """
                Right-click an entity, then right-click a block or another entity to bond the two
                Right-click a block to place a blob of adhesive that sticks entities it touches"""
        );
        register(
            AnvilcraftPlasticraft.of("condenser_tower"),
            "A stackable 3x3x3 module that condenses vapor above a large cauldron",
            """
                Each module stores 64 buckets and exposes four output-only ports
                Oil is separated into high-heat fuel, plastic oil, and crude-oil essence across the first three layers
                Capping the top-center outlet makes a full module apply backpressure to the entire stack"""
        );
        registerNormal(
            AnvilcraftPlasticraft.of("plastic_3d_printing_component"),
            "Installed above the Plastic Molding Chamber, it prints precise plastic products"
        );
        registerNormal(
            AnvilcraftPlasticraft.of("high_heat_fuel_bucket"),
            "Ignites high-temperature plasma jets; they burn fiercely but consume fuel quickly"
        );
        registerNormal(
            AnvilcraftPlasticraft.of("plastic_oil_bucket"),
            "Can be converted into different plastic melts in several ways"
        );
        registerNormal(
            AnvilcraftPlasticraft.of("crude_oil_acid_bucket"),
            "Contains mysterious power and can be converted into many forms"
        );
        registerNormal(
            AnvilcraftPlasticraft.of("universal_plastic"),
            "Ordinary plastic that blocks lasers and breaks when hit by a level 5 or stronger beam"
        );
        register(
            AnvilcraftPlasticraft.of("clear_plastic_melt_bucket"),
            "Transparent plastic melt for clear molded products",
            "Made from plastic oil with tempering glass or frost glass; frost glass runs at half speed and all 16 dye colours are supported"
        );
        registerNormal(
            AnvilcraftPlasticraft.of("clear_plastic_granule"),
            "Transparent plastic feedstock for molding"
        );
        register(
            AnvilcraftPlasticraft.of("clear_plastic"),
            "Clear plastic products that tint beacon beams like stained glass and do not conduct redstone",
            """
                Spectral Anvils can pass through it
                Lasers pass through it without dealing damage
                All 16 dye colours tint beacon beams like their stained-glass counterparts"""
        );
        register(
            AnvilcraftPlasticraft.of("engineering_plastic_melt_bucket"),
            "Royal-steel-reinforced melt for molding engineering plastic products",
            """
                Convert universal plastic melt with any frost-metal item without external cooling
                Royal-steel items instead require a cold block directly below the melt"""
        );
        registerNormal(
            AnvilcraftPlasticraft.of("engineering_plastic_granule"),
            "Solid engineering plastic feedstock retaining its melt colour"
        );
        register(
            AnvilcraftPlasticraft.of("engineering_plastic"),
            "High-strength plastic that blocks lasers and whose molded demolition tools preserve broken blocks",
            """
                Molded anvils break targets above stonecutters with Silk Touch
                Allay Hard Hats grant Silk Touch to demolition allays holding a stonecutter
                Lasers at level 5 or above destroy the engineering plastic they hit"""
        );
        register(
            AnvilcraftPlasticraft.of("heat_resistant_plastic_melt_bucket"),
            "Ember-metal-reinforced melt for fire-resistant plastic products",
            """
                Convert universal plastic melt with an item in the anvilcraftplasticraft:ember_metal_items tag
                The reaction needs heat directly below and preserves the melt colour
                Heat-resistant products and Allay Hard Hats are immune to fire"""
        );
        registerNormal(
            AnvilcraftPlasticraft.of("heat_resistant_plastic_granule"),
            "Solid heat-resistant plastic feedstock retaining its melt colour"
        );
        register(
            AnvilcraftPlasticraft.of("heat_resistant_plastic"),
            "Heat-resistant plastic that blocks lasers and is immune to fire and laser damage",
            """
                Molded anvils break targets above stonecutters with Smelting
                Allay Hard Hats grant fire immunity and Smelting to demolition allays
                The anvil behaves like an Ember Anvil when it lands
                It blocks lasers without taking laser damage"""
        );
        register(
            AnvilcraftPlasticraft.of("catalytic_press_lid"),
            "A sealed full-speed royal-steel catalyst for plastic oil and chilled universal melt",
            """
                Plastic oil converts to universal plastic melt while heated from directly below
                Universal plastic melt converts to engineering plastic melt over a cold block
                Bond this lid above a vessel for full speed, then press it with a falling anvil"""
        );
        register(
            AnvilcraftPlasticraft.of("hardend_resin_anvil"),
            "A hardened resin anvil that functions as a complete anvil",
            """
                Rename it for free; falling does not damage it
                Only impacts on its bottom face process anvil recipes; inserting a magnet lets magnets pull it"""
        );
        registerNormal(
            AnvilcraftPlasticraft.of("hardend_resin_cauldron"),
            "A light, portable cauldron assembled from hardened resin plates"
        );
        register(
            AnvilcraftPlasticraft.of("high_viscosity_resin_block"),
            "A well-sealed resin block that is genuinely sticky",
            """
                Elastic and strongly sticks to adjacent blocks
                Captures creatures of any size; hostile creatures must be weakened
                Each resin-connected group counts as one block against a piston's push limit"""
        );
        register(
            AnvilcraftPlasticraft.of("resin_anvil_hammer"),
            "A lightweight anvil hammer made from an elastic resin anvil",
            """
                Attacks deal no damage but launch targets a long way; hitting a block launches you too
                Can be repaired with resin; repairs in a Hardened Resin Anvil cost no experience"""
        );
        register(
            AnvilcraftPlasticraft.of("resin_anvil"),
            "A resin block kneaded into an anvil shape, full of elasticity",
            """
                Its elasticity means it cannot repair equipment or enchantments
                Only impacts on its bottom face process anvil recipes; inserting a magnet lets magnets pull it"""
        );
        registerNormal(
            AnvilcraftPlasticraft.of("allay_lounge"),
            "Hosts up to 16 hatted Allays and keeps them with the lounge item when broken"
        );
    }

    private static void registerNormal(ResourceLocation itemId, String description) {
        NORMAL.put(itemId, description);
    }

    private static void register(
        ResourceLocation itemId,
        String normalDescription,
        String shiftDescription
    ) {
        NORMAL.put(itemId, normalDescription);
        SHIFT.put(itemId, shiftDescription);
    }

    public static void addTooltip(ItemStack stack, Item.TooltipContext context, List<Component> tooltip) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        boolean specializedMoldedProduct = MoldedPlasticData.get(stack)
            .map(data -> !MoldingProductTypes.NORMAL_ID.equals(data.finalType()))
            .orElse(false);
        boolean demonstrationModel = specializedMoldedProduct
            && PlasticItemData.isDemonstrationModel(stack);
        boolean shiftDown = Screen.hasShiftDown();
        boolean showShiftHint = SHIFT.containsKey(itemId) || demonstrationModel;
        int dynamicTooltipIndex = 1;
        if (showShiftHint) {
            if (shiftDown) {
                if (SHIFT.containsKey(itemId)) {
                    dynamicTooltipIndex += addTranslatedTooltip(tooltip, getTranslationKeyShift(itemId));
                }
                if (itemId.equals(AnvilcraftPlasticraft.of("allay_lounge"))) {
                    dynamicTooltipIndex += addTranslatedTooltip(
                        tooltip,
                        "tooltip.anvilcraftplasticraft.item.allay_lounge.permissions"
                    );
                }
                if (demonstrationModel) {
                    dynamicTooltipIndex += addTranslatedTooltip(tooltip, DEMONSTRATION_TOOLTIP_KEY);
                }
            } else {
                if (NORMAL.containsKey(itemId) && !specializedMoldedProduct) {
                    dynamicTooltipIndex += addTranslatedTooltip(tooltip, getTranslationKey(itemId));
                }
            }
        } else if (NORMAL.containsKey(itemId) && !specializedMoldedProduct) {
            dynamicTooltipIndex += addTranslatedTooltip(tooltip, getTranslationKey(itemId));
        }
        addDynamicTooltip(stack, context, tooltip, dynamicTooltipIndex);
        if (showShiftHint && !shiftDown) {
            tooltip.add(
                Component.translatable(
                    "tooltip.anvilcraft.press_key",
                    Component.literal("[Shift]").withStyle(ChatFormatting.WHITE)
                ).withStyle(ChatFormatting.DARK_GRAY)
            );
        }
    }

    /** 动态提示只读取当前物品堆数据，不参与语言原文注册。 */
    private static void addDynamicTooltip(
        ItemStack stack,
        Item.TooltipContext context,
        List<Component> tooltip,
        int insertionIndex
    ) {
        List<Component> dynamicTooltip = new ArrayList<>();
        MoldingBlueprintDisk.read(stack).ifPresent(blueprint -> {
            MoldingModelBounds visibleBounds = MoldingModelBounds.visible(blueprint.model())
                .orElseGet(MoldingModelBounds::empty);
            MoldingVec3 size = visibleBounds.sizeBlocks();
            dynamicTooltip.add(Component.translatable(
                "item.anvilcraft.structure_disk.structure",
                blueprint.name()
            ));
            dynamicTooltip.add(Component.translatable(
                "item.anvilcraft.structure_disk.size",
                formatDimensions(size)
            ));
            boolean fitsChamber = MoldingModelBounds.all(blueprint.model())
                .map(MoldingModelBounds::fitsWorkspaceSize)
                .orElse(true);
            dynamicTooltip.add(Component.translatable(
                fitsChamber
                    ? "tooltip.anvilcraftplasticraft.molding.structure_disk.fit_chamber"
                    : "tooltip.anvilcraftplasticraft.molding.structure_disk.too_large_for_chamber"
            ).withStyle(fitsChamber ? ChatFormatting.GREEN : ChatFormatting.RED));
        });
        if (stack.getItem() instanceof AbstractPlasticEntityItem<?>) {
            if (PlasticItemData.isMagnetized(stack)) {
                dynamicTooltip.add(Component.translatable("tooltip.anvilcraftplasticraft.magnetized")
                    .withStyle(ChatFormatting.AQUA));
            }
        }
        if (stack.getItem() instanceof UniversalPlasticGranuleItem
            || stack.getItem() instanceof UniversalPlasticMeltBucketItem
            || stack.getItem() instanceof UniversalPlasticBlockItem
        ) {
            if (stack.getItem() instanceof UniversalPlasticBlockItem) {
                addMoldedProductTooltip(stack, dynamicTooltip);
                addPlasticSizeTooltip(stack, dynamicTooltip);
            }
            if (PlasticMaterial.fromKey(PlasticItemData.getMaterial(stack))
                .map(PlasticMaterial::supportsDyeing)
                .orElse(true)) {
                dynamicTooltip.add(Component.translatable(
                    "tooltip.anvilcraftplasticraft.color",
                    Component.translatable("color.minecraft." + PlasticMeltColor.get(stack).getName())
                ).withStyle(ChatFormatting.GRAY));
            }
        }
        if (stack.getItem() instanceof ResinAnvilItem) {
            addCapturedEntityTooltip(stack, context, dynamicTooltip);
        }
        tooltip.addAll(Math.min(insertionIndex, tooltip.size()), dynamicTooltip);
    }

    private static void addPlasticSizeTooltip(ItemStack stack, List<Component> tooltip) {
        AABB bounds = MoldedPlasticData.get(stack)
            .map(MoldedPlasticData::surfaceBounds)
            .orElseGet(() -> BuiltInPlasticEntityModels.UNIVERSAL_PLASTIC.geometry().localBounds());
        tooltip.add(Component.translatable(
            "tooltip.anvilcraftplasticraft.size",
            MoldingModelBounds.formatBlocks(bounds.getXsize()),
            MoldingModelBounds.formatBlocks(bounds.getYsize()),
            MoldingModelBounds.formatBlocks(bounds.getZsize())
        ).withStyle(ChatFormatting.GRAY));
    }

    private static void addMoldedProductTooltip(ItemStack stack, List<Component> tooltip) {
        MoldedPlasticData.get(stack).ifPresent(data -> {
            if (MoldingProductTypes.isChest(data.finalType())) {
                tooltip.add(Component.translatable(
                    "tooltip.anvilcraftplasticraft.molded_chest",
                    data.capacity(),
                    moldedFeature(data, false)
                ).withStyle(ChatFormatting.GRAY));
            } else if (MoldingProductTypes.isTank(data.finalType())) {
                tooltip.add(Component.translatable(
                    "tooltip.anvilcraftplasticraft.molded_tank",
                    data.capacity(),
                    moldedFeature(data, false)
                ).withStyle(ChatFormatting.GRAY));
            } else if (MoldingProductTypes.isCauldron(data.finalType())) {
                String key = cauldronTooltipKey(data);
                tooltip.add((MoldingProductTypes.isLargeCauldron(data.finalType())
                    ? Component.translatable(key)
                    : Component.translatable(key, data.capacity(), moldedFeature(data, false))
                ).withStyle(ChatFormatting.GRAY));
            } else if (MoldingProductTypes.isAnvil(data.finalType())) {
                String key = data.hasGiantAnvilAbility()
                    ? "tooltip.anvilcraftplasticraft.molded_giant_anvil"
                    : "tooltip.anvilcraftplasticraft.molded_anvil";
                tooltip.add(Component.translatable(key, moldedFeature(data, true)).withStyle(ChatFormatting.GRAY));
            } else if (MoldingProductTypes.isTray(data.finalType())) {
                tooltip.add(Component.translatable(
                    "tooltip.anvilcraftplasticraft.molded_tray"
                ).withStyle(ChatFormatting.GRAY));
            } else if (MoldingProductTypes.ALLAY_HARD_HAT_ID.equals(data.finalType())) {
                tooltip.add(Component.translatable(
                    "tooltip.anvilcraftplasticraft.molded_allay_hard_hat",
                    moldedFeature(data, false)
                ).withStyle(ChatFormatting.GRAY));
            }
        });
    }

    private static Component moldedFeature(MoldedPlasticData data, boolean anvil) {
        PlasticMaterial material = PlasticMaterial.fromMelt(data.material()).orElse(null);
        String key = material == PlasticMaterial.HEAT_RESISTANT
            ? "tooltip.anvilcraftplasticraft.molded_feature.heat_resistant"
            : material == PlasticMaterial.CLEAR
                ? "tooltip.anvilcraftplasticraft.molded_feature.clear"
                : anvil && material == PlasticMaterial.ENGINEERING
                    ? "tooltip.anvilcraftplasticraft.molded_feature.engineering_anvil"
                    : null;
        return key == null ? Component.empty() : Component.translatable(key);
    }

    /** 锅的提示只随容量层数分流，所有成型塑料锅均可储存熔岩。 */
    private static String cauldronTooltipKey(MoldedPlasticData data) {
        return MoldingProductTypes.isLargeCauldron(data.finalType())
            ? "tooltip.anvilcraftplasticraft.molded_large_cauldron"
            : "tooltip.anvilcraftplasticraft.molded_cauldron";
    }

    private static String formatDimensions(MoldingVec3 size) {
        return MoldingModelBounds.formatBlocks(size.x()) + " x "
            + MoldingModelBounds.formatBlocks(size.y()) + " x "
            + MoldingModelBounds.formatBlocks(size.z());
    }

    private static void addCapturedEntityTooltip(
        ItemStack stack,
        Item.TooltipContext context,
        List<Component> tooltip
    ) {
        SavedEntity saved = stack.get(ModComponents.SAVED_ENTITY);
        if (saved == null || context.level() == null) return;
        Entity entity = saved.toEntity(context.level());
        if (entity == null) return;
        tooltip.add(Component.literal("- ").append(entity.getDisplayName()).withStyle(ChatFormatting.DARK_GRAY));
        if (saved.isMonster() && entity instanceof LivingEntity living) {
            tooltip.add(Component.translatable(
                "tooltip.anvilcraft.item.resin_block.resentment",
                ResentmentUtil.getResentment(living)
            ).withStyle(ChatFormatting.DARK_RED));
        }
    }

    private static int addTranslatedTooltip(List<Component> tooltip, String key) {
        String[] lines = I18n.get(key).split("\n");
        for (int index = lines.length - 1; index >= 0; index--) {
            tooltip.add(1, Component.literal(lines[index]).withStyle(ChatFormatting.GRAY));
        }
        return lines.length;
    }

    public static String getTranslationKey(ResourceLocation itemId) {
        return "tooltip.%s.item.%s".formatted(itemId.getNamespace(), itemId.getPath());
    }

    public static String getTranslationKeyShift(ResourceLocation itemId) {
        return getTranslationKey(itemId) + ".shift";
    }

    public static Map<ResourceLocation, String> getNormalMap() {
        return Collections.unmodifiableMap(NORMAL);
    }

    public static Map<ResourceLocation, String> getShiftMap() {
        return Collections.unmodifiableMap(SHIFT);
    }
}
