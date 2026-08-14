package dev.anvilcraft.plasticraft.drone.tool;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.dubhe.anvilcraft.init.item.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** 当前注册的无人机工具定义。顺序同时是创造标签页与文档的稳定显示顺序。 */
public final class DroneToolDefinitions {
    /** 新版统一规则:一次瞬时操作扣除的 FE。 */
    public static final long INSTANT_ACTION_ENERGY_COST = 2_560L;
    private static final Map<ResourceLocation, DroneToolDefinition> DEFINITIONS = new LinkedHashMap<>();

    /** 未安装工具的空无人机:没有任何任务能力,是创造物品栏工具选择的默认状态。 */
    public static final DroneToolDefinition NONE = register(new DroneToolDefinition(
        AnvilcraftPlasticraft.of("none"),
        () -> Items.AIR,
        Set.of(),
        0.0D,
        0,
        INSTANT_ACTION_ENERGY_COST,
        List.of("idle"),
        DroneToolBehavior.NONE
    ));

    public static final DroneToolDefinition CONSTRUCTION = register(new DroneToolDefinition(
        AnvilcraftPlasticraft.of("construction"),
        ModItems.CRAB_CLAW::get,
        Set.of(
            DroneCapability.PICK_UP_MATERIAL,
            DroneCapability.CARRY_ITEM,
            DroneCapability.DELIVER_PROJECTION,
            DroneCapability.SEAL_FLUID
        ),
        1.0D,
        0,
        INSTANT_ACTION_ENERGY_COST,
        List.of("idle", "open", "approach", "grab", "hold", "release"),
        ConstructionDroneToolBehavior.INSTANCE
    ));

    public static final DroneToolDefinition DEMOLITION = register(new DroneToolDefinition(
        AnvilcraftPlasticraft.of("demolition"),
        () -> Items.STONECUTTER,
        Set.of(DroneCapability.DEMOLISH),
        1.0D,
        0,
        INSTANT_ACTION_ENERGY_COST,
        List.of("idle", "cut"),
        DemolitionDroneToolBehavior.INSTANCE
    ));

    public static final DroneToolDefinition COLLECTION = register(new DroneToolDefinition(
        AnvilcraftPlasticraft.of("collection"),
        ModItems.MAGNET::get,
        Set.of(DroneCapability.COLLECT_ITEMS),
        1.0D,
        9,
        INSTANT_ACTION_ENERGY_COST,
        List.of("idle", "attract"),
        DroneToolBehavior.NONE
    ));

    public static final DroneToolDefinition OBSERVATION = register(new DroneToolDefinition(
        AnvilcraftPlasticraft.of("observation"),
        () -> Items.SPYGLASS,
        Set.of(DroneCapability.CHUNK_LOADING, DroneCapability.GUIDE_FLEET),
        0.0D,
        0,
        INSTANT_ACTION_ENERGY_COST,
        List.of("idle", "observe"),
        DroneToolBehavior.NONE
    ));

    private DroneToolDefinitions() {
    }

    public static List<DroneToolDefinition> values() {
        synchronized (DEFINITIONS) {
            return List.copyOf(DEFINITIONS.values());
        }
    }

    public static Optional<DroneToolDefinition> get(ResourceLocation id) {
        synchronized (DEFINITIONS) {
            return Optional.ofNullable(DEFINITIONS.get(id));
        }
    }

    public static DroneToolDefinition getOrFallback(ResourceLocation id) {
        return get(id).orElse(CONSTRUCTION);
    }

    /** 供未来剪刀、钓鱼竿等工具模块追加定义;内置 ID 的顺序和含义保持不变。 */
    public static DroneToolDefinition register(DroneToolDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        synchronized (DEFINITIONS) {
            if (DEFINITIONS.putIfAbsent(definition.id(), definition) != null) {
                throw new IllegalStateException("Duplicate drone tool definition " + definition.id());
            }
        }
        return definition;
    }
}
