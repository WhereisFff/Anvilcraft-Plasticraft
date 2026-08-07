package dev.anvilcraft.plasticraft.entity;

import dev.anvilcraft.plasticraft.entity.collision.BuiltInPlasticEntityModels;
import dev.anvilcraft.plasticraft.entity.collision.PlasticEntityGeometry;
import dev.anvilcraft.plasticraft.entity.redstone.MoldedPlasticRedstoneConductor;
import dev.anvilcraft.plasticraft.entity.redstone.MoldedTrayComponentSupport;
import dev.anvilcraft.plasticraft.entity.redstone.MoldedTrayRedstoneRuntime;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.inventory.HardenedResinAnvilMenu;
import dev.anvilcraft.plasticraft.item.DyeableMaterial;
import dev.anvilcraft.plasticraft.item.PlasticItemData;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticContentSummary;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticFluidHandler;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticItemHandler;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticNames;
import dev.anvilcraft.plasticraft.molding.product.MoldedTrayComponent;
import dev.anvilcraft.plasticraft.molding.product.MoldedTrayComponentGeometry;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import dev.dubhe.anvilcraft.api.event.AnvilEvent;
import dev.dubhe.anvilcraft.api.giantanvil.IShockEntity;
import dev.dubhe.anvilcraft.api.giantanvil.ShockAnvilBehavior;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.fluids.FluidUtil;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** 保留通用塑料颜色并复用全部公共塑料物理的轻量制品实体。 */
public class UniversalPlasticEntity extends AbstractPlasticEntity implements IShockEntity {
    private static final EntityDataAccessor<ItemStack> MOLDED_STACK = SynchedEntityData.defineId(
        UniversalPlasticEntity.class,
        EntityDataSerializers.ITEM_STACK
    );
    private static final EntityDataAccessor<CompoundTag> MOLDED_SUMMARY = SynchedEntityData.defineId(
        UniversalPlasticEntity.class,
        EntityDataSerializers.COMPOUND_TAG
    );
    private static Supplier<ItemStack> defaultDropSupplier = () -> ItemStack.EMPTY;
    private Optional<MoldedPlasticData> cachedMoldedData = Optional.empty();
    private PlasticEntityGeometry cachedMoldedGeometry;
    private boolean moldedDataCached;
    private final MoldedPlasticItemHandler moldedItemHandler = new MoldedPlasticItemHandler(
        this::getMoldedData,
        this::replaceMoldedData
    );
    private final MoldedPlasticFluidHandler moldedFluidHandler = new MoldedPlasticFluidHandler(
        this::getMoldedData,
        this::replaceMoldedData,
        this::getDropStack
    );
    private Runnable moldedContentsChanged = () -> {
    };
    private MoldedTrayRedstoneRuntime trayRuntime;

    public static void configureDefaultDrop(Supplier<ItemStack> supplier) {
        defaultDropSupplier = Objects.requireNonNull(supplier, "supplier");
    }

    public UniversalPlasticEntity(EntityType<? extends UniversalPlasticEntity> entityType, Level level) {
        super(entityType, level);
        this.setDisplayState(PlasticraftBlocks.UNIVERSAL_PLASTIC.get().defaultBlockState());
    }

    public UniversalPlasticEntity(
        EntityType<? extends UniversalPlasticEntity> entityType,
        Level level,
        Vec3 position,
        BlockState displayState,
        ItemStack dropStack,
        PlasticEntityOrientation orientation
    ) {
        super(entityType, level, position, displayState, dropStack, orientation);
        // 父类构造期间不能依赖动态回调同步子类数据，初始化完成后再写入一次同步槽
        this.onDropStackChanged(this.getDropStack());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MOLDED_STACK, ItemStack.EMPTY);
        builder.define(MOLDED_SUMMARY, new CompoundTag());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (MOLDED_STACK.equals(key)) {
            this.moldedDataCached = false;
            this.cachedMoldedGeometry = null;
            this.invalidatePlasticGeometry();
        }
    }

    @Override
    protected PlasticEntityGeometry getLocalGeometry() {
        Optional<MoldedPlasticData> data = this.getMoldedData();
        if (data.isEmpty()) return BuiltInPlasticEntityModels.UNIVERSAL_PLASTIC.geometry();
        if (this.cachedMoldedGeometry == null) {
            MoldedPlasticData molded = data.orElseThrow();
            PlasticEntityGeometry geometry = molded.geometry();
            Optional<MoldedTrayComponent> component = molded.contents().trayComponent();
            if (MoldingProductTypes.isTray(molded.finalType()) && component.isPresent()) {
                BlockState state = component.orElseThrow().state();
                BlockPos position = this.blockPosition();
                geometry = geometry.include(
                    MoldedTrayComponentGeometry.localCollisionShape(molded, state, this.level(), position),
                    MoldedTrayComponentGeometry.localInteractionShape(molded, state, this.level(), position)
                );
            }
            this.cachedMoldedGeometry = geometry;
        }
        return this.cachedMoldedGeometry;
    }

    public Optional<MoldedPlasticData> getMoldedData() {
        if (!this.moldedDataCached) {
            Optional<MoldedPlasticData> synchronizedData = MoldedPlasticData.get(this.entityData.get(MOLDED_STACK));
            this.cachedMoldedData = this.level().isClientSide && synchronizedData.isPresent()
                ? synchronizedData
                : MoldedPlasticData.get(this.getDropStack());
            this.moldedDataCached = true;
        }
        return this.cachedMoldedData;
    }

    public boolean isMoldedAnvil() {
        return this.getMoldedData()
            .map(data -> MoldingProductTypes.isAnvil(data.finalType()))
            .orElse(false);
    }

    public boolean isMoldedGiantAnvil() {
        return this.getMoldedData().map(MoldedPlasticData::hasGiantAnvilAbility).orElse(false);
    }

    public boolean isMoldedTray() {
        return this.getMoldedData()
            .map(data -> MoldingProductTypes.isTray(data.finalType()))
            .orElse(false);
    }

    public MoldedTrayRedstoneRuntime getMoldedTrayRuntime() {
        if (this.trayRuntime == null) this.trayRuntime = new MoldedTrayRedstoneRuntime(this);
        return this.trayRuntime;
    }

    public void plasticraft$tickBondedTray() {
        if (!this.level().isClientSide && this.isMoldedTray()) this.getMoldedTrayRuntime().tick();
    }

    public void plasticraft$tickRedstoneConductor() {
        if (!this.level().isClientSide) MoldedPlasticRedstoneConductor.update(this);
    }

    public void plasticraft$removeVirtualRedstone() {
        MoldedPlasticRedstoneConductor.remove(this);
        if (this.trayRuntime != null) this.trayRuntime.remove();
    }

    public BlockEntity plasticraft$getTrayBlockEntity() {
        return this.getMoldedTrayRuntime().blockEntity();
    }

    public void plasticraft$replaceTrayComponent(MoldedTrayComponent component) {
        Optional<MoldedPlasticData> data = this.getMoldedData();
        if (data.isEmpty() || !MoldingProductTypes.isTray(data.orElseThrow().finalType())) return;
        this.replaceMoldedData(data.orElseThrow().withContents(
            data.orElseThrow().contents().withTrayComponent(Optional.of(component))
        ));
    }

    public void plasticraft$clearTrayComponent() {
        Optional<MoldedPlasticData> data = this.getMoldedData();
        if (data.isEmpty() || !MoldingProductTypes.isTray(data.orElseThrow().finalType())) return;
        this.replaceMoldedData(data.orElseThrow().withContents(
            data.orElseThrow().contents().withTrayComponent(Optional.empty())
        ));
    }

    public MoldedPlasticItemHandler getMoldedItemHandler() {
        return this.moldedItemHandler;
    }

    public MoldedPlasticFluidHandler getMoldedFluidHandler() {
        return this.moldedFluidHandler;
    }

    public MoldedPlasticContentSummary getMoldedContentSummary() {
        return MoldedPlasticContentSummary.fromTag(this.entityData.get(MOLDED_SUMMARY), this.registryAccess());
    }

    public void setMoldedContentsChangedListener(Runnable listener) {
        this.moldedContentsChanged = Objects.requireNonNull(listener, "listener");
    }

    private void replaceMoldedData(MoldedPlasticData data) {
        ItemStack stack = this.getDropStack();
        if (stack.isEmpty()) return;
        MoldedPlasticData.set(stack, data);
        this.setDropStack(stack);
        this.moldedContentsChanged.run();
    }

    @Override
    protected InteractionResult interactNormally(Player player, InteractionHand hand) {
        if (this.isMoldedTray()) {
            ItemStack held = player.getItemInHand(hand);
            Optional<MoldedPlasticData> data = this.getMoldedData();
            Optional<MoldedTrayComponent> component =
                data.flatMap(value -> value.contents().trayComponent());
            if (component.isPresent()) return this.getMoldedTrayRuntime().interact(player, hand);
            if (!MoldedTrayComponentSupport.isSupportedItem(held)) return InteractionResult.PASS;
            BlockPos anchor = this.plasticraft$getAnchorBlockPos();
            if (!player.getAbilities().mayBuild || !this.level().mayInteract(player, anchor)) {
                return InteractionResult.PASS;
            }
            if (this.level().isClientSide) return InteractionResult.SUCCESS;
            Direction localFacing = MoldedTrayComponentSupport.localFacing(this, player.getLookAngle());
            MoldedTrayComponent tray = MoldedTrayComponentSupport.create(this, held, localFacing);
            this.replaceMoldedData(data.orElseThrow().withContents(
                data.orElseThrow().contents().withTrayComponent(Optional.of(tray))
            ));
            if (!player.getAbilities().instabuild) held.shrink(1);
            this.level().playSound(
                null,
                anchor,
                tray.state().getSoundType().getPlaceSound(),
                SoundSource.BLOCKS,
                0.8F,
                1.0F
            );
            this.gameEvent(GameEvent.BLOCK_PLACE, player);
            return InteractionResult.CONSUME;
        }
        if (this.isMoldedAnvil()) {
            if (this.level().isClientSide) return InteractionResult.SUCCESS;
            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            HardenedResinAnvilMenu.open(serverPlayer, this);
            player.awardStat(Stats.INTERACT_WITH_ANVIL);
            this.gameEvent(GameEvent.ENTITY_INTERACT, player);
            return InteractionResult.CONSUME;
        }
        if (this.getMoldedData().filter(data -> MoldingProductTypes.isTank(data.finalType())).isEmpty()) {
            return InteractionResult.PASS;
        }
        return FluidUtil.interactWithFluidHandler(player, hand, this.moldedFluidHandler)
            ? InteractionResult.sidedSuccess(this.level().isClientSide)
            : InteractionResult.PASS;
    }

    @Override
    protected Component getTypeName() {
        return this.getMoldedData()
            .map(data -> MoldedPlasticNames.create(this.getDropStack(), data))
            .orElseGet(super::getTypeName);
    }

    @Override
    protected void onDropStackChanged(ItemStack stack) {
        this.synchronizeMoldedStack(stack);
    }

    private void synchronizeMoldedStack(ItemStack stack) {
        ItemStack synchronizedStack = MoldedPlasticData.get(stack)
            .map(data -> {
                ItemStack copy = stack.copyWithCount(1);
                MoldedPlasticData setView = data.withContents(data.contents().renderView());
                MoldedPlasticData.set(copy, setView.withOrientation(PlasticEntityOrientation.DEFAULT));
                return copy;
            })
            .orElse(ItemStack.EMPTY);
        this.entityData.set(MOLDED_STACK, synchronizedStack);
        CompoundTag summary = MoldedPlasticData.get(stack)
            .map(MoldedPlasticContentSummary::create)
            .map(value -> value.toTag(this.registryAccess()))
            .orElseGet(CompoundTag::new);
        this.entityData.set(MOLDED_SUMMARY, summary);
        this.moldedDataCached = false;
        this.cachedMoldedGeometry = null;
        this.invalidatePlasticGeometry();
    }

    @Override
    protected ItemStack prepareDropStack(ItemStack stack) {
        if (this.trayRuntime != null) this.trayRuntime.applyTo(stack);
        MoldedPlasticData.get(stack).ifPresent(data ->
            MoldedPlasticData.set(stack, data.withOrientation(PlasticEntityOrientation.DEFAULT))
        );
        return stack;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (!this.level().isClientSide && this.trayRuntime != null) this.trayRuntime.persist();
        super.addAdditionalSaveData(tag);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isRemoved() || this.level().isClientSide) return;
        this.plasticraft$tickRedstoneConductor();
        if (this.isMoldedTray()) this.getMoldedTrayRuntime().tick();
    }

    @Override
    public InteractionResult interactAt(Player player, Vec3 location, InteractionHand hand) {
        if (this.isMoldedTray() && player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty()) {
            if (!player.getAbilities().mayBuild || !this.level().mayInteract(player, this.plasticraft$getAnchorBlockPos())) {
                return InteractionResult.PASS;
            }
            if (this.getMoldedData().flatMap(data -> data.contents().trayComponent()).isEmpty()) {
                return InteractionResult.PASS;
            }
            if (this.level().isClientSide) return InteractionResult.SUCCESS;
            if (this.getMoldedTrayRuntime().extract(player)) {
                this.level().playSound(
                    null,
                    this.plasticraft$getAnchorBlockPos(),
                    this.getDisplayState().getSoundType().getBreakSound(),
                    SoundSource.BLOCKS,
                    0.8F,
                    1.0F
                );
                this.gameEvent(GameEvent.ENTITY_INTERACT, player);
                return InteractionResult.CONSUME;
            }
        }
        return super.interactAt(player, location, hand);
    }

    @Override
    protected void prepareAnvilHammerPickup(Player player) {
        if (this.isMoldedTray()) this.getMoldedTrayRuntime().flush();
    }

    @Override
    public void remove(RemovalReason reason) {
        this.plasticraft$removeVirtualRedstone();
        super.remove(reason);
    }

    @Override
    public ItemStack getPickResult() {
        if (!this.level().isClientSide) return this.prepareDropStack(this.getDropStack().copyWithCount(1));
        ItemStack synchronizedStack = this.entityData.get(MOLDED_STACK);
        if (synchronizedStack.isEmpty()) return super.getPickResult();
        ItemStack result = synchronizedStack.copyWithCount(1);
        PlasticItemData.setMaterial(result, this.materialKey());
        PlasticItemData.setMagnetized(result, this.isMagnetized());
        return this.prepareDropStack(result);
    }

    @Override
    protected ItemStack createDefaultDropStack() {
        ItemStack fallback = defaultDropSupplier.get();
        if (fallback == null || fallback.isEmpty()) return ItemStack.EMPTY;
        ItemStack stack = fallback.copy();
        PlasticItemData.setMaterial(stack, "universal_plastic");
        BlockState state = this.getDisplayState();
        if (state.hasProperty(DyeableMaterial.COLOR)) {
            PlasticMeltColor.set(stack, state.getValue(DyeableMaterial.COLOR));
        }
        return stack;
    }

    @Override
    protected String materialKey() {
        return "universal_plastic";
    }

    @Override
    protected boolean triggersAnvilCraftLandingEvents(Direction impactDirection) {
        return this.isMoldedAnvil() && super.triggersAnvilCraftLandingEvents(impactDirection);
    }

    @Override
    protected boolean handleAdditionalAnvilCraftLanding(AnvilEvent.OnLand event) {
        return MoldedPlasticAnvilAbilities.handleLanding(this, event);
    }

    @Override
    public double anvilcraft$getShockBounceHeightMultiplier() {
        return this.isMoldedAnvil() ? 1.0D : 0.0D;
    }

    @Override
    public Optional<ShockAnvilBehavior> anvilcraft$getShockAnvilBehavior() {
        return this.isMoldedAnvil()
            ? Optional.of(ShockAnvilBehavior.NORMAL)
            : Optional.empty();
    }

}
