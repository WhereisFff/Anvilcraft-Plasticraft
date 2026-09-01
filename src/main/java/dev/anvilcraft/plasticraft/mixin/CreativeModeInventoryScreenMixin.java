package dev.anvilcraft.plasticraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.anvilcraft.lib.v2.registrum.util.CreativeTabSection;
import dev.anvilcraft.lib.v2.registrum.util.CreativeTabSections;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.client.gui.PlasticraftCreativeTabContents;
import dev.anvilcraft.plasticraft.client.gui.PlasticraftCreativeTabState;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItemGroups;
import dev.anvilcraft.plasticraft.material.PlasticMaterial;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.Optional;

/** 为塑料材料横幅增加整组十六色物品的即时展开与折叠。 */
@Mixin(value = CreativeModeInventoryScreen.class, priority = 1100)
abstract class CreativeModeInventoryScreenMixin
    extends AbstractContainerScreen<CreativeModeInventoryScreen.ItemPickerMenu> {
    private static final int COLUMN_COUNT = 9;
    private static final int VISIBLE_ROW_COUNT = 5;
    private static final int CELL_SIZE = 18;
    private static final int BANNER_FRAME_COUNT = 3;
    private static final int BANNER_WIDTH = CELL_SIZE * BANNER_FRAME_COUNT;
    private static final int BANNER_TEXTURE_HEIGHT = CELL_SIZE * BANNER_FRAME_COUNT;
    private static final int GRID_LEFT = 9;
    private static final int GRID_TOP = 18;

    @Shadow
    private static CreativeModeTab selectedTab;

    @Shadow
    private float scrollOffs;

    @Shadow
    @Final
    private boolean displayOperatorCreativeTab;

    @Unique
    @Nullable
    private PlasticMaterial plasticraft$hoveredMaterial;

    @Unique
    @Nullable
    private PlasticMaterial plasticraft$pressedMaterial;

    protected CreativeModeInventoryScreenMixin(
        CreativeModeInventoryScreen.ItemPickerMenu menu,
        Inventory inventory,
        Component title
    ) {
        super(menu, inventory, title);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void plasticraft$captureBannerPointer(
        GuiGraphics graphics,
        int mouseX,
        int mouseY,
        float partialTick,
        CallbackInfo callback
    ) {
        if (!this.plasticraft$isPlasticraftTab()) {
            this.plasticraft$hoveredMaterial = null;
            this.plasticraft$pressedMaterial = null;
            return;
        }
        this.plasticraft$hoveredMaterial = this.plasticraft$materialAt(mouseX, mouseY).orElse(null);
    }

    // 变体浮层覆盖横幅时由 AnvilLib 先处理点击，避免展开逻辑截断浮层交互。
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, order = 1100)
    private void plasticraft$togglePlasticSection(
        double mouseX,
        double mouseY,
        int button,
        CallbackInfoReturnable<Boolean> callback
    ) {
        if (button != 0 || !this.plasticraft$isPlasticraftTab()) return;
        Optional<PlasticMaterial> material = this.plasticraft$materialAt(mouseX, mouseY);
        if (material.isEmpty()) {
            this.plasticraft$pressedMaterial = null;
            return;
        }
        this.plasticraft$pressedMaterial = material.get();
        PlasticraftCreativeTabState.toggle(material.get());
        this.plasticraft$refreshPlasticTab();
        callback.setReturnValue(true);
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"))
    private void plasticraft$clearPressedBanner(
        double mouseX,
        double mouseY,
        int button,
        CallbackInfoReturnable<Boolean> callback
    ) {
        if (button == 0) this.plasticraft$pressedMaterial = null;
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void plasticraft$clearPressedBannerOnClose(CallbackInfo callback) {
        this.plasticraft$pressedMaterial = null;
        this.plasticraft$hoveredMaterial = null;
    }

    /** 将三行塑料横幅贴图映射为普通、悬停和按下状态，保留 AnvilLib 的文字绘制。 */
    @WrapOperation(
        method = "anvillib$renderSection",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blit("
                + "Lnet/minecraft/resources/ResourceLocation;IIFFIIII)V"
        ),
        require = 1
    )
    private void plasticraft$renderBannerFrame(
        GuiGraphics graphics,
        ResourceLocation texture,
        int x,
        int y,
        float u,
        float v,
        int width,
        int height,
        int textureWidth,
        int textureHeight,
        Operation<Void> original
    ) {
        Optional<PlasticMaterial> material = this.plasticraft$materialOfBanner(texture);
        if (material.isEmpty() || width != BANNER_WIDTH || height != CELL_SIZE
            || u != 0.0F || v != 0.0F) {
            original.call(
                graphics,
                texture,
                x,
                y,
                u,
                v,
                width,
                height,
                textureWidth,
                textureHeight
            );
            return;
        }

        int frame = material.get() == this.plasticraft$pressedMaterial
            && material.get() == this.plasticraft$hoveredMaterial ? 2
            : material.get() == this.plasticraft$hoveredMaterial ? 1 : 0;
        original.call(
            graphics,
            texture,
            x,
            y,
            u,
            v + frame * (float) CELL_SIZE,
            width,
            height,
            textureWidth,
            BANNER_TEXTURE_HEIGHT
        );
    }

    /** 让塑料横幅文字在未按下时上移一像素，按下时回到原始位置。 */
    @WrapOperation(
        method = "anvillib$renderSection",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/CreativeModeInventoryScreen;"
                + "anvillib$renderBannerText("
                + "Lnet/minecraft/client/gui/GuiGraphics;"
                + "Ldev/anvilcraft/lib/v2/registrum/util/CreativeTabSection;"
                + "IIIZ)V"
        ),
        require = 1
    )
    private void plasticraft$renderBannerTextPosition(
        CreativeModeInventoryScreen screen,
        GuiGraphics graphics,
        CreativeTabSection section,
        int bannerX,
        int bannerY,
        int bannerWidth,
        boolean hovered,
        Operation<Void> original
    ) {
        Optional<PlasticMaterial> material = PlasticraftItemGroups.materialOfSection(section);
        if (material.isEmpty()) {
            original.call(screen, graphics, section, bannerX, bannerY, bannerWidth, hovered);
            return;
        }
        int textOffset = material.get() == this.plasticraft$pressedMaterial && hovered ? 1 : 0;
        original.call(screen, graphics, section, bannerX, bannerY + textOffset, bannerWidth, hovered);
    }

    private boolean plasticraft$isPlasticraftTab() {
        if (selectedTab == null || selectedTab.getType() != CreativeModeTab.Type.CATEGORY) return false;
        return PlasticraftItemGroups.MAIN_ID.equals(BuiltInRegistries.CREATIVE_MODE_TAB.getKey(selectedTab));
    }

    @Unique
    private Optional<PlasticMaterial> plasticraft$materialOfBanner(ResourceLocation texture) {
        String path = texture.getPath();
        String prefix = "textures/gui/creative_tab/";
        String suffix = ".png";
        if (!AnvilcraftPlasticraft.MOD_ID.equals(texture.getNamespace())
            || !path.startsWith(prefix)
            || !path.endsWith(suffix)) {
            return Optional.empty();
        }
        return PlasticMaterial.fromKey(path.substring(prefix.length(), path.length() - suffix.length()));
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
