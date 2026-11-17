package dev.anvilcraft.plasticraft.entity;

import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.anvilcraft.plasticraft.molding.product.PlasticCauldronLayout;
import dev.dubhe.anvilcraft.api.fluid.FluidHandlerWrapper;
import dev.dubhe.anvilcraft.api.fluid.LargeCauldronFluidHandler;
import dev.dubhe.anvilcraft.api.itemhandler.ItemHandlerUtil;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

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
        ResourceHandler<FluidResource> fluids = pot.getMoldedFluidHandler().sideAccess(
            Math.max(1, (int) Math.ceil(fraction * LargeCauldronFluidHandler.totalCapacity()))
        );
        if (isBottle(held)) {
            interactWithBottle(pot, player, hand, fluids);
            return InteractionResult.SUCCESS;
        }
        if (FluidUtil.interactWithFluidHandler(player, hand, pot.blockPosition(), fluids)
            || held.getCapability(Capabilities.Fluid.ITEM, ItemAccess.forPlayerInteraction(player, hand)) != null) {
            return InteractionResult.SUCCESS;
        }
        Vec3 worldHit = pot.plasticraft$getGeometry().worldPointAt(pot.position(), pot.getOrientation(), point);
        int slot = inputSlot(pot, worldHit);
        boolean forced = pot.getMoldedData().map(MoldedPlasticData::limitOverride).orElse(false);
        boolean floor = face == Direction.UP && (forced || Math.abs(point.y - cavity.minY) <= EPSILON);
        if (floor && hand == InteractionHand.MAIN_HAND) {
            if (held.isEmpty()) return extract(pot, player, hand, slot);
            if (!pot.level().isClientSide()) insert(pot, held, slot);
            return InteractionResult.SUCCESS;
        }
        if (held.is(ModBlocks.MENGER_SPONGE.asItem())) {
            if (!pot.level().isClientSide() && !pot.getMoldedFluidHandler().getBottomFluid().isEmpty()) {
                pot.getMoldedFluidHandler().discardFluids();
                pot.anvilcraft$setIgnited(false);
                pot.level().playSound(null, pot.blockPosition(), SoundEvents.SPONGE_ABSORB, SoundSource.BLOCKS, 1, 1);
            }
            return InteractionResult.SUCCESS;
        }
        AABB bounds = pot.plasticraft$getGeometry().localBounds();
        boolean rim = part(point.y, bounds.minY, bounds.maxY) == 1 && face != Direction.DOWN;
        if (hand != InteractionHand.MAIN_HAND || held.isEmpty() || !rim) return InteractionResult.PASS;
        return pot.level().isClientSide() || insert(pot, held, slot)
            ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    private static boolean insert(UniversalPlasticEntity pot, ItemStack held, int slot) {
        ItemStack remainder = ItemHandlerUtil.insertItem(preferredInput(pot, slot, false), held.copy(), false);
        int inserted = held.getCount() - remainder.getCount();
        held.shrink(inserted);
        return inserted > 0;
    }

    private static boolean isBottle(ItemStack stack) {
        return stack.is(Items.GLASS_BOTTLE) || stack.is(Items.HONEY_BOTTLE) || stack.is(Items.EXPERIENCE_BOTTLE)
            || stack.is(Items.POTION) && stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.WATER);
    }

    private static void interactWithBottle(UniversalPlasticEntity pot, Player player, InteractionHand hand,
                                           ResourceHandler<FluidResource> fluids) {
        if (pot.level().isClientSide()) {
            return;
        }
        ItemStack held = player.getItemInHand(hand);
        FluidHandlerWrapper wrapper = new FluidHandlerWrapper(fluids);
        boolean drain = held.is(Items.GLASS_BOTTLE);
        ItemStack result = drain ? wrapper.drainToItem(held, false) : wrapper.fillFromItem(held, false, pot.level().getRandom());
        if (result != null) {
            player.awardStat(drain ? Stats.USE_CAULDRON : Stats.FILL_CAULDRON);
            player.awardStat(Stats.ITEM_USED.get(held.getItem()));
            player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, result));
            pot.level().playSound(null, pot.blockPosition(), drain ? SoundEvents.BOTTLE_FILL : SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS);
            pot.level().gameEvent(null, drain ? GameEvent.FLUID_PICKUP : GameEvent.FLUID_PLACE, pot.blockPosition());
        }
    }

    private static InteractionResult extract(UniversalPlasticEntity pot, Player player, InteractionHand hand, int slot) {
        // 客户端没有权威仓储；返回 PASS 会让原版再发送一次无坐标交互，把服务端刚取出的物品塞回去。
        if (pot.level().isClientSide()) return InteractionResult.SUCCESS;
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

    public static ResourceHandler<FluidResource> fluidAccess(UniversalPlasticEntity pot, @Nullable Direction side) {
        return side == pot.getOrientation().attachmentFace().getOpposite()
            ? pot.getMoldedFluidHandler().bottomAccess() : pot.getMoldedFluidHandler().topAccess();
    }

    public static ResourceHandler<ItemResource> itemAccess(UniversalPlasticEntity pot, @Nullable Direction side, Vec3 worldPosition) {
        Direction opening = pot.getOrientation().attachmentFace();
        AABB bounds = pot.plasticraft$getGeometry().localBounds();
        boolean bottom = part(localPoint(pot, worldPosition).y, bounds.minY, bounds.maxY) == -1;
        if (side == opening.getOpposite() || side != opening && bottom) {
            return new Access(pot.getMoldedItemHandler().outputView(), -1, false, true);
        }
        return preferredInput(pot, inputSlot(pot, worldPosition), side != null && side.getAxis() != opening.getAxis());
    }

    public static ResourceHandler<ItemResource> preferredInput(UniversalPlasticEntity pot, int slot, boolean extract) {
        return new Access(pot.getMoldedItemHandler().inputView(), slot, extract, false);
    }

    private static final class Access implements ResourceHandler<ItemResource> {
        private final ResourceHandler<ItemResource> delegate;
        private final int[] order;
        private final int extractSlot;
        private final boolean output;

        private Access(ResourceHandler<ItemResource> delegate, int preferred, boolean extract, boolean output) {
            this.delegate = delegate;
            this.extractSlot = extract ? preferred : -1;
            this.output = output;
            List<Integer> slots = new ArrayList<>();
            for (int slot = 0; slot < delegate.size(); slot++) slots.add(slot);
            if (!output) slots.sort(Comparator.comparingInt((Integer slot) -> distance(slot, preferred)).thenComparingInt(i -> i));
            this.order = slots.stream().mapToInt(Integer::intValue).toArray();
        }

        private static int distance(int slot, int preferred) {
            int dx = INPUT_OFFSETS[slot][0] - (preferred < 0 ? 0 : INPUT_OFFSETS[preferred][0]);
            int dz = INPUT_OFFSETS[slot][1] - (preferred < 0 ? 0 : INPUT_OFFSETS[preferred][1]);
            return dx * dx + dz * dz;
        }

        @Override
        public int size() {
            return this.order.length;
        }

        @Override
        public ItemResource getResource(int slot) {
            return this.delegate.getResource(this.order[slot]);
        }

        @Override
        public long getAmountAsLong(int slot) {
            return this.delegate.getAmountAsLong(this.order[slot]);
        }

        @Override
        public long getCapacityAsLong(int slot, ItemResource resource) {
            return this.delegate.getCapacityAsLong(this.order[slot], resource);
        }

        @Override
        public boolean isValid(int slot, ItemResource resource) {
            return !this.output && this.delegate.isValid(this.order[slot], resource);
        }

        @Override
        public int insert(int slot, ItemResource resource, int amount, TransactionContext transaction) {
            return this.output ? 0 : this.delegate.insert(this.order[slot], resource, amount, transaction);
        }

        @Override
        public int extract(int slot, ItemResource resource, int amount, TransactionContext transaction) {
            return this.output || this.order[slot] == this.extractSlot
                ? this.delegate.extract(this.order[slot], resource, amount, transaction) : 0;
        }
    }
}
