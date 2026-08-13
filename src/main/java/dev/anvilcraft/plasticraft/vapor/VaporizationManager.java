package dev.anvilcraft.plasticraft.vapor;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.vapor.event.LargeCauldronProcessEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/** 协调一个气化源、顶层流体和炼药锅正上方的消费者。仅在服务器线程调用。 */
public final class VaporizationManager {
    private VaporizationManager() {
    }

    public static void tick(ServerLevel level, VaporizationCauldron cauldron) {
        if (!cauldron.isMainVaporizationPart()) return;
        VaporizationContext context = new VaporizationContext(level, cauldron);
        NeoForge.EVENT_BUS.post(new LargeCauldronProcessEvent(
            context,
            LargeCauldronProcessEvent.Phase.BEFORE_VAPORIZATION
        ));
        processFirstSource(context);
        NeoForge.EVENT_BUS.post(new LargeCauldronProcessEvent(
            context,
            LargeCauldronProcessEvent.Phase.AFTER_VAPORIZATION
        ));
    }

    public static int receiveVapor(VaporizationContext context, VaporStack vapor, VaporAction action) {
        if (vapor.isEmpty()) return 0;
        IVaporConsumer consumer = findConsumer(context);
        if (consumer == null) return 0;
        return Math.clamp(consumer.receiveVapor(vapor, action, context), 0, vapor.amount());
    }

    public static @Nullable IVaporConsumer findConsumer(VaporizationContext context) {
        return context.level().getCapability(
            VaporCapabilities.VAPOR_CONSUMER,
            context.outletPos(),
            Direction.DOWN
        );
    }

    /**
     * 炼药锅正上方九格是否都被完整碰撞方块封闭。
     * 消费者自行处理排气路径，因为其输入结构可能占用这些格子。
     */
    public static boolean isOutletBlocked(VaporizationContext context) {
        BlockPos center = context.outletPos();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos pos = center.offset(x, 0, z);
                if (!context.level().getBlockState(pos).isCollisionShapeFullBlock(context.level(), pos)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void processFirstSource(VaporizationContext context) {
        IVaporConsumer consumer = findConsumer(context);
        boolean sealedOutlet = consumer == null
            ? isOutletBlocked(context)
            : consumer.sealsOutlet(context);
        for (VaporizationSource source : VaporizationSources.getSources()) {
            FluidStack available = context.topFluid();
            if (available.isEmpty()) return;
            VaporizationOffer maximum = source.createOffer(context, available.copy(), Integer.MAX_VALUE);
            if (!isValidOffer(maximum, available, Integer.MAX_VALUE)) continue;

            VaporizationOffer offer = maximum;
            int accepted = simulateAcceptance(consumer, maximum.output(), context);
            if (sealedOutlet) {
                if (accepted <= 0) continue;
                offer = source.createOffer(context, available.copy(), accepted);
                if (!isValidOffer(offer, available, accepted)) continue;
                accepted = simulateAcceptance(consumer, offer.output(), context);
                if (accepted != offer.output().amount()) continue;
            }

            FluidStack request = offer.input();
            FluidStack simulated = context.cauldron().drainVaporizationFluid(
                request,
                IFluidHandler.FluidAction.SIMULATE
            );
            if (!FluidStack.matches(simulated, request)) continue;
            FluidStack drained = context.cauldron().drainVaporizationFluid(
                request,
                IFluidHandler.FluidAction.EXECUTE
            );
            if (!FluidStack.matches(drained, request)) {
                AnvilcraftPlasticraft.LOGGER.error(
                    "Vaporization source {} could not drain its simulated input at {}",
                    source.id(),
                    context.cauldronPos()
                );
                return;
            }

            source.commit(context, offer);
            if (consumer != null && accepted > 0) {
                VaporStack delivery = offer.output().withAmount(accepted);
                int delivered = Math.clamp(
                    consumer.receiveVapor(delivery, VaporAction.EXECUTE, context),
                    0,
                    delivery.amount()
                );
                if (delivered != delivery.amount() && consumer.sealsOutlet(context)) {
                    AnvilcraftPlasticraft.LOGGER.error(
                        "Vapor consumer at {} accepted {} mB after simulating {} mB",
                        context.outletPos(),
                        delivered,
                        delivery.amount()
                    );
                }
            }
            return;
        }
    }

    private static int simulateAcceptance(
        @Nullable IVaporConsumer consumer,
        VaporStack vapor,
        VaporizationContext context
    ) {
        if (consumer == null) return 0;
        return Math.clamp(
            consumer.receiveVapor(vapor, VaporAction.SIMULATE, context),
            0,
            vapor.amount()
        );
    }

    private static boolean isValidOffer(
        @Nullable VaporizationOffer offer,
        FluidStack available,
        int maxVapor
    ) {
        if (offer == null || offer.output().amount() > maxVapor) return false;
        FluidStack input = offer.input();
        return input.getAmount() <= available.getAmount()
            && FluidStack.isSameFluidSameComponents(input, available);
    }
}
