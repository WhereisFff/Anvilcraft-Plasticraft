package dev.anvilcraft.plasticraft.item;

import dev.anvilcraft.plasticraft.drone.DroneData;
import dev.anvilcraft.plasticraft.drone.tool.DroneToolDefinition;
import dev.anvilcraft.plasticraft.entity.drone.DroneEntity;
import dev.anvilcraft.plasticraft.init.entity.PlasticraftEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 四种无人机共用的物品实现;工种由构造时绑定的工具定义决定,不派生子类。 */
public class DroneItem extends Item {
    private static final Map<ResourceLocation, DroneItem> BY_TOOL = new HashMap<>();
    private final DroneToolDefinition definition;

    public DroneItem(DroneToolDefinition definition, Properties properties) {
        super(properties.stacksTo(1));
        this.definition = definition;
        synchronized (BY_TOOL) {
            BY_TOOL.put(definition.id(), this);
        }
    }

    /** 实体回收时按工具定义取回对应物品变体。 */
    public static Item byToolId(ResourceLocation toolId) {
        synchronized (BY_TOOL) {
            DroneItem item = BY_TOOL.get(toolId);
            if (item != null) return item;
            if (BY_TOOL.isEmpty()) {
                throw new IllegalStateException("Drone items are not registered yet");
            }
            return BY_TOOL.values().iterator().next();
        }
    }

    public DroneToolDefinition definition() {
        return this.definition;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        // 潜行右击保留给单机设置界面,由能源与设置 TODO 打开。
        if (context.isSecondaryUseActive()) return InteractionResult.PASS;
        Level level = context.getLevel();
        DroneEntity drone = PlasticraftEntities.DRONE.get().create(level);
        if (drone == null) return InteractionResult.FAIL;

        ItemStack stack = context.getItemInHand();
        Player player = context.getPlayer();
        DroneData data = DroneData.get(stack)
            .orElseGet(() -> DroneData.assembled(this.definition.id(), ItemStack.EMPTY, ItemStack.EMPTY));
        if (player != null && data.owner().isEmpty()) {
            data = data.withOwner(player.getUUID());
        }
        drone.applyDroneData(data);
        Component customName = stack.get(DataComponents.CUSTOM_NAME);
        if (customName != null) {
            drone.setCustomName(customName);
        }

        // 无人机是自由实体,放在点击面上的精确位置而不是对齐方块网格,
        // 这样一格空间内可以手动堆放多架。
        Direction face = context.getClickedFace();
        Vec3 position = context.getClickLocation().add(
            face.getStepX() * 0.251D,
            face == Direction.DOWN ? -0.501D : 0.0D,
            face.getStepZ() * 0.251D
        );
        drone.setPos(position);
        if (player != null) {
            drone.setYRot(player.getYRot());
        }
        if (!level.noCollision(drone)) return InteractionResult.FAIL;

        if (!level.isClientSide) {
            if (!level.addFreshEntity(drone)) return InteractionResult.FAIL;
            level.playSound(
                null,
                drone.blockPosition(),
                SoundType.COPPER.getPlaceSound(),
                SoundSource.BLOCKS,
                0.72F,
                1.05F
            );
            level.gameEvent(player, GameEvent.ENTITY_PLACE, position);
            stack.consume(1, player);
        }
        if (player != null) {
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(
        ItemStack stack,
        TooltipContext context,
        List<Component> tooltip,
        TooltipFlag flag
    ) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component
            .translatable("tooltip.anvilcraftplasticraft.drone." + this.definition.id().getPath())
            .withStyle(ChatFormatting.GRAY));
        DroneData data = DroneData.get(stack).orElse(null);
        if (data == null) return;
        tooltip.add(propellerLine("left", data.leftPropeller()));
        tooltip.add(propellerLine("right", data.rightPropeller()));
    }

    private static Component propellerLine(String side, ItemStack propeller) {
        Component name = propeller.isEmpty()
            ? Component.translatable("tooltip.anvilcraftplasticraft.drone.propeller.missing")
            : propeller.getHoverName();
        return Component
            .translatable("tooltip.anvilcraftplasticraft.drone.propeller." + side, name)
            .withStyle(ChatFormatting.DARK_GRAY);
    }
}
