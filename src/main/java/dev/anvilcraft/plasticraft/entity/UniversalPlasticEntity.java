package dev.anvilcraft.plasticraft.entity;

import dev.anvilcraft.plasticraft.entity.collision.BuiltInPlasticEntityModels;
import dev.anvilcraft.plasticraft.entity.collision.PlasticEntityGeometry;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.item.DyeableMaterial;
import dev.anvilcraft.plasticraft.item.PlasticItemData;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** 保留通用塑料颜色并复用全部公共塑料物理的轻量制品实体。 */
public class UniversalPlasticEntity extends AbstractPlasticEntity {
    private static final EntityDataAccessor<ItemStack> MOLDED_STACK = SynchedEntityData.defineId(
        UniversalPlasticEntity.class,
        EntityDataSerializers.ITEM_STACK
    );
    private static Supplier<ItemStack> defaultDropSupplier = () -> ItemStack.EMPTY;
    private Optional<MoldedPlasticData> cachedMoldedData = Optional.empty();
    private PlasticEntityGeometry cachedMoldedGeometry;
    private boolean moldedDataCached;

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
            this.cachedMoldedGeometry = data.orElseThrow().geometry();
        }
        return this.cachedMoldedGeometry;
    }

    public Optional<MoldedPlasticData> getMoldedData() {
        if (!this.moldedDataCached) {
            Optional<MoldedPlasticData> synchronizedData = MoldedPlasticData.get(this.entityData.get(MOLDED_STACK));
            this.cachedMoldedData = synchronizedData.isPresent()
                ? synchronizedData
                : MoldedPlasticData.get(this.getDropStack());
            this.moldedDataCached = true;
        }
        return this.cachedMoldedData;
    }

    @Override
    protected Component getTypeName() {
        return this.getMoldedData()
            .<Component>map(data -> Component.literal(data.name()))
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
                MoldedPlasticData.set(copy, data.withOrientation(PlasticEntityOrientation.DEFAULT));
                return copy;
            })
            .orElse(ItemStack.EMPTY);
        this.entityData.set(MOLDED_STACK, synchronizedStack);
        this.moldedDataCached = false;
        this.cachedMoldedGeometry = null;
        this.invalidatePlasticGeometry();
    }

    @Override
    protected ItemStack prepareDropStack(ItemStack stack) {
        MoldedPlasticData.get(stack).ifPresent(data ->
            MoldedPlasticData.set(stack, data.withOrientation(PlasticEntityOrientation.DEFAULT))
        );
        return stack;
    }

    @Override
    public ItemStack getPickResult() {
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

}
