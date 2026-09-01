package dev.anvilcraft.plasticraft.entity;

import dev.anvilcraft.plasticraft.entity.collision.BuiltInPlasticEntityModels;
import dev.anvilcraft.plasticraft.entity.collision.PlasticEntityGeometry;
import dev.anvilcraft.plasticraft.entity.physics.PlasticEntityPhysics;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.inventory.HardenedResinAnvilMenu;
import dev.anvilcraft.plasticraft.item.PlasticItemData;
import dev.anvilcraft.plasticraft.item.ResinAnvilHammerItem;
import dev.dubhe.anvilcraft.api.event.AnvilEvent;
import dev.dubhe.anvilcraft.api.giantanvil.IShockEntity;
import dev.dubhe.anvilcraft.api.giantanvil.ShockAnvilBehavior;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** 具体的硬化树脂砧，共用移动逻辑位于抽象基类中。 */
public class HardenedResinAnvilEntity extends AbstractPlasticEntity implements IShockEntity {
    private static final PlasticEntityGeometry GEOMETRY = BuiltInPlasticEntityModels.HARDENED_RESIN_ANVIL.geometry();
    private static final double MIN_DAMAGE_SPEED = 0.58D;
    private static final float MIN_IMPACT_DAMAGE = 1.0F;
    private static final float HAMMER_IMPACT_DAMAGE = 10.0F;
    private static final float MAX_IMPACT_DAMAGE = 20.0F;
    private static final double DAMAGE_PER_SPEED = (HAMMER_IMPACT_DAMAGE - MIN_IMPACT_DAMAGE)
        / (ResinAnvilHammerItem.KNOCKBACK_STRENGTH - MIN_DAMAGE_SPEED);
    private static Supplier<ItemStack> defaultDropSupplier = () -> ItemStack.EMPTY;

    /** 配置旧实体或存档加载的实体没有明确物品堆时返回的物品。 */
    public static void configureDefaultDrop(Supplier<ItemStack> supplier) {
        defaultDropSupplier = Objects.requireNonNull(supplier, "supplier");
    }

    public HardenedResinAnvilEntity(EntityType<? extends HardenedResinAnvilEntity> entityType, Level level) {
        super(entityType, level);
        this.setDisplayState(PlasticraftBlocks.HARDEND_RESIN_ANVIL.get().defaultBlockState());
    }

    public HardenedResinAnvilEntity(
        EntityType<? extends HardenedResinAnvilEntity> entityType,
        Level level,
        Vec3 position,
        BlockState displayState,
        ItemStack dropStack,
        PlasticEntityOrientation orientation
    ) {
        super(entityType, level, position, displayState, dropStack, orientation);
    }

    @Override
    protected PlasticEntityGeometry getLocalGeometry() {
        return GEOMETRY;
    }

    @Override
    protected ItemStack createDefaultDropStack() {
        ItemStack fallback = defaultDropSupplier.get();
        if (fallback == null || fallback.isEmpty()) {
            return ItemStack.EMPTY;
        }
        fallback = fallback.copy();
        PlasticItemData.setMaterial(fallback, "hardened_resin");
        return fallback;
    }

    @Override
    protected InteractionResult interactNormally(Player player, InteractionHand hand) {
        if (this.level().isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
        HardenedResinAnvilMenu.open(serverPlayer, this);
        player.awardStat(Stats.INTERACT_WITH_ANVIL);
        this.gameEvent(GameEvent.ENTITY_INTERACT, player);
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onEntityImpact(Entity support, Direction impactDirection, float impactSpeed) {
        super.onEntityImpact(support, impactDirection, impactSpeed);
        if (this.level().isClientSide
            || !EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(support)
            || !EntitySelector.LIVING_ENTITY_STILL_ALIVE.test(support)) {
            return;
        }
        float damage = impactDamage(impactSpeed);
        if (damage <= 0.0F) return;

        DamageSource source = this.damageSources().anvil(this);
        if (support.isInvulnerableTo(source)) return;
        NeoForge.EVENT_BUS.post(new AnvilEvent.HurtEntity(
            this,
            PlasticEntityPhysics.landingPosition(this, impactDirection),
            this.level(),
            support,
            damage
        ));
        support.hurt(source, damage);
    }

    /** 五格自由落体从一伤害起步，树脂铁砧锤的 2.5 速度恰好落在十伤害档。 */
    private static float impactDamage(double speed) {
        if (speed < MIN_DAMAGE_SPEED) return 0.0F;
        int damage = Mth.floor(MIN_IMPACT_DAMAGE + (speed - MIN_DAMAGE_SPEED) * DAMAGE_PER_SPEED + 1.0E-6D);
        return Math.min(damage, MAX_IMPACT_DAMAGE);
    }

    @Override
    public double anvilcraft$getShockBounceHeightMultiplier() {
        return 1.0D;
    }

    @Override
    public Optional<ShockAnvilBehavior> anvilcraft$getShockAnvilBehavior() {
        return Optional.of(ShockAnvilBehavior.NORMAL);
    }
}
