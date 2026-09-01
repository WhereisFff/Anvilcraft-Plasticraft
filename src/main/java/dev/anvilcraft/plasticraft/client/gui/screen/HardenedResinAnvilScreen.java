package dev.anvilcraft.plasticraft.client.gui.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.client.renderer.PlasticPaletteTintManager;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.inventory.HardenedResinAnvilMenu;
import dev.anvilcraft.plasticraft.item.PlasticItemData;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.anvilcraft.plasticraft.material.PlasticMaterial;
import dev.dubhe.anvilcraft.constant.Constant;
import dev.dubhe.anvilcraft.constant.SharedTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.ItemCombinerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundRenameItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

/** 使用浅色塑料背景的 AnvilCraft 风格界面。 */
public class HardenedResinAnvilScreen extends ItemCombinerScreen<HardenedResinAnvilMenu> {
    private static final float CLEAR_BASE_ALPHA = 0.45F;
    private static final ResourceLocation BACKGROUND = AnvilcraftPlasticraft.of(
        "textures/gui/background/hardend_resin_anvil.png"
    );
    private static final ResourceLocation UNIVERSAL_BACKGROUND_BASE = AnvilcraftPlasticraft.of(
        "textures/gui/background/universal_plastic_anvil_base.png"
    );
    private static final ResourceLocation UNIVERSAL_BACKGROUND_OVERLAY = AnvilcraftPlasticraft.of(
        "textures/gui/background/universal_plastic_anvil_overlay.png"
    );
    private static final ResourceLocation ENGINEERING_BACKGROUND_OVERLAY = AnvilcraftPlasticraft.of(
        "textures/gui/background/engineering_plastic_anvil_overlay.png"
    );
    private static final ResourceLocation HEAT_RESISTANT_BACKGROUND_OVERLAY = AnvilcraftPlasticraft.of(
        "textures/gui/background/heat_resistant_plastic_anvil_overlay.png"
    );
    private static final ResourceLocation CLEAR_BACKGROUND_OVERLAY = AnvilcraftPlasticraft.of(
        "textures/gui/background/clear_plastic_anvil_overlay.png"
    );
    private static final ResourceLocation ENGINEERING_BACKGROUND_BASE = AnvilcraftPlasticraft.of(
        "textures/gui/background/engineering_plastic_anvil_base.png"
    );
    private static final ResourceLocation HEAT_RESISTANT_BACKGROUND_BASE = AnvilcraftPlasticraft.of(
        "textures/gui/background/heat_resistant_plastic_anvil_base.png"
    );
    private static final ResourceLocation CLEAR_BACKGROUND_BASE = AnvilcraftPlasticraft.of(
        "textures/gui/background/clear_plastic_anvil_base.png"
    );
    private EditBox name;
    private final Player player;

    public HardenedResinAnvilScreen(HardenedResinAnvilMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, BACKGROUND);
        this.player = playerInventory.player;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
        this.titleLabelY = Constant.SCREEN_TITLE_Y;
    }

    @Override
    protected void subInit() {
        int left = (this.width - this.imageWidth) / 2;
        int top = (this.height - this.imageHeight) / 2;
        this.name = new EditBox(
            this.font,
            left + 62,
            top + 24,
            103,
            12,
            Component.translatable("container.repair")
        );
        this.name.setCanLoseFocus(false);
        this.name.setTextColor(-1);
        this.name.setTextColorUneditable(-1);
        this.name.setBordered(false);
        this.name.setMaxLength(50);
        this.name.setResponder(this::onNameChanged);
        this.name.setValue("");
        this.addWidget(this.name);
        this.setInitialFocus(this.name);
        this.name.setEditable(this.menu.getSlot(0).hasItem());
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        String value = this.name.getValue();
        this.init(minecraft, width, height);
        this.name.setValue(value);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.closeContainer();
        }
        if (this.name.keyPressed(keyCode, scanCode, modifiers) || this.name.canConsumeInput()) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void onNameChanged(String value) {
        Slot slot = this.menu.getSlot(0);
        if (!slot.hasItem()) return;
        String sentValue = value;
        if (!slot.getItem().has(DataComponents.CUSTOM_NAME)
            && value.equals(slot.getItem().getHoverName().getString())) {
            sentValue = "";
        }
        if (this.menu.setItemName(sentValue) && this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.connection.send(new ServerboundRenameItemPacket(sentValue));
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 4210752, false);
        int cost = this.menu.getCost();
        if (this.menu.onlyRenaming() || cost > 0) {
            Component component = this.menu.getSlot(2).hasItem()
                ? Component.translatable("container.repair.cost", cost)
                : null;
            int color = 8453920;
            if (component != null && !this.menu.getSlot(2).mayPickup(this.player)) color = 0xFF6060;
            if (component != null) {
                int x = this.imageWidth - 8 - this.font.width(component) - 2;
                graphics.fill(x - 2, 67, this.imageWidth - 8, 79, 0x4F000000);
                graphics.drawString(this.font, component, x, 69, color, false);
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        PlasticMaterial material = this.targetMaterial();
        if (this.isPlasticAnvilTarget()) {
            boolean transparent = material == PlasticMaterial.CLEAR;
            int tint = PlasticPaletteTintManager.INSTANCE.tint(material, this.targetColor());
            if (transparent) {
                // GUI 默认贴图绘制路径不会开启混合，透明塑料的 alpha 必须在这里显式启用
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
            }
            try {
                setTint(graphics, tint, transparent ? CLEAR_BASE_ALPHA : 1.0F);
                graphics.blit(
                    switch (material) {
                        case ENGINEERING -> ENGINEERING_BACKGROUND_BASE;
                        case HEAT_RESISTANT -> HEAT_RESISTANT_BACKGROUND_BASE;
                        case CLEAR -> CLEAR_BACKGROUND_BASE;
                        case UNIVERSAL -> UNIVERSAL_BACKGROUND_BASE;
                    },
                    this.leftPos,
                    this.topPos,
                    0,
                    0,
                    this.imageWidth,
                    this.imageHeight
                );
                graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
                graphics.blit(
                    switch (material) {
                        case ENGINEERING -> ENGINEERING_BACKGROUND_OVERLAY;
                        case HEAT_RESISTANT -> HEAT_RESISTANT_BACKGROUND_OVERLAY;
                        case CLEAR -> CLEAR_BACKGROUND_OVERLAY;
                        case UNIVERSAL -> UNIVERSAL_BACKGROUND_OVERLAY;
                    },
                    this.leftPos,
                    this.topPos,
                    0,
                    0,
                    this.imageWidth,
                    this.imageHeight
                );
            } finally {
                graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
                if (transparent) {
                    RenderSystem.defaultBlendFunc();
                    RenderSystem.disableBlend();
                }
            }
        } else {
            super.renderBg(graphics, partialTick, mouseX, mouseY);
        }
        ResourceLocation texture = this.menu.getSlot(0).getItem().isEmpty()
            ? SharedTextures.TEXT_FIELD_DISABLE
            : SharedTextures.TEXT_FIELD;
        graphics.blit(texture, this.leftPos + 59, this.topPos + 20, 0, 0, 110, 16, 110, 16);
    }

    private static void setTint(GuiGraphics graphics, int tint, float alpha) {
        graphics.setColor(
            (tint >> 16 & 0xFF) / 255.0F,
            (tint >> 8 & 0xFF) / 255.0F,
            (tint & 0xFF) / 255.0F,
            alpha
        );
    }

    private boolean isPlasticAnvilTarget() {
        if (this.menu.isPlasticAnvilTarget()) return true;
        if (this.menu.bondedBlockPos() != null) {
            if (!(this.player.level().getBlockEntity(this.menu.bondedBlockPos())
                instanceof BondedEntityBlockEntity bonded)) return false;
            return bonded.isMoldedAnvil() || MoldedPlasticData.get(bonded.getStoredDropStack()).isPresent();
        }
        Entity entity = this.player.level().getEntity(this.menu.entityId());
        return entity instanceof UniversalPlasticEntity;
    }

    private DyeColor targetColor() {
        if (this.menu.bondedBlockPos() != null
            && this.player.level().getBlockEntity(this.menu.bondedBlockPos())
                instanceof BondedEntityBlockEntity bonded) {
            return colorOf(bonded.getStoredDropStack());
        }
        Entity entity = this.player.level().getEntity(this.menu.entityId());
        if (entity instanceof UniversalPlasticEntity universal) {
            return universal.getMoldedData()
                .map(data -> PlasticMeltColor.get(data.material()))
                .orElseGet(() -> colorOf(universal.getDropStack()));
        }
        if (entity instanceof AbstractPlasticEntity plastic) return colorOf(plastic.getDropStack());
        return DyeColor.WHITE;
    }

    private PlasticMaterial targetMaterial() {
        if (this.menu.bondedBlockPos() != null
            && this.player.level().getBlockEntity(this.menu.bondedBlockPos())
                instanceof BondedEntityBlockEntity bonded) {
            return materialOf(bonded.getStoredDropStack());
        }
        Entity entity = this.player.level().getEntity(this.menu.entityId());
        if (entity instanceof UniversalPlasticEntity universal) {
            return universal.getMoldedData()
                .flatMap(data -> PlasticMaterial.fromMelt(data.material()))
                .orElseGet(() -> materialOf(universal.getDropStack()));
        }
        if (entity instanceof AbstractPlasticEntity plastic) return materialOf(plastic.getDropStack());
        return PlasticMaterial.UNIVERSAL;
    }

    private static DyeColor colorOf(ItemStack stack) {
        return MoldedPlasticData.get(stack)
            .map(data -> PlasticMeltColor.get(data.material()))
            .orElseGet(() -> PlasticMeltColor.get(stack));
    }

    private static PlasticMaterial materialOf(ItemStack stack) {
        PlasticMaterial molded = MoldedPlasticData.get(stack)
            .flatMap(data -> PlasticMaterial.fromMelt(data.material()))
            .orElse(null);
        if (molded != null) return molded;
        return PlasticMaterial.fromKey(PlasticItemData.getMaterial(stack)).orElse(PlasticMaterial.UNIVERSAL);
    }

    @Override
    public void renderFg(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.name.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderErrorIcon(GuiGraphics graphics, int x, int y) {
        if ((this.menu.getSlot(0).hasItem() || this.menu.getSlot(1).hasItem())
            && !this.menu.getSlot(this.menu.getResultSlot()).hasItem()) {
            graphics.blit(SharedTextures.ERROR_SPRITE, x + 103, y + 47, 0, 0, 16, 16, 16, 16);
        }
    }

    @Override
    public void slotChanged(AbstractContainerMenu container, int slotIndex, ItemStack stack) {
        if (slotIndex == 0) {
            this.name.setValue(stack.isEmpty() ? "" : stack.getHoverName().getString());
            this.name.setEditable(!stack.isEmpty());
            this.setFocused(this.name);
        }
    }
}
