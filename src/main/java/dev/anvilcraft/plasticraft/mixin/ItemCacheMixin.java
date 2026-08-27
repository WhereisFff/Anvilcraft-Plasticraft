package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.lib.v2.recipe.cache.ItemCache;
import dev.anvilcraft.lib.v2.recipe.cache.item.ICacheElement;
import dev.anvilcraft.lib.v2.recipe.cache.item.ItemHandlerCacheElement;
import dev.anvilcraft.plasticraft.entity.PlasticCauldron;
import dev.anvilcraft.plasticraft.recipe.CauldronImpactRecipeProcessor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** 实体锅配方始终按锅格缓存库存，不能让成型制品的动态外包围盒改变匹配范围。 */
@Mixin(ItemCache.class)
abstract class ItemCacheMixin {
    private static final Vec3 RECIPE_CELL_RANGE = new Vec3(1.0D, 1.0D, 1.0D);

    @Shadow
    @Final
    private Set<ICacheElement> inputs;

    @Shadow
    @Final
    private Set<ICacheElement> outputs;

    @Inject(
        method = "toElement(Ldev/anvilcraft/lib/v2/recipe/cache/ItemCache;Lnet/minecraft/world/entity/Entity;)Ljava/util/Map$Entry;",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void plasticraft$anchorActiveCauldron(
        ItemCache cache,
        Entity entity,
        CallbackInfoReturnable<Map.Entry<Set<ICacheElement>, Set<ICacheElement>>> cir
    ) {
        if (!(entity instanceof PlasticCauldron cauldron)
            || cauldron != plasticraft$activeTarget(cache)) {
            return;
        }
        cir.setReturnValue(plasticraft$cauldronElements(cache, cauldron));
    }

    @Inject(method = "grow", at = @At("TAIL"))
    private void plasticraft$includeActiveCauldron(
        Vec3 pos,
        Vec3 range,
        CallbackInfo ci
    ) {
        ItemCache cache = (ItemCache) (Object) this;
        PlasticCauldron cauldron = plasticraft$activeTarget(cache);
        if (cauldron == null) return;
        Map.Entry<Set<ICacheElement>, Set<ICacheElement>> elements = plasticraft$cauldronElements(cache, cauldron);
        this.inputs.addAll(elements.getKey());
        this.outputs.addAll(elements.getValue());
    }

    private static PlasticCauldron plasticraft$activeTarget(ItemCache cache) {
        PlasticCauldron target = CauldronImpactRecipeProcessor.activeRecipeTarget();
        if (target == null || target.isRemoved() || target.level() != cache.getLevel()) return null;
        return CauldronImpactRecipeProcessor.activeRecipeTargetCell() == null ? null : target;
    }

    private static Map.Entry<Set<ICacheElement>, Set<ICacheElement>> plasticraft$cauldronElements(
        ItemCache cache,
        PlasticCauldron cauldron
    ) {
        BlockPos cell = CauldronImpactRecipeProcessor.activeRecipeTargetCell();
        if (cell == null) return Map.entry(Set.of(), Set.of());
        Vec3 anchor = cell.getCenter();
        Set<ICacheElement> inputs = new HashSet<>();
        Set<ICacheElement> outputs = new HashSet<>();
        IItemHandler input = cauldron.getInput();
        for (int slot = 0; slot < input.getSlots(); slot++) {
            inputs.add(new ItemHandlerCacheElement(cache, input, slot, anchor, RECIPE_CELL_RANGE));
        }
        IItemHandler output = cauldron.getOutput();
        for (int slot = 0; slot < output.getSlots(); slot++) {
            outputs.add(new ItemHandlerCacheElement(cache, output, slot, anchor, RECIPE_CELL_RANGE));
        }
        return Map.entry(inputs, outputs);
    }
}
