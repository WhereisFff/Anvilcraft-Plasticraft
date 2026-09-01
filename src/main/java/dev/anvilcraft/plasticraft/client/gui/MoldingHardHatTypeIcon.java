package dev.anvilcraft.plasticraft.client.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.anvilcraft.plasticraft.molding.bake.BakedMoldingModel;
import dev.anvilcraft.plasticraft.molding.bake.MoldingModelBaker;
import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.anvilcraft.plasticraft.molding.type.MoldingHardHatIconModel;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 类型按钮使用模组内置的橙色安全帽几何,不读取世界蓝图库。
 * 玩家之后删除 {@code hard-hat.json} 也不会让按钮图标消失。
 */
public final class MoldingHardHatTypeIcon {
    private static final String MODEL_RESOURCE = "/assets/anvilcraftplasticraft/allay/hard_hat_type_icon.json";

    private MoldingHardHatTypeIcon() {
    }

    public static ItemStack stack() {
        return Holder.STACK;
    }

    private static ItemStack createStack() {
        try {
            return manufacture(loadBundledModel());
        } catch (RuntimeException exception) {
            AnvilcraftPlasticraft.LOGGER.error("Failed to build the bundled allay hard hat type icon", exception);
            return manufacture(MoldingHardHatIconModel.model());
        }
    }

    private static ItemStack manufacture(EditableMoldingModel model) {
        BakedMoldingModel baked = MoldingModelBaker.bake(model);
        int melt = Math.max(1, baked.analysis().minimumMeltMillibuckets());
        FluidStack fluid = new FluidStack(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get(), melt);
        PlasticMeltColor.set(fluid, DyeColor.ORANGE);
        MoldedPlasticData data = MoldedPlasticData.manufacture(model, baked, fluid, melt);
        if (!MoldingProductTypes.ALLAY_HARD_HAT_ID.equals(data.finalType())) {
            throw new IllegalStateException("Bundled hard hat icon model is not a valid allay hard hat");
        }
        ItemStack stack = PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack();
        MoldedPlasticData.set(stack, data);
        return stack;
    }

    private static EditableMoldingModel loadBundledModel() {
        try (InputStream stream = MoldingHardHatTypeIcon.class.getResourceAsStream(MODEL_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing hard hat type icon model " + MODEL_RESOURCE);
            }
            JsonElement json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return EditableMoldingModel.CODEC.parse(JsonOps.INSTANCE, json)
                .getOrThrow(message -> new IllegalStateException("Invalid hard hat type icon model: " + message));
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load the hard hat type icon model", exception);
        }
    }

    private static final class Holder {
        private static final ItemStack STACK = createStack();
    }
}
