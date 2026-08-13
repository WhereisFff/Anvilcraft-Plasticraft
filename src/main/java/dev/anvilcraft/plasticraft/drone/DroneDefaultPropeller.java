package dev.anvilcraft.plasticraft.drone;

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
 * 创造物品栏与配方展示使用的默认白色通用塑料螺旋桨。
 * 模型固化在模组资源中,不读取世界蓝图库,删除存档内的蓝图文件不影响显示。
 */
public final class DroneDefaultPropeller {
    private static final String MODEL_RESOURCE = "/assets/anvilcraftplasticraft/drone/default_propeller.json";
    private static volatile MoldedPlasticData cached;

    private DroneDefaultPropeller() {
    }

    public static MoldedPlasticData data() {
        MoldedPlasticData data = cached;
        if (data == null) {
            synchronized (DroneDefaultPropeller.class) {
                data = cached;
                if (data == null) {
                    data = manufacture();
                    cached = data;
                }
            }
        }
        return data;
    }

    /** 带默认螺旋桨数据的通用塑料物品堆,供创造条目与配方槽展示。 */
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
        return manufactured.withFunction(MoldingProductTypes.PROPELLER_ID, 0, manufactured.cavityMask());
    }

    private static EditableMoldingModel loadModel() {
        try (InputStream stream = DroneDefaultPropeller.class.getResourceAsStream(MODEL_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing default propeller model resource " + MODEL_RESOURCE);
            }
            JsonElement json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return EditableMoldingModel.CODEC.parse(JsonOps.INSTANCE, json)
                .getOrThrow(message -> new IllegalStateException("Invalid default propeller model: " + message));
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load default propeller model", exception);
        }
    }
}
