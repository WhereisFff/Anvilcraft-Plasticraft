package dev.anvilcraft.plasticraft.entity.redstone;

import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.molding.product.MoldedTrayCell;
import dev.dubhe.anvilcraft.block.entity.AdvancedComparatorBlockEntity;
import dev.dubhe.anvilcraft.block.entity.ItemDetectorBlockEntity;
import dev.dubhe.anvilcraft.block.entity.PulseGeneratorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/** 菜单客户端构造器用它定位没有真实方块位置的支架元件。 */
public final class MoldedTrayComponentLookup {
    private MoldedTrayComponentLookup() {
    }

    public static @Nullable BlockEntity find(Level level, BlockPos position, Kind kind) {
        BlockEntity direct = level.getBlockEntity(position);
        if (matches(direct, kind)) return direct;
        if (direct instanceof BondedEntityBlockEntity bonded) {
            BlockEntity tray = trayBlockEntity(bonded, position, kind);
            if (tray != null) return tray;
        }

        AABB search = new AABB(position).inflate(3.0D);
        BlockEntity nearest = null;
        double nearestAnchorDistance = Double.POSITIVE_INFINITY;
        double nearestEntityDistance = Double.POSITIVE_INFINITY;
        for (UniversalPlasticEntity entity : level.getEntitiesOfClass(
            UniversalPlasticEntity.class,
            search,
            candidate -> candidate.isMoldedTray()
                && candidate.plasticraft$getAnchorBlockPos().distSqr(position) <= 9.0D
        )) {
            BlockEntity tray = trayBlockEntity(entity, position, kind);
            if (tray == null) continue;
            double anchorDistance = entity.plasticraft$getAnchorBlockPos().distSqr(position);
            double entityDistance = entity.position().distanceToSqr(position.getCenter());
            if (anchorDistance < nearestAnchorDistance
                || anchorDistance == nearestAnchorDistance && entityDistance < nearestEntityDistance) {
                nearest = tray;
                nearestAnchorDistance = anchorDistance;
                nearestEntityDistance = entityDistance;
            }
        }
        if (nearest != null) return nearest;

        double nearestBondedDistance = Double.POSITIVE_INFINITY;
        for (BlockPos nearby : BlockPos.betweenClosed(
            position.offset(-3, -3, -3),
            position.offset(3, 3, 3)
        )) {
            if (!(level.getBlockEntity(nearby) instanceof BondedEntityBlockEntity bonded)) continue;
            BlockEntity tray = trayBlockEntity(bonded, position, kind);
            if (tray == null) continue;
            double distance = nearby.distSqr(position);
            if (distance < nearestBondedDistance) {
                nearest = tray;
                nearestBondedDistance = distance;
            }
        }
        return nearest;
    }

    private static @Nullable BlockEntity trayBlockEntity(
        BondedEntityBlockEntity bonded,
        BlockPos position,
        Kind kind
    ) {
        Entity renderEntity = bonded.getOrCreateRenderEntity();
        if (!(renderEntity instanceof UniversalPlasticEntity universal) || !universal.isMoldedTray()) return null;
        return trayBlockEntity(universal, position, kind);
    }

    private static @Nullable BlockEntity trayBlockEntity(
        UniversalPlasticEntity entity,
        BlockPos position,
        Kind kind
    ) {
        for (var entry : entity.plasticraft$getTrayBlockEntities().entrySet()) {
            MoldedTrayCell cell = entry.getKey();
            BlockEntity blockEntity = entry.getValue();
            if (MoldedTrayRedstoneNetwork.componentPosition(entity, cell).equals(position)
                && matches(blockEntity, kind)) {
                return blockEntity;
            }
        }
        return null;
    }

    private static boolean matches(@Nullable BlockEntity blockEntity, Kind kind) {
        return switch (kind) {
            case PULSE_GENERATOR -> blockEntity instanceof PulseGeneratorBlockEntity;
            case ITEM_DETECTOR -> blockEntity instanceof ItemDetectorBlockEntity;
            case ADVANCED_COMPARATOR -> blockEntity instanceof AdvancedComparatorBlockEntity;
        };
    }

    public enum Kind {
        PULSE_GENERATOR,
        ITEM_DETECTOR,
        ADVANCED_COMPARATOR
    }
}
