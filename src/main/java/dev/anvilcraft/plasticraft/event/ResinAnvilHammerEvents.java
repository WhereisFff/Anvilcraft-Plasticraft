package dev.anvilcraft.plasticraft.event;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.entity.HardenedResinCauldronEntity;
import dev.anvilcraft.plasticraft.item.ResinAnvilHammerItem;
import dev.dubhe.anvilcraft.util.TriggerUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** 树脂铁砧锤在本体铁砧锤流程之外增加的无伤害击退行为。 */
@EventBusSubscriber(modid = AnvilcraftPlasticraft.MOD_ID)
public final class ResinAnvilHammerEvents {
    private ResinAnvilHammerEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void attackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        ItemStack hammer = player.getMainHandItem();
        if (!(hammer.getItem() instanceof ResinAnvilHammerItem)) return;

        knockbackFromView(event.getTarget(), player);
        player.resetAttackStrengthTicker();
        if (event.getTarget() instanceof HardenedResinCauldronEntity) {
            event.getTarget().hurt(player.damageSources().playerAttack(player), 0.0F);
            return;
        }
        if (player.level().isClientSide) return;

        hammer.hurtAndBreak(1, player, LivingEntity.getSlotForHand(InteractionHand.MAIN_HAND));
        TriggerUtil.anvilHammerHurtEntity(
            player.level(),
            event.getTarget().blockPosition(),
            0.0F
        );
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void leftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START
            || !(event.getItemStack().getItem() instanceof ResinAnvilHammerItem)) {
            return;
        }
        recoilBehindView(event.getEntity());
    }

    /** 反冲沿完整视线的反方向，俯仰角也会影响最终的上下速度。 */
    private static void recoilBehindView(Player player) {
        Vec3 recoil = player.getLookAngle()
            .normalize()
            .scale(-ResinAnvilHammerItem.KNOCKBACK_STRENGTH);
        player.setDeltaMovement(player.getDeltaMovement().scale(0.5D).add(recoil));
        player.hasImpulse = true;
        player.hurtMarked = true;
    }

    /** 使用原版击退附魔的方向与强度换算：击退 V 对应 5 * 0.5。 */
    private static void knockbackFromView(Entity target, Player attacker) {
        float yaw = attacker.getYRot() * Mth.DEG_TO_RAD;
        double directionX = Mth.sin(yaw);
        double directionZ = -Mth.cos(yaw);
        if (target instanceof LivingEntity livingTarget) {
            livingTarget.knockback(ResinAnvilHammerItem.KNOCKBACK_STRENGTH, directionX, directionZ);
        } else {
            target.push(
                -directionX * ResinAnvilHammerItem.KNOCKBACK_STRENGTH,
                0.1D,
                -directionZ * ResinAnvilHammerItem.KNOCKBACK_STRENGTH
            );
        }
        target.hurtMarked = true;
    }
}
