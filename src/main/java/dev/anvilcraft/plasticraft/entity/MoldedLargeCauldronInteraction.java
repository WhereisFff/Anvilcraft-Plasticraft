package dev.anvilcraft.plasticraft.entity;

import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.anvilcraft.plasticraft.molding.product.PlasticCauldronLayout;
import dev.dubhe.anvilcraft.api.fluid.FluidHandlerWrapper;
import dev.dubhe.anvilcraft.api.fluid.LargeCauldronFluidHandler;
import dev.dubhe.anvilcraft.api.itemhandler.ItemHandlerUtil;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 把本体的三乘三部件交互映射到任意尺寸、朝向的成型大锅，槽位始终采用模型局部坐标。 */
public final class MoldedLargeCauldronInteraction {
    private static final int[][] INPUT_OFFSETS = {
        {-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {-1, 1}, {1, -1}, {1, 1}
    };
    private static final double EPSILON = 1.0E-4;

    private MoldedLargeCauldronInteraction() {
    }

    public static boolean applies(UniversalPlasticEntity pot) {
        return pot.isMoldedCauldron() && pot.plasticraft$cauldronLayout() == PlasticCauldronLayout.LARGE;
    }

    public static AABB cavity(UniversalPlasticEntity pot) {
        return pot.getMoldedData().flatMap(MoldedPlasticData::cavityBounds)
            .orElse(pot.plasticraft$getGeometry().localBounds());
    }

    public static Vec3 localPoint(UniversalPlasticEntity pot, Vec3 world) {
        return pot.plasticraft$getGeometry().localPointAt(pot.position(), pot.getOrientation(), world);
    }

    public static int inputSlot(UniversalPlasticEntity pot, Vec3 world) {
        Vec3 point = localPoint(pot, world);
        AABB bounds = pot.plasticraft$getGeometry().localBounds();
        int x = part(point.x, bounds.minX, bounds.maxX);
        int z = part(point.z, bounds.minZ, bounds.maxZ);
        for (int slot = 0; slot < INPUT_OFFSETS.length; slot++) {
            if (INPUT_OFFSETS[slot][0] == x && INPUT_OFFSETS[slot][1] == z) return slot;
        }
        return -1;
    }

    public static Vec3 inputPosition(UniversalPlasticEntity pot, int slot) {
        AABB bounds = pot.plasticraft$getGeometry().localBounds();
        int x = slot < 0 ? 0 : INPUT_OFFSETS[slot][0];
        int z = slot < 0 ? 0 : INPUT_OFFSETS[slot][1];
        return pot.plasticraft$getGeometry().worldPointAt(pot.position(), pot.getOrientation(), new Vec3(
            bounds.getCenter().x + x * bounds.getXsize() / 3.0,
            cavity(pot).minY,
            bounds.getCenter().z + z * bounds.getZsize() / 3.0
        ));
    }

    private static int part(double value, double min, double max) {
        return Math.clamp((int) Math.floor((value - min) * 3.0 / (max - min)), 0, 2) - 1;
    }

    public static InteractionResult interact(
        UniversalPlasticEntity pot, Player player, InteractionHand hand, @Nullable Vec3 relativeHit
    ) {
        Vec3 start = localPoint(pot, player.getEyePosition());
        Vec3 worldEnd = relativeHit == null
            ? player.getEyePosition().add(player.getLookAngle().scale(player.blockInteractionRange()))
            : pot.position().add(relativeHit);
        if (!Double.isFinite(worldEnd.x) || !Double.isFinite(worldEnd.y) || !Double.isFinite(worldEnd.z)) {
            return InteractionResult.PASS;
        }
        Vec3 end = localPoint(pot, worldEnd);
        BlockHitResult ray = pot.plasticraft$getGeometry().interactionShape().clip(
            start, end.add(end.subtract(start).normalize().scale(EPSILON)), BlockPos.ZERO
        );
        if (ray == null) return InteractionResult.PASS;
        Vec3 point = ray.getLocation();
        Direction face = ray.getDirection();
        ItemStack held = player.getItemInHand(hand);
        AABB cavity = cavity(pot);
        double fraction = Math.clamp((point.y - cavity.minY) / cavity.getYsize(), 0.0, 1.0);
        IFluidHandler fluids = pot.getMoldedFluidHandler().sideAccess(
            Math.max(1, (int) Math.ceil(fraction * LargeCauldronFluidHandler.TOTAL_CAPACITY))
        );
        if (FluidHandlerWrapper.tryInteractWithBottle(player, hand, fluids, pot.level(), pot.blockPosition())
            || FluidUtil.interactWithFluidHandler(player, hand, fluids)
            || FluidHandlerWrapper.isFluidInteractionItem(held)) {
            return InteractionResult.sidedSuccess(pot.level().isClientSide);
        }
        Vec3 worldHit = pot.plasticraft$getGeometry().worldPointAt(pot.position(), pot.getOrientation(), point);
        int slot = inputSlot(pot, worldHit);
        boolean forced = pot.getMoldedData().map(MoldedPlasticData::limitOverride).orElse(false);
        boolean floor = face == Direction.UP && (forced || Math.abs(point.y - cavity.minY) <= EPSILON);
        if (floor && hand == InteractionHand.MAIN_HAND) {
            if (held.isEmpty()) return extract(pot, player, hand, slot);
            if (!pot.level().isClientSide) insert(pot, held, slot);
            return InteractionResult.sidedSuccess(pot.level().isClientSide);
        }
        if (held.is(ModBlocks.MENGER_SPONGE.asItem())) {
            if (!pot.level().isClientSide && !pot.getMoldedFluidHandler().getBottomFluid().isEmpty()) {
                pot.getMoldedFluidHandler().discardFluids();
                pot.anvilcraft$setIgnited(false);
                pot.level().playSound(null, pot.blockPosition(), SoundEvents.SPONGE_ABSORB, SoundSource.BLOCKS, 1, 1);
            }
            return InteractionResult.sidedSuccess(pot.level().isClientSide);
        }
        AABB bounds = pot.plasticraft$getGeometry().localBounds();
        boolean rim = part(point.y, bounds.minY, bounds.maxY) == 1 && face != Direction.DOWN;
        if (hand != InteractionHand.MAIN_HAND || held.isEmpty() || !rim) return InteractionResult.PASS;
        return pot.level().isClientSide || insert(pot, held, slot)
            ? InteractionResult.sidedSuccess(pot.level().isClientSide) : InteractionResult.PASS;
    }

    private static boolean insert(UniversalPlasticEntity pot, ItemStack held, int slot) {
        ItemStack remainder = ItemHandlerUtil.insertItem(preferredInput(pot, slot, false), held.copy(), false);
        int inserted = held.getCount() - remainder.getCount();
        held.shrink(inserted);
        return inserted > 0;
    }

    private static InteractionResult extract(UniversalPlasticEntity pot, Player player, InteractionHand hand, int slot) {
        // 客户端没有权威仓储；返回 PASS 会让原版再发送一次无坐标交互，把服务端刚取出的物品塞回去。
        if (pot.level().isClientSide) return InteractionResult.SUCCESS;
        IItemHandler handler = slot < 0 ? pot.getMoldedItemHandler().outputView() : pot.getMoldedItemHandler().inputView();
        List<ItemStack> extracted = new ArrayList<>();
        for (int index = 0; index < handler.getSlots(); index++) {
            if (slot >= 0 && index != slot) continue;
            if (handler.getStackInSlot(index).isEmpty()) continue;
            ItemStack stack;
            while (!(stack = handler.extractItem(index, Integer.MAX_VALUE, false)).isEmpty()) extracted.add(stack);
        }
        if (extracted.isEmpty()) return InteractionResult.PASS;
        player.setItemInHand(hand, extracted.getFirst());
        for (int index = 1; index < extracted.size(); index++) {
            player.getInventory().placeItemBackInInventory(extracted.get(index));
        }
        return InteractionResult.CONSUME;
    }

    public static IFluidHandler fluidAccess(UniversalPlasticEntity pot, @Nullable Direction side) {
        return side == pot.getOrientation().attachmentFace().getOpposite()
            ? pot.getMoldedFluidHandler().bottomAccess() : pot.getMoldedFluidHandler().topAccess();
    }

    public static IItemHandler itemAccess(UniversalPlasticEntity pot, @Nullable Direction side, Vec3 worldPosition) {
        Direction opening = pot.getOrientation().attachmentFace();
        AABB bounds = pot.plasticraft$getGeometry().localBounds();
        boolean bottom = part(localPoint(pot, worldPosition).y, bounds.minY, bounds.maxY) == -1;
        if (side == opening.getOpposite() || side != opening && bottom) {
            return new Access(pot.getMoldedItemHandler().outputView(), -1, false, true);
        }
        return preferredInput(pot, inputSlot(pot, worldPosition), side != null && side.getAxis() != opening.getAxis());
    }

    public static IItemHandler preferredInput(UniversalPlasticEntity pot, int slot, boolean extract) {
        return new Access(pot.getMoldedItemHandler().inputView(), slot, extract, false);
    }

    private static final class Access implements IItemHandler {
        private final IItemHandler delegate;
        private final int[] order;
        private final int extractSlot;
        private final boolean output;

        private Access(IItemHandler delegate, int preferred, boolean extract, boolean output) {
            this.delegate = delegate;
            this.extractSlot = extract ? preferred : -1;
            this.output = output;
            List<Integer> slots = new ArrayList<>();
            for (int slot = 0; slot < delegate.getSlots(); slot++) slots.add(slot);
            if (!output) slots.sort(Comparator.comparingInt((Integer slot) -> distance(slot, preferred)).thenComparingInt(i -> i));
            this.order = slots.stream().mapToInt(Integer::intValue).toArray();
        }

        private static int distance(int slot, int preferred) {
            int dx = INPUT_OFFSETS[slot][0] - (preferred < 0 ? 0 : INPUT_OFFSETS[preferred][0]);
            int dz = INPUT_OFFSETS[slot][1] - (preferred < 0 ? 0 : INPUT_OFFSETS[preferred][1]);
            return dx * dx + dz * dz;
        }

        @Override
        public int getSlots() {
            return this.order.length;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return this.delegate.getStackInSlot(this.order[slot]);
        }

        @Override
        public int getSlotLimit(int slot) {
            return this.delegate.getSlotLimit(this.order[slot]);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return !this.output && this.delegate.isItemValid(this.order[slot], stack);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return this.output ? stack : this.delegate.insertItem(this.order[slot], stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return this.output || this.order[slot] == this.extractSlot
                ? this.delegate.extractItem(this.order[slot], amount, simulate) : ItemStack.EMPTY;
        }
    }
}
