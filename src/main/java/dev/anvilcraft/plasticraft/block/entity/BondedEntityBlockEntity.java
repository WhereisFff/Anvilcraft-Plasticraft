package dev.anvilcraft.plasticraft.block.entity;

import dev.anvilcraft.lib.v2.recipe.cache.ItemResourceHandlerCache;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.AbstractPlasticEntityBlock;
import dev.anvilcraft.plasticraft.block.BondedFallingBlocks;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.HardenedResinAnvilEntity;
import dev.anvilcraft.plasticraft.entity.MoldedLargeCauldronInteraction;
import dev.anvilcraft.plasticraft.entity.PlasticCauldron;
import dev.anvilcraft.plasticraft.entity.PlasticCauldrons;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.entity.collision.BondedPlasticShapeIndex;
import dev.anvilcraft.plasticraft.entity.redstone.MoldedTrayLightSource;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.inventory.HardenedResinAnvilMenu;
import dev.anvilcraft.plasticraft.molding.product.storage.MoldedPlasticCrateAccess;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import dev.anvilcraft.plasticraft.recipe.CauldronImpactRecipeProcessor;
import dev.dubhe.anvilcraft.api.fluid.IFluidResourceHandlerHolder;
import dev.dubhe.anvilcraft.api.injection.tooltip.ITooltipProviderExtension;
import dev.dubhe.anvilcraft.item.tool.AnvilHammerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
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
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.transfer.EmptyResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** 保存被方块化实体完整数据并负责在解胶时恢复实体。 */
public class BondedEntityBlockEntity extends BlockEntity
    implements ITooltipProviderExtension, ItemResourceHandlerCache, IFluidResourceHandlerHolder {
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
    private @Nullable Identifier supportBlockId;
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
    private @Nullable VoxelShape bondedInteractionShape;
    private @Nullable VoxelShape bondedCollisionShape;
    private static final ResourceHandler<ItemResource> EMPTY_ITEM_HANDLER = EmptyResourceHandler.instance();
    // 砧工艺的锅谓词会查询第一个槽位；无可用锅时，对它报告零容量。
    private static final ResourceHandler<FluidResource> EMPTY_FLUID_HANDLER = new FluidStacksResourceHandler(1, 0);

    public BondedEntityBlockEntity(
        BlockEntityType<? extends BondedEntityBlockEntity> type,
        BlockPos pos,
        BlockState state
    ) {
        super(type, pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        BondedPlasticShapeIndex.refresh(this);
    }

    @Override
    public void setRemoved() {
        if (this.renderEntity instanceof UniversalPlasticEntity universal) {
            universal.plasticraft$removeVirtualRedstone();
        }
        if (this.level != null) BondedPlasticShapeIndex.remove(this.level, this.worldPosition);
        super.setRemoved();
    }

    public boolean initialize(
        Entity entity,
        BlockState displayState,
        Direction attachmentFace,
        @Nullable PlasticEntityOrientation plasticOrientation,
        boolean originalNoGravity
    ) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, entity.registryAccess());
        if (!entity.save(output)) return false;
        CompoundTag savedEntity = output.buildResult();
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
        this.clearBondedShapeCache();
        this.setChangedAndSync();
        return true;
    }

    private static void removeTransitFromSnapshot(CompoundTag savedEntity) {
        String attachmentsKey = AttachmentHolder.ATTACHMENTS_NBT_KEY;
        if (!savedEntity.contains(attachmentsKey)) return;
        CompoundTag attachments = savedEntity.getCompoundOrEmpty(attachmentsKey);
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
        return this.displayState.is(PlasticraftBlocks.HARDEND_RESIN_ANVIL.get());
    }

    public boolean isMoldedAnvil() {
        return this.getOrCreateRenderEntity() instanceof UniversalPlasticEntity universal
            && universal.isMoldedAnvil();
    }

    public boolean isAnvil() {
        return this.isHardenedResinAnvil() || this.isMoldedAnvil();
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
        Vec3 targetPosition = plasticEntity.plasticraft$placementPosition(
            this.worldPosition,
            targetOrientation,
            this.adhesiveLocalFace
        );
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
        this.clearBondedShapeCache();
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
            this.renderEntity = EntityType.loadEntityRecursive(this.entityTag.copy(), this.level, EntitySpawnReason.LOAD, entity -> entity);
        }
        if (this.renderEntity instanceof UniversalPlasticEntity universal) {
            universal.setMoldedContentsChangedListener(() -> this.captureCachedEntity(true));
        }
        this.positionCachedEntity(this.renderEntity);
        if (this.renderEntity instanceof UniversalPlasticEntity universal) {
            MoldedTrayLightSource.update(universal);
        }
        return this.renderEntity;
    }

    public @Nullable VoxelShape getBondedInteractionShape() {
        if (this.bondedInteractionShape != null) return this.bondedInteractionShape;
        if (!(this.getOrCreateRenderEntity() instanceof UniversalPlasticEntity universal)) return null;
        this.bondedInteractionShape = universal.plasticraft$getGeometry()
            .placedInteractionShape(this.getPlasticOrientation(), this.adhesiveLocalFace);
        return this.bondedInteractionShape;
    }

    public @Nullable VoxelShape getBondedCollisionShape() {
        if (this.bondedCollisionShape != null) return this.bondedCollisionShape;
        if (!(this.getOrCreateRenderEntity() instanceof UniversalPlasticEntity universal)) return null;
        this.bondedCollisionShape = universal.plasticraft$getGeometry()
            .placedCollisionShape(this.getPlasticOrientation(), this.adhesiveLocalFace);
        return this.bondedCollisionShape;
    }

    public ItemStack getStoredDropStack() {
        return this.getOrCreateRenderEntity() instanceof AbstractPlasticEntity plasticEntity
            ? plasticEntity.getDropStack()
            : ItemStack.EMPTY;
    }

    public InteractionResult interact(Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(this.getOrCreateRenderEntity() instanceof AbstractPlasticEntity plasticEntity)) {
            return InteractionResult.PASS;
        }
        if (player.getItemInHand(hand).getItem() instanceof AnvilHammerItem) {
            if (player.isShiftKeyDown()) return InteractionResult.PASS;
            return this.useAnvilHammer(player, hand, hit.getDirection());
        }

        if (!player.isShiftKeyDown() && !player.isSpectator()
            && plasticEntity instanceof UniversalPlasticEntity crate
            && crate.getMoldedData().filter(data -> MoldingProductTypes.isCrate(data.finalType())).isPresent()) {
            if (player instanceof ServerPlayer serverPlayer) {
                MoldedPlasticCrateAccess.open(serverPlayer, crate, this.getBlockPos());
                this.captureCachedEntity(true);
            }
            return InteractionResult.SUCCESS;
        }
        Vec3 relativeHit = hit.getLocation().subtract(plasticEntity.position());
        InteractionResult result = plasticEntity.interactAt(player, relativeHit, hand);
        if (result == InteractionResult.PASS) {
            // 粘合态只在非潜行的普通铁砧交互中补充菜单，否则潜行空手会意外打开界面。
            if (!player.isShiftKeyDown()
                && hasAnvilMenu(plasticEntity)
                && player instanceof ServerPlayer serverPlayer) {
                HardenedResinAnvilMenu.open(serverPlayer, this);
                player.awardStat(Stats.INTERACT_WITH_ANVIL);
                player.gameEvent(GameEvent.ENTITY_INTERACT);
                result = InteractionResult.CONSUME;
            } else {
                result = plasticEntity.interact(player, hand);
            }
        }
        if (!player.level().isClientSide()) this.captureCachedEntity(true);
        return result;
    }

    public InteractionResult useAnvilHammer(Player player, InteractionHand hand, Direction interactionFace) {
        if (player.isShiftKeyDown()
            || !(player.getItemInHand(hand).getItem() instanceof AnvilHammerItem)
            || !(this.getOrCreateRenderEntity() instanceof AbstractPlasticEntity plasticEntity)) {
            return InteractionResult.PASS;
        }
        InteractionResult result;
        if (hasAnvilMenu(plasticEntity) && player instanceof ServerPlayer serverPlayer) {
            HardenedResinAnvilMenu.open(serverPlayer, this);
            player.awardStat(Stats.INTERACT_WITH_ANVIL);
            player.gameEvent(GameEvent.ENTITY_INTERACT);
            result = InteractionResult.CONSUME;
        } else {
            result = plasticEntity.plasticraft$useAnvilHammer(player, hand, interactionFace);
        }
        if (!player.level().isClientSide()) this.captureCachedEntity(true);
        return result;
    }

    private static boolean hasAnvilMenu(AbstractPlasticEntity entity) {
        return entity instanceof HardenedResinAnvilEntity
            || entity instanceof UniversalPlasticEntity universal && universal.isMoldedAnvil();
    }

    public void tickFunctionalEntity() {
        if (!(this.level instanceof ServerLevel serverLevel)) return;
        this.tickHammerDeflection(serverLevel);
        Entity functionalEntity = this.getOrCreateRenderEntity();
        if (functionalEntity instanceof UniversalPlasticEntity universal) {
            universal.getMoldedItemHandler().refreshCrate();
            universal.plasticraft$tickRedstoneConductor();
            if (universal.isMoldedTray()) {
                universal.plasticraft$tickBondedTray();
                return;
            }
        }
        PlasticCauldron cauldron = PlasticCauldrons.of(functionalEntity);
        if (cauldron == null) return;
        cauldron.plasticraft$tickBonded();
        if (cauldron.plasticraft$wasBurnedByLava()) {
            serverLevel.removeBlock(this.worldPosition, false);
            return;
        }
        if (cauldron.plasticraft$consumeBondedDataDirty()) this.captureCachedEntity(true);
    }

    public void processAnvilImpact(AbstractPlasticEntity anvil, Direction impactDirection) {
        if (!(this.level instanceof ServerLevel)
            || !(this.getOrCreateRenderEntity() instanceof PlasticCauldron cauldron)
            || !canAccessRecipeInventory(cauldron)) {
            return;
        }
        cauldron.processAnvilImpact(anvil, impactDirection);
        this.captureCachedEntity(true);
    }

    @Override
    public ResourceHandler<FluidResource> getFluidHandler() {
        Entity entity = this.getOrCreateRenderEntity();
        if (entity instanceof PlasticCauldron cauldron && canAccessRecipeInventory(cauldron)) {
            ResourceHandler<FluidResource> handler = cauldron.getFluidHandler();
            if (handler.size() > 0) {
                return handler;
            }
        }
        return EMPTY_FLUID_HANDLER;
    }

    public @Nullable ResourceHandler<FluidResource> getCapabilityFluidHandler() {
        Entity entity = this.getOrCreateRenderEntity();
        if (entity instanceof PlasticCauldron cauldron) {
            return canAccessRecipeInventory(cauldron) ? cauldron.getFluidHandler() : null;
        }
        if (entity instanceof UniversalPlasticEntity universal
            && universal.getMoldedFluidHandler().size() > 0) return universal.getMoldedFluidHandler();
        return null;
    }

    public @Nullable ResourceHandler<FluidResource> getCapabilityFluidHandler(@Nullable Direction side) {
        if (this.getOrCreateRenderEntity() instanceof UniversalPlasticEntity pot
            && MoldedLargeCauldronInteraction.applies(pot)) {
            return MoldedLargeCauldronInteraction.fluidAccess(pot, side);
        }
        return this.getCapabilityFluidHandler();
    }

    public @Nullable ResourceHandler<ItemResource> getAutomationItemHandler(@Nullable Direction side) {
        if (this.getOrCreateRenderEntity() instanceof UniversalPlasticEntity pot
            && MoldedLargeCauldronInteraction.applies(pot)) {
            Vec3 point = this.worldPosition.getCenter();
            if (side != null) point = point.add(Vec3.atLowerCornerOf(side.getUnitVec3i()).scale(0.5));
            return MoldedLargeCauldronInteraction.itemAccess(pot, side, point);
        }
        return this.getItemHandler();
    }

    public @Nullable ResourceHandler<ItemResource> getItemHandler() {
        Entity entity = this.getOrCreateRenderEntity();
        if (entity instanceof PlasticCauldron cauldron) {
            return canAccessRecipeInventory(cauldron) ? cauldron.getItemHandler() : null;
        }
        if (entity instanceof UniversalPlasticEntity universal
            && universal.getMoldedItemHandler().size() > 0) return universal.getMoldedItemHandler();
        return null;
    }

    public boolean clearCauldronOutletFacing(Direction direction) {
        if (!(this.level instanceof ServerLevel)
            || !(this.getOrCreateRenderEntity() instanceof PlasticCauldron cauldron)
            || !cauldron.clearOutletFacing(direction)) {
            return false;
        }
        this.captureCachedEntity(true);
        return true;
    }

    @Override
    public ResourceHandler<ItemResource> getInput() {
        Entity entity = this.getOrCreateRenderEntity();
        if (entity instanceof PlasticCauldron cauldron) {
            return canAccessRecipeInventory(cauldron) ? cauldron.getInput() : EMPTY_ITEM_HANDLER;
        }
        return EMPTY_ITEM_HANDLER;
    }

    @Override
    public ResourceHandler<ItemResource> getOutput() {
        Entity entity = this.getOrCreateRenderEntity();
        if (entity instanceof PlasticCauldron cauldron) {
            return canAccessRecipeInventory(cauldron) ? cauldron.getOutput() : EMPTY_ITEM_HANDLER;
        }
        return EMPTY_ITEM_HANDLER;
    }

    public @Nullable ItemStack insertRecipeOutput(ItemStack stack) {
        if (!(this.level instanceof ServerLevel)
            || !(this.getOrCreateRenderEntity() instanceof PlasticCauldron cauldron)
            || !canAccessRecipeInventory(cauldron)) {
            return stack;
        }
        ItemStack remaining = cauldron.insertRecipeOutput(stack);
        this.captureCachedEntity(true);
        return remaining;
    }

    private static boolean canAccessRecipeInventory(PlasticCauldron cauldron) {
        return !cauldron.plasticraft$isCauldron()
            || CauldronImpactRecipeProcessor.canAccessRecipeInventory(cauldron);
    }

    public boolean release() {
        return this.releaseEntity(null) != null;
    }

    public @Nullable Entity releaseEntity(@Nullable Direction changedAttachmentFace) {
        if (!this.initialized || !(this.level instanceof ServerLevel serverLevel)) return null;
        this.captureCachedEntity(false);
        Entity restored = EntityType.loadEntityRecursive(this.entityTag.copy(), serverLevel, EntitySpawnReason.LOAD, entity -> entity);
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
            restored.setPos(plasticEntity.plasticraft$placementPosition(
                this.worldPosition,
                plasticOrientation,
                this.adhesiveLocalFace
            ));
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
            replacement.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, serverLevel.registryAccess(), savedData));
            replacement.setChangedAndSync();
        }
        return null;
    }

    public void moved() {
        if (this.renderEntity instanceof UniversalPlasticEntity universal) {
            universal.plasticraft$removeVirtualRedstone();
        }
        this.renderEntity = null;
        this.clearBondedShapeCache();
        this.setChangedAndSync();
        if (this.level != null) {
            this.level.scheduleTick(this.worldPosition, this.getBlockState().getBlock(), 2);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput tag) {
        this.captureCachedEntity(false);
        super.saveAdditional(tag);
        this.saveBondData(tag);
    }

    @Override
    public void loadAdditional(ValueInput tag) {
        if (this.renderEntity instanceof UniversalPlasticEntity universal) {
            universal.plasticraft$removeVirtualRedstone();
        }
        super.loadAdditional(tag);
        this.initialized = tag.getBooleanOr(TAG_INITIALIZED, false);
        this.entityTag = tag.keySet().contains(TAG_ENTITY)
            ? tag.read(TAG_ENTITY, CompoundTag.CODEC).orElseGet(CompoundTag::new)
            : new CompoundTag();
        this.displayState = tag.keySet().contains(TAG_DISPLAY_STATE)
            ? tag.read(TAG_DISPLAY_STATE, BlockState.CODEC).orElse(Blocks.AIR.defaultBlockState())
            : Blocks.AIR.defaultBlockState();
        Direction loadedFace = Direction.byName(tag.getStringOr(TAG_ATTACHMENT_FACE, ""));
        this.attachmentFace = loadedFace == null ? Direction.UP : loadedFace;
        this.supportBlockId = tag.keySet().contains(TAG_SUPPORT_BLOCK)
            ? Identifier.tryParse(tag.getStringOr(TAG_SUPPORT_BLOCK, ""))
            : null;
        this.plastic = tag.getBooleanOr(TAG_PLASTIC, false);
        this.orientation = tag.keySet().contains(TAG_ORIENTATION)
            ? tag.getByteOr(TAG_ORIENTATION, (byte) 0)
            : PlasticEntityOrientation.DEFAULT.pack();
        this.pistonMovable = !tag.keySet().contains(TAG_PISTON_MOVABLE) || tag.getBooleanOr(TAG_PISTON_MOVABLE, false);
        this.originalNoGravity = tag.getBooleanOr(TAG_ORIGINAL_NO_GRAVITY, false);
        Direction loadedAdhesiveFace = Direction.byName(tag.getStringOr(TAG_ADHESIVE_LOCAL_FACE, ""));
        this.adhesiveLocalFace = loadedAdhesiveFace == null
            ? this.getPlasticOrientation().localDirection(this.attachmentFace.getOpposite())
            : loadedAdhesiveFace;
        this.pendingReturnOrientation = tag.keySet().contains(TAG_PENDING_RETURN_ORIENTATION)
            ? tag.getByteOr(TAG_PENDING_RETURN_ORIENTATION, (byte) 0)
            : null;
        this.hammerReturnAt = tag.keySet().contains(TAG_HAMMER_RETURN_AT)
            ? tag.getLongOr(TAG_HAMMER_RETURN_AT, 0L)
            : -1L;
        this.hammerReturnFrom = tag.keySet().contains(TAG_HAMMER_RETURN_FROM)
            ? tag.getByteOr(TAG_HAMMER_RETURN_FROM, (byte) 0)
            : this.orientation;
        this.hammerReturnStarted = tag.keySet().contains(TAG_HAMMER_RETURN_STARTED)
            ? tag.getLongOr(TAG_HAMMER_RETURN_STARTED, 0L)
            : -1L;
        this.renderEntity = null;
        this.clearBondedShapeCache();
        BondedPlasticShapeIndex.refresh(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        this.captureCachedEntity(false);
        TagValueOutput tag = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, provider);
        this.saveBondData(tag);
        return tag.buildResult();
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

    private void saveBondData(ValueOutput tag) {
        tag.putBoolean(TAG_INITIALIZED, this.initialized);
        if (!this.initialized) return;
        tag.store(TAG_ENTITY, CompoundTag.CODEC, this.entityTag);
        tag.store(TAG_DISPLAY_STATE, BlockState.CODEC, this.displayState);
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
            this.clearBondedShapeCache();
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
        BondedPlasticShapeIndex.refresh(this);
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
        plasticEntity.setPos(plasticEntity.plasticraft$placementPosition(
            this.worldPosition,
            plasticOrientation,
            this.adhesiveLocalFace
        ));
        plasticEntity.setNoGravity(true);
        plasticEntity.setDeltaMovement(Vec3.ZERO);
    }

    private void clearBondedShapeCache() {
        this.bondedInteractionShape = null;
        this.bondedCollisionShape = null;
    }

    private void captureCachedEntity(boolean sync) {
        if (!this.initialized || this.renderEntity == null || this.level == null || this.level.isClientSide()) return;
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, this.renderEntity.registryAccess());
        if (!this.renderEntity.save(output)) return;
        CompoundTag savedEntity = output.buildResult();
        this.entityTag = savedEntity;
        this.clearBondedShapeCache();
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
