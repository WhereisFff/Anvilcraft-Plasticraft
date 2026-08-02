package dev.anvilcraft.plasticraft.entity;

import dev.anvilcraft.plasticraft.block.UniversalPlasticShape;
import dev.anvilcraft.plasticraft.entity.collision.PlasticEntityGeometry;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.item.DyeableMaterial;
import dev.anvilcraft.plasticraft.item.PlasticItemData;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.function.Supplier;

/** 保留通用塑料颜色并复用全部公共塑料物理的轻量制品实体。 */
public class UniversalPlasticEntity extends AbstractPlasticEntity {
    private static Supplier<ItemStack> defaultDropSupplier = () -> ItemStack.EMPTY;

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
    }

    @Override
    protected PlasticEntityGeometry getLocalGeometry() {
        return UniversalPlasticShape.GEOMETRY;
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
