package net.matt.quantize.gui.screen;

import net.matt.quantize.Quantize;
import net.matt.quantize.gui.menu.CrafterMenu;
import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class CrafterScreen extends AbstractContainerScreen<CrafterMenu> {
    private static final ResourceLocation TEXTURE =
            new ResourceIdentifier("textures/gui/crafter.png");

    public CrafterScreen(CrafterMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 176; // vanilla container size
        this.imageHeight = 166;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTicks, int mouseX, int mouseY) {
        // This will be centered automatically using leftPos/topPos
        g.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, this.title, 66, 6, 0x404040, false);
        g.drawString(this.font, this.playerInventoryTitle, 8, 72, 0x404040, false); // sits above player inv

        Component subtitle = Component.translatable("container.quantize.pattern");
        g.drawString(this.font, subtitle, 66, 16, 0x7F7F7F, false);

        Component input = Component.translatable("container.quantize.input");
        g.drawString(this.font, input, 10, 62, 0x7F7F7F, false);

        Component output = Component.translatable("container.quantize.output");
        g.drawString(this.font, output, 116, 62, 0x7F7F7F, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(g);
        super.render(g, mouseX, mouseY, partialTicks);
        this.renderTooltip(g, mouseX, mouseY);
    }
}
