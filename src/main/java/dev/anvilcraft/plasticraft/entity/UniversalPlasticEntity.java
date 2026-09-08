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
import dev.anvilcraft.plasticraft.material.PlasticMaterial;
import dev.dubhe.anvilcraft.api.entity.IGenericAnvilEntity;
import dev.anvilcraft.plasticraft.molding.bake.MoldingTrayShapeAnalyzer;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticContentSummary;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticContents;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticFluidHandler;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticItemHandler;
import dev.anvilcraft.plasticraft.molding.product.storage.MoldedPlasticStorageHandle;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticNames;
import dev.anvilcraft.plasticraft.molding.product.MoldedTrayCell;
import dev.anvilcraft.plasticraft.molding.product.MoldedTrayComponent;
import dev.anvilcraft.plasticraft.molding.product.MoldedTrayComponentGeometry;
import dev.anvilcraft.plasticraft.molding.product.MoldedTrayComponentPlacement;
import dev.anvilcraft.plasticraft.molding.product.PlasticCauldronLayout;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import dev.anvilcraft.plasticraft.recipe.CauldronImpactRecipeProcessor;
import dev.dubhe.anvilcraft.api.event.AnvilEvent;
import dev.dubhe.anvilcraft.api.giantanvil.IShockEntity;
import dev.dubhe.anvilcraft.api.giantanvil.ShockAnvilBehavior;
import dev.dubhe.anvilcraft.init.item.ModItemTags;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.entity.Entity;
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
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/** 保留通用塑料颜色并复用全部公共塑料物理的轻量制品实体。 */
public class UniversalPlasticEntity extends AbstractCauldronPlasticEntity implements IShockEntity, IGenericAnvilEntity {
    private static final EntityDataAccessor<ItemStack> MOLDED_STACK = SynchedEntityData.defineId(
        UniversalPlasticEntity.class,
        EntityDataSerializers.ITEM_STACK
    );
    private static final EntityDataAccessor<CompoundTag> MOLDED_SUMMARY = SynchedEntityData.defineId(
        UniversalPlasticEntity.class,
        EntityDataSerializers.COMPOUND_TAG
    );
    private static final EntityDataAccessor<CompoundTag> DISPLAY_ITEMS = SynchedEntityData.defineId(
        UniversalPlasticEntity.class,
        EntityDataSerializers.COMPOUND_TAG
    );
    private static final EntityDataAccessor<CompoundTag> DISPLAY_FLUIDS = SynchedEntityData.defineId(
        UniversalPlasticEntity.class,
        EntityDataSerializers.COMPOUND_TAG
    );
    private static final int DISPLAY_ITEM_SLOTS = PlasticCauldronLayout.LARGE.totalSlots();
    private static Supplier<ItemStack> defaultDropSupplier = () -> ItemStack.EMPTY;
    private Optional<MoldedPlasticData> cachedMoldedData = Optional.empty();
    private PlasticEntityGeometry cachedMoldedGeometry;
    private boolean moldedDataCached;
    private MoldedPlasticData typeNameData;
    private Component cachedTypeName;
    private final MoldedPlasticItemHandler moldedItemHandler = new MoldedPlasticItemHandler(
        this::getMoldedData,
        this::replaceMoldedData,
        this::onMoldedCauldronOutputChanged
    );
    private final MoldedPlasticFluidHandler moldedFluidHandler = new MoldedPlasticFluidHandler(
        this::getMoldedData,
        this::replaceMoldedData,
        this::getDropStack
    );
    private final IItemHandler moldedInputView = this.moldedItemHandler.inputView();
    private final IItemHandler moldedOutputView = this.moldedItemHandler.outputView();
    /** 客户端只保存渲染所需的槽位快照；权威物品仍在服务端 UUID 仓储。 */
    private final ItemStackHandler syncedItems = new ItemStackHandler(DISPLAY_ITEM_SLOTS);
    /** 客户端只保存炼药锅渲染所需的分层流体快照；权威流体仍在服务端 UUID 仓储。 */
    private List<FluidStack> syncedFluids = List.of();
    /** 配方查询被别的锅占用时顶替上去的空句柄，语义同硬化树脂锅。 */
    private final IItemHandler emptyRecipeHandler = new ItemStackHandler(0);
    /** 内腔在局部空间的包围盒；逐格扫描掩码开销大，随几何缓存一起失效即可。 */
    private AABB cachedCauldronCavity;
    private PlasticEntityGeometry cachedCauldronEntryGeometry;
    private VoxelShape cachedCauldronEntryOpening;
    private Runnable moldedContentsChanged = () -> {
    };
    private MoldedTrayRedstoneRuntimeManager trayRuntimes;
    private IItemHandler largeRecipeInput;
    private boolean largeRecipeProcessing;
    private final Set<Integer> largeOutputInputs = new HashSet<>();
    private final List<ItemStack> originalLargeOutputs = new ArrayList<>();
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
        super(entityType, level, position, displayState, MoldedPlasticCauldronState.withoutState(dropStack), orientation);
        // 父类构造期间不能依赖动态回调同步子类数据，初始化完成后再写入一次同步槽
        this.onDropStackChanged(this.getDropStack());
        // 点燃校验依赖锅底流体，必须等完整仓储已同步后才恢复运行状态。
        MoldedPlasticCauldronState.get(dropStack).ifPresent(this::restorePickedCauldronState);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MOLDED_STACK, ItemStack.EMPTY);
        builder.define(MOLDED_SUMMARY, new CompoundTag());
        builder.define(DISPLAY_ITEMS, new CompoundTag());
        builder.define(DISPLAY_FLUIDS, new CompoundTag());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (MOLDED_STACK.equals(key)) {
            this.moldedDataCached = false;
            this.cachedMoldedGeometry = null;
            this.cachedCauldronCavity = null;
            this.cachedCauldronEntryGeometry = null;
            this.cachedCauldronEntryOpening = null;
            this.invalidatePlasticGeometry();
        }
        // 父类构造期间 setDropStack 会动态回调到本类，此时子类字段初始化器尚未运行。
        if (DISPLAY_ITEMS.equals(key) && this.syncedItems != null) {
            this.readSyncedItems(this.entityData.get(DISPLAY_ITEMS));
        }
        if (DISPLAY_FLUIDS.equals(key) && this.syncedFluids != null) {
            this.readSyncedFluids(this.entityData.get(DISPLAY_FLUIDS));
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

    @Override
    protected PlasticEntityGeometry cauldronResinEntryGeometry() {
        if (!this.isMoldedCauldron()) return this.getLocalGeometry();
        if (this.cachedCauldronEntryGeometry != null) return this.cachedCauldronEntryGeometry;
        PlasticEntityGeometry geometry = this.getLocalGeometry();
        AABB bounds = geometry.localBounds();
        AABB cavity = this.cauldronCavity();
        if (cavity == null) return geometry;
        double entryTop = Math.min(bounds.maxY, cavity.minY);
        if (entryTop <= bounds.minY + 1.0E-6D) return geometry;
        VoxelShape clip = Shapes.box(
            bounds.minX,
            bounds.minY,
            bounds.minZ,
            bounds.maxX,
            entryTop,
            bounds.maxZ
        );
        VoxelShape entryCollision = Shapes.join(
            geometry.collisionShape(),
            clip,
            BooleanOp.AND
        ).optimize();
        this.cachedCauldronEntryGeometry = entryCollision.isEmpty()
            ? geometry
            : PlasticEntityGeometry.of(
                entryCollision,
                entryCollision,
                geometry.rotationPivot(),
                geometry.entityOrigin()
            );
        return this.cachedCauldronEntryGeometry;
    }

    @Override
    protected VoxelShape cauldronResinEntryOpening() {
        if (this.cachedCauldronEntryOpening != null) return this.cachedCauldronEntryOpening;
        AABB cavity = this.cauldronCavity();
        if (cavity == null) return Shapes.empty();
        AABB bounds = this.getLocalGeometry().localBounds();
        this.cachedCauldronEntryOpening = Shapes.box(
            cavity.minX,
            bounds.minY,
            cavity.minZ,
            cavity.maxX,
            bounds.maxY,
            cavity.maxZ
        );
        return this.cachedCauldronEntryOpening;
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

    /** 透明材料覆写此入口，激光路径判定不依赖客户端渲染状态。 */
    public boolean canLaserPassThrough() {
        return false;
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

    public boolean isMoldedCauldron() {
        return this.getMoldedData()
            .map(data -> MoldingProductTypes.isCauldron(data.finalType()))
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

    private void onMoldedCauldronOutputChanged() {
        if (!this.isMoldedCauldron() || this.autoOutputting || this.restoringData || this.largeRecipeProcessing) return;
        this.tryAutoOutputResults();
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
        if (this.isMoldedCauldron()) {
            return super.interactNormally(player, hand);
        }
        if (this.getMoldedData().filter(data -> MoldingProductTypes.holdsFluids(data.finalType())).isEmpty()) {
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

    /**
     * NeoForge 为每个实体每帧构造一次 {@code RenderNameTagEvent}，即使没有名牌要画也会调用
     * {@link #getDisplayName()}，而它会取两次名称。名称只由材质键和最终功能类型决定，
     * 因此按同步到的制品数据实例缓存，避免逐帧复制掉落物栈再拼一次可翻译文本。
     */
    @Override
    protected Component getTypeName() {
        MoldedPlasticData data = this.getMoldedData().orElse(null);
        if (data == null) return super.getTypeName();
        if (this.cachedTypeName == null || this.typeNameData != data) {
            this.typeNameData = data;
            this.cachedTypeName = MoldedPlasticNames.create(this.getDropStack(), data);
        }
        return this.cachedTypeName;
    }

    @Override
    protected void onDropStackChanged(ItemStack stack) {
        this.synchronizeMoldedStack(stack);
    }

    private void synchronizeMoldedStack(ItemStack stack) {
        Optional<MoldedPlasticData> moldedData = MoldedPlasticData.get(stack);
        ItemStack synchronizedStack = moldedData
            .map(data -> {
                ItemStack copy = stack.copyWithCount(1);
                MoldedPlasticData setView = data.withContents(data.contents().renderView());
                MoldedPlasticData.set(copy, setView.withOrientation(PlasticEntityOrientation.DEFAULT));
                return copy;
            })
            .orElse(ItemStack.EMPTY);
        this.entityData.set(MOLDED_STACK, synchronizedStack);
        CompoundTag summary = moldedData
            .map(data -> this.level().isClientSide
                ? data.summary()
                : new MoldedPlasticStorageHandle(() -> Optional.of(data), ignored -> {}).summary())
            .map(value -> value.toTag(this.registryAccess()))
            .orElseGet(CompoundTag::new);
        this.entityData.set(MOLDED_SUMMARY, summary);
        CompoundTag displayItems = moldedData
            .map(data -> this.createDisplayItemsTag(data, this.registryAccess()))
            .orElseGet(CompoundTag::new);
        this.entityData.set(DISPLAY_ITEMS, displayItems);
        CompoundTag displayFluids = moldedData
            .map(data -> this.createDisplayFluidsTag(data, this.registryAccess()))
            .orElseGet(CompoundTag::new);
        this.entityData.set(DISPLAY_FLUIDS, displayFluids);
        this.moldedDataCached = false;
        this.cachedMoldedGeometry = null;
        this.cachedCauldronCavity = null;
        this.cachedCauldronEntryGeometry = null;
        this.cachedCauldronEntryOpening = null;
        this.invalidatePlasticGeometry();
    }

    private CompoundTag createDisplayItemsTag(MoldedPlasticData data, HolderLookup.Provider registries) {
        List<MoldedPlasticContents.StoredItem> items = this.level().isClientSide
            ? data.contents().items()
            : new MoldedPlasticStorageHandle(() -> Optional.of(data), ignored -> {}).items();
        CompoundTag tag = new CompoundTag();
        ListTag stacks = new ListTag();
        for (MoldedPlasticContents.StoredItem item : items) {
            if (item.slot() < 0 || item.slot() >= DISPLAY_ITEM_SLOTS || item.stack().isEmpty()) continue;
            CompoundTag entry = new CompoundTag();
            entry.putInt("Slot", item.slot());
            // ItemStack 的持久化 Codec 限制数量为 99；大锅输入可达 576，数量必须独立同步。
            entry.putInt("Count", item.stack().getCount());
            entry.put("Stack", item.stack().copyWithCount(1).save(registries));
            stacks.add(entry);
        }
        tag.put("Stacks", stacks);
        return tag;
    }

    private void readSyncedItems(CompoundTag tag) {
        for (int slot = 0; slot < this.syncedItems.getSlots(); slot++) {
            this.syncedItems.setStackInSlot(slot, ItemStack.EMPTY);
        }
        ListTag stacks = tag.getList("Stacks", Tag.TAG_COMPOUND);
        for (int index = 0; index < stacks.size(); index++) {
            CompoundTag entry = stacks.getCompound(index);
            int slot = entry.getInt("Slot");
            if (slot < 0 || slot >= this.syncedItems.getSlots()) continue;
            ItemStack parsed = ItemStack.parseOptional(this.registryAccess(), entry.getCompound("Stack"));
            if (!parsed.isEmpty()) {
                int count = Math.clamp(entry.getInt("Count"), 0, this.plasticraft$cauldronLayout().slotLimit(slot, parsed));
                this.syncedItems.setStackInSlot(slot, parsed.copyWithCount(count));
            }
        }
    }

    private CompoundTag createDisplayFluidsTag(MoldedPlasticData data, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (!MoldingProductTypes.isCauldron(data.finalType())) return tag;
        List<FluidStack> fluids = this.level().isClientSide
            ? data.contents().fluids()
            : new MoldedPlasticStorageHandle(() -> Optional.of(data), ignored -> {}).fluids();
        ListTag stacks = new ListTag();
        for (FluidStack fluid : fluids) {
            if (!fluid.isEmpty()) stacks.add(fluid.save(registries));
        }
        tag.put("Fluids", stacks);
        return tag;
    }

    private void readSyncedFluids(CompoundTag tag) {
        ListTag stacks = tag.getList("Fluids", Tag.TAG_COMPOUND);
        int count = Math.min(stacks.size(), PlasticCauldronLayout.LARGE.fluidLayers());
        List<FluidStack> fluids = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            FluidStack fluid = FluidStack.parse(this.registryAccess(), stacks.getCompound(index))
                .orElse(FluidStack.EMPTY);
            if (!fluid.isEmpty()) fluids.add(fluid);
        }
        this.syncedFluids = List.copyOf(fluids);
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
        if (!this.level().isClientSide) {
            this.getMoldedData().ifPresent(data -> {
                tag.put("DisplayItems", this.createDisplayItemsTag(data, this.registryAccess()));
                tag.put("DisplayFluids", this.createDisplayFluidsTag(data, this.registryAccess()));
            });
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (this.level().isClientSide && tag.contains("DisplayItems", Tag.TAG_COMPOUND)) {
            CompoundTag displayItems = tag.getCompound("DisplayItems");
            this.entityData.set(DISPLAY_ITEMS, displayItems);
            this.readSyncedItems(displayItems);
        }
        if (this.level().isClientSide && tag.contains("DisplayFluids", Tag.TAG_COMPOUND)) {
            CompoundTag displayFluids = tag.getCompound("DisplayFluids");
            this.entityData.set(DISPLAY_FLUIDS, displayFluids);
            this.readSyncedFluids(displayFluids);
        }
    }

    @Override
    public void tick() {
        PlasticHeatDamage.hurtFromSupport(this);
        if (this.isRemoved()) return;
        super.tick();
        if (this.isRemoved()) return;
        // 托盘光源只由位置与朝向派生，休眠期间两者都不变，跳过重复的收集与网络比较。
        if (!this.plasticraft$isResting()) MoldedTrayLightSource.update(this);
    }

    @Override
    protected void tickAdditionalState() {
        if (this.level().isClientSide) return;
        this.plasticraft$tickRedstoneConductor();
        if (this.isMoldedTray()) this.getMoldedTrayRuntimes().tick();
    }

    @Override
    protected void tickFunctionalState() {
        boolean large = MoldedLargeCauldronInteraction.applies(this);
        if (large) MoldedLargeCauldronEnvironment.absorbSources(this);
        super.tickFunctionalState();
        if (large) MoldedLargeCauldronEnvironment.tick(this);
    }

    @Override
    protected void hurtEntitiesInIgnitedFluid() {
        if (!MoldedLargeCauldronInteraction.applies(this)) super.hurtEntitiesInIgnitedFluid();
    }

    @Override
    public InteractionResult interactAt(Player player, Vec3 location, InteractionHand hand) {
        if (MoldedLargeCauldronInteraction.applies(this) && !player.isShiftKeyDown()
            && !player.getItemInHand(hand).is(ModItemTags.ANVIL_HAMMER)) {
            this.plasticraft$wakeFromRest();
            return this.interactCauldron(player, hand, location);
        }
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
        if (!this.isMoldedCauldron()) return;
        for (ItemStack stack : this.extractAllStacks()) {
            player.getInventory().placeItemBackInInventory(stack);
        }
        this.moldedFluidHandler.discardFluids();
    }

    @Override
    public void remove(RemovalReason reason) {
        this.plasticraft$removeVirtualRedstone();
        super.remove(reason);
    }

    @Override
    public ItemStack getCompletePickResult() {
        return this.withCauldronPickState(super.getCompletePickResult());
    }

    @Override
    protected ItemStack prepareInitialPickResult(ItemStack stack) {
        MoldedPlasticCauldronState.clear(stack);
        MoldedPlasticData.get(stack).ifPresent(data -> MoldedPlasticData.set(stack, data.withoutContents()));
        return stack;
    }

    private ItemStack withCauldronPickState(ItemStack stack) {
        if (!this.isMoldedCauldron()) {
            MoldedPlasticCauldronState.clear(stack);
            return stack;
        }
        MoldedPlasticCauldronState.set(
            stack,
            new MoldedPlasticCauldronState(
                Optional.ofNullable(this.getOutletLocalDirection()),
                this.anvilcraft$isIgnited()
            )
        );
        return stack;
    }

    private void restorePickedCauldronState(MoldedPlasticCauldronState state) {
        if (!this.isMoldedCauldron()) return;
        boolean previousRestoring = this.restoringData;
        this.restoringData = true;
        try {
            this.setOutletLocalDirection(state.outletSide().orElse(null));
            this.anvilcraft$setIgnited(state.ignited());
        } finally {
            this.restoringData = previousRestoring;
        }
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
    protected void handleLandingOnce(BlockPos centerPos, float fallDistance) {
        MoldedPlasticAnvilAbilities.handleLandingOnce(this, centerPos, fallDistance);
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
            ? Optional.of(ShockAnvilBehavior.fromMiningEffect(MoldedPlasticAnvilAbilities.miningEffect(this)))
            : Optional.empty();
    }

    @Override
    public boolean plasticraft$isCauldron() {
        return this.isMoldedCauldron();
    }

    /** 非炼药锅制品不会走到锅逻辑，回退到普通布局只为让接口始终有值可答。 */
    @Override
    public PlasticCauldronLayout plasticraft$cauldronLayout() {
        PlasticCauldronLayout layout = this.getMoldedData()
            .map(data -> PlasticCauldronLayout.of(data.finalType()))
            .orElse(null);
        return layout == null ? PlasticCauldronLayout.NORMAL : layout;
    }

    @Override
    public FluidStack plasticraft$bottomFluid() {
        return this.moldedFluidHandler.getBottomFluid();
    }

    @Override
    public IFluidHandler plasticraft$bottomFluidAccess() {
        return this.moldedFluidHandler.bottomAccess();
    }

    @Override
    public FluidStack plasticraft$ignitionFluid() {
        if (!MoldedLargeCauldronInteraction.applies(this)) return this.plasticraft$bottomFluid();
        List<FluidStack> fluids = this.getSyncedFluids();
        return fluids.isEmpty() ? FluidStack.EMPTY : fluids.getLast();
    }

    @Override
    public IFluidHandler plasticraft$ignitionFluidAccess() {
        return MoldedLargeCauldronInteraction.applies(this) ? this.moldedFluidHandler.topAccess() : this.plasticraft$bottomFluidAccess();
    }

    @Override
    public IItemHandler getInput() {
        if (!CauldronImpactRecipeProcessor.canAccessRecipeInventory(this)) return this.emptyRecipeHandler;
        if (MoldedLargeCauldronInteraction.applies(this)) {
            return this.largeRecipeInput == null ? this.emptyRecipeHandler : this.largeRecipeInput;
        }
        return this.processingOutput ? this.moldedOutputView : this.moldedInputView;
    }

    @Override
    public void beginRecipeProcessing() {
        super.beginRecipeProcessing();
        this.largeRecipeProcessing = MoldedLargeCauldronInteraction.applies(this);
        if (this.largeRecipeProcessing) {
            this.originalLargeOutputs.clear();
            for (int slot = 0; slot < this.moldedOutputView.getSlots(); slot++) {
                this.originalLargeOutputs.add(this.moldedOutputView.getStackInSlot(slot).copy());
            }
            this.selectLargeRecipeInput(-1, false);
        }
    }

    public void selectLargeRecipeInput(int slot, boolean outputs) {
        this.processingOutput = outputs;
        this.largeRecipeInput = slot < 0 ? null : outputs
            ? this.moldedItemHandler.outputRecipeView(slot, this.originalLargeOutputs)
            : this.moldedItemHandler.recipeView(false, slot);
        this.largeOutputInputs.clear();
        if (outputs) {
            for (int index = 0; index < this.moldedOutputView.getSlots(); index++) {
                if (!this.moldedOutputView.getStackInSlot(index).isEmpty()) this.largeOutputInputs.add(index);
            }
        }
    }

    @Override
    public void finishRecipeProcessing() {
        super.finishRecipeProcessing();
        boolean wasLarge = this.largeRecipeProcessing;
        this.largeRecipeProcessing = false;
        this.largeRecipeInput = null;
        this.largeOutputInputs.clear();
        this.originalLargeOutputs.clear();
        if (wasLarge) this.tryAutoOutputResults();
    }

    @Override
    public ItemStack insertRecipeOutput(ItemStack stack) {
        if (!this.largeRecipeProcessing || !this.processingOutput) return super.insertRecipeOutput(stack);
        ItemStack remainder = stack;
        for (int slot = 0; slot < this.moldedOutputView.getSlots() && !remainder.isEmpty(); slot++) {
            if (!this.largeOutputInputs.contains(slot)) remainder = this.moldedOutputView.insertItem(slot, remainder, false);
        }
        return remainder;
    }

    @Override
    public IItemHandler getOutput() {
        if (!CauldronImpactRecipeProcessor.canAccessRecipeInventory(this)) return this.emptyRecipeHandler;
        return this.processingOutput ? this.emptyRecipeHandler : this.moldedOutputView;
    }

    @Override
    public IItemHandler getItemHandler() {
        return this.moldedItemHandler;
    }

    @Override
    public IFluidHandler getFluidHandler() {
        return this.moldedFluidHandler;
    }

    public @Nullable IItemHandler getAutomationItemHandler(@Nullable Direction side) {
        if (this.moldedItemHandler.getSlots() == 0) return null;
        Vec3 point = this.getBoundingBox().getCenter();
        if (side != null) {
            AABB bounds = this.getBoundingBox();
            point = point.add(side.getStepX() * bounds.getXsize() / 2,
                side.getStepY() * bounds.getYsize() / 2, side.getStepZ() * bounds.getZsize() / 2);
        }
        return MoldedLargeCauldronInteraction.applies(this)
            ? MoldedLargeCauldronInteraction.itemAccess(this, side, point) : this.moldedItemHandler;
    }

    public @Nullable IFluidHandler getAutomationFluidHandler(@Nullable Direction side) {
        if (this.moldedFluidHandler.getTanks() == 0) return null;
        return MoldedLargeCauldronInteraction.applies(this)
            ? MoldedLargeCauldronInteraction.fluidAccess(this, side) : this.moldedFluidHandler;
    }

    @Override
    protected IItemHandler cauldronInputHandler() {
        return this.moldedInputView;
    }

    @Override
    protected IItemHandler cauldronOutputHandler() {
        return this.moldedOutputView;
    }

    /** 成型仓储的每次写入都已经过 {@link #replaceMoldedData} 同步与落库，这里只补一次唤醒。 */
    @Override
    protected void onCauldronContentsChanged() {
        this.hasImpulse = true;
    }

    @Override
    protected List<ItemStack> extractAllStacks() {
        List<ItemStack> stacks = new ArrayList<>();
        for (int slot = 0; slot < this.moldedItemHandler.getSlots(); slot++) {
            // 多倍堆叠槽单次只交出一个完整堆叠，需要反复抽取直到清空
            while (true) {
                ItemStack extracted = this.moldedItemHandler.extractItem(slot, Integer.MAX_VALUE, false);
                if (extracted.isEmpty()) break;
                stacks.add(extracted);
            }
        }
        return stacks;
    }

    @Override
    protected void discardPersistentContents() {
        if (!this.isMoldedCauldron()) return;
        boolean previousAutoOutputting = this.autoOutputting;
        this.autoOutputting = true;
        try {
            this.extractAllStacks();
            this.moldedFluidHandler.discardFluids();
        } finally {
            this.autoOutputting = previousAutoOutputting;
        }
    }

    /** 底层流体只占整锅容量中的对应高度，不能把大型锅的一层误算成整口锅。 */
    @Override
    protected AABB cauldronFluidArea() {
        int totalCapacity = this.cauldronTotalFluidCapacity();
        double fill = totalCapacity <= 0
            ? 0.0D
            : Math.clamp(this.plasticraft$bottomFluid().getAmount() / (double) totalCapacity, 0.0D, 1.0D);
        return this.cauldronFluidArea(fill);
    }

    /** 喷流与外部装置需要整锅的真实液面，而不是底层流体的上表面。 */
    @Override
    public double getFluidSurfaceY() {
        int totalCapacity = this.cauldronTotalFluidCapacity();
        long totalAmount = 0L;
        for (int tank = 0; tank < this.moldedFluidHandler.getTanks(); tank++) {
            totalAmount += this.moldedFluidHandler.getFluidInTank(tank).getAmount();
        }
        double fill = totalCapacity <= 0
            ? 0.0D
            : Math.clamp(totalAmount / (double) totalCapacity, 0.0D, 1.0D);
        return this.cauldronFluidArea(fill).maxY;
    }

    AABB cauldronFluidArea(double fill) {
        AABB cavity = this.cauldronCavity();
        if (cavity == null) return this.getBoundingBox();
        // 先在模型局部空间裁出液体盒，再把八个角逐一变换到世界，不能只变换两个对角点。
        // 墙面、天花板和非对称模型下，旋转后的轴向包围盒不再由原来的两个点决定。
        double localMaxY = cavity.minY + (cavity.maxY - cavity.minY) * fill;
        double[] xs = {cavity.minX, cavity.maxX};
        double[] ys = {cavity.minY, localMaxY};
        double[] zs = {cavity.minZ, cavity.maxZ};
        PlasticEntityGeometry geometry = this.plasticraft$getGeometry();
        Vec3 position = this.position();
        PlasticEntityOrientation orientation = this.getOrientation();
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (double x : xs) {
            for (double y : ys) {
                for (double z : zs) {
                    Vec3 world = geometry.worldPointAt(position, orientation, new Vec3(x, y, z));
                    minX = Math.min(minX, world.x);
                    minY = Math.min(minY, world.y);
                    minZ = Math.min(minZ, world.z);
                    maxX = Math.max(maxX, world.x);
                    maxY = Math.max(maxY, world.y);
                    maxZ = Math.max(maxZ, world.z);
                }
            }
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private @Nullable AABB cauldronCavity() {
        if (this.cachedCauldronCavity == null) {
            this.cachedCauldronCavity = this.getMoldedData().flatMap(MoldedPlasticData::cavityBounds).orElse(null);
        }
        return this.cachedCauldronCavity;
    }

    @Override
    protected int cauldronFluidCapacity() {
        return this.getMoldedData()
            .map(data -> this.plasticraft$cauldronLayout().fluidLayerCapacity(data.capacity()))
            .orElse(0);
    }

    private int cauldronTotalFluidCapacity() {
        return Math.multiplyExact(
            this.cauldronFluidCapacity(),
            this.plasticraft$cauldronLayout().fluidLayers()
        );
    }

    @Override
    protected boolean resistsWorldLava() {
        return this.getMoldedData()
            .flatMap(data -> PlasticMaterial.fromMelt(data.material()))
            .filter(material -> material == PlasticMaterial.HEAT_RESISTANT)
            .isPresent();
    }

    @Override
    public List<ItemStack> getSyncedItems() {
        if (this.level().isClientSide) {
            List<ItemStack> stacks = new ArrayList<>();
            for (int slot = 0; slot < this.syncedItems.getSlots(); slot++) {
                ItemStack stack = this.syncedItems.getStackInSlot(slot);
                if (!stack.isEmpty()) stacks.add(stack);
            }
            return stacks;
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (int slot = 0; slot < this.moldedItemHandler.getSlots(); slot++) {
            ItemStack stack = this.moldedItemHandler.getStackInSlot(slot);
            if (!stack.isEmpty()) stacks.add(stack);
        }
        return stacks;
    }

    public ItemStack getSyncedItemInSlot(int slot) {
        return this.level().isClientSide ? this.syncedItems.getStackInSlot(slot).copy() : this.moldedItemHandler.getStackInSlot(slot);
    }

    public List<FluidStack> getSyncedFluids() {
        if (this.level().isClientSide) {
            return this.syncedFluids.stream().map(FluidStack::copy).toList();
        }
        return this.getMoldedData()
            .filter(data -> MoldingProductTypes.isCauldron(data.finalType()))
            .map(data -> new MoldedPlasticStorageHandle(() -> Optional.of(data), ignored -> {}).fluids())
            .orElse(List.of());
    }

    @Override
    public boolean plasticraft$isEntityInsidePlasticMelt(Entity entity) {
        if (MoldedLargeCauldronInteraction.applies(this)) return MoldedLargeCauldronEnvironment.insideMelt(this, entity);
        if (!this.isMoldedCauldron() || !PlasticMaterial.isMelt(this.plasticraft$bottomFluid())) return false;
        if (this.getOrientation().attachmentFace() != Direction.UP) return false;
        return this.cauldronFluidArea().intersects(entity.getBoundingBox());
    }

}
