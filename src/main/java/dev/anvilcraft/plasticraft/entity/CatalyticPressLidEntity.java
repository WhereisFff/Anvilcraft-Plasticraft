package dev.anvilcraft.plasticraft.entity;

import dev.anvilcraft.plasticraft.init.block.ModBlocks;
import dev.anvilcraft.plasticraft.recipe.CatalyticPressProcess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.function.Supplier;

/** 保存催化进度、抬升状态和压制动画的持久化压盖实体。 */
public final class CatalyticPressLidEntity extends AbstractPlasticEntity {
    public static final float WIDTH = 1.0F;
    public static final float HEIGHT = 1.0F;
    public static final int PRESS_ANIMATION_TICKS = 4;
    public static final double RENDER_BOUNDS_EXPANSION = 2.25D;

    private static final EntityDataAccessor<Integer> PROGRESS = SynchedEntityData.defineId(
        CatalyticPressLidEntity.class,
        EntityDataSerializers.INT
    );
    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(
        CatalyticPressLidEntity.class,
        EntityDataSerializers.INT
    );
    private static final EntityDataAccessor<Boolean> READY = SynchedEntityData.defineId(
        CatalyticPressLidEntity.class,
        EntityDataSerializers.BOOLEAN
    );
    private static final EntityDataAccessor<Long> PRESS_STARTED = SynchedEntityData.defineId(
        CatalyticPressLidEntity.class,
        EntityDataSerializers.LONG
    );
    private static Supplier<ItemStack> defaultDropSupplier = () -> ItemStack.EMPTY;

    private String activeRecipe = "";
    private boolean pressConfirmed;
    private boolean bondedDataDirty;

    public static void configureDefaultDrop(Supplier<ItemStack> supplier) {
        defaultDropSupplier = Objects.requireNonNull(supplier, "supplier");
    }

    public CatalyticPressLidEntity(EntityType<? extends CatalyticPressLidEntity> type, Level level) {
        super(type, level);
        this.setDisplayState(ModBlocks.CATALYTIC_PRESS_LID.get().defaultBlockState());
    }

    public CatalyticPressLidEntity(
        EntityType<? extends CatalyticPressLidEntity> type,
        Level level,
        Vec3 position,
        BlockState displayState,
        ItemStack dropStack,
        PlasticEntityOrientation orientation
    ) {
        super(type, level, position, displayState, dropStack, orientation);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PROGRESS, 0)
            .define(TARGET, 800)
            .define(READY, false)
            .define(PRESS_STARTED, -1L);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(WIDTH, HEIGHT);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(RENDER_BOUNDS_EXPANSION);
    }

    @Override
    protected ItemStack createDefaultDropStack() {
        ItemStack stack = defaultDropSupplier.get();
        return stack == null ? ItemStack.EMPTY : stack.copy();
    }

    @Override
    protected void openAnvilMenu(ServerPlayer player) {
    }

    @Override
    protected String materialKey() {
        return "hardened_resin";
    }

    @Override
    protected boolean triggersAnvilCraftLandingEvents(net.minecraft.core.Direction impactDirection) {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.isRemoved() && this.level() instanceof ServerLevel) this.tickCatalyticProcess();
    }

    public void plasticraft$tickBonded() {
        if (this.level() instanceof ServerLevel) this.tickCatalyticProcess();
    }

    private void tickCatalyticProcess() {
        if (this.pressConfirmed && this.pressAnimationProgress(0.0F) >= 1.0F) {
            this.pressConfirmed = false;
            CatalyticPressProcess.press(this);
            return;
        }
        CatalyticPressProcess.tick(this);
    }

    public void setCatalyticProgress(String recipeId, int progress, int target) {
        this.activeRecipe = recipeId == null ? "" : recipeId;
        this.entityData.set(PROGRESS, Math.max(0, progress));
        this.entityData.set(TARGET, Math.max(1, target));
        this.markFunctionalDirty();
    }

    public String activeRecipe() {
        return this.activeRecipe;
    }

    public int catalyticProgress() {
        return this.entityData.get(PROGRESS);
    }

    public int catalyticTarget() {
        return this.entityData.get(TARGET);
    }

    public boolean isReady() {
        return this.entityData.get(READY);
    }

    public void completeCatalysis() {
        if (this.isReady()) return;
        this.entityData.set(READY, true);
        this.entityData.set(PROGRESS, this.entityData.get(TARGET));
        this.entityData.set(PRESS_STARTED, -1L);
        this.markFunctionalDirty();
        this.level().playSound(
            null,
            this.blockPosition(),
            SoundEvents.FIRE_EXTINGUISH,
            SoundSource.BLOCKS,
            0.9F,
            0.75F + this.random.nextFloat() * 0.1F
        );
    }

    public void resetCatalysis() {
        this.activeRecipe = "";
        this.entityData.set(PROGRESS, 0);
        this.entityData.set(TARGET, 800);
        this.entityData.set(READY, false);
        this.entityData.set(PRESS_STARTED, -1L);
        this.pressConfirmed = false;
        this.markFunctionalDirty();
    }

    public void notifyIncomingAnvil() {
        if (!this.isReady() || this.entityData.get(PRESS_STARTED) >= 0L) return;
        this.entityData.set(PRESS_STARTED, this.level().getGameTime());
        this.markFunctionalDirty();
    }

    public void confirmAnvilPress() {
        if (!this.isReady()) return;
        this.notifyIncomingAnvil();
        this.pressConfirmed = true;
        this.markFunctionalDirty();
    }

    public float armLift(float partialTick) {
        long pressStarted = this.entityData.get(PRESS_STARTED);
        if (pressStarted >= 0L) return 1.0F - this.pressAnimationProgress(partialTick);
        if (this.isReady()) return 1.0F;
        return Math.clamp((this.entityData.get(PROGRESS) + partialTick) / this.entityData.get(TARGET), 0.0F, 1.0F);
    }

    public float pressAnimationProgress(float partialTick) {
        long pressStarted = this.entityData.get(PRESS_STARTED);
        if (pressStarted < 0L) return 0.0F;
        return Math.clamp(
            (this.level().getGameTime() + partialTick - pressStarted) / PRESS_ANIMATION_TICKS,
            0.0F,
            1.0F
        );
    }

    public boolean plasticraft$consumeBondedDataDirty() {
        boolean dirty = this.bondedDataDirty;
        this.bondedDataDirty = false;
        return dirty;
    }

    private void markFunctionalDirty() {
        this.hasImpulse = true;
        this.hurtMarked = true;
        this.bondedDataDirty = true;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("CatalyticRecipe", this.activeRecipe);
        tag.putInt("CatalyticProgress", this.entityData.get(PROGRESS));
        tag.putInt("CatalyticTarget", this.entityData.get(TARGET));
        tag.putBoolean("CatalyticReady", this.entityData.get(READY));
        tag.putLong("PressStarted", this.entityData.get(PRESS_STARTED));
        tag.putBoolean("PressConfirmed", this.pressConfirmed);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.activeRecipe = tag.getString("CatalyticRecipe");
        this.entityData.set(PROGRESS, Math.max(0, tag.getInt("CatalyticProgress")));
        this.entityData.set(TARGET, Math.max(1, tag.contains("CatalyticTarget") ? tag.getInt("CatalyticTarget") : 800));
        this.entityData.set(READY, tag.getBoolean("CatalyticReady"));
        this.entityData.set(PRESS_STARTED, tag.contains("PressStarted") ? tag.getLong("PressStarted") : -1L);
        this.pressConfirmed = tag.getBoolean("PressConfirmed");
    }
}
