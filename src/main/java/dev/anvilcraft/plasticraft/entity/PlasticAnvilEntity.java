package dev.anvilcraft.plasticraft.entity;

import dev.anvilcraft.plasticraft.block.PlasticAnvilBlock;
import dev.anvilcraft.plasticraft.inventory.PlasticAnvilMenu;
import dev.anvilcraft.plasticraft.init.PlasticBlocks;
import dev.anvilcraft.plasticraft.item.PlasticAnvilItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * A persistent, solid plastic anvil. Unlike a falling block, this entity is
 * deliberately gravity-free and remains in the world until broken.
 */
public class PlasticAnvilEntity extends Entity {
    private static final EntityDataAccessor<BlockState> DISPLAY_STATE = SynchedEntityData.defineId(
        PlasticAnvilEntity.class,
        EntityDataSerializers.BLOCK_STATE
    );
    private static Supplier<ItemStack> defaultDropSupplier = () -> ItemStack.EMPTY;

    private ItemStack dropStack = ItemStack.EMPTY;

    /** Configure the item returned when an old/save-loaded entity has no explicit stack. */
    public static void configureDefaultDrop(Supplier<ItemStack> supplier) {
        defaultDropSupplier = Objects.requireNonNull(supplier, "supplier");
    }

    public PlasticAnvilEntity(EntityType<? extends PlasticAnvilEntity> entityType, Level level) {
        super(entityType, level);
        this.setNoGravity(true);
        this.blocksBuilding = true;
    }

    /** Creates an entity at the supplied bottom-center position. */
    public PlasticAnvilEntity(
        EntityType<? extends PlasticAnvilEntity> entityType,
        Level level,
        Vec3 position,
        BlockState displayState,
        ItemStack dropStack
    ) {
        this(entityType, level);
        this.setPos(position);
        this.xo = position.x;
        this.yo = position.y;
        this.zo = position.z;
        this.setDisplayState(displayState);
        this.setDropStack(dropStack);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DISPLAY_STATE, PlasticBlocks.PLASTIC_ANVIL.get().defaultBlockState());
    }

    public BlockState getDisplayState() {
        return this.entityData.get(DISPLAY_STATE);
    }

    public void setDisplayState(BlockState state) {
        this.entityData.set(DISPLAY_STATE, Objects.requireNonNull(state, "state"));
        this.refreshDimensions();
    }

    public ItemStack getDropStack() {
        if (!this.dropStack.isEmpty()) {
            return this.dropStack.copy();
        }
        ItemStack fallback = defaultDropSupplier.get();
        if (fallback == null || fallback.isEmpty()) {
            return ItemStack.EMPTY;
        }
        fallback = fallback.copy();
        BlockState state = this.getDisplayState();
        if (state.hasProperty(PlasticAnvilBlock.COLOR)) {
            DyeColor color = state.getValue(PlasticAnvilBlock.COLOR);
            if (color != DyeColor.WHITE) {
                PlasticAnvilItem.setColor(fallback, color);
            }
        }
        return fallback;
    }

    public void setDropStack(ItemStack stack) {
        this.dropStack = stack == null ? ItemStack.EMPTY : stack.copy();
        if (this.dropStack.getCount() > 1) {
            this.dropStack.setCount(1);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isRemoved()) {
            return;
        }

        // Entity#tick does not move arbitrary Entity subclasses.  Applying the
        // velocity here lets player/entity pushes resolve through block collision.
        Vec3 movement = this.getDeltaMovement();
        if (!movement.equals(Vec3.ZERO)) {
            this.move(MoverType.SELF, movement);
        }
        this.setDeltaMovement(this.getDeltaMovement().multiply(0.80D, 1.0D, 0.80D));

        // LivingEntity normally performs this pass itself.  A plastic anvil is
        // a bare Entity, so also push entities that stand against it.
        List<Entity> nearby = this.level().getEntities(
            this,
            this.getBoundingBox().inflate(0.20D, 0.02D, 0.20D),
            EntitySelector.pushableBy(this)
        );
        for (Entity entity : nearby) {
            if (!entity.isPassengerOfSameVehicle(this)) {
                this.push(entity);
            }
        }
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return Boat.canVehicleCollide(this, entity);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        // Royal Anvil's model fits inside a full block and needs a full-height
        // collision box so players can stand against/push it.
        return EntityDimensions.scalable(1.0F, 1.0F);
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.NORMAL;
    }

    @Override
    public ItemStack getPickResult() {
        return this.getDropStack();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            PlasticAnvilMenu.open(serverPlayer, this);
            player.awardStat(Stats.INTERACT_WITH_ANVIL);
            this.gameEvent(GameEvent.ENTITY_INTERACT, player);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        }
        if (!this.level().isClientSide && this.level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
            this.spawnAtLocation(this.getDropStack());
            this.discard();
        }
        return true;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put("BlockState", NbtUtils.writeBlockState(this.getDisplayState()));
        ItemStack stack = this.getDropStack();
        if (!stack.isEmpty()) tag.put("DropStack", stack.save(this.registryAccess()));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("BlockState")) {
            this.setDisplayState(NbtUtils.readBlockState(
                this.level().holderLookup(Registries.BLOCK),
                tag.getCompound("BlockState")
            ));
        }
        if (tag.contains("DropStack")) {
            this.setDropStack(ItemStack.parseOptional(this.registryAccess(), tag.getCompound("DropStack")));
        } else if (tag.contains("DropItem")) {
            ResourceLocation key = ResourceLocation.parse(tag.getString("DropItem"));
            this.setDropStack(new ItemStack(BuiltInRegistries.ITEM.get(key)));
        }
    }
}
