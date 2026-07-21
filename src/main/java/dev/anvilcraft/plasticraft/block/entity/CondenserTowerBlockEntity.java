package dev.anvilcraft.plasticraft.block.entity;

import dev.anvilcraft.plasticraft.block.CondenserTowerBlock;
import dev.anvilcraft.plasticraft.recipe.CondenserGas;
import dev.dubhe.anvilcraft.api.fluid.IFluidHandlerHolder;
import dev.dubhe.anvilcraft.api.fluid.network.FluidNetworkManager;
import dev.dubhe.anvilcraft.block.state.Cube3x3PartHalf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** 冷凝塔单模块的 64 B 流体缓存；顶层接口只允许向外排液。 */
public class CondenserTowerBlockEntity extends BlockEntity implements IFluidHandlerHolder {
    public static final int CAPACITY = 64 * FluidType.BUCKET_VOLUME;
    private static final String TAG_TANK = "Tank";
    private static final String TAG_GAS_ID = "GasId";
    private static final String TAG_GAS_AMOUNT = "GasAmount";

    private final FluidTank tank = new FluidTank(CAPACITY) {
        @Override
        protected void onContentsChanged() {
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
    private final IFluidHandler outputHandler = new OutputOnlyHandler(this.tank);
    private final List<BlockPos> registeredInterfaces = new ArrayList<>();
    private ResourceLocation gasId;
    private int gasAmount;

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
    public IFluidHandler getFluidHandler() {
        CondenserTowerBlockEntity main = this.getMainPart();
        return main.tank;
    }

    public IFluidHandler getOutputHandler() {
        return this.getMainPart().outputHandler;
    }

    /** 给后续冷凝产物处理调用；接口能力本身不会暴露输入。 */
    public int collect(FluidStack stack) {
        return this.getMainPart().tank.fill(stack, IFluidHandler.FluidAction.EXECUTE);
    }

    /** 将虚拟气体存入塔内，返回实际接收量。 */
    public int collectGas(ResourceLocation id, int amount) {
        CondenserTowerBlockEntity main = this.getMainPart();
        if (!CondenserGas.isGas(id) || amount <= 0) return 0;
        if (main.gasId != null && !main.gasId.equals(id)) return 0;
        int accepted = Math.min(amount, CAPACITY - main.gasAmount);
        if (accepted <= 0) return 0;
        main.gasId = id;
        main.gasAmount += accepted;
        main.setChanged();
        main.sendTankUpdate();
        return accepted;
    }

    public ResourceLocation getGasId() {
        return this.getMainPart().gasId;
    }

    public int getGasAmount() {
        return this.getMainPart().gasAmount;
    }

    public int drainGas(ResourceLocation id, int amount) {
        CondenserTowerBlockEntity main = this.getMainPart();
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
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        if (this.isMainPart()) {
            tag.put(TAG_TANK, this.tank.writeToNBT(provider, new CompoundTag()));
            if (this.gasId != null && this.gasAmount > 0) {
                tag.putString(TAG_GAS_ID, this.gasId.toString());
                tag.putInt(TAG_GAS_AMOUNT, this.gasAmount);
            }
        }
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (this.isMainPart()) {
            this.tank.readFromNBT(provider, tag.getCompound(TAG_TANK));
            ResourceLocation parsed = ResourceLocation.tryParse(tag.getString(TAG_GAS_ID));
            this.gasId = CondenserGas.isGas(parsed) ? parsed : null;
            this.gasAmount = this.gasId == null ? 0 : Math.clamp(tag.getInt(TAG_GAS_AMOUNT), 0, CAPACITY);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        if (this.isMainPart()) {
            tag.put(TAG_TANK, this.tank.writeToNBT(provider, new CompoundTag()));
            if (this.gasId != null && this.gasAmount > 0) {
                tag.putString(TAG_GAS_ID, this.gasId.toString());
                tag.putInt(TAG_GAS_AMOUNT, this.gasAmount);
            }
        }
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** 从任意塔部件返回对应的外向端口能力。 */
    public static @Nullable IFluidHandler capability(
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

    private static final class OutputOnlyHandler implements IFluidHandler {
        private final IFluidHandler delegate;

        private OutputOnlyHandler(IFluidHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public int getTanks() {
            return this.delegate.getTanks();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return this.delegate.getFluidInTank(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            return this.delegate.getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return false;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return this.delegate.drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return this.delegate.drain(maxDrain, action);
        }
    }

    private void sendTankUpdate() {
        if (this.level == null) return;
        this.level.sendBlockUpdated(
            this.worldPosition,
            this.getBlockState(),
            this.getBlockState(),
            Block.UPDATE_ALL
        );
    }
}
