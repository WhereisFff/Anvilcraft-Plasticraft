package dev.anvilcraft.plasticraft.entity.drone;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.drone.DroneData;
import dev.anvilcraft.plasticraft.drone.DronePropellerTraits;
import dev.anvilcraft.plasticraft.drone.DroneShortageStrategy;
import dev.anvilcraft.plasticraft.drone.tool.DroneToolDefinition;
import dev.anvilcraft.plasticraft.drone.tool.DroneToolDefinitions;
import dev.anvilcraft.plasticraft.item.DroneItem;
import dev.dubhe.anvilcraft.item.AnvilHammerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 通用机械无人机实体。建设、拆除、收集和观察是由已安装工具定义的物品变体,
 * 世界中只注册这一个实体类型;工种差异全部经 {@link DroneToolDefinition} 表达,
 * 因此该类不出现按工种分支的行为代码。
 */
public class DroneEntity extends Entity {
    private static final EntityDataAccessor<String> DATA_TOOL_ID =
        SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<ItemStack> DATA_LEFT_PROPELLER =
        SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> DATA_RIGHT_PROPELLER =
        SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Byte> DATA_ACTION_STATE =
        SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.BYTE);

    private ResourceLocation cachedToolId = DroneToolDefinitions.CONSTRUCTION.id();
    private int energy;
    private UUID owner;
    private DroneShortageStrategy shortageStrategy = DroneShortageStrategy.PAUSE;
    private List<ItemStack> collectionInventory = new ArrayList<>();

    public DroneEntity(EntityType<? extends DroneEntity> entityType, Level level) {
        super(entityType, level);
        this.blocksBuilding = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_TOOL_ID, DroneToolDefinitions.CONSTRUCTION.id().toString());
        builder.define(DATA_LEFT_PROPELLER, ItemStack.EMPTY);
        builder.define(DATA_RIGHT_PROPELLER, ItemStack.EMPTY);
        builder.define(DATA_ACTION_STATE, (byte) 0);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (DATA_TOOL_ID.equals(accessor)) {
            this.cachedToolId = ResourceLocation.parse(this.entityData.get(DATA_TOOL_ID));
        }
    }

    @Override
    public void tick() {
        super.tick();
        // 无任务无人机在地面等待;飞行状态机属于能源系统 TODO,这里只保证
        // 自由落体、着地静止以及被推动后经过方块碰撞的位移。
        if (!this.isNoGravity()) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.04D, 0.0D));
        }
        this.move(MoverType.SELF, this.getDeltaMovement());
        Vec3 motion = this.getDeltaMovement();
        if (this.onGround()) {
            this.setDeltaMovement(motion.x * 0.6D, motion.y < 0.0D ? 0.0D : motion.y * 0.98D, motion.z * 0.6D);
        } else {
            this.setDeltaMovement(motion.x * 0.91D, motion.y * 0.98D, motion.z * 0.91D);
        }
        this.pushOverlappingDrones();
    }

    /** 碰撞箱真正重叠的无人机互相推开;整齐堆放的相邻碰撞箱不会触发。 */
    private void pushOverlappingDrones() {
        List<Entity> drones = this.level().getEntities(
            this,
            this.getBoundingBox(),
            entity -> entity instanceof DroneEntity && entity.isPushable()
        );
        for (Entity drone : drones) {
            this.push(drone);
        }
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
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean fireImmune() {
        return super.fireImmune()
            || DronePropellerTraits.isFireResistant(this.getLeftPropeller(), this.getRightPropeller());
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && stack.getItem() instanceof AnvilHammerItem) {
            return this.pickUpWithAnvilHammer(player);
        }
        // 右击打开单机设置界面由能源与设置 TODO 提供。
        return InteractionResult.PASS;
    }

    private InteractionResult pickUpWithAnvilHammer(Player player) {
        BlockPos occupiedPos = this.blockPosition();
        if (!player.getAbilities().mayBuild || !this.level().mayInteract(player, occupiedPos)) {
            return InteractionResult.PASS;
        }
        ItemStack drop = this.getDropStack();
        if (drop.isEmpty()) return InteractionResult.FAIL;
        if (this.level().isClientSide) return InteractionResult.SUCCESS;

        player.getInventory().placeItemBackInInventory(drop);
        this.level().playSound(
            null,
            occupiedPos,
            SoundType.COPPER.getBreakSound(),
            SoundSource.BLOCKS,
            0.8F,
            1.0F
        );
        this.gameEvent(GameEvent.ENTITY_INTERACT, player);
        this.discard();
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) return false;
        if (this.level().isClientSide || this.isRemoved()) return true;
        boolean creativePlayer = source.getEntity() instanceof Player player && player.getAbilities().instabuild;
        if (!creativePlayer) {
            this.spawnAtLocation(this.getDropStack());
        }
        this.level().playSound(
            null,
            this.blockPosition(),
            SoundType.COPPER.getBreakSound(),
            SoundSource.BLOCKS,
            0.8F,
            1.0F
        );
        this.discard();
        return true;
    }

    public ResourceLocation toolId() {
        return this.cachedToolId;
    }

    public DroneToolDefinition toolDefinition() {
        return DroneToolDefinitions.getOrFallback(this.toolId());
    }

    public ItemStack getLeftPropeller() {
        return this.entityData.get(DATA_LEFT_PROPELLER);
    }

    public ItemStack getRightPropeller() {
        return this.entityData.get(DATA_RIGHT_PROPELLER);
    }

    public byte getActionState() {
        return this.entityData.get(DATA_ACTION_STATE);
    }

    public Optional<UUID> getOwner() {
        return Optional.ofNullable(this.owner);
    }

    public void setOwner(UUID ownerId) {
        this.owner = ownerId;
    }

    public DroneData toDroneData() {
        return new DroneData(
            this.toolId(),
            this.getLeftPropeller().copy(),
            this.getRightPropeller().copy(),
            this.energy,
            Optional.ofNullable(this.owner),
            this.shortageStrategy,
            List.copyOf(this.collectionInventory)
        );
    }

    public void applyDroneData(DroneData data) {
        this.entityData.set(DATA_TOOL_ID, data.toolId().toString());
        this.cachedToolId = data.toolId();
        this.entityData.set(DATA_LEFT_PROPELLER, data.leftPropeller().copy());
        this.entityData.set(DATA_RIGHT_PROPELLER, data.rightPropeller().copy());
        this.energy = data.energy();
        this.owner = data.owner().orElse(null);
        this.shortageStrategy = data.shortageStrategy();
        this.collectionInventory = new ArrayList<>(data.collectionInventory());
    }

    /** 铁砧锤回收与摧毁掉落共用的完整数据物品;自定义名称随物品往返。 */
    public ItemStack getDropStack() {
        ItemStack stack = new ItemStack(DroneItem.byToolId(this.toolId()));
        DroneData.set(stack, this.toDroneData());
        if (this.hasCustomName()) {
            stack.set(DataComponents.CUSTOM_NAME, this.getCustomName());
        }
        return stack;
    }

    @Override
    public ItemStack getPickResult() {
        return this.getDropStack();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (!tag.contains("DroneData")) return;
        DroneData.CODEC
            .parse(this.registryAccess().createSerializationContext(NbtOps.INSTANCE), tag.get("DroneData"))
            .resultOrPartial(error -> AnvilcraftPlasticraft.LOGGER.error("Failed to load drone data: {}", error))
            .ifPresent(this::applyDroneData);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        DroneData.CODEC
            .encodeStart(this.registryAccess().createSerializationContext(NbtOps.INSTANCE), this.toDroneData())
            .resultOrPartial(error -> AnvilcraftPlasticraft.LOGGER.error("Failed to save drone data: {}", error))
            .ifPresent(encoded -> tag.put("DroneData", encoded));
    }
}
