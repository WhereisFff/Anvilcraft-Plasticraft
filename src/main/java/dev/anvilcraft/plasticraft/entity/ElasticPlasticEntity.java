package dev.anvilcraft.plasticraft.entity;

import dev.anvilcraft.plasticraft.api.entity.ElasticCollisionEntity;
import dev.anvilcraft.plasticraft.entity.physics.ElasticPlasticPhysics;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.item.DyeableMaterial;
import dev.anvilcraft.plasticraft.item.PlasticItemData;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** 保留弹性塑料材料身份的动态成型制品实体。 */
public final class ElasticPlasticEntity extends UniversalPlasticEntity implements ElasticCollisionEntity {
    private final ElasticPlasticPhysics elasticPhysics = new ElasticPlasticPhysics(this, true);

    public ElasticPlasticEntity(EntityType<? extends UniversalPlasticEntity> entityType, Level level) {
        super(entityType, level);
        this.setDisplayState(PlasticraftBlocks.ELASTIC_PLASTIC.get().defaultBlockState());
    }

    public ElasticPlasticEntity(
        EntityType<? extends UniversalPlasticEntity> entityType,
        Level level,
        Vec3 position,
        BlockState displayState,
        ItemStack dropStack,
        PlasticEntityOrientation orientation
    ) {
        super(entityType, level, position, displayState, dropStack, orientation);
    }

    @Override
    protected ItemStack createDefaultDropStack() {
        ItemStack stack = PlasticraftBlocks.ELASTIC_PLASTIC.asStack();
        PlasticItemData.setMaterial(stack, this.materialKey());
        BlockState state = this.getDisplayState();
        if (state.hasProperty(DyeableMaterial.COLOR)) {
            PlasticMeltColor.set(stack, state.getValue(DyeableMaterial.COLOR));
        }
        if (this.isMagnetized()) PlasticItemData.setMagnetized(stack, true);
        return stack;
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
    protected String materialKey() {
        return "elastic_plastic";
    }

    @Override
    protected SoundEvent impactSound() {
        return ModBlocks.RESIN_BLOCK.getDefaultState().getSoundType().getHitSound();
    }

    @Override
    public double anvilcraft$getShockBounceHeightMultiplier() {
        return 2.0D;
    }
}
