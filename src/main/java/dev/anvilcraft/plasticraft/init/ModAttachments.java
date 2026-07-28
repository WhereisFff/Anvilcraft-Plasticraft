package dev.anvilcraft.plasticraft.init;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.BondedFallingChunkData;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesiveElasticMotion;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesiveTransit;
import dev.anvilcraft.plasticraft.entity.adhesive.EntityAdhesion;
import dev.anvilcraft.plasticraft.entity.adhesive.EntityBondState;
import dev.anvilcraft.plasticraft.entity.adhesive.SlidingAdhesionData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Plasticraft 的 NeoForge 数据附件注册入口。 */
public final class ModAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(
        NeoForgeRegistries.ATTACHMENT_TYPES,
        AnvilcraftPlasticraft.MOD_ID
    );

    public static final Supplier<AttachmentType<EntityAdhesion>> ENTITY_ADHESION = ATTACHMENTS.register(
        "entity_adhesion",
        () -> AttachmentType.builder(() -> new EntityAdhesion(
                BlockPos.ZERO,
                Direction.UP,
                ResourceLocation.fromNamespaceAndPath("minecraft", "air"),
                Vec3.ZERO,
                false
            ))
            .serialize(EntityAdhesion.CODEC)
            .sync(EntityAdhesion.STREAM_CODEC)
            .build()
    );

    public static final Supplier<AttachmentType<AdhesiveTransit>> ADHESIVE_TRANSIT = ATTACHMENTS.register(
        "adhesive_transit",
        () -> AttachmentType.builder(() -> new AdhesiveTransit(
                BlockPos.ZERO,
                Direction.UP,
                ResourceLocation.fromNamespaceAndPath("minecraft", "air"),
                Direction.DOWN,
                Optional.empty(),
                -1,
                Vec3.ZERO,
                List.of(Vec3.ZERO, Vec3.ZERO),
                0L,
                1,
                false,
                false,
                (byte) 0,
                (byte) 0
            ))
            .serialize(AdhesiveTransit.CODEC)
            .sync(AdhesiveTransit.STREAM_CODEC)
            .build()
    );

    public static final Supplier<AttachmentType<AdhesiveElasticMotion>> ADHESIVE_ELASTIC_MOTION = ATTACHMENTS.register(
        "adhesive_elastic_motion",
        () -> AttachmentType.builder(() -> new AdhesiveElasticMotion(Vec3.ZERO, 0L, 10, false))
            .serialize(AdhesiveElasticMotion.CODEC)
            .sync(AdhesiveElasticMotion.STREAM_CODEC)
            .build()
    );

    public static final Supplier<AttachmentType<EntityBondState>> ENTITY_BONDS = ATTACHMENTS.register(
        "entity_bonds",
        () -> AttachmentType.builder(() -> new EntityBondState(
                new UUID(0L, 0L),
                -1,
                Vec3.ZERO,
                false,
                List.of()
            ))
            .serialize(EntityBondState.CODEC)
            .sync(EntityBondState.STREAM_CODEC)
            .build()
    );

    public static final Supplier<AttachmentType<BondedFallingChunkData>> BONDED_FALLING_BLOCKS = ATTACHMENTS.register(
        "bonded_falling_blocks",
        () -> AttachmentType.builder(BondedFallingChunkData::empty)
            .serialize(BondedFallingChunkData.SERIALIZER)
            .sync(BondedFallingChunkData.STREAM_CODEC)
            .build()
    );

    public static final Supplier<AttachmentType<SlidingAdhesionData>> SLIDING_BLOCK_ADHESION = ATTACHMENTS.register(
        "sliding_block_adhesion",
        () -> AttachmentType.builder(SlidingAdhesionData::empty)
            .sync(SlidingAdhesionData.STREAM_CODEC)
            .build()
    );

    private ModAttachments() {
    }

    public static void register(IEventBus eventBus) {
        ATTACHMENTS.register(eventBus);
    }
}
