package dev.anvilcraft.plasticraft.client.event;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.client.gui.screen.PlasticHammerScreen;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.network.BondedPlasticHammerUsePacket;
import dev.anvilcraft.plasticraft.network.PlasticEntityHammerUsePacket;
import dev.dubhe.anvilcraft.client.gui.screen.AnvilHammerScreen;
import dev.dubhe.anvilcraft.item.AnvilHammerItem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;

/** 将塑料实体的铁砧锤交互分为快速释放和长按环形菜单。 */
@EventBusSubscriber(modid = AnvilcraftPlasticraft.MOD_ID, value = Dist.CLIENT)
public final class PlasticHammerInteractionHandler {
    @Nullable
    private static PendingInteraction pending;

    private PlasticHammerInteractionHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) return;

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || player.isShiftKeyDown()) return;
        if (pending == null) {
            if (minecraft.screen != null) return;
            InteractionHand hammerHand = event.getHand();
            if (!(player.getItemInHand(hammerHand).getItem() instanceof AnvilHammerItem)) return;
            if (minecraft.hitResult instanceof EntityHitResult hit
                && hit.getEntity() instanceof AbstractPlasticEntity target
                && target.supportsAnvilHammerOrientationMenu()) {
                Direction interactionFace = target.nearestInteractionFace(
                    hit.getLocation().subtract(target.position())
                );
                pending = PendingInteraction.entity(target, hammerHand, interactionFace);
            } else if (minecraft.hitResult instanceof BlockHitResult hit
                && minecraft.level != null
                && minecraft.level.getBlockEntity(hit.getBlockPos()) instanceof BondedEntityBlockEntity bonded
                && bonded.isInitialized()
                && bonded.isPlastic()
                && bonded.getOrCreateRenderEntity() instanceof AbstractPlasticEntity target
                && target.supportsAnvilHammerOrientationMenu()) {
                pending = PendingInteraction.block(
                    target,
                    hit.getBlockPos(),
                    hammerHand,
                    hit.getDirection()
                );
            } else {
                return;
            }
        }

        event.setSwingHand(false);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        PendingInteraction interaction = pending;
        if (interaction == null) return;

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null
            || minecraft.level == null
            || !interaction.isValid(minecraft)
            || !(player.getItemInHand(interaction.hand).getItem() instanceof AnvilHammerItem)) {
            cancel();
            return;
        }

        if (interaction.wheelOpened) {
            if (!(minecraft.screen instanceof PlasticHammerScreen screen)
                || !interaction.targets(screen)) {
                cancel();
                return;
            }
            return;
        }

        if (minecraft.screen != null) {
            cancel();
            return;
        }

        if (Util.getMillis() - interaction.startedAtMillis < AnvilHammerScreen.DELAY) return;
        interaction.wheelOpened = true;
        minecraft.setScreen(interaction.createScreen());
    }

    @SubscribeEvent
    public static void onKeyReleased(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_RELEASE) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.options.keyUse.matches(event.getKey(), event.getScanCode())) return;
        releasePendingInteraction();
    }

    @SubscribeEvent
    public static void onMouseReleased(InputEvent.MouseButton.Post event) {
        if (event.getAction() != GLFW.GLFW_RELEASE) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.options.keyUse.matchesMouse(event.getButton())) return;
        releasePendingInteraction();
    }

    private static void releasePendingInteraction() {
        PendingInteraction interaction = pending;
        if (interaction == null) return;
        if (interaction.wheelOpened) {
            if (Minecraft.getInstance().screen instanceof PlasticHammerScreen screen
                && interaction.targets(screen)) {
                screen.completeSelection();
            }
        } else if (interaction.bondedBlockPos != null) {
            PacketDistributor.sendToServer(new BondedPlasticHammerUsePacket(
                interaction.bondedBlockPos,
                interaction.hand,
                interaction.interactionFace
            ));
        } else {
            PacketDistributor.sendToServer(new PlasticEntityHammerUsePacket(
                interaction.target.getId(),
                interaction.hand,
                interaction.interactionFace
            ));
        }
        pending = null;
    }

    private static void cancel() {
        if (pending != null && pending.wheelOpened
            && Minecraft.getInstance().screen instanceof PlasticHammerScreen screen
            && pending.targets(screen)) {
            screen.cancelSelection();
        }
        pending = null;
    }

    private static final class PendingInteraction {
        private final AbstractPlasticEntity target;
        @Nullable
        private final BlockPos bondedBlockPos;
        private final InteractionHand hand;
        private final Direction interactionFace;
        private final long startedAtMillis;
        private boolean wheelOpened;

        private PendingInteraction(
            AbstractPlasticEntity target,
            @Nullable BlockPos bondedBlockPos,
            InteractionHand hand,
            Direction interactionFace,
            long startedAtMillis
        ) {
            this.target = target;
            this.bondedBlockPos = bondedBlockPos == null ? null : bondedBlockPos.immutable();
            this.hand = hand;
            this.interactionFace = interactionFace;
            this.startedAtMillis = startedAtMillis;
        }

        private static PendingInteraction entity(
            AbstractPlasticEntity target,
            InteractionHand hand,
            Direction interactionFace
        ) {
            return new PendingInteraction(target, null, hand, interactionFace, Util.getMillis());
        }

        private static PendingInteraction block(
            AbstractPlasticEntity target,
            BlockPos pos,
            InteractionHand hand,
            Direction interactionFace
        ) {
            return new PendingInteraction(target, pos, hand, interactionFace, Util.getMillis());
        }

        private boolean isValid(Minecraft minecraft) {
            if (this.bondedBlockPos == null) {
                return this.target.isAlive()
                    && minecraft.level != null
                    && minecraft.level.getEntity(this.target.getId()) == this.target;
            }
            return minecraft.level != null
                && minecraft.level.getBlockEntity(this.bondedBlockPos) instanceof BondedEntityBlockEntity bonded
                && bonded.isInitialized()
                && bonded.isPlastic();
        }

        private boolean targets(PlasticHammerScreen screen) {
            return this.bondedBlockPos == null
                ? screen.targets(this.target)
                : screen.targets(this.bondedBlockPos);
        }

        private PlasticHammerScreen createScreen() {
            return this.bondedBlockPos == null
                ? new PlasticHammerScreen(this.target, this.hand)
                : new PlasticHammerScreen(this.target, this.hand, this.bondedBlockPos);
        }
    }
}
