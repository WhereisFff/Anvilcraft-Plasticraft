package dev.anvilcraft.plasticraft.entity;

import dev.anvilcraft.plasticraft.api.entity.ElasticCollisionEntity;
import dev.anvilcraft.plasticraft.entity.collision.BuiltInPlasticEntityModels;
import dev.anvilcraft.plasticraft.entity.collision.PlasticEntityGeometry;
import dev.anvilcraft.plasticraft.entity.physics.ElasticPlasticPhysics;
import dev.anvilcraft.plasticraft.entity.physics.ResinShockDropBehavior;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.item.PlasticItemData;
import dev.dubhe.anvilcraft.api.giantanvil.IShockEntity;
import dev.dubhe.anvilcraft.api.giantanvil.ShockAnvilBehavior;
import dev.dubhe.anvilcraft.block.item.HasMobBlockItem;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import dev.dubhe.anvilcraft.init.item.ModComponents;
import dev.dubhe.anvilcraft.util.BlockMiningEffect;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 初期弹性树脂砧。它有意不提供菜单：空手使用会释放可选的已保存生物组件，
 * 常规放置和贴实体面放置仍由共用基类负责。
 */
public class ResinAnvilEntity extends AbstractPlasticEntity implements ElasticCollisionEntity, IShockEntity {
    private static final PlasticEntityGeometry GEOMETRY = BuiltInPlasticEntityModels.RESIN_ANVIL.geometry();
    private static final ShockAnvilBehavior SHOCK_ANVIL_BEHAVIOR = new ShockAnvilBehavior(
        BlockMiningEffect.NORMAL,
        ResinShockDropBehavior.INSTANCE
    );

    private static Supplier<ItemStack> defaultDropSupplier = () -> ItemStack.EMPTY;
    private final ElasticPlasticPhysics elasticPhysics = new ElasticPlasticPhysics(this);

    public static void configureDefaultDrop(Supplier<ItemStack> supplier) {
        defaultDropSupplier = Objects.requireNonNull(supplier, "supplier");
    }

    public ResinAnvilEntity(EntityType<? extends ResinAnvilEntity> entityType, Level level) {
        super(entityType, level);
        this.setDisplayState(PlasticraftBlocks.RESIN_ANVIL.get().defaultBlockState());
    }

    public ResinAnvilEntity(
        EntityType<? extends ResinAnvilEntity> entityType,
        Level level,
        Vec3 position,
        BlockState displayState,
        ItemStack dropStack,
        PlasticEntityOrientation orientation
    ) {
        super(entityType, level, position, displayState, dropStack, orientation);
    }

    @Override
    protected String materialKey() {
        return "resin";
    }

    @Override
    protected PlasticEntityGeometry getLocalGeometry() {
        return GEOMETRY;
    }

    @Override
    protected ItemStack createDefaultDropStack() {
        ItemStack stack = defaultDropSupplier.get();
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        stack = stack.copyWithCount(1);
        PlasticItemData.setMaterial(stack, "resin");
        return stack;
    }

    @Override
    protected ItemStack prepareInitialPickResult(ItemStack stack) {
        stack.remove(ModComponents.SAVED_ENTITY);
        return stack;
    }

    @Override
    public double anvilcraft$getShockBounceHeightMultiplier() {
        return 2.0D;
    }

    @Override
    public Optional<ShockAnvilBehavior> anvilcraft$getShockAnvilBehavior() {
        return Optional.of(SHOCK_ANVIL_BEHAVIOR);
    }

    @Override
    public Optional<BlockState> anvilcraft$getShockBaseState() {
        return Optional.of(ModBlocks.RESIN_BLOCK.getDefaultState());
    }

    public boolean hasCapturedMob() {
        return this.getDropStack().has(ModComponents.SAVED_ENTITY);
    }

    @Override
    protected Vec3 resolveCollisionVelocity(
        Vec3 requestedMovement,
        Vec3 actualMovement,
        Vec3 velocityAfterMove,
        Direction gravityDirection
    ) {
        return this.elasticPhysics.resolveCollisionVelocity(
            requestedMovement, actualMovement, velocityAfterMove, gravityDirection, this.isOnSlidingRail()
        );
    }

    @Override
    public void plasticraft$onEntityCollision(
        Entity collider,
        AABB colliderStartBox,
        Vec3 requestedMovement,
        Vec3 actualMovement,
        Vec3 incomingVelocity
    ) {
        this.elasticPhysics.onEntityCollision(collider, colliderStartBox, requestedMovement, actualMovement, incomingVelocity);
    }

    @Override
    protected SoundEvent impactSound() {
        return ModBlocks.RESIN_BLOCK.getDefaultState().getSoundType().getHitSound();
    }

    @Override
    protected InteractionResult interactNormally(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!held.isEmpty() || !this.hasCapturedMob()) {
            return InteractionResult.PASS;
        }
        if (this.level().isClientSide) return InteractionResult.SUCCESS;
        return this.releaseCapturedMob(player, this.getOrientation().attachmentFace())
            ? InteractionResult.CONSUME
            : InteractionResult.FAIL;
    }

    /** 在树脂开口上方释放已保存实体，并保留砧物品。 */
    private boolean releaseCapturedMob(Player player, Direction releaseFace) {
        ItemStack stored = this.getDropStack();
        Entity entity = HasMobBlockItem.getMobFromItem(this.level(), stored);
        if (!(entity instanceof Mob mob)) return false;

        Vec3 center = this.getBoundingBox().getCenter();
        double offset = 0.55D + Math.max(mob.getBbWidth(), mob.getBbHeight()) * 0.5D;
        Vec3 target = center.add(Vec3.atLowerCornerOf(releaseFace.getNormal()).scale(offset));
        mob.moveTo(target.x, target.y, target.z, mob.getYRot(), mob.getXRot());
        mob.setDeltaMovement(Vec3.ZERO);
        if (!this.level().noCollision(mob, mob.getBoundingBox().inflate(0.02D))) return false;
        if (!this.level().addFreshEntity(mob)) return false;

        stored.remove(ModComponents.SAVED_ENTITY);
        this.setDropStack(stored);
        this.level().playSound(
            null,
            this.blockPosition(),
            ModBlocks.RESIN_BLOCK.getDefaultState().getSoundType().getPlaceSound(),
            SoundSource.BLOCKS,
            0.8F,
            0.9F + this.random.nextFloat() * 0.2F
        );
        this.gameEvent(GameEvent.ENTITY_PLACE, player);
        return true;
    }
}
