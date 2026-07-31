package dev.anvilcraft.plasticraft.block.entity;

import dev.anvilcraft.lib.v2.recipe.cache.IItemHandlerCache;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.AbstractPlasticEntityBlock;
import dev.anvilcraft.plasticraft.block.BondedFallingBlocks;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.CatalyticPressLidEntity;
import dev.anvilcraft.plasticraft.entity.HardenedResinAnvilEntity;
import dev.anvilcraft.plasticraft.entity.HardenedResinCauldronEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.init.block.ModBlocks;
import dev.anvilcraft.plasticraft.inventory.HardenedResinAnvilMenu;
import dev.dubhe.anvilcraft.api.fluid.IFluidHandlerHolder;
import dev.dubhe.anvilcraft.api.injection.tooltip.ITooltipProviderExtension;
import dev.dubhe.anvilcraft.item.AnvilHammerItem;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;

/** 保存被方块化实体完整数据并负责在解胶时恢复实体。 */
public class BondedEntityBlockEntity extends BlockEntity
    implements ITooltipProviderExtension, IItemHandlerCache, IFluidHandlerHolder {
    private static final String TAG_INITIALIZED = "Initialized";
    private static final String TAG_ENTITY = "Entity";
    private static final String TAG_DISPLAY_STATE = "DisplayState";
    private static final String TAG_ATTACHMENT_FACE = "AttachmentFace";
    private static final String TAG_SUPPORT_BLOCK = "SupportBlock";
    private static final String TAG_PLASTIC = "Plastic";
    private static final String TAG_ORIENTATION = "Orientation";
    private static final String TAG_PISTON_MOVABLE = "PistonMovable";
    private static final String TAG_ORIGINAL_NO_GRAVITY = "OriginalNoGravity";
    private static final String TAG_ADHESIVE_LOCAL_FACE = "AdhesiveLocalFace";
    private static final String TAG_PENDING_RETURN_ORIENTATION = "PendingReturnOrientation";
    private static final String TAG_HAMMER_RETURN_AT = "HammerReturnAt";
    private static final String TAG_HAMMER_RETURN_FROM = "HammerReturnFrom";
    private static final String TAG_HAMMER_RETURN_STARTED = "HammerReturnStarted";
    private static final int HAMMER_DEFLECTION_HOLD_TICKS = 2;
    public static final int HAMMER_RETURN_ANIMATION_TICKS = 5;
    private static final String ADHESIVE_TRANSIT_ATTACHMENT =
        AnvilcraftPlasticraft.of("adhesive_transit").toString();
    private static final String ENTITY_BONDS_ATTACHMENT =
        AnvilcraftPlasticraft.of("entity_bonds").toString();

    private boolean initialized;
    private CompoundTag entityTag = new CompoundTag();
    private BlockState displayState = Blocks.AIR.defaultBlockState();
    private Direction attachmentFace = Direction.UP;
    private @Nullable ResourceLocation supportBlockId;
    private boolean plastic;
    private byte orientation = PlasticEntityOrientation.DEFAULT.pack();
    private boolean pistonMovable = true;
    private boolean originalNoGravity;
    private Direction adhesiveLocalFace = Direction.DOWN;
    private @Nullable Byte pendingReturnOrientation;
    private long hammerReturnAt = -1L;
    private byte hammerReturnFrom = PlasticEntityOrientation.DEFAULT.pack();
    private long hammerReturnStarted = -1L;
    private @Nullable Entity renderEntity;
    private static final IItemHandler EMPTY_ITEM_HANDLER = new ItemStackHandler(0);
    private static final IFluidHandler EMPTY_FLUID_HANDLER =
        new FluidTank(0);

    public BondedEntityBlockEntity(
        BlockEntityType<? extends BondedEntityBlockEntity> type,
        BlockPos pos,
        BlockState state
    ) {
        super(type, pos, state);
    }

    public boolean initialize(
        Entity entity,
        BlockState displayState,
        Direction attachmentFace,
        @Nullable PlasticEntityOrientation plasticOrientation,
        boolean originalNoGravity
    ) {
        CompoundTag savedEntity = new CompoundTag();
        if (!entity.save(savedEntity)) return false;
        removeTransitFromSnapshot(savedEntity);
        this.entityTag = savedEntity;
        this.displayState = displayState;
        this.attachmentFace = attachmentFace;
        if (this.level != null) {
            this.supportBlockId = BuiltInRegistries.BLOCK.getKey(
                this.level.getBlockState(this.getSupportPos()).getBlock()
            );
        }
        this.plastic = plasticOrientation != null;
        if (plasticOrientation != null) {
            this.orientation = plasticOrientation.pack();
            this.adhesiveLocalFace = plasticOrientation.localDirection(attachmentFace.getOpposite());
            this.entityTag.putString("AttachmentFace", plasticOrientation.attachmentFace().getName());
            this.entityTag.putInt("InPlaneRotation", plasticOrientation.quarterTurn());
        }
        this.pistonMovable = plasticOrientation != null
            || displayState.getPistonPushReaction() == PushReaction.NORMAL
                && entity.getPistonPushReaction() == PushReaction.NORMAL
                && !(displayState.getBlock() instanceof AnvilBlock);
        this.originalNoGravity = originalNoGravity;
        this.initialized = true;
        this.renderEntity = null;
        this.setChangedAndSync();
        return true;
    }

    private static void removeTransitFromSnapshot(CompoundTag savedEntity) {
        String attachmentsKey = AttachmentHolder.ATTACHMENTS_NBT_KEY;
        if (!savedEntity.contains(attachmentsKey, Tag.TAG_COMPOUND)) return;
        CompoundTag attachments = savedEntity.getCompound(attachmentsKey);
        attachments.remove(ADHESIVE_TRANSIT_ATTACHMENT);
        attachments.remove(ENTITY_BONDS_ATTACHMENT);
        if (attachments.isEmpty()) {
            savedEntity.remove(attachmentsKey);
        } else {
            savedEntity.put(attachmentsKey, attachments);
        }
    }

    public boolean isInitialized() {
        return this.initialized;
    }

    public boolean isPlastic() {
        return this.plastic;
    }

    public boolean isHardenedResinAnvil() {
        return this.displayState.is(ModBlocks.HARDEND_RESIN_ANVIL.get());
    }

    public boolean isPistonMovable() {
        return this.pistonMovable;
    }

    public BlockState getDisplayState() {
        return this.displayState;
    }

    public Direction getAttachmentFace() {
        return this.attachmentFace;
    }

    public PlasticEntityOrientation getPlasticOrientation() {
        return PlasticEntityOrientation.unpack(this.orientation);
    }

    public Direction getAdhesiveLocalFace() {
        return this.adhesiveLocalFace;
    }

    public @Nullable HammerRotationAnimation getHammerRotationAnimation(float partialTick) {
        if (this.hammerReturnStarted < 0L || this.level == null) return null;
        float progress = Math.clamp(
            (this.level.getGameTime() + partialTick - this.hammerReturnStarted) / HAMMER_RETURN_ANIMATION_TICKS,
            0.0F,
            1.0F
        );
        return new HammerRotationAnimation(
            PlasticEntityOrientation.unpack(this.hammerReturnFrom),
            this.getPlasticOrientation(),
            progress
        );
    }

    public boolean isHammerDeflected() {
        return this.pendingReturnOrientation != null || this.hammerReturnStarted >= 0L;
    }

    public boolean canHammerRotateTo(PlasticEntityOrientation targetOrientation) {
        if (!(this.getOrCreateRenderEntity() instanceof AbstractPlasticEntity plasticEntity)) return false;
        Vec3 targetPosition = plasticEntity.plasticraft$placementPosition(this.worldPosition, targetOrientation);
        return plasticEntity.canHammerRotateTo(targetOrientation, targetPosition, this.worldPosition);
    }

    public boolean startHammerDeflection(PlasticEntityOrientation targetOrientation) {
        if (!this.initialized
            || !this.plastic
            || this.level == null
            || !this.canHammerRotateTo(targetOrientation)) {
            return false;
        }
        byte stableOrientation = this.pendingReturnOrientation == null
            ? this.orientation
            : this.pendingReturnOrientation;
        if (targetOrientation.pack() == stableOrientation) return false;

        this.pendingReturnOrientation = stableOrientation;
        this.orientation = targetOrientation.pack();
        this.hammerReturnFrom = this.orientation;
        this.hammerReturnAt = this.level.getGameTime() + HAMMER_DEFLECTION_HOLD_TICKS;
        this.hammerReturnStarted = -1L;
        this.renderEntity = null;
        this.setChangedAndSync();
        return true;
    }

    public BlockPos getSupportPos() {
        return this.worldPosition.relative(this.attachmentFace.getOpposite());
    }

    public boolean hasSupport() {
        if (this.level == null || !this.level.hasChunkAt(this.getSupportPos())) return true;
        BlockState supportState = this.level.getBlockState(this.getSupportPos());
        // 粘附物和支撑同时被活塞搬运时，支撑会短暂变成移动活塞载体。
        if (supportState.is(Blocks.MOVING_PISTON)) return true;
        return !supportState.isAir()
            && (this.supportBlockId == null
                || this.supportBlockId.equals(BuiltInRegistries.BLOCK.getKey(supportState.getBlock())));
    }

    public @Nullable Entity getOrCreateRenderEntity() {
        if (!this.initialized || !this.plastic || this.level == null) return null;
        if (this.renderEntity == null) {
            this.renderEntity = EntityType.loadEntityRecursive(this.entityTag.copy(), this.level, entity -> entity);
        }
        this.positionCachedEntity(this.renderEntity);
        return this.renderEntity;
    }

    public InteractionResult interact(Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(this.getOrCreateRenderEntity() instanceof AbstractPlasticEntity plasticEntity)) {
            return InteractionResult.PASS;
        }
        if (player.getItemInHand(hand).getItem() instanceof AnvilHammerItem) {
            if (player.isShiftKeyDown()) return InteractionResult.PASS;
            return this.useAnvilHammer(player, hand, hit.getDirection());
        }

        Vec3 relativeHit = hit.getLocation().subtract(plasticEntity.position());
        InteractionResult result = plasticEntity.interactAt(player, relativeHit, hand);
        if (result == InteractionResult.PASS) {
            // Keep shift-click semantics identical to the live entity.  The bonded menu
            // provider is only needed for the normal (non-shift) anvil interaction;
            // otherwise a shift-click with an empty hand would unexpectedly open a GUI.
            if (!player.isShiftKeyDown()
                && plasticEntity instanceof HardenedResinAnvilEntity
                && player instanceof ServerPlayer serverPlayer) {
                HardenedResinAnvilMenu.open(serverPlayer, this);
                player.awardStat(Stats.INTERACT_WITH_ANVIL);
                player.gameEvent(GameEvent.ENTITY_INTERACT);
                result = InteractionResult.CONSUME;
            } else {
                result = plasticEntity.interact(player, hand);
            }
        }
        if (!player.level().isClientSide) this.captureCachedEntity(true);
        return result;
    }

    public InteractionResult useAnvilHammer(Player player, InteractionHand hand, Direction interactionFace) {
        if (player.isShiftKeyDown()
            || !(player.getItemInHand(hand).getItem() instanceof AnvilHammerItem)
            || !(this.getOrCreateRenderEntity() instanceof AbstractPlasticEntity plasticEntity)) {
            return InteractionResult.PASS;
        }
        InteractionResult result;
        if (plasticEntity instanceof HardenedResinAnvilEntity && player instanceof ServerPlayer serverPlayer) {
            HardenedResinAnvilMenu.open(serverPlayer, this);
            player.awardStat(Stats.INTERACT_WITH_ANVIL);
            player.gameEvent(GameEvent.ENTITY_INTERACT);
            result = InteractionResult.CONSUME;
        } else {
            result = plasticEntity.plasticraft$useAnvilHammer(player, hand, interactionFace);
        }
        if (!player.level().isClientSide) this.captureCachedEntity(true);
        return result;
    }

    public void tickFunctionalEntity() {
        if (!(this.level instanceof ServerLevel serverLevel)) return;
        this.tickHammerDeflection(serverLevel);
        Entity functionalEntity = this.getOrCreateRenderEntity();
        if (functionalEntity instanceof CatalyticPressLidEntity lid) {
            lid.plasticraft$tickBonded();
            if (lid.plasticraft$consumeBondedDataDirty()) this.captureCachedEntity(true);
            return;
        }
        if (!(functionalEntity instanceof HardenedResinCauldronEntity cauldron)) return;
        cauldron.plasticraft$tickBonded();
        if (cauldron.plasticraft$wasBurnedByLava()) {
            if (!cauldron.plasticraft$leftLavaSource()) {
                serverLevel.removeBlock(this.worldPosition, false);
            }
            return;
        }
        if (cauldron.plasticraft$consumeBondedDataDirty()) this.captureCachedEntity(true);
    }

    public void processAnvilImpact(AbstractPlasticEntity anvil, Direction impactDirection) {
        if (!(this.level instanceof ServerLevel)
            || !(this.getOrCreateRenderEntity() instanceof HardenedResinCauldronEntity cauldron)) {
            return;
        }
        cauldron.processAnvilImpact(anvil, impactDirection);
        this.captureCachedEntity(true);
    }

    @Override
    public IFluidHandler getFluidHandler() {
        return this.getOrCreateRenderEntity() instanceof HardenedResinCauldronEntity cauldron
            ? cauldron.getFluidHandler()
            : EMPTY_FLUID_HANDLER;
    }

    public @Nullable IFluidHandler getCapabilityFluidHandler() {
        return this.getOrCreateRenderEntity() instanceof HardenedResinCauldronEntity cauldron
            ? cauldron.getFluidHandler()
            : null;
    }

    public @Nullable IItemHandler getItemHandler() {
        return this.getOrCreateRenderEntity() instanceof HardenedResinCauldronEntity cauldron
            ? cauldron.getItemHandler()
            : null;
    }

    public boolean clearCauldronOutletFacing(Direction direction) {
        if (!(this.level instanceof ServerLevel)
            || !(this.getOrCreateRenderEntity() instanceof HardenedResinCauldronEntity cauldron)
            || !cauldron.clearOutletFacing(direction)) {
            return false;
        }
        this.captureCachedEntity(true);
        return true;
    }

    @Override
    public IItemHandler getInput() {
        return this.getOrCreateRenderEntity() instanceof HardenedResinCauldronEntity cauldron
            ? cauldron.getInput()
            : EMPTY_ITEM_HANDLER;
    }

    @Override
    public IItemHandler getOutput() {
        return this.getOrCreateRenderEntity() instanceof HardenedResinCauldronEntity cauldron
            ? cauldron.getOutput()
            : EMPTY_ITEM_HANDLER;
    }

    public @Nullable ItemStack insertRecipeOutput(ItemStack stack) {
        if (!(this.level instanceof ServerLevel)
            || !(this.getOrCreateRenderEntity() instanceof HardenedResinCauldronEntity cauldron)) {
            return stack;
        }
        ItemStack remaining = cauldron.insertRecipeOutput(stack);
        this.captureCachedEntity(true);
        return remaining;
    }

    public boolean release() {
        return this.releaseEntity(null) != null;
    }

    public @Nullable Entity releaseEntity(@Nullable Direction changedAttachmentFace) {
        if (!this.initialized || !(this.level instanceof ServerLevel serverLevel)) return null;
        this.captureCachedEntity(false);
        Entity restored = EntityType.loadEntityRecursive(this.entityTag.copy(), serverLevel, entity -> entity);
        if (restored == null) return null;

        if (restored instanceof AbstractPlasticEntity plasticEntity) {
            PlasticEntityOrientation plasticOrientation = this.getPlasticOrientation();
            if (changedAttachmentFace != null) {
                plasticOrientation = new PlasticEntityOrientation(
                    changedAttachmentFace,
                    plasticOrientation.quarterTurn()
                );
            }
            plasticEntity.setOrientation(plasticOrientation);
            plasticEntity.setDisplayState(this.displayState);
            restored.setPos(plasticEntity.plasticraft$placementPosition(this.worldPosition, plasticOrientation));
        } else {
            restored.setPos(
                this.worldPosition.getX() + 0.5D,
                this.worldPosition.getY(),
                this.worldPosition.getZ() + 0.5D
            );
        }
        restored.setDeltaMovement(Vec3.ZERO);
        restored.setNoGravity(this.originalNoGravity);
        restored.fallDistance = 0.0F;

        BlockState fixedState = this.getBlockState();
        CompoundTag savedData = this.saveWithoutMetadata(serverLevel.registryAccess());
        if (!serverLevel.setBlock(this.worldPosition, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)) return null;
        if (serverLevel.addFreshEntity(restored)) {
            BondedFallingBlocks.removeAll(serverLevel, this.worldPosition);
            return restored;
        }

        serverLevel.setBlock(this.worldPosition, fixedState, Block.UPDATE_ALL);
        if (serverLevel.getBlockEntity(this.worldPosition) instanceof BondedEntityBlockEntity replacement) {
            replacement.loadWithComponents(savedData, serverLevel.registryAccess());
            replacement.setChangedAndSync();
        }
        return null;
    }

    public void moved() {
        this.renderEntity = null;
        this.setChangedAndSync();
        if (this.level != null) {
            this.level.scheduleTick(this.worldPosition, this.getBlockState().getBlock(), 2);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        this.captureCachedEntity(false);
        super.saveAdditional(tag, provider);
        this.saveBondData(tag);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        this.initialized = tag.getBoolean(TAG_INITIALIZED);
        this.entityTag = tag.contains(TAG_ENTITY, Tag.TAG_COMPOUND)
            ? tag.getCompound(TAG_ENTITY).copy()
            : new CompoundTag();
        this.displayState = tag.contains(TAG_DISPLAY_STATE, Tag.TAG_COMPOUND)
            ? NbtUtils.readBlockState(provider.lookupOrThrow(Registries.BLOCK), tag.getCompound(TAG_DISPLAY_STATE))
            : Blocks.AIR.defaultBlockState();
        Direction loadedFace = Direction.byName(tag.getString(TAG_ATTACHMENT_FACE));
        this.attachmentFace = loadedFace == null ? Direction.UP : loadedFace;
        this.supportBlockId = tag.contains(TAG_SUPPORT_BLOCK, Tag.TAG_STRING)
            ? ResourceLocation.tryParse(tag.getString(TAG_SUPPORT_BLOCK))
            : null;
        this.plastic = tag.getBoolean(TAG_PLASTIC);
        this.orientation = tag.contains(TAG_ORIENTATION, Tag.TAG_ANY_NUMERIC)
            ? tag.getByte(TAG_ORIENTATION)
            : PlasticEntityOrientation.DEFAULT.pack();
        this.pistonMovable = !tag.contains(TAG_PISTON_MOVABLE) || tag.getBoolean(TAG_PISTON_MOVABLE);
        this.originalNoGravity = tag.getBoolean(TAG_ORIGINAL_NO_GRAVITY);
        Direction loadedAdhesiveFace = Direction.byName(tag.getString(TAG_ADHESIVE_LOCAL_FACE));
        this.adhesiveLocalFace = loadedAdhesiveFace == null
            ? this.getPlasticOrientation().localDirection(this.attachmentFace.getOpposite())
            : loadedAdhesiveFace;
        this.pendingReturnOrientation = tag.contains(TAG_PENDING_RETURN_ORIENTATION, Tag.TAG_ANY_NUMERIC)
            ? tag.getByte(TAG_PENDING_RETURN_ORIENTATION)
            : null;
        this.hammerReturnAt = tag.contains(TAG_HAMMER_RETURN_AT, Tag.TAG_ANY_NUMERIC)
            ? tag.getLong(TAG_HAMMER_RETURN_AT)
            : -1L;
        this.hammerReturnFrom = tag.contains(TAG_HAMMER_RETURN_FROM, Tag.TAG_ANY_NUMERIC)
            ? tag.getByte(TAG_HAMMER_RETURN_FROM)
            : this.orientation;
        this.hammerReturnStarted = tag.contains(TAG_HAMMER_RETURN_STARTED, Tag.TAG_ANY_NUMERIC)
            ? tag.getLong(TAG_HAMMER_RETURN_STARTED)
            : -1L;
        this.renderEntity = null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        this.captureCachedEntity(false);
        CompoundTag tag = super.getUpdateTag(provider);
        this.saveBondData(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public List<Component> anvilcraft$getTooltip() {
        return this.initialized
            ? List.of(Component.translatable("tooltip.anvilcraftplasticraft.bonded"))
            : List.of();
    }

    private void saveBondData(CompoundTag tag) {
        tag.putBoolean(TAG_INITIALIZED, this.initialized);
        if (!this.initialized) return;
        tag.put(TAG_ENTITY, this.entityTag.copy());
        tag.put(TAG_DISPLAY_STATE, NbtUtils.writeBlockState(this.displayState));
        tag.putString(TAG_ATTACHMENT_FACE, this.attachmentFace.getName());
        if (this.supportBlockId != null) tag.putString(TAG_SUPPORT_BLOCK, this.supportBlockId.toString());
        tag.putBoolean(TAG_PLASTIC, this.plastic);
        tag.putByte(TAG_ORIENTATION, this.orientation);
        tag.putBoolean(TAG_PISTON_MOVABLE, this.pistonMovable);
        tag.putBoolean(TAG_ORIGINAL_NO_GRAVITY, this.originalNoGravity);
        tag.putString(TAG_ADHESIVE_LOCAL_FACE, this.adhesiveLocalFace.getName());
        if (this.pendingReturnOrientation != null) {
            tag.putByte(TAG_PENDING_RETURN_ORIENTATION, this.pendingReturnOrientation);
            tag.putLong(TAG_HAMMER_RETURN_AT, this.hammerReturnAt);
        }
        if (this.hammerReturnStarted >= 0L) {
            tag.putByte(TAG_HAMMER_RETURN_FROM, this.hammerReturnFrom);
            tag.putLong(TAG_HAMMER_RETURN_STARTED, this.hammerReturnStarted);
        }
    }

    private void tickHammerDeflection(ServerLevel level) {
        long gameTime = level.getGameTime();
        if (this.pendingReturnOrientation != null && gameTime >= this.hammerReturnAt) {
            this.hammerReturnFrom = this.orientation;
            this.orientation = this.pendingReturnOrientation;
            this.pendingReturnOrientation = null;
            this.hammerReturnAt = -1L;
            this.hammerReturnStarted = gameTime;
            this.renderEntity = null;
            this.setChangedAndSync();
            return;
        }
        if (this.hammerReturnStarted >= 0L
            && gameTime - this.hammerReturnStarted >= HAMMER_RETURN_ANIMATION_TICKS) {
            this.hammerReturnStarted = -1L;
            this.setChangedAndSync();
        }
    }

    private void setChangedAndSync() {
        this.setChanged();
        Level currentLevel = this.level;
        if (currentLevel == null) return;
        currentLevel.invalidateCapabilities(this.worldPosition);
        BlockState state = this.getBlockState();
        currentLevel.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_CLIENTS);
    }

    private void positionCachedEntity(@Nullable Entity entity) {
        if (!(entity instanceof AbstractPlasticEntity plasticEntity)) return;
        PlasticEntityOrientation plasticOrientation = this.getPlasticOrientation();
        plasticEntity.setOrientation(plasticOrientation);
        plasticEntity.setDisplayState(this.displayState);
        plasticEntity.setPos(plasticEntity.plasticraft$placementPosition(this.worldPosition, plasticOrientation));
        plasticEntity.setNoGravity(true);
        plasticEntity.setDeltaMovement(Vec3.ZERO);
    }

    private void captureCachedEntity(boolean sync) {
        if (!this.initialized || this.renderEntity == null || this.level == null || this.level.isClientSide) return;
        CompoundTag savedEntity = new CompoundTag();
        if (!this.renderEntity.save(savedEntity)) return;
        this.entityTag = savedEntity;
        if (this.renderEntity instanceof AbstractPlasticEntity plasticEntity) {
            this.displayState = plasticEntity.getDisplayState();
            BlockState fixedState = this.getBlockState();
            if (fixedState.hasProperty(AbstractPlasticEntityBlock.MAGNETIZED)
                && this.displayState.hasProperty(AbstractPlasticEntityBlock.MAGNETIZED)) {
                boolean magnetized = this.displayState.getValue(AbstractPlasticEntityBlock.MAGNETIZED);
                if (fixedState.getValue(AbstractPlasticEntityBlock.MAGNETIZED) != magnetized) {
                    this.level.setBlock(
                        this.worldPosition,
                        fixedState.setValue(AbstractPlasticEntityBlock.MAGNETIZED, magnetized),
                        Block.UPDATE_CLIENTS
                    );
                }
            }
        }
        if (sync) this.setChangedAndSync();
    }

    public record HammerRotationAnimation(
        PlasticEntityOrientation from,
        PlasticEntityOrientation to,
        float progress
    ) {
    }
}
