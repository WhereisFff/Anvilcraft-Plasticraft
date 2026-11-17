package dev.anvilcraft.plasticraft.block.entity;

import dev.anvilcraft.plasticraft.block.CondenserTowerBlock;
import dev.anvilcraft.plasticraft.recipe.CondenserGas;
import dev.anvilcraft.plasticraft.recipe.CondenserTowerProcess;
import dev.anvilcraft.plasticraft.vapor.IVaporConsumer;
import dev.anvilcraft.plasticraft.vapor.VaporAction;
import dev.anvilcraft.plasticraft.vapor.VaporStack;
import dev.anvilcraft.plasticraft.vapor.VaporizationContext;
import dev.dubhe.anvilcraft.api.fluid.IFluidResourceHandlerHolder;
import dev.dubhe.anvilcraft.api.fluid.network.FluidNetworkManager;
import dev.dubhe.anvilcraft.api.injection.tooltip.ITooltipProviderExtension;
import dev.dubhe.anvilcraft.block.state.Cube3x3PartHalf;
import dev.dubhe.anvilcraft.util.UnitUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.transfer.DelegatingResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** 冷凝塔单模块的 64 B 流体缓存；顶层接口只允许向外排液。 */
public class CondenserTowerBlockEntity extends BlockEntity implements IFluidResourceHandlerHolder, ITooltipProviderExtension {
    public static final int CAPACITY = 64 * FluidType.BUCKET_VOLUME;
    private static final String TAG_TANK = "Tank";
    private static final String TAG_GAS_ID = "GasId";
    private static final String TAG_GAS_AMOUNT = "GasAmount";

    private final PlasticFluidTank tank = new PlasticFluidTank(CAPACITY) {
        @Override
        protected void onContentsChanged(int index, FluidStack previous) {
            CondenserTowerBlockEntity.this.setChanged();
            if (CondenserTowerBlockEntity.this.level != null) {
                CondenserTowerBlockEntity.this.level.sendBlockUpdated(
                    CondenserTowerBlockEntity.this.worldPosition,
                    CondenserTowerBlockEntity.this.getBlockState(),
                    CondenserTowerBlockEntity.this.getBlockState(),
                    Block.UPDATE_ALL
                );
            }
        }
    };
    private final ResourceHandler<FluidResource> outputHandler = new DelegatingResourceHandler<>(this.tank) {
        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public int insert(FluidResource resource, int amount, TransactionContext transaction) {
            return 0;
        }
    };
    private final IVaporConsumer vaporConsumer = new IVaporConsumer() {
        @Override
        public int receiveVapor(VaporStack vapor, VaporAction action, VaporizationContext context) {
            return CondenserTowerProcess.receiveVapor(
                CondenserTowerBlockEntity.this,
                vapor,
                action,
                context
            );
        }

        @Override
        public boolean sealsOutlet(VaporizationContext context) {
            return CondenserTowerProcess.isTowerStackSealed(context);
        }
    };
    private final List<BlockPos> registeredInterfaces = new ArrayList<>();
    private Identifier gasId;
    private int gasAmount;
    private long vaporRateTick = Long.MIN_VALUE;
    private int vaporAcceptedThisTick;

    public CondenserTowerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static @Nullable CondenserTowerBlockEntity getMain(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof CondenserTowerBlock block)) return null;
        BlockEntity entity = level.getBlockEntity(block.getMainPartPos(pos, state));
        return entity instanceof CondenserTowerBlockEntity tower ? tower : null;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level == null || this.level.isClientSide() || !this.isMainPart()) return;
        this.registeredInterfaces.clear();
        CondenserTowerBlock block = (CondenserTowerBlock) this.getBlockState().getBlock();
        BlockPos base = this.worldPosition.below();
        for (Cube3x3PartHalf part : Cube3x3PartHalf.values()) {
            if (CondenserTowerBlock.outputDirection(block.placedState(part, this.getBlockState())) == null) continue;
            BlockPos interfacePos = base.offset(part.getOffset()).immutable();
            this.registeredInterfaces.add(interfacePos);
            FluidNetworkManager.INSTANCE.addContainer(this.level, interfacePos);
        }
    }

    @Override
    public void setRemoved() {
        if (this.level != null && !this.level.isClientSide()) {
            for (BlockPos pos : this.registeredInterfaces) {
                FluidNetworkManager.INSTANCE.removeContainer(this.level, pos);
            }
            this.registeredInterfaces.clear();
        }
        super.setRemoved();
    }

    public boolean isMainPart() {
        return this.getBlockState().getBlock() instanceof CondenserTowerBlock block
            && block.isMainPart(this.getBlockState());
    }

    @Override
    public ResourceHandler<FluidResource> getFluidHandler() {
        CondenserTowerBlockEntity main = this.getMainPart();
        return main.tank;
    }

    public ResourceHandler<FluidResource> getOutputHandler() {
        return this.getMainPart().outputHandler;
    }

    /** 给后续冷凝产物处理调用；接口能力本身不会暴露输入。 */
    public int collect(FluidStack stack) {
        return this.getMainPart().tank.fill(stack, IFluidHandler.FluidAction.EXECUTE);
    }

    /** 将虚拟气体存入塔内，返回实际接收量。 */
    public int collectGas(Identifier id, int amount) {
        return this.getMainPart().acceptGas(id, amount, VaporAction.EXECUTE);
    }

    /** 按单层每刻吞吐上限接收蒸汽，模拟调用不会推进计数器。 */
    public int receiveGas(
        Identifier id,
        int amount,
        int rateLimit,
        long gameTime,
        VaporAction action
    ) {
        CondenserTowerBlockEntity main = this.getMainPart();
        int used = main.vaporRateTick == gameTime ? main.vaporAcceptedThisTick : 0;
        int remainingRate = Math.max(0, rateLimit - used);
        int accepted = Math.min(amount, remainingRate);
        accepted = Math.min(accepted, CAPACITY - main.gasAmount);
        id = CondenserGas.canonicalize(id);
        if (id == null || accepted <= 0 || main.gasId != null && !main.gasId.equals(id)) return 0;
        if (action == VaporAction.SIMULATE) return accepted;
        if (main.vaporRateTick != gameTime) {
            main.vaporRateTick = gameTime;
            main.vaporAcceptedThisTick = 0;
        }
        main.vaporAcceptedThisTick += accepted;
        return main.acceptGas(id, accepted, VaporAction.EXECUTE);
    }

    private int acceptGas(Identifier id, int amount, VaporAction action) {
        CondenserTowerBlockEntity main = this.getMainPart();
        id = CondenserGas.canonicalize(id);
        if (id == null || amount <= 0) return 0;
        if (main.gasId != null && !main.gasId.equals(id)) return 0;
        int accepted = Math.min(amount, CAPACITY - main.gasAmount);
        if (accepted <= 0 || action == VaporAction.SIMULATE) return accepted;
        main.gasId = id;
        main.gasAmount += accepted;
        main.setChanged();
        main.sendTankUpdate();
        return accepted;
    }

    public Identifier getGasId() {
        return this.getMainPart().gasId;
    }

    public int getGasAmount() {
        return this.getMainPart().gasAmount;
    }

    public int drainGas(Identifier id, int amount) {
        CondenserTowerBlockEntity main = this.getMainPart();
        id = CondenserGas.canonicalize(id);
        if (amount <= 0 || main.gasId == null || !main.gasId.equals(id)) return 0;
        int drained = Math.min(amount, main.gasAmount);
        main.gasAmount -= drained;
        if (main.gasAmount == 0) main.gasId = null;
        if (drained > 0) {
            main.setChanged();
            main.sendTankUpdate();
        }
        return drained;
    }

    public FluidStack getStoredFluid() {
        return this.getMainPart().tank.getFluid().copy();
    }

    public boolean isStorageFull() {
        CondenserTowerBlockEntity main = this.getMainPart();
        return main.gasAmount >= CAPACITY && main.tank.getFluidAmount() >= CAPACITY;
    }

    @Override
    public List<Component> anvilcraft$getTooltip() {
        CondenserTowerBlockEntity main = this.getMainPart();
        List<Component> lines = new ArrayList<>();
        FluidStack fluid = main.tank.getFluid();
        boolean hasGas = main.gasId != null && main.gasAmount > 0;
        if (!fluid.isEmpty() || hasGas) {
            lines.add(Component.translatable("tooltip.anvilcraft.fluid_tank.fluid").withStyle(ChatFormatting.BLUE));
            if (!fluid.isEmpty()) {
                lines.add(Component.literal("  ")
                    .append(fluid.getHoverName())
                    .append(Component.literal(" " + UnitUtil.fluidUnit(fluid.getAmount(), false)))
                    .withStyle(ChatFormatting.GRAY));
            }
            if (hasGas) {
                lines.add(Component.literal("  ")
                    .append(Component.translatable("jei.anvilcraftplasticraft.gas." + main.gasId.getPath()))
                    .append(Component.literal(" " + UnitUtil.fluidUnit(main.gasAmount, false)))
                    .withStyle(ChatFormatting.GRAY));
            }
        }
        lines.add(Component.translatable("tooltip.anvilcraft.fluid_tank.capacity").withStyle(ChatFormatting.BLUE));
        int displayedAmount = fluid.isEmpty() && hasGas ? main.gasAmount : fluid.getAmount();
        lines.add(Component.translatable(
            "tooltip.anvilcraft.fluid_tank.capacity.value",
            UnitUtil.fluidUnit(displayedAmount, false),
            UnitUtil.fluidUnit(CAPACITY, false)
        ).withStyle(ChatFormatting.GRAY));
        if (!fluid.isEmpty() && hasGas) {
            lines.add(Component.translatable("jei.anvilcraftplasticraft.gas." + main.gasId.getPath())
                .append(Component.literal(
                    " " + UnitUtil.fluidUnit(main.gasAmount, false)
                        + " / " + UnitUtil.fluidUnit(CAPACITY, false)
                ))
                .withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    public void dropContents() {
        CondenserTowerBlockEntity main = this.getMainPart();
        if (main.level == null) return;
        // 暂无专用冷凝产物物品；破坏时只清空缓存，避免生成错误的原油桶。
        main.tank.setFluid(FluidStack.EMPTY);
        main.gasId = null;
        main.gasAmount = 0;
    }

    private CondenserTowerBlockEntity getMainPart() {
        if (this.level == null || !(this.getBlockState().getBlock() instanceof CondenserTowerBlock block)) return this;
        BlockEntity entity = this.level.getBlockEntity(block.getMainPartPos(this.worldPosition, this.getBlockState()));
        return entity instanceof CondenserTowerBlockEntity tower ? tower : this;
    }

    @Override
    protected void saveAdditional(ValueOutput tag) {
        super.saveAdditional(tag);
        if (this.isMainPart()) {
            tag.putChild(TAG_TANK, this.tank);
            if (this.gasId != null && this.gasAmount > 0) {
                tag.putString(TAG_GAS_ID, this.gasId.toString());
                tag.putInt(TAG_GAS_AMOUNT, this.gasAmount);
            }
        }
    }

    @Override
    public void loadAdditional(ValueInput tag) {
        super.loadAdditional(tag);
        if (this.isMainPart()) {
            tag.readChild(TAG_TANK, this.tank);
            Identifier parsed = CondenserGas.canonicalize(
                Identifier.tryParse(tag.getStringOr(TAG_GAS_ID, ""))
            );
            int amount = parsed == null ? 0 : Math.clamp(tag.getIntOr(TAG_GAS_AMOUNT, 0), 0, CAPACITY);
            this.gasId = amount > 0 ? parsed : null;
            this.gasAmount = amount;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        return this.saveWithoutMetadata(provider);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** 从任意塔部件返回对应的外向端口能力。 */
    public static @Nullable ResourceHandler<FluidResource> capability(
        Level level,
        BlockPos pos,
        BlockState state,
        @Nullable BlockEntity ignored,
        @Nullable Direction side
    ) {
        Direction output = CondenserTowerBlock.outputDirection(state);
        if (output == null || side != null && side != output) return null;
        CondenserTowerBlockEntity main = getMain(level, pos, state);
        return main == null ? null : main.outputHandler;
    }

    /** Exposes vapor input only on the bottom-center face connected to a large cauldron. */
    public static @Nullable IVaporConsumer vaporCapability(
        Level level,
        BlockPos pos,
        BlockState state,
        @Nullable BlockEntity ignored,
        @Nullable Direction side
    ) {
        if (!(state.getBlock() instanceof CondenserTowerBlock)
            || !state.hasProperty(CondenserTowerBlock.HALF)
            || state.getValue(CondenserTowerBlock.HALF) != Cube3x3PartHalf.BOTTOM_CENTER
            || side != null && side != Direction.DOWN) {
            return null;
        }
        CondenserTowerBlockEntity main = getMain(level, pos, state);
        return main == null ? null : main.vaporConsumer;
    }

    private void sendTankUpdate() {
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level instanceof ServerLevel serverLevel) {
            serverLevel.invalidateCapabilities(pos);
            FluidNetworkManager.INSTANCE.markDirty(serverLevel);
            if (this.isMainPart()) {
                this.dropContents();
            }
        }
    }
}
