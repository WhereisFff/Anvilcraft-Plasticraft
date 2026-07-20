package dev.anvilcraft.plasticraft.client.event;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.client.gui.screen.PlasticHammerScreen;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.network.PlasticEntityHammerUsePacket;
import dev.dubhe.anvilcraft.client.gui.screen.AnvilHammerScreen;
import dev.dubhe.anvilcraft.item.AnvilHammerItem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.EntityHitResult;
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
        if (!(minecraft.hitResult instanceof EntityHitResult hit)
            || !(hit.getEntity() instanceof AbstractPlasticEntity target)
            || !target.supportsAnvilHammerOrientationMenu()) {
            return;
        }

        if (pending == null) {
            if (minecraft.screen != null) return;
            InteractionHand hammerHand = event.getHand();
            if (!(player.getItemInHand(hammerHand).getItem() instanceof AnvilHammerItem)) return;
            Direction interactionFace = target.nearestInteractionFace(
                hit.getLocation().subtract(target.position())
            );
            pending = new PendingInteraction(
                target,
                hammerHand,
                interactionFace,
                Util.getMillis()
            );
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
            || !interaction.target.isAlive()
            || minecraft.level.getEntity(interaction.target.getId()) != interaction.target
            || !(player.getItemInHand(interaction.hand).getItem() instanceof AnvilHammerItem)) {
            cancel();
            return;
        }

        if (interaction.wheelOpened) {
            if (!(minecraft.screen instanceof PlasticHammerScreen screen)
                || !screen.targets(interaction.target)) {
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
        minecraft.setScreen(new PlasticHammerScreen(interaction.target, interaction.hand));
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
                && screen.targets(interaction.target)) {
                screen.completeSelection();
            }
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
            && screen.targets(pending.target)) {
            screen.cancelSelection();
        }
        pending = null;
    }

    private static final class PendingInteraction {
        private final AbstractPlasticEntity target;
        private final InteractionHand hand;
        private final Direction interactionFace;
        private final long startedAtMillis;
        private boolean wheelOpened;

        private PendingInteraction(
            AbstractPlasticEntity target,
            InteractionHand hand,
            Direction interactionFace,
            long startedAtMillis
        ) {
            this.target = target;
            this.hand = hand;
            this.interactionFace = interactionFace;
            this.startedAtMillis = startedAtMillis;
        }
    }
}
