package dev.anvilcraft.plasticraft.entity.adhesive;

import dev.anvilcraft.plasticraft.block.BlockAdhesionState;
import dev.anvilcraft.plasticraft.block.BondedFallingBlocks;
import dev.anvilcraft.plasticraft.init.PlasticraftAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;

/** 将飞溅范围内的高粘性树脂胶永久标记为不可见。 */
public final class AdhesiveInvisibilityService {
    private static final double HORIZONTAL_RANGE = ThrownPotion.SPLASH_RANGE;
    private static final double VERTICAL_RANGE = 2.0D;
    private static final double RANGE_SQR = HORIZONTAL_RANGE * HORIZONTAL_RANGE;

    private AdhesiveInvisibilityService() {
    }

    public static void projectileImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof ThrownPotion potion)
            || !(potion.level() instanceof ServerLevel level)
            || !isInvisibilityPotion(potion.getItem())) {
            return;
        }
        makeInvisible(level, event.getRayTraceResult().getLocation());
    }

    public static int makeInvisible(ServerLevel level, Vec3 impact) {
        int changed = makeBlockAdhesiveInvisible(level, impact);
        for (Entity entity : level.getAllEntities()) {
            if (!entity.isAlive()) continue;
            changed += makeEntityAdhesionInvisible(level, impact, entity);
            changed += makeEntityBondsInvisible(level, impact, entity);
        }
        return changed;
    }

    private static boolean isInvisibilityPotion(ItemStack stack) {
        if (!stack.is(Items.SPLASH_POTION) && !stack.is(Items.LINGERING_POTION)) return false;
        PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        for (var effect : contents.getAllEffects()) {
            if (effect.is(MobEffects.INVISIBILITY)) return true;
        }
        return false;
    }

    private static int makeBlockAdhesiveInvisible(ServerLevel level, Vec3 impact) {
        int changed = 0;
        BlockPos min = BlockPos.containing(
            impact.x - HORIZONTAL_RANGE,
            impact.y - VERTICAL_RANGE,
            impact.z - HORIZONTAL_RANGE
        );
        BlockPos max = BlockPos.containing(
            impact.x + HORIZONTAL_RANGE,
            impact.y + VERTICAL_RANGE,
            impact.z + HORIZONTAL_RANGE
        );
        for (BlockPos mutablePos : BlockPos.betweenClosed(min, max)) {
            BlockPos pos = mutablePos.immutable();
            if (!level.hasChunkAt(pos)) continue;
            BlockAdhesionState state = BondedFallingBlocks.getAdhesion(level, pos);
            if (state == null) continue;
            for (Direction face : Direction.values()) {
                if (!state.hasAdhesive(face)
                    || state.isInvisible(face)
                    || !withinSplash(impact, blockFaceCenter(pos, face))) {
                    continue;
                }
                if (BondedFallingBlocks.setInvisible(level, pos, face)) changed++;
            }
        }
        return changed;
    }

    private static int makeEntityAdhesionInvisible(ServerLevel level, Vec3 impact, Entity entity) {
        EntityAdhesion adhesion = entity.getExistingDataOrNull(PlasticraftAttachments.ENTITY_ADHESION.get());
        if (adhesion == null || adhesion.invisible()) return 0;
        Vec3 anchor = blockFaceCenter(adhesion.supportPos(), adhesion.attachmentFace());
        Vec3 attachedPoint = AdhesiveFaces.worldFaceAlignmentPoint(
            entity,
            adhesion.attachmentFace().getOpposite()
        );
        if (!withinSplash(impact, anchor, attachedPoint)) return 0;
        entity.setData(PlasticraftAttachments.ENTITY_ADHESION, adhesion.withInvisible());
        BondedFallingBlocks.setInvisible(level, adhesion.supportPos(), adhesion.attachmentFace());
        return 1;
    }

    private static int makeEntityBondsInvisible(ServerLevel level, Vec3 impact, Entity entity) {
        EntityBondState state = EntityBondManager.get(entity);
        if (state == null) return 0;
        int changed = 0;
        for (EntityBondLink link : state.links()) {
            Entity other = EntityBondManager.resolve(level, link);
            if (link.invisible()
                || other == null
                || entity.getUUID().compareTo(other.getUUID()) >= 0
                || !withinSplash(
                    impact,
                    AdhesiveFaces.storedFaceAlignmentPoint(entity, link.face()),
                    AdhesiveFaces.storedFaceAlignmentPoint(other, link.otherFace())
                )) {
                continue;
            }
            if (EntityBondManager.setBondInvisible(level, entity, link.face())) changed++;
        }
        return changed;
    }

    private static Vec3 blockFaceCenter(BlockPos pos, Direction face) {
        return Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.5D));
    }

    private static boolean withinSplash(Vec3 impact, Vec3 point) {
        Vec3 difference = point.subtract(impact);
        return Math.abs(difference.y) <= VERTICAL_RANGE && difference.lengthSqr() < RANGE_SQR;
    }

    private static boolean withinSplash(Vec3 impact, Vec3 from, Vec3 to) {
        Vec3 segment = to.subtract(from);
        double lengthSqr = segment.lengthSqr();
        if (lengthSqr <= 1.0E-8D) return withinSplash(impact, from);
        double progress = Math.clamp(impact.subtract(from).dot(segment) / lengthSqr, 0.0D, 1.0D);
        return withinSplash(impact, from.add(segment.scale(progress)));
    }
}
