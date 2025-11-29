package net.matt.quantize.utils;

import net.minecraft.client.gui.GuiGraphics;

public class GuiUtils {
    public static void renderEnergyBar(
            GuiGraphics guiGraphics,
            int x, int y,
            int barWidth, int barHeight,
            int energy, int maxEnergy,
            int screenWidth, int screenHeight, int imageWidth, int imageHeight
    ) {
        // Default position on the left side of the GUI
        if (x == -1) {
            x = (screenWidth - imageWidth) / 2 + 10; // Default x position (left of the GUI)
        }
        if (y == -1) {
            y = (screenHeight - imageHeight) / 2 + 16; // Default y position (aligned with the GUI top)
        }
        barWidth = barWidth == -1 ? 10 : barWidth; // Default width (narrow for vertical bar)
        barHeight = barHeight == -1 ? 35 : barHeight; // Default height (tall for vertical bar)

        int energyHeight = (int) ((energy / (float) maxEnergy) * barHeight); // Calculate red bar height

        // Draw black background bar
        guiGraphics.fill(x, y, x + barWidth, y + barHeight, 0xFF561F1F); // Black color (ARGB)

        // Draw red energy bar
        guiGraphics.fill(x, y + (barHeight - energyHeight), x + barWidth, y + barHeight, 0xFFFF0000); // Red color (ARGB)
    }

    public static void renderProgressBar(
            GuiGraphics guiGraphics,
            int x, int y,
            int barWidth, int barHeight,
            int progress, int maxProgress,
            int screenWidth, int screenHeight, int imageWidth, int imageHeight
    ) {
        // Default position on the GUI
        if (x == -1) {
            x = (screenWidth - imageWidth) / 2 + 76; // Default x position (centered horizontally)
        }
        if (y == -1) {
            y = (screenHeight - imageHeight) / 2 + 35; // Default y position (aligned with the GUI middle)
        }
        barWidth = barWidth == -1 ? 22 : barWidth; // Default width (wide for horizontal bar)
        barHeight = barHeight == -1 ? 16 : barHeight; // Default height (short for horizontal bar)

        int progressWidth = (int) ((progress / (float) maxProgress) * barWidth); // Calculate filled bar width

        // Draw gray background bar
        guiGraphics.fill(x, y, x + barWidth, y + barHeight, 0xFF808080); // Gray color (ARGB)

        // Draw blue progress bar
        guiGraphics.fill(x, y, x + progressWidth, y + barHeight, 0xFF0000FF); // Blue color (ARGB)
    }
}