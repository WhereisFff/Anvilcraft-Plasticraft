package dev.anvilcraft.plasticraft.entity;

import dev.anvilcraft.plasticraft.entity.collision.BuiltInPlasticEntityModels;
import dev.anvilcraft.plasticraft.entity.collision.PlasticEntityGeometry;
import dev.anvilcraft.plasticraft.entity.redstone.MoldedPlasticRedstoneConductor;
import dev.anvilcraft.plasticraft.entity.redstone.MoldedTrayComponentSupport;
import dev.anvilcraft.plasticraft.entity.redstone.MoldedTrayLightSource;
import dev.anvilcraft.plasticraft.entity.redstone.MoldedTrayRedstoneNetwork;
import dev.anvilcraft.plasticraft.entity.redstone.MoldedTrayRedstoneRuntime;
import dev.anvilcraft.plasticraft.entity.redstone.MoldedTrayRedstoneRuntimeManager;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.inventory.HardenedResinAnvilMenu;
import dev.anvilcraft.plasticraft.item.DyeableMaterial;
import dev.anvilcraft.plasticraft.item.PlasticItemData;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.dubhe.anvilcraft.api.entity.IGenericAnvilEntity;
import dev.anvilcraft.plasticraft.molding.bake.MoldingTrayShapeAnalyzer;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticContentSummary;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticFluidHandler;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticItemHandler;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticNames;
import dev.anvilcraft.plasticraft.molding.product.MoldedTrayCell;
import dev.anvilcraft.plasticraft.molding.product.MoldedTrayComponent;
import dev.anvilcraft.plasticraft.molding.product.MoldedTrayComponentGeometry;
import dev.anvilcraft.plasticraft.molding.product.MoldedTrayComponentPlacement;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.fluids.FluidUtil;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** 保留通用塑料颜色并复用全部公共塑料物理的轻量制品实体。 */
public class UniversalPlasticEntity extends AbstractPlasticEntity implements IShockEntity, IGenericAnvilEntity {
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
    private MoldedTrayRedstoneRuntimeManager trayRuntimes;

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
            if (MoldingProductTypes.isTray(molded.finalType())) {
                BlockPos position = this.blockPosition();
                for (MoldedTrayComponentPlacement placement : molded.contents().trayComponents()) {
                    BlockState state = placement.component().state();
                    geometry = geometry.include(
                        MoldedTrayComponentGeometry.localCollisionShape(
                            molded, placement.cell(), state, this.level(), position
                        ),
                        MoldedTrayComponentGeometry.localInteractionShape(
                            molded, placement.cell(), state, this.level(), position
                        )
                    );
                }
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

    @Override
    public boolean anvilcraft$isGenericAnvil() {
        return this.isMoldedAnvil();
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
        return this.getMoldedTrayRuntime(MoldedTrayCell.CENTER);
    }

    public MoldedTrayRedstoneRuntime getMoldedTrayRuntime(MoldedTrayCell cell) {
        return this.getMoldedTrayRuntimes().runtime(cell);
    }

    public MoldedTrayRedstoneRuntimeManager getMoldedTrayRuntimes() {
        if (this.trayRuntimes == null) this.trayRuntimes = new MoldedTrayRedstoneRuntimeManager(this);
        return this.trayRuntimes;
    }

    public void plasticraft$tickBondedTray() {
        if (!this.level().isClientSide && this.isMoldedTray()) this.getMoldedTrayRuntimes().tick();
        MoldedTrayLightSource.update(this);
    }

    public void plasticraft$tickRedstoneConductor() {
        if (!this.level().isClientSide) MoldedPlasticRedstoneConductor.update(this);
    }

    public void plasticraft$removeVirtualRedstone() {
        MoldedPlasticRedstoneConductor.remove(this);
        if (this.trayRuntimes != null) this.trayRuntimes.remove();
        MoldedTrayLightSource.remove(this);
    }

    public BlockEntity plasticraft$getTrayBlockEntity() {
        return this.getMoldedTrayRuntime().blockEntity();
    }

    public BlockEntity plasticraft$getTrayBlockEntity(MoldedTrayCell cell) {
        return this.getMoldedTrayRuntime(cell).blockEntity();
    }

    public Map<MoldedTrayCell, BlockEntity> plasticraft$getTrayBlockEntities() {
        return this.getMoldedTrayRuntimes().blockEntities();
    }

    public void plasticraft$replaceTrayComponent(MoldedTrayComponent component) {
        this.plasticraft$replaceTrayComponent(MoldedTrayCell.CENTER, component);
    }

    public void plasticraft$replaceTrayComponent(MoldedTrayCell cell, MoldedTrayComponent component) {
        Optional<MoldedPlasticData> data = this.getMoldedData();
        if (data.isEmpty() || !MoldingProductTypes.isTray(data.orElseThrow().finalType())) return;
        this.replaceMoldedData(data.orElseThrow().withContents(
            data.orElseThrow().contents().withTrayComponent(cell, Optional.of(component))
        ));
    }

    public void plasticraft$clearTrayComponent() {
        this.plasticraft$clearTrayComponent(MoldedTrayCell.CENTER);
    }

    public void plasticraft$clearTrayComponent(MoldedTrayCell cell) {
        Optional<MoldedPlasticData> data = this.getMoldedData();
        if (data.isEmpty() || !MoldingProductTypes.isTray(data.orElseThrow().finalType())) return;
        this.replaceMoldedData(data.orElseThrow().withContents(
            data.orElseThrow().contents().withTrayComponent(cell, Optional.empty())
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
            if (data.isEmpty()) return InteractionResult.PASS;
            Optional<MoldedTrayCell> target = this.fallbackTrayCell(data.orElseThrow(), held);
            return target.isPresent()
                ? this.interactTrayCell(player, hand, target.orElseThrow())
                : InteractionResult.PASS;
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

    private InteractionResult interactTrayCell(Player player, InteractionHand hand, MoldedTrayCell cell) {
        Optional<MoldedPlasticData> optionalData = this.getMoldedData();
        if (optionalData.isEmpty()) return InteractionResult.PASS;
        MoldedPlasticData data = optionalData.orElseThrow();
        Optional<MoldedTrayComponent> existing = data.contents().trayComponent(cell);
        if (existing.isPresent()) return this.getMoldedTrayRuntime(cell).interact(player, hand);

        ItemStack held = player.getItemInHand(hand);
        if (!MoldedTrayComponentSupport.isSupportedItem(held) || !supportsTrayCell(data, cell)) {
            return InteractionResult.PASS;
        }
        BlockPos position = MoldedTrayRedstoneNetwork.componentPosition(this, cell);
        if (!player.getAbilities().mayBuild || !this.level().mayInteract(player, position)) {
            return InteractionResult.PASS;
        }
        if (this.level().isClientSide) return InteractionResult.SUCCESS;
        Direction localFacing = MoldedTrayComponentSupport.localFacing(this, player.getLookAngle());
        MoldedTrayComponent component = MoldedTrayComponentSupport.create(this, cell, held, localFacing);
        this.replaceMoldedData(data.withContents(
            data.contents().withTrayComponent(cell, Optional.of(component))
        ));
        this.getMoldedTrayRuntimes().publish();
        if (!player.getAbilities().instabuild) held.shrink(1);
        this.level().playSound(
            null,
            position,
            component.state().getSoundType().getPlaceSound(),
            SoundSource.BLOCKS,
            0.8F,
            1.0F
        );
        this.gameEvent(GameEvent.BLOCK_PLACE, player);
        return InteractionResult.CONSUME;
    }

    private Optional<MoldedTrayCell> fallbackTrayCell(MoldedPlasticData data, ItemStack held) {
        boolean placing = MoldedTrayComponentSupport.isSupportedItem(held);
        if (placing) {
            if (supportsTrayCell(data, MoldedTrayCell.CENTER)
                && data.contents().trayComponent(MoldedTrayCell.CENTER).isEmpty()) {
                return Optional.of(MoldedTrayCell.CENTER);
            }
            for (MoldedTrayCell cell : MoldedTrayCell.VALUES) {
                if (supportsTrayCell(data, cell) && data.contents().trayComponent(cell).isEmpty()) {
                    return Optional.of(cell);
                }
            }
        }
        if (data.contents().trayComponent(MoldedTrayCell.CENTER).isPresent()) {
            return Optional.of(MoldedTrayCell.CENTER);
        }
        return data.contents().trayComponents().stream()
            .map(MoldedTrayComponentPlacement::cell)
            .findFirst();
    }

    private static boolean supportsTrayCell(MoldedPlasticData data, MoldedTrayCell cell) {
        return (MoldingTrayShapeAnalyzer.supportedCellMask(data.volumeMask()) & cell.bit()) != 0;
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
        if (this.trayRuntimes != null) this.trayRuntimes.applyTo(stack);
        MoldedPlasticData.get(stack).ifPresent(data ->
            MoldedPlasticData.set(stack, data.withOrientation(PlasticEntityOrientation.DEFAULT))
        );
        return stack;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (!this.level().isClientSide && this.trayRuntimes != null) this.trayRuntimes.persist();
        super.addAdditionalSaveData(tag);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isRemoved()) return;
        if (!this.level().isClientSide) {
            this.plasticraft$tickRedstoneConductor();
            if (this.isMoldedTray()) this.getMoldedTrayRuntimes().tick();
        }
        MoldedTrayLightSource.update(this);
    }

    @Override
    public InteractionResult interactAt(Player player, Vec3 location, InteractionHand hand) {
        if (this.isMoldedTray()) {
            Optional<MoldedTrayCell> target = this.trayCellAt(location);
            if (target.isPresent()) {
                MoldedTrayCell cell = target.orElseThrow();
                if (!player.isShiftKeyDown()) {
                    InteractionResult result = this.interactTrayCell(player, hand, cell);
                    if (result != InteractionResult.PASS) return result;
                } else if (player.getItemInHand(hand).isEmpty()) {
                    BlockPos position = MoldedTrayRedstoneNetwork.componentPosition(this, cell);
                    if (!player.getAbilities().mayBuild || !this.level().mayInteract(player, position)) {
                        return InteractionResult.PASS;
                    }
                    if (this.getMoldedData().flatMap(data -> data.contents().trayComponent(cell)).isEmpty()) {
                        return InteractionResult.PASS;
                    }
                    if (this.level().isClientSide) return InteractionResult.SUCCESS;
                    if (this.getMoldedTrayRuntime(cell).extract(player)) {
                        this.level().playSound(
                            null,
                            position,
                            this.getDisplayState().getSoundType().getBreakSound(),
                            SoundSource.BLOCKS,
                            0.8F,
                            1.0F
                        );
                        this.gameEvent(GameEvent.ENTITY_INTERACT, player);
                        return InteractionResult.CONSUME;
                    }
                }
            }
        }
        return super.interactAt(player, location, hand);
    }

    private Optional<MoldedTrayCell> trayCellAt(Vec3 relativeLocation) {
        Optional<MoldedPlasticData> optionalData = this.getMoldedData();
        if (optionalData.isEmpty()) return Optional.empty();
        MoldedPlasticData data = optionalData.orElseThrow();
        Vec3 worldPoint = this.position().add(relativeLocation);
        Vec3 localPoint = this.plasticraft$getGeometry().localPointAt(
            this.position(),
            this.getOrientation(),
            worldPoint
        );
        MoldedTrayCell componentHit = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (MoldedTrayComponentPlacement placement : data.contents().trayComponents()) {
            VoxelShape shape = MoldedTrayComponentGeometry.localInteractionShape(
                data,
                placement.cell(),
                placement.component().state(),
                this.level(),
                this.blockPosition()
            );
            for (AABB bounds : shape.toAabbs()) {
                if (!bounds.inflate(1.0E-5D).contains(localPoint)) continue;
                double distance = bounds.getCenter().distanceToSqr(localPoint);
                if (componentHit == null || distance < nearestDistance) {
                    componentHit = placement.cell();
                    nearestDistance = distance;
                }
            }
        }
        if (componentHit != null) return Optional.of(componentHit);
        if (localPoint.x < -1.0E-5D || localPoint.x > MoldedTrayCell.GRID_SIZE + 1.0E-5D
            || localPoint.z < -1.0E-5D || localPoint.z > MoldedTrayCell.GRID_SIZE + 1.0E-5D) {
            return Optional.empty();
        }
        int x = Math.clamp((int) Math.floor(localPoint.x), 0, MoldedTrayCell.GRID_SIZE - 1);
        int z = Math.clamp((int) Math.floor(localPoint.z), 0, MoldedTrayCell.GRID_SIZE - 1);
        return Optional.of(new MoldedTrayCell(x, z));
    }

    @Override
    protected void prepareAnvilHammerPickup(Player player) {
        if (this.isMoldedTray()) this.getMoldedTrayRuntimes().flush();
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
