package dev.anvilcraft.plasticraft.allay;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.anvilcraft.plasticraft.molding.bake.BakedMoldingModel;
import dev.anvilcraft.plasticraft.molding.bake.MoldingModelBaker;
import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 测试与展示使用的默认白色悦灵安全帽。
 * 模型固化在模组资源中,不读取世界蓝图库。
 */
public final class AllayDefaultHardHat {
    private static final String MODEL_RESOURCE = "/assets/anvilcraftplasticraft/allay/default_hard_hat.json";
    private static volatile MoldedPlasticData cached;

    private AllayDefaultHardHat() {
    }

    public static MoldedPlasticData data() {
        MoldedPlasticData data = cached;
        if (data == null) {
            synchronized (AllayDefaultHardHat.class) {
                data = cached;
                if (data == null) {
                    data = manufacture();
                    cached = data;
                }
            }
        }
        return data;
    }

    public static ItemStack stack() {
        ItemStack stack = PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack();
        MoldedPlasticData.set(stack, data());
        return stack;
    }

    private static MoldedPlasticData manufacture() {
        EditableMoldingModel model = loadModel();
        BakedMoldingModel baked = MoldingModelBaker.bake(model);
        int amount = Math.max(250, (baked.analysis().volume() + 3) / 4);
        FluidStack melt = new FluidStack(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get(), amount);
        PlasticMeltColor.set(melt, DyeColor.WHITE);
        MoldedPlasticData manufactured = MoldedPlasticData.manufacture(model, baked, melt, amount);
        return manufactured.withFunction(MoldingProductTypes.ALLAY_HARD_HAT_ID, 0, manufactured.cavityMask());
    }

    private static EditableMoldingModel loadModel() {
        try (InputStream stream = AllayDefaultHardHat.class.getResourceAsStream(MODEL_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing default hard hat model resource " + MODEL_RESOURCE);
            }
            JsonElement json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return EditableMoldingModel.CODEC.parse(JsonOps.INSTANCE, json)
                .getOrThrow(message -> new IllegalStateException("Invalid default hard hat model: " + message));
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load default hard hat model", exception);
        }
    }
}
