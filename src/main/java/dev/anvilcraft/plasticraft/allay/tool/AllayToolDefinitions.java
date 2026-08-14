package dev.anvilcraft.plasticraft.allay.tool;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.dubhe.anvilcraft.init.item.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** 当前注册的戴帽悦灵工具定义。工种由主手物品解析,不存盘。空手是通用工,特殊物品改写能力。 */
public final class AllayToolDefinitions {
    private static final Map<ResourceLocation, AllayToolDefinition> DEFINITIONS = new LinkedHashMap<>();
    private static final Set<AllayCapability> CONSTRUCT = Set.of(
        AllayCapability.PICK_UP_MATERIAL,
        AllayCapability.CARRY_ITEM,
        AllayCapability.DELIVER_PROJECTION,
        AllayCapability.SEAL_FLUID
    );
    private static final Set<AllayCapability> CONSTRUCT_AND_COLLECT = Set.of(
        AllayCapability.PICK_UP_MATERIAL,
        AllayCapability.CARRY_ITEM,
        AllayCapability.DELIVER_PROJECTION,
        AllayCapability.SEAL_FLUID,
        AllayCapability.COLLECT_ITEMS
    );
    private static final Set<AllayCapability> DEMOLISH_ONLY = Set.of(AllayCapability.DEMOLISH);
    private static final Set<AllayCapability> COLLECT_ONLY = Set.of(AllayCapability.COLLECT_ITEMS);

    public static final AllayToolDefinition NONE = register(new AllayToolDefinition(
        AnvilcraftPlasticraft.of("none"),
        () -> Items.AIR,
        CONSTRUCT_AND_COLLECT,
        1.0D,
        0,
        List.of("idle", "open", "approach", "grab", "hold", "release", "attract"),
        GeneralAllayToolBehavior.INSTANCE
    ));

    public static final AllayToolDefinition CONSTRUCTION = register(new AllayToolDefinition(
        AnvilcraftPlasticraft.of("construction"),
        ModItems.CRAB_CLAW::get,
        CONSTRUCT,
        4.0D,
        0,
        List.of("idle", "open", "approach", "grab", "hold", "release"),
        GeneralAllayToolBehavior.INSTANCE
    ));

    public static final AllayToolDefinition DEMOLITION = register(new AllayToolDefinition(
        AnvilcraftPlasticraft.of("demolition"),
        () -> Items.STONECUTTER,
        DEMOLISH_ONLY,
        1.0D,
        0,
        List.of("idle", "cut"),
        GeneralAllayToolBehavior.INSTANCE
    ));

    public static final AllayToolDefinition COLLECTION = register(new AllayToolDefinition(
        AnvilcraftPlasticraft.of("collection"),
        ModItems.MAGNET::get,
        COLLECT_ONLY,
        1.0D,
        9,
        List.of("idle", "attract"),
        GeneralAllayToolBehavior.INSTANCE
    ));

    public static final AllayToolDefinition OBSERVATION = register(new AllayToolDefinition(
        AnvilcraftPlasticraft.of("observation"),
        () -> Items.SPYGLASS,
        Set.of(AllayCapability.CHUNK_LOADING, AllayCapability.GUIDE_FLEET),
        0.0D,
        0,
        List.of("idle", "observe"),
        AllayToolBehavior.NONE
    ));

    private AllayToolDefinitions() {
    }

    public static List<AllayToolDefinition> values() {
        synchronized (DEFINITIONS) {
            return List.copyOf(DEFINITIONS.values());
        }
    }

    public static Optional<AllayToolDefinition> get(ResourceLocation id) {
        synchronized (DEFINITIONS) {
            return Optional.ofNullable(DEFINITIONS.get(id));
        }
    }

    public static AllayToolDefinition getOrFallback(ResourceLocation id) {
        return get(id).orElse(NONE);
    }

    public static AllayToolDefinition fromHeldItem(ItemStack stack) {
        if (stack.isEmpty()) return NONE;
        synchronized (DEFINITIONS) {
            for (AllayToolDefinition definition : DEFINITIONS.values()) {
                if (definition.matchesToolItem(stack)) return definition;
            }
        }
        return NONE;
    }

    public static AllayToolDefinition register(AllayToolDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        synchronized (DEFINITIONS) {
            if (DEFINITIONS.putIfAbsent(definition.id(), definition) != null) {
                throw new IllegalStateException("Duplicate allay tool definition " + definition.id());
            }
        }
        return definition;
    }
}
