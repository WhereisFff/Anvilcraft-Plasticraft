package dev.anvilcraft.plasticraft.material;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.entity.ClearPlasticEntity;
import dev.anvilcraft.plasticraft.entity.EngineeringPlasticEntity;
import dev.anvilcraft.plasticraft.entity.HeatResistantPlasticEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.init.entity.PlasticraftEntities;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItems;
import dev.anvilcraft.plasticraft.item.DyeableMaterial;
import dev.anvilcraft.plasticraft.item.PlasticItemData;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;

import java.util.Optional;

/** 塑料熔体、凝固制品与运行时实体之间的公共材料边界。 */
public enum PlasticMaterial {
    UNIVERSAL("universal_plastic"),
    ENGINEERING("engineering_plastic"),
    CLEAR("clear_plastic"),
    HEAT_RESISTANT("heat_resistant_plastic");

    private final String key;

    PlasticMaterial(String key) {
        this.key = key;
    }

    public String key() {
        return this.key;
    }

    public Fluid melt() {
        return switch (this) {
            case UNIVERSAL -> PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get();
            case ENGINEERING -> PlasticraftFluids.ENGINEERING_PLASTIC_MELT.get();
            case CLEAR -> PlasticraftFluids.CLEAR_PLASTIC_MELT.get();
            case HEAT_RESISTANT -> PlasticraftFluids.HEAT_RESISTANT_PLASTIC_MELT.get();
        };
    }

    public FluidType meltType() {
        return switch (this) {
            case UNIVERSAL -> PlasticraftFluids.UNIVERSAL_PLASTIC_MELT_TYPE.get();
            case ENGINEERING -> PlasticraftFluids.ENGINEERING_PLASTIC_MELT_TYPE.get();
            case CLEAR -> PlasticraftFluids.CLEAR_PLASTIC_MELT_TYPE.get();
            case HEAT_RESISTANT -> PlasticraftFluids.HEAT_RESISTANT_PLASTIC_MELT_TYPE.get();
        };
    }

    public Block meltBlock() {
        return switch (this) {
            case UNIVERSAL -> PlasticraftBlocks.UNIVERSAL_PLASTIC_MELT.get();
            case ENGINEERING -> PlasticraftBlocks.ENGINEERING_PLASTIC_MELT.get();
            case CLEAR -> PlasticraftBlocks.CLEAR_PLASTIC_MELT.get();
            case HEAT_RESISTANT -> PlasticraftBlocks.HEAT_RESISTANT_PLASTIC_MELT.get();
        };
    }

    public Block meltCauldron() {
        return switch (this) {
            case UNIVERSAL -> PlasticraftBlocks.UNIVERSAL_PLASTIC_MELT_CAULDRON.get();
            case ENGINEERING -> PlasticraftBlocks.ENGINEERING_PLASTIC_MELT_CAULDRON.get();
            case CLEAR -> PlasticraftBlocks.CLEAR_PLASTIC_MELT_CAULDRON.get();
            case HEAT_RESISTANT -> PlasticraftBlocks.HEAT_RESISTANT_PLASTIC_MELT_CAULDRON.get();
        };
    }

    public Block productBlock() {
        return switch (this) {
            case UNIVERSAL -> PlasticraftBlocks.UNIVERSAL_PLASTIC.get();
            case ENGINEERING -> PlasticraftBlocks.ENGINEERING_PLASTIC.get();
            case CLEAR -> PlasticraftBlocks.CLEAR_PLASTIC.get();
            case HEAT_RESISTANT -> PlasticraftBlocks.HEAT_RESISTANT_PLASTIC.get();
        };
    }

    public Item bucket() {
        return switch (this) {
            case UNIVERSAL -> PlasticraftItems.UNIVERSAL_PLASTIC_MELT_BUCKET.get();
            case ENGINEERING -> PlasticraftItems.ENGINEERING_PLASTIC_MELT_BUCKET.get();
            case CLEAR -> PlasticraftItems.CLEAR_PLASTIC_MELT_BUCKET.get();
            case HEAT_RESISTANT -> PlasticraftItems.HEAT_RESISTANT_PLASTIC_MELT_BUCKET.get();
        };
    }

    public Item granule() {
        return switch (this) {
            case UNIVERSAL -> PlasticraftItems.UNIVERSAL_PLASTIC_GRANULE.get();
            case ENGINEERING -> PlasticraftItems.ENGINEERING_PLASTIC_GRANULE.get();
            case CLEAR -> PlasticraftItems.CLEAR_PLASTIC_GRANULE.get();
            case HEAT_RESISTANT -> PlasticraftItems.HEAT_RESISTANT_PLASTIC_GRANULE.get();
        };
    }

    public EntityType<? extends UniversalPlasticEntity> entityType() {
        return switch (this) {
            case UNIVERSAL -> PlasticraftEntities.UNIVERSAL_PLASTIC.get();
            case ENGINEERING -> PlasticraftEntities.ENGINEERING_PLASTIC.get();
            case CLEAR -> PlasticraftEntities.CLEAR_PLASTIC.get();
            case HEAT_RESISTANT -> PlasticraftEntities.HEAT_RESISTANT_PLASTIC.get();
        };
    }

    public ItemStack productStack(DyeColor color) {
        ItemStack stack = new ItemStack(this.productBlock());
        PlasticItemData.setMaterial(stack, this.key);
        if (this.supportsDyeing()) PlasticMeltColor.set(stack, color);
        return stack;
    }

    public BlockState displayState(DyeColor color) {
        BlockState state = this.productBlock().defaultBlockState();
        return this.supportsDyeing() && state.hasProperty(DyeableMaterial.COLOR)
            ? state.setValue(DyeableMaterial.COLOR, color)
            : state;
    }

    public boolean supportsDyeing() {
        return true;
    }

    public boolean hasColorState() {
        return this.supportsDyeing();
    }

    public boolean isTransparent() {
        return this == CLEAR;
    }

    public boolean hasPalette() {
        return true;
    }

    public UniversalPlasticEntity createEntity(
        Level level,
        Vec3 position,
        BlockState displayState,
        ItemStack dropStack,
        PlasticEntityOrientation orientation
    ) {
        return switch (this) {
            case UNIVERSAL -> new UniversalPlasticEntity(
                PlasticraftEntities.UNIVERSAL_PLASTIC.get(),
                level,
                position,
                displayState,
                dropStack,
                orientation
            );
            case ENGINEERING -> new EngineeringPlasticEntity(
                PlasticraftEntities.ENGINEERING_PLASTIC.get(),
                level,
                position,
                displayState,
                dropStack,
                orientation
            );
            case CLEAR -> new ClearPlasticEntity(
                PlasticraftEntities.CLEAR_PLASTIC.get(),
                level,
                position,
                displayState,
                dropStack,
                orientation
            );
            case HEAT_RESISTANT -> new HeatResistantPlasticEntity(
                PlasticraftEntities.HEAT_RESISTANT_PLASTIC.get(),
                level,
                position,
                displayState,
                dropStack,
                orientation
            );
        };
    }

    public ResourceLocation baseTexture(int size) {
        return AnvilcraftPlasticraft.of("textures/entity/" + this.key + "_base_" + size + "x" + size + ".png");
    }

    public ResourceLocation paletteTexture() {
        return AnvilcraftPlasticraft.of("textures/palette/" + this.key + "_palette.png");
    }

    public ResourceLocation sprite(DyeColor color) {
        return AnvilcraftPlasticraft.of("block/" + this.key + "/" + color.getName());
    }

    public static Optional<PlasticMaterial> fromMelt(FluidStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        for (PlasticMaterial material : values()) {
            if (stack.getFluid().getFluidType() == material.meltType()) return Optional.of(material);
        }
        return Optional.empty();
    }

    public static Optional<PlasticMaterial> fromMeltBlock(BlockState state) {
        for (PlasticMaterial material : values()) {
            if (state.is(material.meltBlock())) return Optional.of(material);
        }
        return Optional.empty();
    }

    public static Optional<PlasticMaterial> fromMeltCauldron(BlockState state) {
        for (PlasticMaterial material : values()) {
            if (state.is(material.meltCauldron())) return Optional.of(material);
        }
        return Optional.empty();
    }

    public static Optional<PlasticMaterial> fromProductBlock(BlockState state) {
        for (PlasticMaterial material : values()) {
            if (state.is(material.productBlock())) return Optional.of(material);
        }
        return Optional.empty();
    }

    public static Optional<PlasticMaterial> fromGranule(ItemStack stack) {
        for (PlasticMaterial material : values()) {
            if (stack.is(material.granule())) return Optional.of(material);
        }
        return Optional.empty();
    }

    public static Optional<PlasticMaterial> fromKey(String key) {
        for (PlasticMaterial material : values()) {
            if (material.key.equals(key)) return Optional.of(material);
        }
        return Optional.empty();
    }

    public static boolean isMelt(FluidStack stack) {
        return fromMelt(stack).isPresent();
    }
}
