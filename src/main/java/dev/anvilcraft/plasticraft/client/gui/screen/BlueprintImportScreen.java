package dev.anvilcraft.plasticraft.client.gui.screen;

import dev.anvilcraft.plasticraft.client.blueprint.BlueprintClientFiles;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构蓝图文件导入界面:列出客户端导入目录中的文件,点击文件名直接上传;
 * 结果经聊天消息回报。使用原版控件,不依赖专用贴图。
 */
public class BlueprintImportScreen extends Screen {
    private static final int PAGE_SIZE = 7;
    private static final int ROW_HEIGHT = 24;

    private List<String> files = List.of();
    private final List<Button> fileButtons = new ArrayList<>();
    private int page;
    private Button previousPageButton;
    private Button nextPageButton;

    public BlueprintImportScreen() {
        super(Component.translatable("screen.anvilcraftplasticraft.blueprint_import.title"));
    }

    @Override
    protected void init() {
        this.files = BlueprintClientFiles.listImportableFiles();
        this.fileButtons.clear();
        int listTop = 40;
        int buttonWidth = Math.min(320, this.width - 40);
        int left = (this.width - buttonWidth) / 2;
        for (int row = 0; row < PAGE_SIZE; row++) {
            int index = row;
            Button button = Button.builder(Component.empty(), pressed -> this.importRow(index))
                .bounds(left, listTop + row * ROW_HEIGHT, buttonWidth, 20)
                .build();
            this.fileButtons.add(button);
            this.addRenderableWidget(button);
        }

        int bottom = listTop + PAGE_SIZE * ROW_HEIGHT + 8;
        this.previousPageButton = this.addRenderableWidget(Button.builder(
            Component.literal("<"),
            pressed -> this.turnPage(-1)
        ).bounds(left, bottom, 20, 20).build());
        this.nextPageButton = this.addRenderableWidget(Button.builder(
            Component.literal(">"),
            pressed -> this.turnPage(1)
        ).bounds(left + buttonWidth - 20, bottom, 20, 20).build());
        this.addRenderableWidget(Button.builder(
            Component.translatable("screen.anvilcraftplasticraft.blueprint_import.refresh"),
            pressed -> this.rebuildWidgets()
        ).bounds(left + 24, bottom, (buttonWidth - 48) / 2 - 2, 20).build());
        this.addRenderableWidget(Button.builder(
            Component.translatable("screen.anvilcraftplasticraft.blueprint_import.open_folder"),
            pressed -> BlueprintClientFiles.openFolder()
        ).bounds(left + 24 + (buttonWidth - 48) / 2 + 2, bottom, (buttonWidth - 48) / 2 - 2, 20).build());

        this.refreshPage();
    }

    private void turnPage(int delta) {
        int maxPage = Math.max(0, (this.files.size() - 1) / PAGE_SIZE);
        this.page = Math.clamp(this.page + delta, 0, maxPage);
        this.refreshPage();
    }

    private void refreshPage() {
        int maxPage = Math.max(0, (this.files.size() - 1) / PAGE_SIZE);
        this.page = Math.clamp(this.page, 0, maxPage);
        for (int row = 0; row < PAGE_SIZE; row++) {
            int index = this.page * PAGE_SIZE + row;
            Button button = this.fileButtons.get(row);
            if (index < this.files.size()) {
                button.setMessage(Component.literal(this.files.get(index)));
                button.visible = true;
                button.active = true;
            } else {
                button.setMessage(Component.empty());
                button.visible = false;
                button.active = false;
            }
        }
        this.previousPageButton.active = this.page > 0;
        this.nextPageButton.active = this.page < maxPage;
    }

    private void importRow(int row) {
        int index = this.page * PAGE_SIZE + row;
        if (index >= this.files.size()) return;
        if (BlueprintClientFiles.upload(this.files.get(index))) {
            this.onClose();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);
        if (this.files.isEmpty()) {
            graphics.drawCenteredString(
                this.font,
                Component.translatable("screen.anvilcraftplasticraft.blueprint_import.empty"),
                this.width / 2,
                this.height / 2,
                0xA0A0A0
            );
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
