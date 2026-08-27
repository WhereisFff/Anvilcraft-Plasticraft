package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.client.gui.PlasticraftCreativeTabContents;
import dev.anvilcraft.plasticraft.client.gui.PlasticraftCreativeTabState;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItemGroups;
import dev.anvilcraft.plasticraft.material.PlasticMaterial;
import dev.anvilcraft.lib.v2.registrum.util.CreativeTabSections;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.Optional;

/** 为塑料材料横幅增加整组十六色物品的即时展开与折叠。 */
@Mixin(CreativeModeInventoryScreen.class)
abstract class CreativeModeInventoryScreenMixin
    extends AbstractContainerScreen<CreativeModeInventoryScreen.ItemPickerMenu> {
    private static final int COLUMN_COUNT = 9;
    private static final int VISIBLE_ROW_COUNT = 5;
    private static final int CELL_SIZE = 18;
    private static final int GRID_LEFT = 9;
    private static final int GRID_TOP = 18;

    @Shadow
    private static CreativeModeTab selectedTab;

    @Shadow
    private float scrollOffs;

    @Shadow
    @Final
    private boolean displayOperatorCreativeTab;

    protected CreativeModeInventoryScreenMixin(
        CreativeModeInventoryScreen.ItemPickerMenu menu,
        Inventory inventory,
        Component title
    ) {
        super(menu, inventory, title);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void plasticraft$togglePlasticSection(
        double mouseX,
        double mouseY,
        int button,
        CallbackInfoReturnable<Boolean> callback
    ) {
        if (button != 0 || !this.plasticraft$isPlasticraftTab()) return;
        Optional<PlasticMaterial> material = this.plasticraft$materialAt(mouseX, mouseY);
        if (material.isEmpty()) return;
        PlasticraftCreativeTabState.toggle(material.get());
        this.plasticraft$refreshPlasticTab();
        callback.setReturnValue(true);
    }

    private boolean plasticraft$isPlasticraftTab() {
        if (selectedTab == null || selectedTab.getType() != CreativeModeTab.Type.CATEGORY) return false;
        return PlasticraftItemGroups.MAIN_ID.equals(BuiltInRegistries.CREATIVE_MODE_TAB.getKey(selectedTab));
    }

    private Optional<PlasticMaterial> plasticraft$materialAt(double mouseX, double mouseY) {
        int rowCount = (this.menu.items.size() + COLUMN_COUNT - 1) / COLUMN_COUNT - VISIBLE_ROW_COUNT;
        int scrollRow = Math.max((int) (this.scrollOffs * rowCount + 0.5F), 0);
        for (CreativeTabSections.PlacedSection placedSection
            : CreativeTabSections.placedSections(PlasticraftItemGroups.MAIN_ID)) {
            int row = placedSection.itemIndex() / COLUMN_COUNT - scrollRow;
            if (row < 0 || row >= VISIBLE_ROW_COUNT) continue;
            int bannerX = this.leftPos + GRID_LEFT - 1;
            int bannerY = this.topPos + GRID_TOP + row * CELL_SIZE - 1;
            int bannerWidth = placedSection.section().bannerLength() * CELL_SIZE;
            if (mouseX < bannerX || mouseX >= bannerX + bannerWidth
                || mouseY < bannerY || mouseY >= bannerY + CELL_SIZE) {
                continue;
            }
            return PlasticraftItemGroups.materialOfSection(placedSection.section());
        }
        return Optional.empty();
    }

    private void plasticraft$refreshPlasticTab() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || selectedTab == null) return;
        CreativeModeTab.ItemDisplayParameters parameters = new CreativeModeTab.ItemDisplayParameters(
            player.connection.enabledFeatures(),
            player.canUseGameMasterBlocks() && this.displayOperatorCreativeTab,
            player.level().registryAccess()
        );
        Collection<ItemStack> items = PlasticraftCreativeTabContents.rebuild(selectedTab, parameters);
        ((CreativeModeInventoryScreenAccessor) (Object) this).plasticraft$refreshCurrentTabContents(items);
    }
}
