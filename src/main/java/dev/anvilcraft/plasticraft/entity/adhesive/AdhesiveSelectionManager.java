package dev.anvilcraft.plasticraft.entity.adhesive;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 保存每名玩家当前用树脂桶选中的实体。 */
public final class AdhesiveSelectionManager {
    private static final Map<UUID, Selection> CLIENT_SELECTIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, Selection> SERVER_SELECTIONS = new ConcurrentHashMap<>();

    private AdhesiveSelectionManager() {
    }

    public static void select(Player player, Entity target) {
        selectStoredFace(player, target, AdhesiveFaces.defaultStoredFace(target));
    }

    public static void select(Player player, Entity target, Direction clickedWorldFace) {
        Direction clickedFace = AdhesiveFaces.storedFace(target, clickedWorldFace);
        Selection current = selections(player).get(player.getUUID());
        Direction selectedFace = current != null
            && current.targetUuid().equals(target.getUUID())
            && target instanceof dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity
            && current.selectedFace() == clickedFace
            ? clickedFace.getOpposite()
            : clickedFace;
        selectStoredFace(player, target, selectedFace);
    }

    private static void selectStoredFace(Player player, Entity target, Direction selectedFace) {
        selections(player).put(
            player.getUUID(),
            new Selection(target.getUUID(), target.getId(), selectedFace)
        );
    }

    public static boolean hasSelection(Player player) {
        return selections(player).containsKey(player.getUUID());
    }

    public static @Nullable UUID getSelectedUuid(Player player) {
        Selection selection = selections(player).get(player.getUUID());
        return selection == null ? null : selection.targetUuid();
    }

    public static int getSelectedEntityId(Player player) {
        Selection selection = selections(player).get(player.getUUID());
        return selection == null ? -1 : selection.entityId();
    }

    public static Direction getSelectedFace(Player player) {
        Selection selection = selections(player).get(player.getUUID());
        return selection == null ? Direction.DOWN : selection.selectedFace();
    }

    public static @Nullable Entity resolveServerSelection(Player player) {
        UUID targetUuid = getSelectedUuid(player);
        if (targetUuid == null || !(player.level() instanceof ServerLevel serverLevel)) return null;
        Entity target = serverLevel.getEntity(targetUuid);
        if (target == null || !target.isAlive()) {
            clear(player);
            return null;
        }
        return target;
    }

    public static void clear(Player player) {
        selections(player).remove(player.getUUID());
    }

    private static Map<UUID, Selection> selections(Player player) {
        // 单人游戏的客户端与集成服务端共用 JVM，但一次点击在两侧各处理一次。
        return player.level().isClientSide ? CLIENT_SELECTIONS : SERVER_SELECTIONS;
    }

    private record Selection(UUID targetUuid, int entityId, Direction selectedFace) {
    }
}
