package dev.anvilcraft.plasticraft.molding.product.storage;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.init.PlasticraftStorageTypes;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import dev.anvilcraft.plasticraft.network.MoldedPlasticCrateOpenPacket;
import dev.dubhe.anvilcraft.block.container.storage.CrateBlock;
import dev.dubhe.anvilcraft.block.entity.storage.CrateBlockEntity;
import dev.dubhe.anvilcraft.block.entity.storage.StorageBlockEntity;
import dev.dubhe.anvilcraft.init.block.ModBlockEntities;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import dev.dubhe.anvilcraft.saved.storage.IStorageType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 将玩家实际打开的移动或黏合板条箱绑定到本体仓储请求，每次请求重新校验实体和距离。 */
@EventBusSubscriber(modid = AnvilcraftPlasticraft.MOD_ID)
public final class MoldedPlasticCrateAccess {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static int nextSource;

    private MoldedPlasticCrateAccess() {
    }

    public static long open(ServerPlayer player, UniversalPlasticEntity entity, @Nullable BlockPos bondedPos) {
        if (player.isSpectator() || player.level() != entity.level()
            || player.position().distanceToSqr(entity.getBoundingBox().getCenter()) > 64) {
            throw new IllegalArgumentException("Player cannot access this plastic crate");
        }
        MoldedPlasticCrateStorage storage = entity.getMoldedItemHandler().crateStorage(true).orElseThrow();
        long source = new BlockPos(-33_554_432, -2_048, nextSource++ & 0x1FFFFFF).asLong();
        SESSIONS.put(player.getUUID(), new Session(
            source, player.level().dimension(), entity.getUUID(), bondedPos, storage.getId()
        ));
        PacketDistributor.sendToPlayer(player, new MoldedPlasticCrateOpenPacket(BlockPos.of(source), entity.getDisplayName()));
        return source;
    }

    // 使用世界边界之外的固定标记，过期请求也必须被识别并拒绝，避免触发随机区块读取。
    public static boolean isSource(long source) {
        BlockPos pos = BlockPos.of(source);
        return pos.getX() == -33_554_432 && pos.getY() == -2_048;
    }

    public static @Nullable StorageBlockEntity resolve(ServerPlayer player, long source) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.source() != source || player.isSpectator()
            || player.containerMenu != player.inventoryMenu || !session.dimension().equals(player.level().dimension())) {
            return null;
        }
        UniversalPlasticEntity entity;
        if (session.bondedPos() != null) {
            if (!player.serverLevel().hasChunkAt(session.bondedPos())
                || !(player.level().getBlockEntity(session.bondedPos()) instanceof BondedEntityBlockEntity bonded)
                || !(bonded.getOrCreateRenderEntity() instanceof UniversalPlasticEntity plastic)) {
                return null;
            }
            entity = plastic;
        } else if (player.serverLevel().getEntity(session.entityId()) instanceof UniversalPlasticEntity plastic) {
            entity = plastic;
        } else {
            return null;
        }
        MoldedPlasticData data = entity.getMoldedData().orElse(null);
        if (entity.isRemoved() || data == null || !MoldingProductTypes.isCrate(data.finalType())
            || !data.storageId().filter(session.storageId()::equals).isPresent()
            || player.position().distanceToSqr(entity.getBoundingBox().getCenter()) > 64) {
            return null;
        }
        return new Source(entity, session.storageId());
    }

    public static List<CrateBlockEntity> nearby(ServerPlayer player, long source) {
        StorageBlockEntity resolved = resolve(player, source);
        if (!(resolved instanceof Source current)) {
            return List.of();
        }
        List<CrateBlockEntity> crates = new ArrayList<>(CrateBlock.getNearbyCrates(player.level(), current.getBlockPos()));
        crates.removeIf(crate -> current.getId().equals(crate.getId()));
        crates.add(current);
        return crates;
    }

    public static List<CrateBlockEntity> includeNearby(Level level, BlockPos pos, List<CrateBlockEntity> vanilla) {
        if (level.isClientSide) {
            return vanilla;
        }
        List<CrateBlockEntity> crates = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (UniversalPlasticEntity entity : level.getEntitiesOfClass(UniversalPlasticEntity.class, new AABB(pos).inflate(1))) {
            addNearby(entity, crates, seen);
        }
        for (BlockPos neighbor : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
            if (level.hasChunkAt(neighbor) && level.getBlockEntity(neighbor) instanceof BondedEntityBlockEntity bonded
                && bonded.getOrCreateRenderEntity() instanceof UniversalPlasticEntity entity) {
                addNearby(entity, crates, seen);
            }
        }
        crates.addAll(vanilla);
        return crates;
    }

    private static void addNearby(UniversalPlasticEntity entity, List<CrateBlockEntity> crates, Set<UUID> seen) {
        if (entity.isRemoved()) {
            return;
        }
        entity.getMoldedItemHandler().crateStorage(true).ifPresent(storage -> {
            if (seen.add(storage.getId())) {
                crates.add(new Source(entity, storage.getId()));
            }
        });
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) {
        SESSIONS.clear();
    }

    private record Session(
        long source, ResourceKey<Level> dimension, UUID entityId, @Nullable BlockPos bondedPos, UUID storageId
    ) {
    }

    // 本体仓储通过 StorageBlockEntity 读取身份，此对象仅提供已经校验的身份，不进入世界方块实体表。
    private static final class Source extends CrateBlockEntity {
        private final UUID storageId;
        private final UniversalPlasticEntity entity;

        private Source(UniversalPlasticEntity entity, UUID storageId) {
            super(
                ModBlockEntities.CRATE.get(),
                BlockPos.containing(entity.getBoundingBox().getCenter()),
                ModBlocks.CRATE.getDefaultState()
            );
            this.entity = entity;
            this.storageId = storageId;
            this.setLevel(entity.level());
        }

        @Override
        public Holder<IStorageType<?>> getStorageTypeHolder() {
            return PlasticraftStorageTypes.MOLDED_CRATE;
        }

        @Override
        public IStorageType<?> getStorageType() {
            return PlasticraftStorageTypes.MOLDED_CRATE.get();
        }

        @Override
        public void refreshDispose() {
            this.entity.getMoldedItemHandler().crateStorage(false);
        }

        @Override
        public UUID getId() {
            return this.storageId;
        }
    }
}
