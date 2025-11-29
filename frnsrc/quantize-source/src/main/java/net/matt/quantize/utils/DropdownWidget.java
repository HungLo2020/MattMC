package net.matt.quantize.utils;

import net.minecraft.client.Minecraft;
import net.matt.quantize.Quantize;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.List;

public class DropdownWidget extends AbstractWidget {
    private final List<String> options;
    private int selectedIndex;
    private boolean isExpanded;
    private final OnSelectCallback onSelect;

    public DropdownWidget(int x, int y, int width, int height, List<String> options, int initialIndex, OnSelectCallback onSelect) {
        super(x, y, width, height, Component.literal(options.get(initialIndex)));
        this.options = options;
        this.selectedIndex = initialIndex;
        this.isExpanded = false;
        this.onSelect = onSelect;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        Minecraft minecraft = Minecraft.getInstance();

        // Render the main button
        boolean isHovered = mouseX >= this.getX() && mouseX <= this.getX() + this.width &&
                mouseY >= this.getY() && mouseY <= this.getY() + this.height;
        int textureX = 0; // X coordinate for button texture
        int textureY = isHovered ? 46 : 66; // Y coordinate for button texture (hovered vs normal state)
        graphics.blit(AbstractWidget.WIDGETS_LOCATION, this.getX(), this.getY(), textureX, textureY, this.width, this.height, 256, 256);

        // Render the text on the main button
        graphics.drawCenteredString(minecraft.font, this.getMessage().getString(), this.getX() + this.width / 2, this.getY() + 5, 0xFFFFFF);

        // Render dropdown options if expanded
        if (isExpanded) {
            renderDropdownOptions(graphics, mouseX, mouseY);
        }
    }

    private void renderDropdownOptions(GuiGraphics graphics, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();

        graphics.pose().pushPose(); // Push a new rendering layer
        graphics.pose().translate(0, 0, 1000); // Move dropdown rendering to a higher Z layer

        for (int i = 0; i < options.size(); i++) {
            int optionYStart = this.getY() + this.height * (i + 1);
            int optionYEnd = optionYStart + this.height;

            // Highlight the option if the mouse is hovering over it
            boolean isHovered = mouseY >= optionYStart && mouseY <= optionYEnd &&
                    mouseX >= this.getX() && mouseX <= this.getX() + this.width;
            int textureX = 0;
            int textureY = isHovered ? 46 : 66;

            graphics.blit(AbstractWidget.WIDGETS_LOCATION, this.getX(), optionYStart, textureX, textureY, this.width, this.height, 256, 256);
            graphics.drawCenteredString(minecraft.font, options.get(i), this.getX() + this.width / 2, optionYStart + 5, 0xFFFFFF);
        }

        graphics.pose().popPose(); // Restore the previous rendering layer
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        // No-op implementation to satisfy the abstract method requirement
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Quantize.LOGGER.debug("DropdownWidget mouseClicked at: mouseX={}, mouseY={}, button={}", mouseX, mouseY, button);
        if (isExpanded) {
            for (int i = 0; i < options.size(); i++) {
                int optionYStart = this.getY() + this.height * (i + 1);
                int optionYEnd = optionYStart + this.height;
                if (mouseY >= optionYStart && mouseY <= optionYEnd &&
                        mouseX >= this.getX() && mouseX <= this.getX() + this.width) {
                    selectedIndex = i;
                    this.setMessage(Component.literal(options.get(selectedIndex)));
                    isExpanded = false;
                    if (onSelect != null) {
                        onSelect.onSelect(this, options.get(selectedIndex));
                    }
                    Quantize.LOGGER.debug("Option selected: {}", options.get(selectedIndex));
                    return true;
                }
            }
        } else {
            if (mouseX >= this.getX() && mouseX <= this.getX() + this.width &&
                    mouseY >= this.getY() && mouseY <= this.getY() + this.height) {
                isExpanded = true;
                Quantize.LOGGER.debug("Dropdown expanded");
                return true;
            }
        }
        return false;
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public String getSelectedOption() {
        return options.get(selectedIndex);
    }

    @FunctionalInterface
    public interface OnSelectCallback {
        void onSelect(DropdownWidget widget, String selectedOption);
    }
}