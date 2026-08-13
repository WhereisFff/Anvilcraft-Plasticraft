package dev.anvilcraft.plasticraft.item;

import dev.anvilcraft.plasticraft.drone.DroneData;
import dev.anvilcraft.plasticraft.drone.DroneDefaultPropeller;
import dev.anvilcraft.plasticraft.drone.DroneEnergyModel;
import dev.anvilcraft.plasticraft.drone.tool.DroneToolDefinition;
import dev.anvilcraft.plasticraft.drone.tool.DroneToolDefinitions;
import dev.anvilcraft.plasticraft.entity.drone.DroneEntity;
import dev.anvilcraft.plasticraft.init.entity.PlasticraftEntities;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItems;
import dev.anvilcraft.plasticraft.inventory.DroneMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 空无人机与四种工具变体共用的物品实现;工种由构造时绑定的工具定义决定,不派生子类。 */
public class DroneItem extends Item implements CreativeVariantPickerItem {
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

    /** 创造物品栏展示的默认条目:装好两个默认白色螺旋桨的空无人机。 */
    public static ItemStack creativePickerSource() {
        ItemStack stack = new ItemStack(PlasticraftItems.DRONE.get());
        DroneData.set(stack, DroneData.assembled(
            DroneToolDefinitions.NONE.id(),
            DroneDefaultPropeller.stack(),
            DroneDefaultPropeller.stack()
        ));
        return stack;
    }

    /** 叠加层变体:同一份螺旋桨与设置数据换装全部已注册工具,首格是空无人机。 */
    @Override
    public List<ItemStack> createCreativePickerVariants(ItemStack source) {
        DroneData data = DroneData.get(source).orElseGet(() -> DroneData.assembled(
            this.definition.id(),
            DroneDefaultPropeller.stack(),
            DroneDefaultPropeller.stack()
        ));
        Component customName = source.get(DataComponents.CUSTOM_NAME);
        List<ItemStack> variants = new ArrayList<>();
        for (DroneToolDefinition toolDefinition : DroneToolDefinitions.values()) {
            DroneItem item;
            synchronized (BY_TOOL) {
                item = BY_TOOL.get(toolDefinition.id());
            }
            if (item == null) continue;
            ItemStack variant = new ItemStack(item);
            DroneData.set(variant, new DroneData(
                toolDefinition.id(),
                data.leftPropeller().copy(),
                data.rightPropeller().copy(),
                data.energy(),
                data.owner(),
                data.shortageStrategy(),
                data.collectionInventory()
            ));
            if (customName != null) {
                variant.set(DataComponents.CUSTOM_NAME, customName);
            }
            variants.add(variant);
        }
        return variants;
    }

    /** 潜行右击打开与实体形态相同的单机设置界面。 */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isSecondaryUseActive()) return InteractionResultHolder.pass(stack);
        if (player instanceof ServerPlayer serverPlayer) {
            DroneMenu.openForItem(serverPlayer, hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        // 潜行右击方块面同样进入设置界面,交给 use 统一处理。
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
        tooltip.add(Component
            .translatable(
                "tooltip.anvilcraftplasticraft.drone.energy",
                String.format(Locale.ROOT, "%,d", data.energy()),
                String.format(Locale.ROOT, "%,d", DroneEnergyModel.capacity())
            )
            .withStyle(ChatFormatting.DARK_GRAY));
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
