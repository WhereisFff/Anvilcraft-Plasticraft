package dev.anvilcraft.plasticraft.init;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.entity.HardenedResinCauldronContents;
import dev.anvilcraft.plasticraft.entity.MoldedPlasticCauldronState;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** Plasticraft 物品数据组件注册入口。 */
public final class PlasticraftDataComponents {
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(
        Registries.DATA_COMPONENT_TYPE,
        AnvilcraftPlasticraft.MOD_ID
    );

    private static final DeferredRegister<EntityDataSerializer<?>> ENTITY_SERIALIZERS = DeferredRegister.create(
        NeoForgeRegistries.ENTITY_DATA_SERIALIZERS, AnvilcraftPlasticraft.MOD_ID
    );
    public static final EntityDataSerializer<CompoundTag> COMPOUND_TAG = new EntityDataSerializer<>() {
        @Override
        public StreamCodec<ByteBuf, CompoundTag> codec() {
            return ByteBufCodecs.COMPOUND_TAG;
        }

        @Override
        public CompoundTag copy(CompoundTag value) {
            return value.copy();
        }
    };

    public static final Supplier<DataComponentType<MoldedPlasticData>> MOLDED_PLASTIC = COMPONENTS.register(
        "molded_plastic",
        () -> DataComponentType.<MoldedPlasticData>builder()
            .persistent(MoldedPlasticData.CODEC)
            .networkSynchronized(MoldedPlasticData.STREAM_CODEC)
            .build()
    );

    /** 硬化树脂锅 Ctrl 中键复制时保留的物品、流体与功能状态。 */
    public static final Supplier<DataComponentType<HardenedResinCauldronContents>> HARDENED_RESIN_CAULDRON_CONTENTS =
        COMPONENTS.register(
            "hardened_resin_cauldron_contents",
            () -> DataComponentType.<HardenedResinCauldronContents>builder()
                .persistent(HardenedResinCauldronContents.CODEC)
                .networkSynchronized(HardenedResinCauldronContents.STREAM_CODEC)
                .build()
        );

    /** 成型塑料锅 Ctrl 中键复制时保留的出料口与点燃状态。 */
    public static final Supplier<DataComponentType<MoldedPlasticCauldronState>> MOLDED_PLASTIC_CAULDRON_STATE =
        COMPONENTS.register(
            "molded_plastic_cauldron_state",
            () -> DataComponentType.<MoldedPlasticCauldronState>builder()
                .persistent(MoldedPlasticCauldronState.CODEC)
                .networkSynchronized(MoldedPlasticCauldronState.STREAM_CODEC)
                .build()
        );

    private PlasticraftDataComponents() {
    }

    public static void register(IEventBus eventBus) {
        COMPONENTS.register(eventBus);
        ENTITY_SERIALIZERS.register("compound_tag", () -> COMPOUND_TAG);
        ENTITY_SERIALIZERS.register(eventBus);
    }
}
