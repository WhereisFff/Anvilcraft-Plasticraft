package dev.anvilcraft.plasticraft.drone.tool;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 无人机工具定义。工种不写成实体类内的 switch,每项定义声明稳定资源 ID、
 * 匹配的工具物品、任务能力、触及距离、内部库存布局、瞬时操作能量报价、
 * 服务端动作执行器和需要同步的显示状态名;新增工具只注册新定义。
 *
 * @param id                    稳定资源 ID,进入存档与网络后不再改动
 * @param toolItem              合成输入与回收辨识使用的工具物品
 * @param capabilities          支持的任务能力
 * @param reachDistance         工具到目标的触及距离(格);无世界交互的工具声明 0
 * @param inventorySize         内部物品库存槽数;任务托管携带物不算普通库存
 * @param instantActionEnergyCost 每次瞬时操作扣除的 FE
 * @param displayStates         需要同步给渲染层的显示状态名,顺序即同步字节值
 * @param behavior              服务端动作执行器,后续任务 TODO 替换实现
 */
public record DroneToolDefinition(
    ResourceLocation id,
    Supplier<Item> toolItem,
    Set<DroneCapability> capabilities,
    double reachDistance,
    int inventorySize,
    long instantActionEnergyCost,
    List<String> displayStates,
    DroneToolBehavior behavior
) {
    public DroneToolDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(toolItem, "toolItem");
        capabilities = Set.copyOf(capabilities);
        displayStates = List.copyOf(displayStates);
        Objects.requireNonNull(behavior, "behavior");
    }

    public boolean hasCapability(DroneCapability capability) {
        return this.capabilities.contains(capability);
    }

    public boolean matchesToolItem(ItemStack stack) {
        return !stack.isEmpty() && stack.is(this.toolItem.get());
    }
}
