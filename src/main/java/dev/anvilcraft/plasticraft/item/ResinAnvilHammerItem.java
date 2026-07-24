package dev.anvilcraft.plasticraft.item;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.api.tooltip.PlasticItemTooltipManager;
import dev.anvilcraft.plasticraft.init.block.ModBlocks;
import dev.dubhe.anvilcraft.api.event.AnvilEvent;
import dev.dubhe.anvilcraft.item.AnvilHammerItem;
import dev.dubhe.anvilcraft.util.TriggerUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.NeoForge;

/** 继承本体全部铁砧锤能力，并提供树脂材质的轻量攻击与修复规则。 */
public class ResinAnvilHammerItem extends AnvilHammerItem {
    public static final double KNOCKBACK_STRENGTH = 2.5D;

    private static final ItemAttributeModifiers ATTRIBUTES = ItemAttributeModifiers.builder()
        .add(
            Attributes.ATTACK_DAMAGE,
            new AttributeModifier(BASE_ATTACK_DAMAGE_ID, -1.0D, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.MAINHAND
        )
        .add(
            Attributes.ATTACK_SPEED,
            new AttributeModifier(BASE_ATTACK_SPEED_ID, 0.0D, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.MAINHAND
        )
        .build();

    public ResinAnvilHammerItem(Properties properties) {
        super(properties);
        PlasticItemTooltipManager.register(
            AnvilcraftPlasticraft.of("resin_anvil_hammer"),
            "A lightweight anvil hammer made from an elastic resin anvil",
            """
                Retains every standard Anvil Hammer function except the portable anvil menu
                Left-clicking any entity deals no damage and applies Knockback V
                Left-clicking a block launches you opposite your full view direction with Knockback V
                Has 35 durability and can be repaired with resin
                Repairs in a Hardened Resin Anvil cost no experience and add no prior-work penalty"""
        );
    }

    /** 发布树脂铁砧落地事件，并使用树脂击打音效替代原版铁砧落地音效。 */
    public static boolean triggerAnvilImpact(Player player, Level level, BlockPos impactPos) {
        if (level.isClientSide) return false;
        ItemStack hammerStack = player.getMainHandItem();
        if (!(hammerStack.getItem() instanceof ResinAnvilHammerItem hammer)) return false;
        if (player.getCooldowns().isOnCooldown(hammer)) return false;

        player.getCooldowns().addCooldown(hammer, 5);
        FallingBlockEntity dummyAnvil = new FallingBlockEntity(EntityType.FALLING_BLOCK, level);
        dummyAnvil.blockState = hammer.getAnvil().defaultBlockState();
        NeoForge.EVENT_BUS.post(new AnvilEvent.OnLand(
            level,
            impactPos.above(),
            dummyAnvil,
            player.fallDistance
        ));
        level.playSound(
            null,
            impactPos,
            dev.dubhe.anvilcraft.init.block.ModBlocks.RESIN_BLOCK.getDefaultState().getSoundType().getHitSound(),
            SoundSource.PLAYERS,
            0.8F,
            0.9F + player.getRandom().nextFloat() * 0.2F
        );
        hammerStack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(InteractionHand.MAIN_HAND));
        TriggerUtil.anvilHammerClickBlock(level, impactPos, "left_click");
        return true;
    }

    /** 攻击事件会先完成击退与耐久处理；返回 true 可阻止原版伤害流程。 */
    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        return true;
    }

    /** 防止绕过常规玩家攻击入口的调用重新触发本体铁砧坠落伤害。 */
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.hurtAndBreak(1, attacker, LivingEntity.getSlotForHand(InteractionHand.MAIN_HAND));
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        return InteractionResultHolder.pass(player.getItemInHand(usedHand));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
        return stack;
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repairCandidate) {
        return repairCandidate.is(dev.dubhe.anvilcraft.init.item.ModItems.RESIN.get());
    }

    @Override
    protected float getAttackDamageModifierAmount() {
        return -1.0F;
    }

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
        return ATTRIBUTES;
    }

    @Override
    public Block getAnvil() {
        return ModBlocks.RESIN_ANVIL.get();
    }
}
