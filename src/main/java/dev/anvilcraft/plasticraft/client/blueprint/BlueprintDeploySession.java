package dev.anvilcraft.plasticraft.client.blueprint;

import dev.anvilcraft.plasticraft.blueprint.BlueprintPlacement;
import dev.anvilcraft.plasticraft.blueprint.ConstructionBlueprintData;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJob;
import dev.anvilcraft.plasticraft.network.BlueprintCancelPacket;
import dev.anvilcraft.plasticraft.network.BlueprintDeployPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * 客户端部署会话:手持已导入磁盘右击进入,投影跟随准星直到锁定锚点,
 * 滚轮在工具间切换,右击执行当前工具,Shift+滚轮快捷调层;确认时把最终
 * 放置参数发给服务端。会话本身不改动世界,服务端只在确认与取消时参与。
 */
public final class BlueprintDeploySession {
    /** 部署工具条,顺序即滚轮循环顺序。 */
    public enum Tool {
        MOVE("move"),
        ROTATE("rotate"),
        FLIP("flip"),
        LAYER_DOWN("layer_down"),
        LAYER_UP("layer_up"),
        CONFIRM("confirm"),
        CANCEL("cancel");

        private final String id;

        Tool(String id) {
            this.id = id;
        }

        public String id() {
            return this.id;
        }
    }

    /** 准星没有命中方块时,锚点沿视线投射的距离(格)。 */
    private static final double FREE_PLACE_DISTANCE = 8.0D;
    /** 准星拾取方块的最大距离(格)。 */
    private static final double PICK_DISTANCE = 48.0D;
    /** 显示全部层的分层查看取值。 */
    public static final int LAYERS_ALL = -1;

    @Nullable
    private static SessionState state;

    private BlueprintDeploySession() {
    }

    private static final class SessionState {
        private final InteractionHand hand;
        private final String hash;
        private final String name;
        private final Vec3i size;
        private final Optional<UUID> jobId;
        private Rotation rotation = Rotation.NONE;
        private Mirror mirror = Mirror.NONE;
        private BlockPos anchor = BlockPos.ZERO;
        private boolean anchorLocked;
        private int layerView = LAYERS_ALL;
        private Tool selectedTool = Tool.MOVE;

        private SessionState(
            InteractionHand hand,
            String hash,
            String name,
            Vec3i size,
            Optional<UUID> jobId
        ) {
            this.hand = hand;
            this.hash = hash;
            this.name = name;
            this.size = size;
            this.jobId = jobId;
        }
    }

    public static boolean isActive() {
        return state != null;
    }

    /** 手持已导入磁盘进入会话;磁盘已部署时载入其当前放置参数用于重摆。 */
    public static void begin(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        ConstructionBlueprintData data = ConstructionBlueprintData.get(held).orElse(null);
        if (data == null) return;
        SessionState session = new SessionState(
            hand,
            data.hash(),
            data.name(),
            data.size(),
            data.jobId()
        );
        ConstructionJob job = data.jobId().map(ClientBlueprintJobCache::job).orElse(null);
        if (job != null) {
            session.rotation = job.rotation();
            session.mirror = job.mirror();
            session.anchor = job.anchor();
            session.anchorLocked = true;
        }
        state = session;
    }

    public static void exit() {
        state = null;
    }

    /** 每客户端刻更新:手持物离开磁盘自动退出;未锁定时锚点跟随准星。 */
    public static void clientTick(Minecraft minecraft) {
        SessionState session = state;
        if (session == null) return;
        Player player = minecraft.player;
        if (player == null || minecraft.level == null) {
            exit();
            return;
        }
        ItemStack held = player.getItemInHand(session.hand);
        ConstructionBlueprintData data = ConstructionBlueprintData.get(held).orElse(null);
        if (data == null || !data.hash().equals(session.hash)) {
            exit();
            return;
        }
        if (!session.anchorLocked) {
            session.anchor = aimedAnchor(minecraft, player, session);
        }
    }

    /** 未锁定锚点时的跟随位置:命中方块则贴其相邻面,否则沿视线固定距离。 */
    private static BlockPos aimedAnchor(Minecraft minecraft, Player player, SessionState session) {
        HitResult hit = player.pick(PICK_DISTANCE, minecraft.getTimer().getGameTimeDeltaPartialTick(true), false);
        Vec3 base;
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            base = Vec3.atCenterOf(blockHit.getBlockPos().relative(blockHit.getDirection()));
        } else {
            base = player.getEyePosition().add(player.getLookAngle().scale(FREE_PLACE_DISTANCE));
        }
        // 让蓝图包围盒水平居中于目标点,底面落在目标高度。
        BoundingBox bounds = new BlueprintPlacement(BlockPos.ZERO, session.rotation, session.mirror)
            .bounds(session.size);
        int anchorX = Mth.floor(base.x) - bounds.minX() - bounds.getXSpan() / 2;
        int anchorY = Mth.floor(base.y) - bounds.minY();
        int anchorZ = Mth.floor(base.z) - bounds.minZ() - bounds.getZSpan() / 2;
        return new BlockPos(anchorX, anchorY, anchorZ);
    }

    public static void cycleTool(int delta) {
        SessionState session = state;
        if (session == null) return;
        Tool[] tools = Tool.values();
        int next = Math.floorMod(session.selectedTool.ordinal() + delta, tools.length);
        session.selectedTool = tools[next];
    }

    /** Shift+滚轮的快捷分层:向上滚进入更高单层,超过顶层回到全部。 */
    public static void stepLayer(int delta) {
        SessionState session = state;
        if (session == null) return;
        int maxLayer = session.size.getY() - 1;
        int current = session.layerView;
        int next = current == LAYERS_ALL ? (delta > 0 ? 0 : maxLayer) : current + delta;
        session.layerView = next < 0 || next > maxLayer ? LAYERS_ALL : next;
    }

    public static void executeSelectedTool() {
        SessionState session = state;
        if (session == null) return;
        switch (session.selectedTool) {
            case MOVE -> session.anchorLocked = !session.anchorLocked;
            case ROTATE -> applyTransform(session, session.rotation.getRotated(Rotation.CLOCKWISE_90), session.mirror);
            case FLIP -> applyTransform(session, session.rotation, nextMirror(session.mirror));
            case LAYER_DOWN -> stepLayer(-1);
            case LAYER_UP -> stepLayer(1);
            case CONFIRM -> confirm(session);
            case CANCEL -> cancel(session);
        }
    }

    private static Mirror nextMirror(Mirror mirror) {
        return switch (mirror) {
            case NONE -> Mirror.LEFT_RIGHT;
            case LEFT_RIGHT -> Mirror.FRONT_BACK;
            case FRONT_BACK -> Mirror.NONE;
        };
    }

    /** 旋转与镜像时保持包围盒中心不动,避免投影绕锚点甩开。 */
    private static void applyTransform(SessionState session, Rotation newRotation, Mirror newMirror) {
        BoundingBox before = new BlueprintPlacement(session.anchor, session.rotation, session.mirror)
            .bounds(session.size);
        session.rotation = newRotation;
        session.mirror = newMirror;
        BoundingBox after = new BlueprintPlacement(session.anchor, session.rotation, session.mirror)
            .bounds(session.size);
        session.anchor = session.anchor.offset(
            (before.minX() + before.getXSpan() / 2) - (after.minX() + after.getXSpan() / 2),
            before.minY() - after.minY(),
            (before.minZ() + before.getZSpan() / 2) - (after.minZ() + after.getZSpan() / 2)
        );
    }

    private static void confirm(SessionState session) {
        PacketDistributor.sendToServer(new BlueprintDeployPacket(
            session.hand,
            session.anchor,
            session.rotation,
            session.mirror
        ));
        exit();
    }

    /** 已部署磁盘的取消会移除世界中的蓝图;未部署时仅退出会话。 */
    private static void cancel(SessionState session) {
        UUID jobId = session.jobId.orElse(null);
        if (jobId != null && ClientBlueprintJobCache.job(jobId) != null) {
            PacketDistributor.sendToServer(new BlueprintCancelPacket(jobId));
        }
        exit();
    }

    // ==================== 渲染与 HUD 读取的会话视图 ====================

    @Nullable
    public static String activeHash() {
        SessionState session = state;
        return session == null ? null : session.hash;
    }

    @Nullable
    public static BlueprintPlacement activePlacement() {
        SessionState session = state;
        return session == null
            ? null
            : new BlueprintPlacement(session.anchor, session.rotation, session.mirror);
    }

    @Nullable
    public static UUID activeJobId() {
        SessionState session = state;
        return session == null ? null : session.jobId.orElse(null);
    }

    public static int layerView() {
        SessionState session = state;
        return session == null ? LAYERS_ALL : session.layerView;
    }

    @Nullable
    public static Tool selectedTool() {
        SessionState session = state;
        return session == null ? null : session.selectedTool;
    }

    @Nullable
    public static String activeName() {
        SessionState session = state;
        return session == null ? null : session.name;
    }

    public static Rotation activeRotation() {
        SessionState session = state;
        return session == null ? Rotation.NONE : session.rotation;
    }

    public static Mirror activeMirror() {
        SessionState session = state;
        return session == null ? Mirror.NONE : session.mirror;
    }

    public static boolean isAnchorLocked() {
        SessionState session = state;
        return session != null && session.anchorLocked;
    }
}
