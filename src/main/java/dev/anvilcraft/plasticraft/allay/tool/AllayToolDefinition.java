package dev.anvilcraft.plasticraft.allay.tool;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 戴帽悦灵工具定义。工种由主手物品匹配,不写成实体类内的 switch。
 *
 * @param id            稳定资源 ID
 * @param toolItem      主手匹配的工具物品
 * @param capabilities  支持的任务能力
 * @param reachDistance 工具到目标的触及距离(格)
 * @param inventorySize 内部物品库存槽数;任务托管携带物不算普通库存
 * @param displayStates 需要同步给渲染层的显示状态名
 * @param behavior      服务端动作执行器
 */
public record AllayToolDefinition(
    ResourceLocation id,
    Supplier<Item> toolItem,
    Set<AllayCapability> capabilities,
    double reachDistance,
    int inventorySize,
    List<String> displayStates,
    AllayToolBehavior behavior
) {
    public AllayToolDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(toolItem, "toolItem");
        capabilities = Set.copyOf(capabilities);
        displayStates = List.copyOf(displayStates);
        Objects.requireNonNull(behavior, "behavior");
    }

    public boolean hasCapability(AllayCapability capability) {
        return this.capabilities.contains(capability);
    }

    public boolean matchesToolItem(ItemStack stack) {
        return !stack.isEmpty() && stack.is(this.toolItem.get());
    }
}
