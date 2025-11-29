package net.matt.quantize.gui.screen;

import net.matt.quantize.gui.menu.ElectricFurnaceMenu;
import net.matt.quantize.utils.GuiUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.world.entity.player.Inventory;

public class ElectricFurnaceScreen extends AbstractContainerScreen<ElectricFurnaceMenu> {
    private static final ResourceIdentifier TEXTURE =
            new ResourceIdentifier("textures/gui/furnace.png");

    public ElectricFurnaceScreen(ElectricFurnaceMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        guiGraphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        this.renderTooltip(guiGraphics, mouseX, mouseY);

        int energy = this.menu.getEnergy();
        int maxEnergy = this.menu.getMaxEnergy();

        // Render the energy bar
        int barX = (this.width - this.imageWidth) / 2 + 10; // Adjusted x position
        int barY = (this.height - this.imageHeight) / 2 + 16; // Adjusted y position
        int barWidth = 10;
        int barHeight = 35;

        GuiUtils.renderEnergyBar(guiGraphics, barX, barY, barWidth, barHeight, energy, maxEnergy, this.width, this.height, this.imageWidth, this.imageHeight);

        // Check if the mouse is hovering over the energy bar
        if (mouseX >= barX && mouseX <= barX + barWidth && mouseY >= barY && mouseY <= barY + barHeight) {
            // Render tooltip with energy information
            guiGraphics.renderTooltip(this.font, Component.literal("Energy: " + energy + " / " + maxEnergy), mouseX, mouseY);
        }

        //Render the progress bar
        int progress = this.menu.getProgress();
        int maxProgress = this.menu.getMaxProgress();

        int progressBarX = (this.width - this.imageWidth) / 2 + 76;
        int progressBarY = (this.height - this.imageHeight) / 2 + 35;
        int progressBarWidth = 22;
        int progressBarHeight = 8;

        //Quantize.LOGGER.info("Rendering Progress Bar: " + progress + "/" + maxProgress);

        GuiUtils.renderProgressBar(guiGraphics, progressBarX, progressBarY, progressBarWidth, progressBarHeight, progress, maxProgress, this.width, this.height, this.imageWidth, this.imageHeight);
    }
}