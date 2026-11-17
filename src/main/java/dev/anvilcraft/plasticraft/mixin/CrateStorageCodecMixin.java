package dev.anvilcraft.plasticraft.mixin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.anvilcraft.plasticraft.molding.product.storage.MoldedPlasticCrateStorage;
import dev.dubhe.anvilcraft.saved.storage.CrateStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(CrateStorage.class)
abstract class CrateStorageCodecMixin {
    // 本体通过固定枚举分派板条箱。保存制品标记使读档后仍能拒绝嵌套，合成数据继续由本体编解码。
    @ModifyArg(
        method = "<clinit>",
        at = @At(value = "INVOKE", target = "Ldev/dubhe/anvilcraft/saved/storage/BaseStorage;withCrafting(Lcom/mojang/serialization/MapCodec;)Lcom/mojang/serialization/MapCodec;"),
        index = 0
    )
    private static MapCodec<CrateStorage> plasticraft$withMoldedType(MapCodec<CrateStorage> original) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
            original.forGetter((CrateStorage storage) -> storage),
            Codec.BOOL.optionalFieldOf("anvilcraftplasticraft_molded", false)
                .forGetter(storage -> storage instanceof MoldedPlasticCrateStorage)
        ).apply(instance, (storage, molded) -> {
            if (!molded) {
                return storage;
            }
            MoldedPlasticCrateStorage result = new MoldedPlasticCrateStorage(storage.getId());
            result.getItems().sync(storage.getItems());
            return result;
        }));
    }
}
