package dev.anvilcraft.plasticraft.entity;

import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.item.DyeableMaterial;
import dev.anvilcraft.plasticraft.item.PlasticItemData;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** 保留工程塑料材料身份的动态成型制品实体。 */
public final class EngineeringPlasticEntity extends UniversalPlasticEntity {
    public EngineeringPlasticEntity(EntityType<? extends UniversalPlasticEntity> entityType, Level level) {
        super(entityType, level);
        this.setDisplayState(PlasticraftBlocks.ENGINEERING_PLASTIC.get().defaultBlockState());
    }

    public EngineeringPlasticEntity(
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
    protected ItemStack createDefaultDropStack() {
        ItemStack stack = PlasticraftBlocks.ENGINEERING_PLASTIC.asStack();
        PlasticItemData.setMaterial(stack, this.materialKey());
        BlockState state = this.getDisplayState();
        if (state.hasProperty(DyeableMaterial.COLOR)) {
            PlasticMeltColor.set(stack, state.getValue(DyeableMaterial.COLOR));
        }
        return stack;
    }

    @Override
    protected String materialKey() {
        return "engineering_plastic";
    }
}
