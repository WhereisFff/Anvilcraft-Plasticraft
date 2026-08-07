package dev.anvilcraft.plasticraft.entity.redstone;

import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.anvilcraft.plasticraft.molding.product.MoldedTrayComponent;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import dev.dubhe.anvilcraft.api.item.IDiskCloneable;
import dev.dubhe.anvilcraft.block.entity.AdvancedComparatorBlockEntity;
import dev.dubhe.anvilcraft.block.entity.ItemDetectorBlockEntity;
import dev.dubhe.anvilcraft.block.entity.PulseGeneratorBlockEntity;
import dev.dubhe.anvilcraft.init.ModMenuTypes;
import dev.dubhe.anvilcraft.init.item.ModItems;
import dev.dubhe.anvilcraft.inventory.AdvancedComparatorMenu;
import dev.dubhe.anvilcraft.inventory.ItemDetectorMenu;
import dev.dubhe.anvilcraft.inventory.PulseGeneratorMenu;
import dev.dubhe.anvilcraft.item.DiskItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.ticks.TickPriority;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.ToIntFunction;

/** 支架元件的加载、行为分派、端口查询与运行状态持久化。 */
public final class MoldedTrayRedstoneRuntime {
    private final UniversalPlasticEntity host;
    private MoldedTrayComponent loadedComponent;
    private BlockEntity blockEntity;
    private int remainingTicks;
    private List<Long> torchToggleTimes = List.of();
    private int detectorOutput;
    private boolean initialized;
    private boolean ticking;

    public MoldedTrayRedstoneRuntime(UniversalPlasticEntity host) {
        this.host = Objects.requireNonNull(host, "host");
    }

    public void tick() {
        if (this.host.level().isClientSide || !(this.host.level() instanceof ServerLevel)) return;
        MoldedTrayComponent component = this.currentComponent();
        if (component == null) {
            MoldedTrayRedstoneScheduler.cancel(this);
            this.removeNetwork();
            return;
        }
        this.ensureLoaded(component);
        MoldedTrayRedstoneScheduler.relocateIfNeeded(this);
        if (this.blockEntity != null) this.blockEntity.setBlockState(this.loadedComponent.state());
        this.ticking = true;
        boolean changed;
        try {
            BlockState state = this.state();
            MoldedTrayRedstoneBehavior behavior = MoldedTrayRedstoneBehaviors.find(state);
            changed = behavior.tick(this, state);
        } finally {
            this.ticking = false;
        }
        MoldedTrayComponent snapshot = this.snapshot();
        this.persistSnapshot(snapshot, changed);
        MoldedTrayRedstoneNetwork.update(this.host, snapshot);
    }

    public void flush() {
        MoldedTrayComponent component = this.currentComponent();
        if (component == null) {
            this.removeNetwork();
            return;
        }
        this.ensureLoaded(component);
        MoldedTrayComponent snapshot = this.snapshot();
        this.persistSnapshot(snapshot, true);
        MoldedTrayRedstoneNetwork.update(this.host, snapshot);
    }

    public void persist() {
        MoldedTrayComponent component = this.currentComponent();
        if (component == null) return;
        this.ensureLoaded(component);
        this.persistSnapshot(this.snapshot(), true);
    }

    /** 将尚未落盘的倒计时写入即将掉落的物品。 */
    public void applyTo(ItemStack stack) {
        MoldedPlasticData.get(stack)
            .filter(data -> MoldingProductTypes.isTray(data.finalType()))
            .ifPresent(data -> data.contents().trayComponent().ifPresent(stored -> {
                MoldedTrayComponent current = this.loadedComponent == null
                    || !sameConfiguration(this.loadedComponent, stored)
                    ? stored
                    : this.snapshot();
                MoldedPlasticData.set(
                    stack,
                    data.withContents(data.contents().withTrayComponent(Optional.of(current)))
                );
            }));
    }

    public BlockEntity blockEntity() {
        MoldedTrayComponent component = this.currentComponent();
        if (component == null) return null;
        this.ensureLoaded(component);
        return this.blockEntity;
    }

    public void remove() {
        MoldedTrayRedstoneScheduler.cancel(this);
        this.removeNetwork();
        this.blockEntity = null;
        this.loadedComponent = null;
        this.initialized = false;
    }

    public InteractionResult interact(Player player, InteractionHand hand) {
        MoldedTrayComponent component = this.currentComponent();
        if (component == null) return InteractionResult.PASS;
        this.ensureLoaded(component);
        ItemStack held = player.getItemInHand(hand);
        BlockState state = this.state();
        MoldedTrayRedstoneBehavior behavior = MoldedTrayRedstoneBehaviors.find(state);
        if (behavior.supportsStructureDisk() && held.is(ModItems.DISK.get())) {
            InteractionResult diskResult = this.useDisk(player, hand);
            if (diskResult != InteractionResult.PASS) return diskResult;
        }
        return behavior.interact(this, player, hand, state);
    }

    public boolean extract(Player player) {
        MoldedTrayComponent component = this.currentComponent();
        if (component == null) return false;
        this.ensureLoaded(component);
        ItemStack result = MoldedTrayComponentSupport.extractionStack(
            this.snapshot(),
            this.blockEntity,
            player.level()
        );
        player.getInventory().placeItemBackInInventory(result);
        this.host.plasticraft$clearTrayComponent();
        this.remove();
        return true;
    }

    public boolean isTicking() {
        return this.ticking;
    }

    InteractionResult openMenu(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || this.blockEntity == null) {
            return InteractionResult.sidedSuccess(player.level().isClientSide);
        }
        BlockPos menuPosition = this.host.plasticraft$getAnchorBlockPos();
        BlockEntity menuEntity = this.blockEntity;
        MenuProvider provider = new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return menuEntity.getBlockState().getBlock().getName();
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player menuPlayer) {
                if (menuEntity instanceof PulseGeneratorBlockEntity pulse) {
                    return new PulseGeneratorMenu(
                        ModMenuTypes.PULSE_GENERATOR.get(), id, inventory, pulse
                    ) {
                        @Override
                        public boolean stillValid(Player candidate) {
                            return MoldedTrayRedstoneRuntime.this.menuStillValid(candidate);
                        }

                        @Override
                        public void removed(Player candidate) {
                            MoldedTrayRedstoneRuntime.this.flush();
                            super.removed(candidate);
                        }
                    };
                }
                if (menuEntity instanceof ItemDetectorBlockEntity detector) {
                    return new ItemDetectorMenu(
                        ModMenuTypes.ITEM_DETECTOR.get(), id, inventory, detector
                    ) {
                        @Override
                        public boolean stillValid(Player candidate) {
                            return MoldedTrayRedstoneRuntime.this.menuStillValid(candidate);
                        }

                        @Override
                        public void removed(Player candidate) {
                            MoldedTrayRedstoneRuntime.this.flush();
                            super.removed(candidate);
                        }
                    };
                }
                if (menuEntity instanceof AdvancedComparatorBlockEntity comparator) {
                    return new AdvancedComparatorMenu(
                        ModMenuTypes.ADVANCED_COMPARATOR.get(), id, inventory, comparator
                    ) {
                        @Override
                        public boolean stillValid(Player candidate) {
                            return MoldedTrayRedstoneRuntime.this.menuStillValid(candidate);
                        }

                        @Override
                        public void removed(Player candidate) {
                            MoldedTrayRedstoneRuntime.this.flush();
                            super.removed(candidate);
                        }
                    };
                }
                return null;
            }
        };
        if (menuEntity instanceof PulseGeneratorBlockEntity pulse) {
            serverPlayer.openMenu(provider, buffer -> {
                buffer.writeBlockPos(menuPosition);
                buffer.writeNbt(pulse.constructDataNbt());
            });
        } else if (menuEntity instanceof AdvancedComparatorBlockEntity comparator) {
            serverPlayer.openMenu(provider, buffer -> {
                buffer.writeBlockPos(menuPosition);
                buffer.writeNbt(comparator.constructDataNbt());
            });
        } else {
            serverPlayer.openMenu(provider, buffer -> buffer.writeBlockPos(menuPosition));
        }
        return InteractionResult.CONSUME;
    }

    UniversalPlasticEntity host() {
        return this.host;
    }

    ServerLevel serverLevel() {
        return (ServerLevel) this.host.level();
    }

    MoldedTrayComponent loadedComponent() {
        return this.loadedComponent;
    }

    BlockEntity cachedBlockEntity() {
        return this.blockEntity;
    }

    BlockState state() {
        return this.blockEntity == null ? this.loadedComponent.state() : this.blockEntity.getBlockState();
    }

    void setState(BlockState state) {
        if (this.blockEntity != null) this.blockEntity.setBlockState(state);
        this.loadedComponent = this.loadedComponent.withState(state);
    }

    int remainingTicks() {
        return MoldedTrayRedstoneScheduler.remainingTicks(this, this.remainingTicks);
    }

    void remainingTicks(int ticks) {
        this.remainingTicks = ticks;
    }

    void scheduleTick(int delay, TickPriority priority) {
        this.remainingTicks = delay;
        MoldedTrayRedstoneScheduler.scheduleTick(this, delay, priority);
    }

    void scheduleBlockEvent(int eventId) {
        this.remainingTicks = 0;
        MoldedTrayRedstoneScheduler.scheduleBlockEvent(this, eventId);
    }

    void cancelScheduledAction() {
        this.remainingTicks = 0;
        MoldedTrayRedstoneScheduler.cancel(this);
    }

    List<Long> torchToggleTimes() {
        return this.torchToggleTimes;
    }

    void torchToggleTimes(List<Long> toggleTimes) {
        this.torchToggleTimes = List.copyOf(toggleTimes);
    }

    int detectorOutput() {
        return this.detectorOutput;
    }

    void detectorOutput(int output) {
        this.detectorOutput = Math.clamp(output, 0, 15);
    }

    int inputSignal(Direction localDirection) {
        Direction worldDirection = this.host.getOrientation().worldDirection(localDirection);
        return this.nearestSignal(localDirection, source -> {
            int signal = MoldedTrayRedstoneNetwork.excludingHost(
                this.host,
                () -> ((SignalGetter) this.host.level()).getSignal(source, worldDirection)
            );
            BlockState state = this.host.level().getBlockState(source);
            if (state.is(Blocks.REDSTONE_WIRE)) {
                signal = Math.max(signal, state.getValue(RedStoneWireBlock.POWER));
            }
            return Math.clamp(signal, 0, 15);
        });
    }

    int comparatorInput(Direction localDirection) {
        Direction worldDirection = this.host.getOrientation().worldDirection(localDirection);
        return this.nearestSignal(
            localDirection,
            source -> this.comparatorSignalAt(source, worldDirection)
        );
    }

    int sideSignal(Direction localDirection, boolean diodesOnly) {
        Direction worldDirection = this.host.getOrientation().worldDirection(localDirection);
        return this.nearestSignal(
            localDirection,
            source -> MoldedTrayRedstoneNetwork.excludingHost(
                this.host,
                () -> ((SignalGetter) this.host.level()).getControlInputSignal(
                    source,
                    worldDirection,
                    diodesOnly
                )
            )
        );
    }

    boolean shouldPrioritizeDiode(Direction inputDirection) {
        return MoldedTrayRedstoneNetwork.shouldPrioritizeDiode(
            this.host,
            inputDirection.getOpposite()
        );
    }

    void persistIfChanged() {
        this.persistSnapshot(this.snapshot(), true);
    }

    void persistAndPublish() {
        MoldedTrayComponent snapshot = this.snapshot();
        this.persistSnapshot(snapshot, true);
        MoldedTrayRedstoneNetwork.update(this.host, snapshot);
    }

    void publishNetwork() {
        MoldedTrayRedstoneNetwork.update(this.host, this.snapshot());
    }

    void runScheduledTick() {
        this.runScheduledAction(null);
    }

    void runBlockEvent(int eventId) {
        this.runScheduledAction(eventId);
    }

    private InteractionResult useDisk(Player player, InteractionHand hand) {
        if (!(this.blockEntity instanceof IDiskCloneable cloneable)) return InteractionResult.PASS;
        if (!player.getAbilities().mayBuild) return InteractionResult.PASS;
        if (player.isShiftKeyDown()) return InteractionResult.FAIL;
        if (player.level().isClientSide) return InteractionResult.SUCCESS;

        ItemStack disk = player.getItemInHand(hand);
        if (DiskItem.hasDataStored(disk)) {
            CompoundTag data = DiskItem.getData(disk);
            if (!isDiskCompatible(data, this.blockEntity, cloneable)) {
                player.displayClientMessage(
                    Component.translatable("message.anvilcraft.disk.data_incompatible")
                        .withStyle(ChatFormatting.RED),
                    true
                );
                return InteractionResult.FAIL;
            }
            cloneable.applyDiskData(data);
            player.displayClientMessage(
                Component.translatable("message.anvilcraft.disk.data_applied"),
                true
            );
        } else {
            CompoundTag data = DiskItem.createData(disk);
            data.putString(
                "StoredFrom",
                BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(this.blockEntity.getType()).toString()
            );
            CompoundTag groups = new CompoundTag();
            List<String> compatibleGroups = cloneable.getDiskCompatibleGroups();
            for (int index = 0; index < compatibleGroups.size(); index++) {
                groups.putString(Integer.toString(index), compatibleGroups.get(index));
            }
            data.put("CompatibleGroups", groups);
            cloneable.storeDiskData(data);
            player.displayClientMessage(
                Component.translatable("message.anvilcraft.disk.data_stored"),
                true
            );
        }
        this.persistIfChanged();
        return InteractionResult.SUCCESS;
    }

    private static boolean isDiskCompatible(
        CompoundTag data,
        BlockEntity blockEntity,
        IDiskCloneable cloneable
    ) {
        String targetType = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()).toString();
        if (targetType.equals(data.getString("StoredFrom"))) return true;
        if (!data.contains("CompatibleGroups", CompoundTag.TAG_COMPOUND)) return false;
        List<String> targetGroups = cloneable.getDiskCompatibleGroups();
        CompoundTag storedGroups = data.getCompound("CompatibleGroups");
        return storedGroups.getAllKeys().stream()
            .map(storedGroups::getString)
            .anyMatch(targetGroups::contains);
    }

    private boolean menuStillValid(Player player) {
        return !this.host.isRemoved()
            && player.level() == this.host.level()
            && player.distanceToSqr(this.host) <= 64.0D
            && this.currentComponent() != null;
    }

    private MoldedTrayComponent currentComponent() {
        return this.host.getMoldedData()
            .filter(data -> MoldingProductTypes.isTray(data.finalType()))
            .flatMap(data -> data.contents().trayComponent())
            .orElse(null);
    }

    private void ensureLoaded(MoldedTrayComponent component) {
        boolean configurationChanged = !this.initialized
            || this.loadedComponent == null
            || !sameConfiguration(this.loadedComponent, component);
        if (!configurationChanged) return;

        MoldedTrayRedstoneScheduler.cancel(this);
        this.loadedComponent = component;
        this.remainingTicks = component.scheduledTicks();
        this.torchToggleTimes = List.copyOf(component.torchToggleTimes());
        this.detectorOutput = 0;
        this.blockEntity = MoldedTrayComponentSupport.createBlockEntity(component, this.host);
        this.initialized = true;
        if (this.blockEntity != null) this.blockEntity.setBlockState(component.state());
        MoldedTrayRedstoneBehavior behavior = MoldedTrayRedstoneBehaviors.find(component.state());
        behavior.loadRuntime(this, component);
        if (!this.host.level().isClientSide) behavior.resumeScheduledAction(this, component);
    }

    private void runScheduledAction(Integer eventId) {
        if (!(this.host.level() instanceof ServerLevel) || this.host.isRemoved()) return;
        MoldedTrayComponent component = this.currentComponent();
        if (component == null) {
            this.remove();
            return;
        }
        boolean configurationChanged = !this.initialized
            || this.loadedComponent == null
            || !sameConfiguration(this.loadedComponent, component);
        this.ensureLoaded(component);
        if (configurationChanged) {
            MoldedTrayRedstoneNetwork.update(this.host, this.snapshot());
            return;
        }

        this.remainingTicks = 0;
        if (this.blockEntity != null) this.blockEntity.setBlockState(this.loadedComponent.state());
        this.ticking = true;
        try {
            BlockState state = this.state();
            MoldedTrayRedstoneBehavior behavior = MoldedTrayRedstoneBehaviors.find(state);
            if (eventId == null) behavior.scheduledTick(this, state);
            else behavior.blockEvent(this, state, eventId);
        } finally {
            this.ticking = false;
        }
        MoldedTrayComponent snapshot = this.snapshot();
        this.persistSnapshot(snapshot, true);
        MoldedTrayRedstoneNetwork.update(this.host, snapshot);
    }

    private static boolean sameConfiguration(MoldedTrayComponent first, MoldedTrayComponent second) {
        return first.hasSameConfiguration(second);
    }

    private int nearestSignal(
        Direction localDirection,
        ToIntFunction<BlockPos> signalAt
    ) {
        for (List<BlockPos> layer : MoldedTrayRedstoneNetwork.inputCandidateLayers(
            this.host,
            localDirection
        )) {
            int signal = 0;
            for (BlockPos position : layer) {
                signal = Math.max(signal, Math.clamp(signalAt.applyAsInt(position), 0, 15));
            }
            if (signal > 0) return signal;
        }
        return 0;
    }

    private int comparatorSignalAt(BlockPos source, Direction worldDirection) {
        int signal = MoldedTrayRedstoneNetwork.excludingHost(
            this.host,
            () -> ((SignalGetter) this.host.level()).getSignal(source, worldDirection)
        );
        BlockState sourceState = this.host.level().getBlockState(source);
        if (sourceState.is(Blocks.REDSTONE_WIRE)) {
            signal = Math.max(signal, sourceState.getValue(RedStoneWireBlock.POWER));
        }
        if (sourceState.hasAnalogOutputSignal()) {
            return Math.clamp(sourceState.getAnalogOutputSignal(this.host.level(), source), 0, 15);
        }
        if (signal >= 15 || !sourceState.isRedstoneConductor(this.host.level(), source)) {
            return Math.clamp(signal, 0, 15);
        }

        BlockPos secondary = source.relative(worldDirection);
        BlockState secondaryState = this.host.level().getBlockState(secondary);
        ItemFrame frame = this.singleItemFrame(secondary, worldDirection);
        int frameOutput = frame == null ? Integer.MIN_VALUE : frame.getAnalogOutput();
        int blockOutput = secondaryState.hasAnalogOutputSignal()
            ? secondaryState.getAnalogOutputSignal(this.host.level(), secondary)
            : Integer.MIN_VALUE;
        int analogOutput = Math.max(frameOutput, blockOutput);
        return analogOutput == Integer.MIN_VALUE ? signal : Math.clamp(analogOutput, 0, 15);
    }

    private ItemFrame singleItemFrame(BlockPos position, Direction direction) {
        List<ItemFrame> frames = this.host.level().getEntitiesOfClass(
            ItemFrame.class,
            new AABB(position),
            frame -> frame.getDirection() == direction
        );
        return frames.size() == 1 ? frames.getFirst() : null;
    }

    private MoldedTrayComponent snapshot() {
        if (this.loadedComponent == null) return this.currentComponent();
        BlockState state = this.state();
        CompoundTag data = this.loadedComponent.blockEntityData().copy();
        if (this.blockEntity != null) {
            data = this.blockEntity.saveWithoutMetadata(this.host.registryAccess());
        }
        MoldedTrayRedstoneBehaviors.find(state).writeSnapshotData(this, data);
        return this.loadedComponent.replace(state, data, this.remainingTicks(), this.torchToggleTimes);
    }

    private void persistSnapshot(MoldedTrayComponent next, boolean includeRuntimeState) {
        MoldedTrayComponent current = this.currentComponent();
        boolean unchanged = current != null && (includeRuntimeState
            ? current.equals(next)
            : sameConfiguration(current, next));
        this.loadedComponent = next;
        if (!unchanged) this.host.plasticraft$replaceTrayComponent(next);
    }

    private void removeNetwork() {
        MoldedTrayRedstoneNetwork.remove(this.host);
    }
}
