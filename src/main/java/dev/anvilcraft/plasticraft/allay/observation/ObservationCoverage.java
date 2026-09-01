package dev.anvilcraft.plasticraft.allay.observation;

import dev.anvilcraft.plasticraft.allay.AllayWorkRecord;
import dev.anvilcraft.plasticraft.allay.tool.AllayToolDefinition;
import dev.anvilcraft.plasticraft.allay.tool.AllayToolDefinitions;
import dev.anvilcraft.plasticraft.block.entity.AllayLoungeBlockEntity;
import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 一份观察覆盖的几何与资格定义。加载单位是完整高度区块柱,
 * 因此这里只做 X/Z 上的九柱枚举,不设任何 Y 向窗口。
 */
public final class ObservationCoverage {
    /** 覆盖半径:中心柱加水平八邻区。 */
    public static final int RADIUS = 1;
    /** 一份覆盖固定占用的完整区块柱数量。 */
    public static final int COLUMNS = (RADIUS * 2 + 1) * (RADIUS * 2 + 1);

    private ObservationCoverage() {
    }

    /** 以 center 为中心的九柱,顺序稳定以便断言与日志比对。 */
    public static List<ChunkPos> columns(ChunkPos center) {
        List<ChunkPos> result = new ArrayList<>(COLUMNS);
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                result.add(new ChunkPos(center.x + dx, center.z + dz));
            }
        }
        return result;
    }

    public static boolean covers(ChunkPos center, ChunkPos chunk) {
        return Math.abs(center.x - chunk.x) <= RADIUS && Math.abs(center.z - chunk.z) <= RADIUS;
    }

    /**
     * 世界中的观察悦灵资格:仍戴帽、主手望远镜、实体真实存在于该维度。
     * 不看内部 FE,也不看休息室供电。
     */
    public static boolean isEligible(@Nullable WorkingAllayEntity worker) {
        if (worker == null) return false;
        if (!worker.isAlive() || worker.isDeadOrDying() || worker.isRemoved()) return false;
        if (worker.getHardHat().isEmpty()) return false;
        return isObservationTool(worker.toolDefinition());
    }

    /** 托管记录资格:记录本身必须是戴帽的望远镜悦灵,普通箱子和玩家背包里的望远镜不算。 */
    public static boolean isEligible(@Nullable AllayWorkRecord record) {
        if (record == null) return false;
        if (record.hardHat().isEmpty()) return false;
        return isObservationTool(AllayToolDefinitions.fromHeldItem(record.heldTool()));
    }

    public static boolean isObservationTool(AllayToolDefinition definition) {
        return definition.id().equals(AllayToolDefinitions.OBSERVATION.id());
    }

    /**
     * 休息室资格:16 条有效托管记录里至少一只手持望远镜的观察悦灵,
     * 或正在入库通道中的那条记录本身就是观察悦灵。存放多只不叠加范围。
     * 入库途中的记录同样计入,否则实体撤票与托管落位之间会出现一刻断刻。
     */
    public static boolean isEligible(@Nullable AllayLoungeBlockEntity lounge) {
        if (lounge == null || lounge.isRemoved()) return false;
        if (isEligible(lounge.dockingRecord())) return true;
        return lounge.hasHosted(ObservationCoverage::isEligible);
    }

    public static ChunkPos centerOf(WorkingAllayEntity worker) {
        return new ChunkPos(worker.blockPosition());
    }

    public static ChunkPos centerOf(BlockPos pos) {
        return new ChunkPos(pos);
    }

    /** 该坐标当前是否已由任意来源(玩家、观察悦灵、其他模组加载器)保持实体刻。 */
    public static boolean isTicking(ServerLevel level, BlockPos pos) {
        return level.isPositionEntityTicking(pos);
    }

    /** 该区块柱当前是否已由任意来源保持实体刻,查询不会触发加载。 */
    public static boolean isTicking(ServerLevel level, ChunkPos chunk) {
        return level.isPositionEntityTicking(chunk.getWorldPosition());
    }
}
