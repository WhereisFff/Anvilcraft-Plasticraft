package dev.anvilcraft.plasticraft.api.tooltip;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.entity.collision.BuiltInPlasticEntityModels;
import dev.anvilcraft.plasticraft.entity.redstone.MoldedPlasticRedstoneConductor;
import dev.anvilcraft.plasticraft.item.AbstractPlasticEntityItem;
import dev.anvilcraft.plasticraft.item.PlasticItemData;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.anvilcraft.plasticraft.item.ResinAnvilItem;
import dev.anvilcraft.plasticraft.item.UniversalPlasticBlockItem;
import dev.anvilcraft.plasticraft.item.UniversalPlasticGranuleItem;
import dev.anvilcraft.plasticraft.item.UniversalPlasticMeltBucketItem;
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
                Right-click an entity, then a block or another entity to bond them
                Moving selections use their selected position; movement over 2 blocks or lasting 2 seconds refreshes the route
                Unanchored bonded groups move together under knockback; block-anchored groups rebound together
                A moving white dashed line means the server is still searching
                The colored solid line shows the server result; bondable routes match the later white transit trail
                Green and yellow can bond; red cannot
                Valid endpoint models use the standard pale-blue overlay; rejected endpoints use pale red
                Splash or lingering Invisibility potions permanently hide adhesive without weakening its bond"""
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
            "Stores a chamber-filled melt batch and consumes 1 mB/gt to print when installed directly above the chamber"
        );
        register(
            AnvilcraftPlasticraft.of("high_heat_fuel_bucket"),
            "Ignitable fuel for the blue enhanced plasma jet",
            """
                An enhanced jet vaporizes 50 mB each tick while consuming 10 mB of high-heat fuel
                Each 250 mB layer of a full layered cauldron extends the enhanced jet by 50 game ticks"""
        );
        register(
            AnvilcraftPlasticraft.of("plastic_oil_bucket"),
            "The second-layer condensate and primary feedstock for later plastic processing",
            """
                Heat it from directly below while it touches a royal-steel or frost-metal item to create universal plastic melt
                Royal steel starts at 25% speed and reaches 50% with eight distinct items; frost metal runs at half speed
                A Large Cauldron averages the actual heat output of all nine blocks beneath it"""
        );
        registerNormal(
            AnvilcraftPlasticraft.of("crude_oil_acid_bucket"),
            "The third-layer essence separated from gaseous crude oil"
        );
        registerNormal(
            AnvilcraftPlasticraft.of("universal_plastic"),
            "An ordinary piece of plastic"
        );
        register(
            AnvilcraftPlasticraft.of("catalytic_press_lid"),
            "A sealed full-speed royal-steel catalyst for converting plastic oil",
            """
                Plastic oil reacts with royal-steel or frost-metal items while heated from directly below
                Royal steel starts at 25% speed; frost metal runs at half the open-catalysis speed
                Bond this lid above a vessel for full speed, then press it with a falling anvil"""
        );
        register(
            AnvilcraftPlasticraft.of("hardend_resin_anvil"),
            "A hardened resin anvil that functions as a complete anvil",
            """
                Pushable anvil with the vanilla anvil workflow
                Shift-use with any Anvil Hammer to retrieve it directly
                Renaming costs no experience and adds no prior-work penalty
                Creative players can Shift-use a magnet to magnetize it
                Can be placed in any direction; only impacts on its bottom face can process recipes"""
        );
        register(
            AnvilcraftPlasticraft.of("hardend_resin_cauldron"),
            "A light, portable cauldron assembled from hardened resin plates",
            """
                Pushable cauldron for items and up to 1000 mB of fluid
                Shift-use with any Anvil Hammer to retrieve it and its stored items
                Connects to pipe heads, pumps, and control valves from any side
                Creative players can Shift-use a magnet to magnetize it
                Can be placed in any direction; when it does not face up, it looks like the fluid will spill"""
        );
        register(
            AnvilcraftPlasticraft.of("high_viscosity_resin_block"),
            "Bonds two pieces of metal firmly and provides an excellent seal",
            """
                Elastic and strongly sticks to adjacent blocks
                Captures creatures of any size; hostile creatures must be weakened
                Each resin-connected group counts as one block against a piston's push limit"""
        );
        register(
            AnvilcraftPlasticraft.of("resin_anvil_hammer"),
            "A lightweight anvil hammer made from an elastic resin anvil",
            """
                Retains every standard Anvil Hammer function except the portable anvil menu
                Left-clicking any entity deals no damage and applies Knockback V
                Left-clicking a block launches you opposite your full view direction with Knockback V
                Has 35 durability and can be repaired with resin
                Repairs in a Hardened Resin Anvil cost no experience and add no prior-work penalty"""
        );
        register(
            AnvilcraftPlasticraft.of("resin_anvil"),
            "A resin block kneaded into an anvil shape, full of elasticity",
            """
                Elastic and pushable; rebounds from blocks and entities
                Shift-use with any Anvil Hammer to retrieve it directly
                Retains Resin Block capture and time-warp behavior
                Dry fast cooking hardens it
                Creative players can Shift-use a magnet to magnetize it
                Can be placed in any direction; only impacts on its bottom face can process recipes"""
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
            .map(data -> MoldingProductTypes.isChest(data.finalType())
                || MoldingProductTypes.isTank(data.finalType())
                || MoldingProductTypes.isAnvil(data.finalType())
                || MoldingProductTypes.isTray(data.finalType()))
            .orElse(false);
        int dynamicTooltipIndex = 1;
        if (SHIFT.containsKey(itemId)) {
            if (Screen.hasShiftDown()) {
                dynamicTooltipIndex += addTranslatedTooltip(tooltip, getTranslationKeyShift(itemId));
            } else {
                if (NORMAL.containsKey(itemId) && !specializedMoldedProduct) {
                    dynamicTooltipIndex += addTranslatedTooltip(tooltip, getTranslationKey(itemId));
                }
                tooltip.add(
                    1,
                    Component.translatable(
                        "tooltip.anvilcraft.press_key",
                        Component.literal("[Shift]").withStyle(ChatFormatting.WHITE)
                    ).withStyle(ChatFormatting.DARK_GRAY)
                );
                dynamicTooltipIndex++;
            }
        } else if (NORMAL.containsKey(itemId) && !specializedMoldedProduct) {
            dynamicTooltipIndex += addTranslatedTooltip(tooltip, getTranslationKey(itemId));
        }
        addDynamicTooltip(stack, context, tooltip, dynamicTooltipIndex);
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
                addPlasticSizeTooltip(stack, dynamicTooltip);
                addMoldedProductTooltip(stack, dynamicTooltip);
            }
            dynamicTooltip.add(Component.translatable(
                "tooltip.anvilcraftplasticraft.color",
                Component.translatable("color.minecraft." + PlasticMeltColor.get(stack).getName())
            ).withStyle(ChatFormatting.GRAY));
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
        if (MoldedPlasticRedstoneConductor.isFullBlockSized(bounds)) {
            tooltip.add(Component.translatable("tooltip.anvilcraftplasticraft.redstone_conductor")
                .withStyle(ChatFormatting.RED));
        }
    }

    private static void addMoldedProductTooltip(ItemStack stack, List<Component> tooltip) {
        MoldedPlasticData.get(stack).ifPresent(data -> {
            if (MoldingProductTypes.isChest(data.finalType())) {
                tooltip.add(Component.translatable(
                    "tooltip.anvilcraftplasticraft.molded_chest",
                    data.capacity()
                ).withStyle(ChatFormatting.GRAY));
            } else if (MoldingProductTypes.isTank(data.finalType())) {
                tooltip.add(Component.translatable(
                    "tooltip.anvilcraftplasticraft.molded_tank",
                    data.capacity()
                ).withStyle(ChatFormatting.GRAY));
            } else if (MoldingProductTypes.isAnvil(data.finalType())) {
                tooltip.add(Component.translatable(
                    data.hasGiantAnvilAbility()
                        ? "tooltip.anvilcraftplasticraft.molded_giant_anvil"
                        : "tooltip.anvilcraftplasticraft.molded_anvil"
                ).withStyle(ChatFormatting.GRAY));
            } else if (MoldingProductTypes.isTray(data.finalType())) {
                tooltip.add(Component.translatable(
                    "tooltip.anvilcraftplasticraft.molded_tray"
                ).withStyle(ChatFormatting.GRAY));
            }
        });
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
