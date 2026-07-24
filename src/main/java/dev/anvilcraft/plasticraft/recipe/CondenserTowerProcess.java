package dev.anvilcraft.plasticraft.recipe;

import dev.anvilcraft.lib.v2.recipe.cache.BlockCache;
import dev.anvilcraft.lib.v2.util.predicate.ChanceItemStack;
import dev.anvilcraft.lib.v2.util.predicate.ItemIngredientPredicate;
import dev.anvilcraft.plasticraft.block.CondenserTowerBlock;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.block.entity.CondenserTowerBlockEntity;
import dev.anvilcraft.plasticraft.api.blockentity.EnhancedPlasmaJetExtension;
import dev.anvilcraft.plasticraft.entity.HardenedResinCauldronEntity;
import dev.anvilcraft.plasticraft.init.ModRecipeTypes;
import dev.anvilcraft.plasticraft.init.ModParticles;
import dev.anvilcraft.plasticraft.mixin.VillagerExperienceAccessor;
import dev.anvilcraft.plasticraft.particle.DynamicFluidVaporParticleOptions;
import dev.anvilcraft.lib.v2.yukkuri.api.event.LargeCauldronProcessEvent;
import dev.anvilcraft.lib.v2.yukkuri.api.vapor.IVaporConsumer;
import dev.anvilcraft.lib.v2.yukkuri.api.vapor.VaporAction;
import dev.anvilcraft.lib.v2.yukkuri.api.vapor.VaporizationContext;
import dev.anvilcraft.lib.v2.yukkuri.api.vapor.VaporizationManager;
import dev.anvilcraft.lib.v2.yukkuri.api.vapor.VaporStack;
import dev.dubhe.anvilcraft.api.block.IIgnitableCauldron;
import dev.dubhe.anvilcraft.api.fluid.network.FluidContainerLookup;
import dev.dubhe.anvilcraft.api.itemhandler.ItemHandlerUtil;
import dev.dubhe.anvilcraft.api.fluid.LargeCauldronFluidHandler;
import dev.dubhe.anvilcraft.block.LargeCauldronBlock;
import dev.dubhe.anvilcraft.block.Layered4LevelCauldronBlock;
import dev.dubhe.anvilcraft.block.entity.FishTankBlockEntity;
import dev.dubhe.anvilcraft.block.entity.LargeCauldronBlockEntity;
import dev.dubhe.anvilcraft.block.state.Cube3x3PartHalf;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import dev.dubhe.anvilcraft.init.block.ModFluids;
import dev.dubhe.anvilcraft.init.block.ModFluidTags;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.SuperHeatingRecipe;
import dev.dubhe.anvilcraft.recipe.anvil.outcome.RoyalPreferenceOutcome;
import dev.dubhe.anvilcraft.recipe.anvil.predicate.block.HasCauldron;
import dev.dubhe.anvilcraft.recipe.component.HasCauldronSimple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 等离子喷流对容器的气化、持续加工，以及冷凝塔堆叠识别。 */
public final class CondenserTowerProcess {
    public static final int VAPORIZATION_PER_JET = 5;
    public static final int ENHANCED_VAPORIZATION_PER_JET = 50;
    public static final int MAX_PRODUCTIVE_TOWERS = 4;
    private static final int GASEOUS_EXPERIENCE_PER_PLAYER_XP = 10;
    private static final int GASEOUS_EXPERIENCE_PER_VILLAGER_XP = 256;
    private static final String PLAYER_EXPERIENCE_REMAINDER =
        "anvilcraftplasticraft_gaseous_experience_remainder";
    private static final String VILLAGER_EXPERIENCE_REMAINDER =
        "anvilcraftplasticraft_villager_gaseous_experience_remainder";
    private static final String ACTIVE_BACKPRESSURE = "anvilcraftplasticraft_active_backpressure";
    private static final double LARGE_CAULDRON_MIN_Y = -0.5D + 0.001D;
    private static final double LARGE_CAULDRON_MAX_Y = 1.75D - 0.001D;
    private static final double LARGE_CAULDRON_CONTENT_HEIGHT = 2.25D;
    private static final double LARGE_CAULDRON_VAPOR_HALF_WIDTH = 1.15D;
    private static final double PRESSURIZED_VAPOR_HALF_WIDTH = 0.92D;
    private static final int PRESSURIZED_VAPOR_GRID_SIZE = 3;
    private static final double SMALL_CONTAINER_VAPOR_HALF_WIDTH = 0.11D;
    private CondenserTowerProcess() {
    }

    /** 供测试和旧调用方使用的完整大锅处理入口；运行时由 AnvilCraft 直接调用 Yukkuri。 */
    public static void tickLargeCauldron(ServerLevel level, LargeCauldronBlockEntity cauldron) {
        VaporizationManager.tick(level, cauldron);
    }

    /** 在通用气化事务前处理喷流配方，在事务后推进冷凝。 */
    public static void onLargeCauldronProcess(LargeCauldronProcessEvent event) {
        VaporizationContext context = event.context();
        if (event.phase() == LargeCauldronProcessEvent.Phase.BEFORE_VAPORIZATION) {
            // 先处理已有缓存，避免可冷凝的气体在新蒸汽到达时被误判为溢流。
            condenseTowers(context.level(), context.cauldronPos());
            int jets = countJetsBelow(context.level(), context.cauldronPos());
            if (context.cauldron() instanceof LargeCauldronBlockEntity cauldron) {
                boolean backpressured = isBackpressured(context);
                CompoundTag persistentData = cauldron.getPersistentData();
                boolean activeBackpressure = persistentData.getBoolean(ACTIVE_BACKPRESSURE);
                if (backpressured && (jets > 0 || activeBackpressure)) {
                    if (!activeBackpressure) {
                        persistentData.putBoolean(ACTIVE_BACKPRESSURE, true);
                        cauldron.setChanged();
                    }
                    emitPressurizedVaporParticles(context.level(), cauldron, context.topFluid());
                    extinguishJetsBelow(context.level(), context.cauldronPos());
                    return;
                }
                if (activeBackpressure) {
                    persistentData.remove(ACTIVE_BACKPRESSURE);
                    cauldron.setChanged();
                }
                if (jets > 0) {
                    processLargeRecipes(
                        context.level(),
                        cauldron,
                        jets,
                        getVaporizationRateBelow(context.level(), context.cauldronPos()),
                        false
                    );
                }
            }
        } else {
            // Cached gas can finish condensing after its source has disappeared.
            condenseTowers(context.level(), context.cauldronPos());
            EscapingVaporEffects.tickOilVapor(context.level(), context.cauldronPos());
        }
    }

    /** 普通炼药锅、鱼缸和实体锅只气化，不接入冷凝塔。 */
    public static void tickSmallContainerAboveJet(ServerLevel level, BlockPos jetPos) {
        BlockPos targetPos = jetPos.above();
        BlockState targetState = level.getBlockState(targetPos);
        if (targetState.getBlock() instanceof LargeCauldronBlock
            || targetState.getBlock() instanceof CondenserTowerBlock) {
            return;
        }
        int vaporizationRate = vaporizationRateAt(level, jetPos);

        FluidContainerLookup.Result endpoint = FluidContainerLookup.find(level, targetPos, null);
        boolean bondedCauldron = level.getBlockEntity(targetPos) instanceof BondedEntityBlockEntity bonded
            && bonded.isInitialized()
            && bonded.isPlastic()
            && bonded.getOrCreateRenderEntity() instanceof HardenedResinCauldronEntity;
        if (endpoint != null && (endpoint.cauldron() || endpoint.entity() instanceof HardenedResinCauldronEntity
            || bondedCauldron)) {
            FluidStack stored = findVaporizableFluid(endpoint.handler());
            if (!stored.isEmpty()) {
                FluidStack request = stored.copyWithAmount(Math.min(vaporizationRate, stored.getAmount()));
                FluidStack drained = endpoint.handler().drain(request, IFluidHandler.FluidAction.EXECUTE);
                if (!drained.isEmpty()) {
                    emitSmallContainerVaporParticles(
                        level,
                        smallContainerSurface(level, targetPos, endpoint),
                        drained.getAmount(),
                        drained
                    );
                    return;
                }
            }
        }

        if (!(targetState.getBlock() instanceof IIgnitableCauldron cauldron)) return;
        BlockCache cache = new BlockCache(level);
        Fluid fluid = cauldron.getFluid(cache, targetPos);
        if (!fluid.builtInRegistryHolder().is(ModFluidTags.OIL)) return;
        int interval = Math.max(1, Math.ceilDiv(250, vaporizationRate));
        if (Math.floorMod(level.getGameTime() + targetPos.asLong(), interval) != 0) return;
        if (cauldron.consumeOnce(cache, targetPos)) {
            cache.accept();
            emitSmallContainerVaporParticles(
                level,
                ordinaryCauldronSurface(targetPos, targetState),
                vaporizationRate,
                new FluidStack(fluid, 250)
            );
        }
    }

    public static boolean isPlasmaPassThrough(BlockState state) {
        return state.getBlock() instanceof LargeCauldronBlock
            || state.getBlock() instanceof CondenserTowerBlock;
    }

    public static List<CondenserTowerBlockEntity> findProductiveTowers(Level level, BlockPos cauldronMain) {
        List<CondenserTowerBlockEntity> towers = new ArrayList<>(MAX_PRODUCTIVE_TOWERS);
        BlockPos towerMain = cauldronMain.above(3);
        for (int layer = 0; layer < MAX_PRODUCTIVE_TOWERS; layer++, towerMain = towerMain.above(3)) {
            BlockState state = level.getBlockState(towerMain);
            boolean firstTowerAligned = layer != 0
                || state.hasProperty(CondenserTowerBlock.SEALED) && state.getValue(CondenserTowerBlock.SEALED)
                || CondenserTowerBlock.isAlignedWithLargeCauldron(level, towerMain.below());
            if (!(state.getBlock() instanceof CondenserTowerBlock)
                || !state.hasProperty(CondenserTowerBlock.HALF)
                || state.getValue(CondenserTowerBlock.HALF) != Cube3x3PartHalf.MID_CENTER
                || !firstTowerAligned) {
                break;
            }
            if (!(level.getBlockEntity(towerMain) instanceof CondenserTowerBlockEntity tower)
                || !tower.isMainPart()) break;
            towers.add(tower);
        }
        return List.copyOf(towers);
    }

    public static int countJetsBelow(Level level, BlockPos cauldronMain) {
        int jets = 0;
        BlockPos center = cauldronMain.below(2);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (level.getBlockState(center.offset(x, 0, z)).is(ModBlocks.PLASMA_JETS)) jets++;
            }
        }
        return jets;
    }

    public static int getVaporizationRateBelow(Level level, BlockPos cauldronMain) {
        int rate = 0;
        BlockPos center = cauldronMain.below(2);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos jetPos = center.offset(x, 0, z);
                if (level.getBlockState(jetPos).is(ModBlocks.PLASMA_JETS)) {
                    rate += vaporizationRateAt(level, jetPos);
                }
            }
        }
        return rate;
    }

    private static int vaporizationRateAt(Level level, BlockPos jetPos) {
        return level.getBlockEntity(jetPos) instanceof EnhancedPlasmaJetExtension extension
            && extension.plasticraft$isEnhanced()
            ? ENHANCED_VAPORIZATION_PER_JET
            : VAPORIZATION_PER_JET;
    }

    public static int receiveVapor(
        CondenserTowerBlockEntity entryTower,
        VaporStack vapor,
        VaporAction action,
        VaporizationContext context
    ) {
        List<CondenserTowerBlockEntity> towers = findProductiveTowers(
            context.level(),
            context.cauldronPos()
        );
        if (towers.isEmpty()) towers = List.of(entryTower);
        if (isTowerStackSealed(context) && towers.stream().anyMatch(CondenserTowerBlockEntity::isStorageFull)) {
            return 0;
        }

        ResourceLocation vaporType = CondenserGas.canonicalize(vapor.type());
        boolean gaseousExperience = CondenserGas.GASEOUS_EXPERIENCE.equals(vaporType);
        int remaining = vapor.amount();
        int acceptedTotal = 0;
        boolean hasLayerRecipe = false;
        for (int index = 0; index < towers.size() && remaining > 0; index++) {
            int towerLevel = index + 1;
            Optional<CondenserRecipe> recipe = findCondenserRecipe(context.level(), vapor.type(), towerLevel);
            if (recipe.isEmpty()) continue;
            hasLayerRecipe = true;
            // 气态经验必须先用尽 64 B 气体缓存；250 mB 是冷凝批量，不是溢流阈值。
            int portion = gaseousExperience ? remaining : Math.min(remaining, recipe.get().consume());
            int rateLimit = gaseousExperience ? Integer.MAX_VALUE : recipe.get().consume();
            int accepted = towers.get(index).receiveGas(
                vaporType,
                portion,
                rateLimit,
                context.level().getGameTime(),
                action
            );
            acceptedTotal += accepted;
            // 已满层拒收的蒸汽继续上行，后续层仍只能按自己的配方吞吐上限接收。
            remaining -= accepted;
        }

        if (!hasLayerRecipe) {
            return entryTower.receiveGas(
                vaporType,
                vapor.amount(),
                Integer.MAX_VALUE,
                context.level().getGameTime(),
                action
            );
        }
        return acceptedTotal;
    }

    /** 在真实开放出口渲染未被接收的蒸汽，并应用对应气体的世界效果。 */
    public static void releaseEscapingVapor(
        VaporizationContext context,
        VaporStack vapor,
        FluidStack sourceFluid
    ) {
        if (vapor.isEmpty()) return;
        List<BlockPos> outlets = openVaporOutlets(context);
        if (outlets.isEmpty()) return;
        ResourceLocation vaporType = CondenserGas.canonicalize(vapor.type());
        if (vaporType == null) return;

        if (CondenserGas.GASEOUS_OIL.equals(vaporType)) {
            EscapingVaporEffects.updateOilVapor(
                context.level(),
                context.cauldronPos(),
                outlets,
                vapor.amount()
            );
        }
        emitOutletVaporParticles(
            context.level(),
            outlets,
            vaporType,
            sourceFluid,
            vapor.amount()
        );
        if (CondenserGas.GASEOUS_EXPERIENCE.equals(vaporType)) {
            absorbExperienceVapor(context.level(), outlets, vapor.amount());
        } else if (CondenserGas.GASEOUS_WATER.equals(vaporType)) {
            EscapingVaporEffects.extinguishWaterVapor(context.level(), outlets);
        }
    }

    /** 将冷凝塔未接收、并从真实开放出口逸出的气态经验交给出口处实体。 */
    public static void absorbEscapingExperienceVapor(VaporizationContext context, int amount) {
        if (amount <= 0) return;
        List<BlockPos> outlets = openVaporOutlets(context);
        if (outlets.isEmpty()) return;
        absorbExperienceVapor(context.level(), outlets, amount);
    }

    private static BlockPos towerOutlet(
        VaporizationContext context,
        List<CondenserTowerBlockEntity> towers
    ) {
        return towers.isEmpty() ? context.outletPos() : towers.getLast().getBlockPos().above(2);
    }

    private static void absorbExperienceVapor(ServerLevel level, List<BlockPos> outlets, int amount) {
        List<LivingEntity> absorbers = experienceAbsorbers(level, outlets);
        if (absorbers.isEmpty()) return;
        int share = Math.max(0, (int) Math.round(amount / (double) absorbers.size()));
        if (share <= 0) return;
        for (LivingEntity absorber : absorbers) {
            if (absorber instanceof Player player) {
                givePlayerGaseousExperience(player, share);
            } else if (absorber instanceof Villager villager) {
                giveVillagerGaseousExperience(villager, share);
            }
        }
    }

    private static List<LivingEntity> experienceAbsorbers(ServerLevel level, List<BlockPos> outlets) {
        Set<LivingEntity> absorbers = new LinkedHashSet<>();
        for (BlockPos outlet : outlets) {
            AABB area = EscapingVaporEffects.effectArea(outlet);
            absorbers.addAll(level.getEntitiesOfClass(
                Player.class,
                area,
                player -> !player.isSpectator() && EscapingVaporEffects.isInEffectRange(player, outlet)
            ));
            absorbers.addAll(level.getEntitiesOfClass(
                Villager.class,
                area,
                villager -> canAbsorbExperience(villager)
                    && EscapingVaporEffects.isInEffectRange(villager, outlet)
            ));
        }
        return List.copyOf(absorbers);
    }

    private static boolean canAbsorbExperience(Villager villager) {
        return !villager.isBaby()
            && villager.getVillagerData().getProfession() != VillagerProfession.NONE
            && villager.getVillagerData().getProfession() != VillagerProfession.NITWIT
            && VillagerData.canLevelUp(villager.getVillagerData().getLevel());
    }

    private static void givePlayerGaseousExperience(Player player, int gaseousAmount) {
        CompoundTag data = player.getPersistentData();
        int total = data.getInt(PLAYER_EXPERIENCE_REMAINDER) + gaseousAmount;
        int points = total / GASEOUS_EXPERIENCE_PER_PLAYER_XP;
        int remainder = total % GASEOUS_EXPERIENCE_PER_PLAYER_XP;
        if (remainder == 0) {
            data.remove(PLAYER_EXPERIENCE_REMAINDER);
        } else {
            data.putInt(PLAYER_EXPERIENCE_REMAINDER, remainder);
        }
        if (points > 0) player.giveExperiencePoints(points);
    }

    private static void giveVillagerGaseousExperience(Villager villager, int gaseousAmount) {
        CompoundTag data = villager.getPersistentData();
        int total = data.getInt(VILLAGER_EXPERIENCE_REMAINDER) + gaseousAmount;
        int points = total / GASEOUS_EXPERIENCE_PER_VILLAGER_XP;
        int remainder = total % GASEOUS_EXPERIENCE_PER_VILLAGER_XP;
        if (remainder == 0) {
            data.remove(VILLAGER_EXPERIENCE_REMAINDER);
        } else {
            data.putInt(VILLAGER_EXPERIENCE_REMAINDER, remainder);
        }
        if (points <= 0) return;
        villager.setVillagerXp(Math.min(250, villager.getVillagerXp() + points));
        while (VillagerData.canLevelUp(villager.getVillagerData().getLevel())
            && villager.getVillagerXp() >= VillagerData.getMaxXpPerLevel(villager.getVillagerData().getLevel())) {
            ((VillagerExperienceAccessor) villager).plasticraft$increaseMerchantCareer();
        }
    }

    public static boolean isTowerStackSealed(VaporizationContext context) {
        List<CondenserTowerBlockEntity> towers = findProductiveTowers(
            context.level(),
            context.cauldronPos()
        );
        if (towers.isEmpty()) return false;
        return isOutletBlocked(context.level(), towerOutlet(context, towers));
    }

    public static boolean isPhysicalOutletBlocked(VaporizationContext context) {
        return openVaporOutlets(context).isEmpty();
    }

    private static List<BlockPos> openVaporOutlets(VaporizationContext context) {
        List<CondenserTowerBlockEntity> towers = findProductiveTowers(
            context.level(),
            context.cauldronPos()
        );
        if (!towers.isEmpty()) {
            BlockPos outlet = towerOutlet(context, towers);
            return isOutletBlocked(context.level(), outlet) ? List.of() : List.of(outlet);
        }

        List<BlockPos> outlets = new ArrayList<>(9);
        BlockPos center = context.outletPos();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos outlet = center.offset(x, 0, z);
                if (!isOutletBlocked(context.level(), outlet)) outlets.add(outlet.immutable());
            }
        }
        return List.copyOf(outlets);
    }

    private static boolean isOutletBlocked(Level level, BlockPos outlet) {
        return level.getBlockState(outlet).isCollisionShapeFullBlock(level, outlet);
    }

    private static boolean isBackpressured(VaporizationContext context) {
        if (context.topFluid().isEmpty()) return false;
        IVaporConsumer consumer = VaporizationManager.findConsumer(context);
        if (consumer == null) return isPhysicalOutletBlocked(context);
        if (!consumer.sealsOutlet(context)) return false;
        List<CondenserTowerBlockEntity> towers = findProductiveTowers(context.level(), context.cauldronPos());
        return !towers.isEmpty() && towers.stream().anyMatch(CondenserTowerBlockEntity::isStorageFull);
    }

    private static void extinguishJetsBelow(ServerLevel level, BlockPos cauldronMain) {
        BlockPos center = cauldronMain.below(2);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos jetPos = center.offset(x, 0, z);
                if (level.getBlockState(jetPos).is(ModBlocks.PLASMA_JETS)) {
                    level.removeBlock(jetPos, false);
                }
            }
        }
    }

    private static void condenseTowers(ServerLevel level, BlockPos cauldronMain) {
        List<CondenserTowerBlockEntity> towers = findProductiveTowers(level, cauldronMain);
        for (int index = 0; index < towers.size(); index++) {
            condenseTower(level, towers.get(index), index + 1);
        }
    }

    private static void condenseTower(
        ServerLevel level,
        CondenserTowerBlockEntity tower,
        int towerLevel
    ) {
        ResourceLocation gasId = tower.getGasId();
        if (gasId == null || tower.getGasAmount() <= 0) return;
        Optional<CondenserRecipe> found = findCondenserRecipe(level, gasId, towerLevel)
            .filter(recipe -> recipe.matches(gasId, tower.getGasAmount()));
        if (found.isEmpty()) return;
        CondenserRecipe recipe = found.get();
        Fluid outputFluid = BuiltInRegistries.FLUID.get(recipe.fluid());
        if (outputFluid == null || outputFluid == Fluids.EMPTY) return;
        int batches = tower.getGasAmount() / recipe.consume();
        if (batches <= 0) return;
        int requested = (int) Math.min(Integer.MAX_VALUE, (long) batches * recipe.produce());
        int accepted = tower.getFluidHandler().fill(
            new FluidStack(outputFluid, requested),
            IFluidHandler.FluidAction.SIMULATE
        );
        batches = Math.min(batches, accepted / recipe.produce());
        if (batches <= 0) return;
        int consumed = batches * recipe.consume();
        int produced = batches * recipe.produce();
        if (tower.drainGas(gasId, consumed) != consumed) return;
        tower.getFluidHandler().fill(
            new FluidStack(outputFluid, produced),
            IFluidHandler.FluidAction.EXECUTE
        );
    }

    private static Optional<CondenserRecipe> findCondenserRecipe(
        ServerLevel level,
        ResourceLocation gas,
        int towerLevel
    ) {
        return level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.CONDENSER_TYPE.get()).stream()
            .filter(holder -> holder.value().towerLevel() == towerLevel)
            .filter(holder -> holder.value().gas().equals(CondenserGas.canonicalize(gas)))
            .sorted(Comparator.comparing(holder -> holder.id().toString()))
            .map(RecipeHolder::value)
            .findFirst();
    }

    private static void processLargeRecipes(
        ServerLevel level,
        LargeCauldronBlockEntity cauldron,
        int jets,
        int vaporizationRate,
        boolean backpressured
    ) {
        IItemHandler input = cauldron.getInputHandler();
        IItemHandler output = cauldron.getOutputHandler();
        List<RecipeHolder<PlasmaJetBlastingRecipe>> plasmaRecipes = new ArrayList<>(
            level.getRecipeManager().getAllRecipesFor(
                ModRecipeTypes.PLASMA_JET_BLASTING_TYPE.get()
            )
        );
        plasmaRecipes.sort(Comparator.comparingInt(
            (RecipeHolder<PlasmaJetBlastingRecipe> holder) -> holder.value().priority()
        ).reversed());
        List<RecipeHolder<SuperHeatingRecipe>> superHeatingRecipes = new ArrayList<>(
            level.getRecipeManager().getAllRecipesFor(
                dev.dubhe.anvilcraft.init.recipe.ModRecipeTypes.SUPER_HEATING_TYPE.get()
            )
        );
        superHeatingRecipes.sort(Comparator.comparingInt(
            (RecipeHolder<SuperHeatingRecipe> holder) -> holder.value().priority()
        ).reversed());
        // 每条喷流每 tick 至多推进一次配方，避免单条喷流瞬间清空整个输入栏。
        for (int operation = 0; operation < jets; operation++) {
            if (processPlasmaRecipe(level, cauldron, input, output, plasmaRecipes)) continue;
            if (processSuperHeatingItemRecipe(level, cauldron, input, output, superHeatingRecipes)) continue;
            if (processVanillaItemRecipe(level, input, output)) continue;
            break;
        }
    }

    private static boolean processPlasmaRecipe(
        ServerLevel level,
        LargeCauldronBlockEntity cauldron,
        IItemHandler input,
        IItemHandler output,
        List<RecipeHolder<PlasmaJetBlastingRecipe>> recipes
    ) {
        for (RecipeHolder<PlasmaJetBlastingRecipe> holder : recipes) {
            PlasmaJetBlastingRecipe recipe = holder.value();
            if (isDirectVaporizationRecipe(recipe)) continue;
            if (applyProcess(level, cauldron, input, output,
                new ItemProcessDefinition(
                    recipe.getInputItems(),
                    recipe.getResultItems(),
                    recipe.getHasCauldron(),
                    false
                ))) {
                return true;
            }
        }
        return false;
    }

    private static boolean processSuperHeatingItemRecipe(
        ServerLevel level,
        LargeCauldronBlockEntity cauldron,
        IItemHandler input,
        IItemHandler output,
        List<RecipeHolder<SuperHeatingRecipe>> recipes
    ) {
        for (RecipeHolder<SuperHeatingRecipe> holder : recipes) {
            SuperHeatingRecipe recipe = holder.value();
            if (recipe.getInputItems().isEmpty()) continue;
            if (applyProcess(level, cauldron, input, output,
                new ItemProcessDefinition(
                    recipe.getInputItems(),
                    recipe.getResultItems(),
                    recipe.getHasCauldron(),
                    recipe.isHasRoyalPreference() && hasRoyalPreferredInput(level, input)
                ))) {
                return true;
            }
        }
        return false;
    }

    private static boolean processVanillaItemRecipe(
        ServerLevel level,
        IItemHandler input,
        IItemHandler output
    ) {
        for (int slot = 0; slot < input.getSlots(); slot++) {
            ItemStack stack = input.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            ItemStack result = findVanillaBlastingResult(level, stack);
            if (result.isEmpty()) result = findVanillaSmeltingResult(level, stack);
            if (result.isEmpty()) continue;
            if (!ItemHandlerUtil.insertItem(output, result.copy(), true).isEmpty()) continue;
            input.extractItem(slot, 1, false);
            ItemHandlerUtil.insertItem(output, result.copy(), false);
            return true;
        }
        return false;
    }

    private static boolean applyProcess(
        ServerLevel level,
        LargeCauldronBlockEntity cauldron,
        IItemHandler input,
        IItemHandler output,
        ItemProcessDefinition definition
    ) {
        List<MatchedItem> matched = matchItems(input, definition.items());
        if (matched == null) return false;

        FluidStack fluidInput = resolveFluidInput(cauldron, definition.cauldron());
        if (definition.hasFluidInput() && fluidInput == null) return false;
        FluidStack vaporParticleFluid = fluidInput == null
            ? FluidStack.EMPTY
            : fluidInput.copyWithAmount(1);

        List<ItemStack> results = new ArrayList<>();
        for (ChanceItemStack chance : definition.results()) {
            ItemStack result = chance.getResult(level);
            if (result.isEmpty()) continue;
            results.add(result);
            if (definition.royalPreferenceBonus() && isRoyalSteel(result)) results.add(result.copy());
        }
        ResourceLocation outputId = definition.cauldron().transform();
        int fluidAmount = definition.cauldron().produce();
        Fluid outputFluid = null;
        VaporizationContext vaporContext = null;
        VaporStack vaporOutput = null;
        int acceptedVapor = 0;
        if (definition.hasFluidOutput() && CondenserGas.isGas(outputId)) {
            ResourceLocation vaporType = CondenserGas.canonicalize(outputId);
            if (vaporType == null) return false;
            vaporContext = new VaporizationContext(level, cauldron);
            vaporOutput = new VaporStack(vaporType, fluidAmount);
            IVaporConsumer consumer = VaporizationManager.findConsumer(vaporContext);
            if (consumer != null) {
                acceptedVapor = Math.clamp(
                    consumer.receiveVapor(vaporOutput, VaporAction.SIMULATE, vaporContext),
                    0,
                    fluidAmount
                );
                if (consumer.sealsOutlet(vaporContext) && acceptedVapor != fluidAmount) return false;
            } else if (isPhysicalOutletBlocked(vaporContext)) {
                return false;
            }
        }
        if (definition.hasFluidOutput() && !CondenserGas.isGas(outputId)) {
            outputFluid = BuiltInRegistries.FLUID.get(outputId);
            if (outputFluid == null || outputFluid == Fluids.EMPTY) return false;
            if (!canApplyFluidTransform(
                cauldron,
                fluidInput,
                definition.cauldron().consume(),
                outputFluid,
                fluidAmount
            )) return false;
        }
        if (definition.results().isEmpty() && !definition.hasFluidOutput()) return false;
        if (!canInsertAll(output, results)) return false;

        for (MatchedItem item : matched) input.extractItem(item.slot(), item.amount(), false);
        if (fluidInput != null && definition.cauldron().consume() > 0) {
            FluidStack drained = cauldron.getFluids().drainStoredFluid(
                fluidInput.copyWithAmount(definition.cauldron().consume()),
                IFluidHandler.FluidAction.EXECUTE
            );
            if (drained.getAmount() != definition.cauldron().consume()) return false;
        }
        for (ItemStack result : results) ItemHandlerUtil.insertItem(output, result.copy(), false);
        if (definition.hasFluidOutput()) {
            if (CondenserGas.isGas(outputId)) {
                emitLargeCauldronVaporParticles(
                    level,
                    largeCauldronSurface(cauldron),
                    definition.cauldron().consume(),
                    vaporParticleFluid
                );
                if (vaporContext != null && vaporOutput != null && acceptedVapor > 0) {
                    VaporizationManager.receiveVapor(
                        vaporContext,
                        vaporOutput.withAmount(acceptedVapor),
                        VaporAction.EXECUTE
                    );
                }
                if (vaporContext != null && vaporOutput != null && acceptedVapor < fluidAmount) {
                    releaseEscapingVapor(
                        vaporContext,
                        vaporOutput.withAmount(fluidAmount - acceptedVapor),
                        vaporParticleFluid
                    );
                }
            } else if (outputFluid != null) {
                cauldron.getFluids().fill(
                    new FluidStack(outputFluid, fluidAmount),
                    IFluidHandler.FluidAction.EXECUTE
                );
            }
        }
        return true;
    }

    public static boolean isDirectVaporizationRecipe(PlasmaJetBlastingRecipe recipe) {
        if (!recipe.getInputItems().isEmpty() || !recipe.getResultItems().isEmpty()) return false;
        return recipe.hasFluidInput()
            && recipe.hasFluidOutput()
            && CondenserGas.isGas(recipe.getHasCauldron().transform());
    }

    private static boolean canApplyFluidTransform(
        LargeCauldronBlockEntity cauldron,
        @org.jetbrains.annotations.Nullable FluidStack input,
        int consume,
        Fluid output,
        int produce
    ) {
        LargeCauldronFluidHandler simulated = new LargeCauldronFluidHandler(() -> {
        });
        simulated.setFluids(cauldron.getFluids().copyFluids());
        if (input != null && consume > 0) {
            FluidStack drained = simulated.drainStoredFluid(
                input.copyWithAmount(consume),
                IFluidHandler.FluidAction.EXECUTE
            );
            if (drained.getAmount() != consume) return false;
        }
        return simulated.fill(new FluidStack(output, produce), IFluidHandler.FluidAction.EXECUTE) == produce;
    }

    private static boolean canInsertAll(IItemHandler output, List<ItemStack> results) {
        List<ItemStack> slots = new ArrayList<>(output.getSlots());
        for (int slot = 0; slot < output.getSlots(); slot++) slots.add(output.getStackInSlot(slot).copy());
        for (ItemStack result : results) {
            if (result.isEmpty()) continue;
            int remaining = result.getCount();
            for (int slot = 0; slot < output.getSlots() && remaining > 0; slot++) {
                ItemStack stored = slots.get(slot);
                if (stored.isEmpty()
                    || !output.isItemValid(slot, result)
                    || !ItemStack.isSameItemSameComponents(stored, result)) continue;
                int limit = Math.min(output.getSlotLimit(slot), stored.getMaxStackSize());
                int inserted = Math.min(remaining, Math.max(0, limit - stored.getCount()));
                if (inserted <= 0) continue;
                slots.set(slot, stored.copyWithCount(stored.getCount() + inserted));
                remaining -= inserted;
            }
            for (int slot = 0; slot < output.getSlots() && remaining > 0; slot++) {
                if (!slots.get(slot).isEmpty() || !output.isItemValid(slot, result)) continue;
                int inserted = Math.min(
                    remaining,
                    Math.min(output.getSlotLimit(slot), result.getMaxStackSize())
                );
                if (inserted <= 0) continue;
                slots.set(slot, result.copyWithCount(inserted));
                remaining -= inserted;
            }
            if (remaining > 0) return false;
        }
        return true;
    }

    private static boolean hasRoyalPreferredInput(ServerLevel level, IItemHandler input) {
        for (int slot = 0; slot < input.getSlots(); slot++) {
            ItemStack stack = input.getStackInSlot(slot);
            if (!stack.isEmpty() && RoyalPreferenceOutcome.RoyalPreference.isRoyalPreferred(level, stack)) return true;
        }
        return false;
    }

    private static boolean isRoyalSteel(ItemStack stack) {
        return stack.is(dev.dubhe.anvilcraft.init.item.ModItems.ROYAL_STEEL_INGOT.get())
            || stack.is(ModBlocks.ROYAL_STEEL_BLOCK.get().asItem());
    }

    private static @org.jetbrains.annotations.Nullable List<MatchedItem> matchItems(
        IItemHandler input,
        List<ItemIngredientPredicate> predicates
    ) {
        if (predicates.isEmpty()) return List.of();
        int[] available = new int[input.getSlots()];
        for (int slot = 0; slot < input.getSlots(); slot++) available[slot] = input.getStackInSlot(slot).getCount();
        List<Integer> order = new ArrayList<>(predicates.size());
        for (int index = 0; index < predicates.size(); index++) order.add(index);
        // Assign the most constrained predicates first; the backtracking fallback handles overlapping tags.
        order.sort(Comparator.comparingInt(index -> countCandidateSlots(input, predicates.get(index), available)));
        List<MatchedItem> matched = new ArrayList<>(predicates.size());
        return matchItems(input, predicates, order, 0, available, matched) ? List.copyOf(matched) : null;
    }

    private static int countCandidateSlots(
        IItemHandler input,
        ItemIngredientPredicate predicate,
        int[] available
    ) {
        int count = 0;
        for (int slot = 0; slot < input.getSlots(); slot++) {
            if (available[slot] >= predicate.count()
                && predicate.test(input.getStackInSlot(slot).copyWithCount(available[slot]))) count++;
        }
        return count;
    }

    private static boolean matchItems(
        IItemHandler input,
        List<ItemIngredientPredicate> predicates,
        List<Integer> order,
        int index,
        int[] available,
        List<MatchedItem> matched
    ) {
        if (index >= order.size()) return true;
        ItemIngredientPredicate predicate = predicates.get(order.get(index));
        for (int slot = 0; slot < input.getSlots(); slot++) {
            if (available[slot] < predicate.count()
                || !predicate.test(input.getStackInSlot(slot).copyWithCount(available[slot]))) continue;
            available[slot] -= predicate.count();
            matched.add(new MatchedItem(slot, predicate.count()));
            if (matchItems(input, predicates, order, index + 1, available, matched)) return true;
            matched.removeLast();
            available[slot] += predicate.count();
        }
        return false;
    }

    private static @org.jetbrains.annotations.Nullable FluidStack resolveFluidInput(
        LargeCauldronBlockEntity cauldron,
        HasCauldronSimple definition
    ) {
        if (!HasCauldron.isNotEmpty(definition.fluid()) && definition.fluidTag() == null) return null;
        FluidStack top = cauldron.getTopFluid();
        if (top.isEmpty() || definition.consume() <= 0 || top.getAmount() < definition.consume()
            || !matchesFluidInput(top, definition)) {
            return null;
        }
        return top;
    }

    private static boolean matchesFluidInput(FluidStack input, HasCauldronSimple definition) {
        if (input.isEmpty()) return false;
        if (definition.fluidTag() != null) {
            net.minecraft.tags.TagKey<Fluid> tag = net.minecraft.tags.TagKey.create(
                net.minecraft.core.registries.Registries.FLUID,
                definition.fluidTag()
            );
            return input.is(tag);
        }
        if (!HasCauldron.isNotEmpty(definition.fluid())) return false;
        Fluid expected = BuiltInRegistries.FLUID.get(definition.fluid());
        return expected != null && expected != Fluids.EMPTY && input.is(expected);
    }

    private record MatchedItem(int slot, int amount) {
    }

    private record ItemProcessDefinition(
        List<ItemIngredientPredicate> items,
        List<ChanceItemStack> results,
        HasCauldronSimple cauldron,
        boolean royalPreferenceBonus
    ) {
        private boolean hasFluidInput() {
            return (HasCauldron.isNotEmpty(this.cauldron.fluid()) || this.cauldron.fluidTag() != null)
                && this.cauldron.consume() > 0;
        }

        private boolean hasFluidOutput() {
            return HasCauldron.isNotEmpty(this.cauldron.transform()) && this.cauldron.produce() > 0;
        }
    }

    private static ItemStack findVanillaBlastingResult(ServerLevel level, ItemStack input) {
        Optional<RecipeHolder<BlastingRecipe>> holder = level.getRecipeManager().getRecipeFor(
            RecipeType.BLASTING,
            new SingleRecipeInput(input.copyWithCount(1)),
            level
        );
        return holder.map(value -> value.value().assemble(
            new SingleRecipeInput(input.copyWithCount(1)),
            level.registryAccess()
        )).orElse(ItemStack.EMPTY);
    }

    private static ItemStack findVanillaSmeltingResult(ServerLevel level, ItemStack input) {
        return level.getRecipeManager().getRecipeFor(
                RecipeType.SMELTING,
                new SingleRecipeInput(input.copyWithCount(1)),
                level
            )
            .map(value -> value.value().assemble(
                new SingleRecipeInput(input.copyWithCount(1)),
                level.registryAccess()
            ))
            .orElse(ItemStack.EMPTY);
    }

    private static FluidStack findVaporizableFluid(IFluidHandler handler) {
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack stored = handler.getFluidInTank(tank);
            if (stored.is(ModFluidTags.OIL)) return stored.copy();
        }
        return FluidStack.EMPTY;
    }

    static Vec3 largeCauldronSurface(LargeCauldronBlockEntity cauldron) {
        float fill = Math.clamp(
            (float) cauldron.getFluids().getTotalAmount() / LargeCauldronFluidHandler.TOTAL_CAPACITY,
            0.0F,
            1.0F
        );
        // The renderer's pose is rooted at the main block position, not its center.
        return cauldron.getBlockPos().getCenter().add(
            0.0,
            LARGE_CAULDRON_MIN_Y - 0.5D + LARGE_CAULDRON_CONTENT_HEIGHT * fill,
            0.0
        );
    }

    private static Vec3 smallContainerSurface(
        ServerLevel level,
        BlockPos pos,
        FluidContainerLookup.Result endpoint
    ) {
        if (endpoint.entity() instanceof HardenedResinCauldronEntity pot) {
            Vec3 center = pot.getBoundingBox().getCenter();
            return new Vec3(center.x, pot.getFluidSurfaceY(), center.z);
        }
        if (level.getBlockEntity(pos) instanceof BondedEntityBlockEntity bonded
            && bonded.isInitialized()
            && bonded.isPlastic()
            && bonded.getOrCreateRenderEntity() instanceof HardenedResinCauldronEntity pot) {
            Vec3 center = pot.getBoundingBox().getCenter();
            return new Vec3(center.x, pot.getFluidSurfaceY(), center.z);
        }
        if (level.getBlockEntity(pos) instanceof FishTankBlockEntity tank) {
            float fill = Math.clamp(
                (float) tank.getFluidHandler().getFluidAmount() / tank.getFluidHandler().getCapacity(),
                0.0F,
                1.0F
            );
            return pos.getBottomCenter().add(0.0, 1.0 / 16.0 + 0.001 + (1.0 - 2.0 / 16.0 - 0.002) * fill, 0.0);
        }
        return ordinaryCauldronSurface(pos, level.getBlockState(pos));
    }

    private static Vec3 ordinaryCauldronSurface(BlockPos pos, BlockState state) {
        double height = 0.75D;
        if (state.getBlock() instanceof Layered4LevelCauldronBlock
            && state.hasProperty(Layered4LevelCauldronBlock.LEVEL)) {
            height = (6.0D + state.getValue(Layered4LevelCauldronBlock.LEVEL) * 2.0D) / 16.0D;
        }
        return pos.getCenter().add(0.0, height - 0.5, 0.0);
    }

    static void emitLargeCauldronVaporParticles(
        ServerLevel level,
        Vec3 surface,
        int vaporizationRate,
        @org.jetbrains.annotations.Nullable FluidStack sourceFluid
    ) {
        emitLargeCauldronVaporParticles(
            level,
            surface,
            vaporizationRate,
            vaporParticle(sourceFluid, vaporizationRate)
        );
    }

    static void emitLargeCauldronExperienceVaporParticles(
        ServerLevel level,
        Vec3 surface,
        int vaporizationRate
    ) {
        emitLargeCauldronVaporParticles(
            level,
            surface,
            vaporizationRate,
            ModParticles.EXPERIENCE_VAPOR.get()
        );
    }

    private static void emitLargeCauldronVaporParticles(
        ServerLevel level,
        Vec3 surface,
        int vaporizationRate,
        ParticleOptions particle
    ) {
        RandomSource random = level.getRandom();
        int count = vaporParticleCount(vaporizationRate, 28);
        int gridSize = (int) Math.ceil(Math.sqrt(count));
        int phase = Math.floorMod(level.getGameTime(), gridSize);
        for (int index = 0; index < count; index++) {
            int cellX = index % gridSize;
            int band = index / gridSize;
            // 按交错对角线轮换采样，使低粒子数时也能同时覆盖液面两侧。
            int cellZ = Math.floorMod(cellX + band + phase, gridSize);
            double x = surface.x + cellCoordinate(cellX, gridSize, random)
                * LARGE_CAULDRON_VAPOR_HALF_WIDTH;
            double z = surface.z + cellCoordinate(cellZ, gridSize, random)
                * LARGE_CAULDRON_VAPOR_HALF_WIDTH;
            sendVaporParticle(level, particle, random, x, surface.y, z, vaporizationRate);
        }
    }

    private static void emitPressurizedVaporParticles(
        ServerLevel level,
        LargeCauldronBlockEntity cauldron,
        FluidStack sourceFluid
    ) {
        if (sourceFluid.isEmpty() || level.getGameTime() % 3 != 0) return;
        Vec3 surface = largeCauldronSurface(cauldron);
        double minY = surface.y + 0.08D;
        double maxY = cauldron.getBlockPos().getY() + LARGE_CAULDRON_MAX_Y - 0.10D;
        if (maxY <= minY) return;
        RandomSource random = level.getRandom();
        ParticleOptions particle = isExperienceFluid(sourceFluid)
            ? ModParticles.EXPERIENCE_VAPOR.get()
            : new DynamicFluidVaporParticleOptions(sourceFluid, true);
        for (int cellY = 0; cellY < PRESSURIZED_VAPOR_GRID_SIZE; cellY++) {
            for (int cellX = 0; cellX < PRESSURIZED_VAPOR_GRID_SIZE; cellX++) {
                for (int cellZ = 0; cellZ < PRESSURIZED_VAPOR_GRID_SIZE; cellZ++) {
                    double x = surface.x + cellCoordinate(cellX, PRESSURIZED_VAPOR_GRID_SIZE, random)
                        * PRESSURIZED_VAPOR_HALF_WIDTH;
                    double yProgress = (cellY + 0.15D + random.nextDouble() * 0.70D)
                        / PRESSURIZED_VAPOR_GRID_SIZE;
                    double y = minY + (maxY - minY) * yProgress;
                    double z = surface.z + cellCoordinate(cellZ, PRESSURIZED_VAPOR_GRID_SIZE, random)
                        * PRESSURIZED_VAPOR_HALF_WIDTH;
                    level.sendParticles(
                        particle,
                        x,
                        y,
                        z,
                        0,
                        (random.nextDouble() - 0.5D) * 0.002D,
                        0.0005D + random.nextDouble() * 0.0015D,
                        (random.nextDouble() - 0.5D) * 0.002D,
                        1.0D
                    );
                }
            }
        }
    }

    private static void emitSmallContainerVaporParticles(
        ServerLevel level,
        Vec3 surface,
        int vaporizationRate,
        @org.jetbrains.annotations.Nullable FluidStack sourceFluid
    ) {
        RandomSource random = level.getRandom();
        int count = vaporParticleCount(vaporizationRate, 8);
        ParticleOptions particle = vaporParticle(sourceFluid, vaporizationRate);
        for (int i = 0; i < count; i++) {
            double x = surface.x + (random.nextDouble() * 2.0D - 1.0D) * SMALL_CONTAINER_VAPOR_HALF_WIDTH;
            double z = surface.z + (random.nextDouble() * 2.0D - 1.0D) * SMALL_CONTAINER_VAPOR_HALF_WIDTH;
            sendVaporParticle(level, particle, random, x, surface.y, z, vaporizationRate);
        }
    }

    private static int vaporParticleCount(int vaporizationRate, int maximum) {
        return Math.clamp(1 + Math.ceilDiv(Math.max(1, vaporizationRate), VAPORIZATION_PER_JET), 2, maximum);
    }

    private static void emitOutletVaporParticles(
        ServerLevel level,
        List<BlockPos> outlets,
        ResourceLocation vaporType,
        FluidStack sourceFluid,
        int escapingAmount
    ) {
        int count = outletVaporParticleCount(escapingAmount);
        ParticleOptions particle = outletVaporParticle(vaporType, sourceFluid, escapingAmount);
        RandomSource random = level.getRandom();
        int phase = Math.floorMod(level.getGameTime(), outlets.size());
        double intensity = Math.clamp(escapingAmount / (double) ENHANCED_VAPORIZATION_PER_JET, 0.1D, 1.0D);
        for (int index = 0; index < count; index++) {
            BlockPos outlet = outlets.get(Math.floorMod(phase + index, outlets.size()));
            double x = outlet.getX() + 0.25D + random.nextDouble() * 0.5D;
            double y = outlet.getY() + 0.06D + random.nextDouble() * 0.08D;
            double z = outlet.getZ() + 0.25D + random.nextDouble() * 0.5D;
            level.sendParticles(
                particle,
                x,
                y,
                z,
                0,
                (random.nextDouble() - 0.5D) * 0.020D,
                0.050D + random.nextDouble() * 0.025D + intensity * 0.020D,
                (random.nextDouble() - 0.5D) * 0.020D,
                1.0D
            );
        }
    }

    static int outletVaporParticleCount(int escapingAmount) {
        return Math.max(1, vaporParticleCount(escapingAmount, 28) / 2);
    }

    private static ParticleOptions outletVaporParticle(
        ResourceLocation vaporType,
        FluidStack sourceFluid,
        int escapingAmount
    ) {
        if (CondenserGas.GASEOUS_EXPERIENCE.equals(vaporType)) {
            return ModParticles.EXPERIENCE_VAPOR_OUTLET.get();
        }
        FluidStack particleFluid = sourceFluid.isEmpty()
            ? fallbackVaporFluid(vaporType)
            : sourceFluid.copyWithAmount(1);
        return particleFluid.isEmpty()
            ? ParticleTypes.CLOUD
            : DynamicFluidVaporParticleOptions.atOutlet(particleFluid, escapingAmount);
    }

    private static FluidStack fallbackVaporFluid(ResourceLocation vaporType) {
        if (CondenserGas.GASEOUS_WATER.equals(vaporType)) return new FluidStack(Fluids.WATER, 1);
        if (CondenserGas.GASEOUS_OIL.equals(vaporType)) return new FluidStack(ModFluids.OIL.get(), 1);
        if (CondenserGas.GASEOUS_EXPERIENCE.equals(vaporType)) return new FluidStack(ModFluids.EXP_FLUID.get(), 1);
        return FluidStack.EMPTY;
    }

    private static ParticleOptions vaporParticle(
        @org.jetbrains.annotations.Nullable FluidStack sourceFluid,
        int vaporizationRate
    ) {
        if (isExperienceFluid(sourceFluid)) return ModParticles.EXPERIENCE_VAPOR.get();
        return sourceFluid == null || sourceFluid.isEmpty()
            ? ParticleTypes.CLOUD
            : new DynamicFluidVaporParticleOptions(sourceFluid, vaporizationRate);
    }

    private static boolean isExperienceFluid(@org.jetbrains.annotations.Nullable FluidStack fluid) {
        return fluid != null && !fluid.isEmpty() && fluid.is(ModFluids.EXP_FLUID.get());
    }

    private static double cellCoordinate(int cell, int gridSize, RandomSource random) {
        return ((cell + random.nextDouble()) / gridSize) * 2.0D - 1.0D;
    }

    private static void sendVaporParticle(
        ServerLevel level,
        ParticleOptions particle,
        RandomSource random,
        double x,
        double surfaceY,
        double z,
        int vaporizationRate
    ) {
        double intensity = Math.clamp(vaporizationRate / (double) ENHANCED_VAPORIZATION_PER_JET, 0.1D, 1.0D);
        level.sendParticles(
            particle,
            x,
            surfaceY + 0.015D + random.nextDouble() * 0.025D,
            z,
            0,
            (random.nextDouble() - 0.5D) * (0.010D + intensity * 0.008D),
            0.018D + random.nextDouble() * 0.018D
                + intensity * (0.014D + random.nextDouble() * 0.014D),
            (random.nextDouble() - 0.5D) * (0.010D + intensity * 0.008D),
            1.0D
        );
    }

}
