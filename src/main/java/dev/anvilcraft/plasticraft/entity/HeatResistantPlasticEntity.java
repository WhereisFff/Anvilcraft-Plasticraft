package dev.anvilcraft.plasticraft.entity;

import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.item.DyeableMaterial;
import dev.anvilcraft.plasticraft.item.PlasticItemData;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.dubhe.anvilcraft.init.entity.ModDamageTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** 耐热塑料制品实体，保留通用塑料的成型数据并免疫火焰伤害。 */
public final class HeatResistantPlasticEntity extends UniversalPlasticEntity {
    public HeatResistantPlasticEntity(EntityType<? extends UniversalPlasticEntity> entityType, Level level) {
        super(entityType, level);
        this.setDisplayState(PlasticraftBlocks.HEAT_RESISTANT_PLASTIC.get().defaultBlockState());
    }

    public HeatResistantPlasticEntity(
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
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return !source.is(ModDamageTypes.LASER) && super.hurt(source, amount);
    }

    @Override
    protected ItemStack createDefaultDropStack() {
        ItemStack stack = PlasticraftBlocks.HEAT_RESISTANT_PLASTIC.asStack();
        PlasticItemData.setMaterial(stack, this.materialKey());
        BlockState state = this.getDisplayState();
        if (state.hasProperty(DyeableMaterial.COLOR)) {
            PlasticMeltColor.set(stack, state.getValue(DyeableMaterial.COLOR));
        }
        return stack;
    }

    @Override
    protected String materialKey() {
        return "heat_resistant_plastic";
    }
}
