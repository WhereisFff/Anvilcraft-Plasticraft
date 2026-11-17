package dev.anvilcraft.plasticraft.block;

import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.anvilcraft.plasticraft.material.PlasticMaterial;
import dev.dubhe.anvilcraft.block.cauldron.Layered4LevelCauldronBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.core.cauldron.CauldronInteractions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.Map;

public class UniversalPlasticMeltCauldronBlock extends Layered4LevelCauldronBlock {
    private static final Vec3 STICK_SPEED = new Vec3(
        0.25D,
        0.05D,
        0.25D
    );
    public static final EnumProperty<DyeColor> COLOR = EnumProperty.create("color", DyeColor.class);
    private static final Map<PlasticMaterial, CauldronInteraction.Dispatcher> INTERACTIONS = createInteractions();
    private final PlasticMaterial material;

    public UniversalPlasticMeltCauldronBlock(Properties properties, PlasticMaterial material) {
        super(properties, INTERACTIONS.get(material));
        this.material = material;
        BlockState state = this.stateDefinition.any().setValue(LEVEL, 1);
        if (this.hasColorState()) state = state.setValue(COLOR, DyeColor.WHITE);
        this.registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL);
        if (this.hasColorState()) builder.add(COLOR);
    }

    protected boolean hasColorState() {
        return this.material == null || this.material.hasColorState();
    }

    public boolean containsEntity(BlockState state, BlockPos pos, Entity entity) {
        return this.isEntityInsideContent(state, pos, entity);
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (this.containsEntity(state, pos, entity)) entity.makeStuckInBlock(state, STICK_SPEED);
    }

    public static void registerInteractions() {
        for (PlasticMaterial material : PlasticMaterial.values()) {
            CauldronInteraction.Dispatcher interactions = INTERACTIONS.get(material);
            interactions.put(Items.BUCKET, (state, level, pos, player, hand, stack) ->
                CauldronInteractions.fillBucket(
                    state,
                    level,
                    pos,
                    player,
                    hand,
                    stack,
                    coloredBucket(material, state),
                    candidate -> candidate.is(material.meltCauldron())
                        && candidate.getValue(LEVEL) == MAX_LEVEL,
                    SoundEvents.BUCKET_FILL
                )
            );
            CauldronInteractions.EMPTY.put(
                material.bucket(),
                (state, level, pos, player, hand, stack) -> CauldronInteractions.emptyBucket(
                    level,
                    pos,
                    player,
                    hand,
                    stack,
                    filledState(material, stack),
                    SoundEvents.BUCKET_EMPTY
                ));
        }
    }

    public PlasticMaterial material() {
        return this.material;
    }

    private static ItemStack coloredBucket(PlasticMaterial material, BlockState state) {
        ItemStack bucket = new ItemStack(material.bucket());
        if (material.supportsDyeing() && state.hasProperty(COLOR)) {
            PlasticMeltColor.set(bucket, state.getValue(COLOR));
        }
        return bucket;
    }

    private static BlockState filledState(PlasticMaterial material, ItemStack stack) {
        BlockState state = material.meltCauldron().defaultBlockState().setValue(LEVEL, MAX_LEVEL);
        return material.supportsDyeing() && state.hasProperty(COLOR)
            ? state.setValue(COLOR, PlasticMeltColor.get(stack))
            : state;
    }

    private static Map<PlasticMaterial, CauldronInteraction.Dispatcher> createInteractions() {
        Map<PlasticMaterial, CauldronInteraction.Dispatcher> result = new EnumMap<>(PlasticMaterial.class);
        for (PlasticMaterial material : PlasticMaterial.values()) {
            result.put(
                material,
                new CauldronInteraction.Dispatcher()
            );
        }
        return result;
    }
}
