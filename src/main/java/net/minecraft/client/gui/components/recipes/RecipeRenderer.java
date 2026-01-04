package net.minecraft.client.gui.components.recipes;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Base class for rendering recipes with their authentic GUI textures.
 */
public abstract class RecipeRenderer {
	/**
	 * Render the recipe at the given position.
	 * 
	 * @param guiGraphics The GUI graphics context
	 * @param x The x position (top-left corner)
	 * @param y The y position (top-left corner)
	 * @param recipe The recipe to render
	 * @param mouseX Mouse x position for hover effects
	 * @param mouseY Mouse y position for hover effects
	 * @param gameTime Current game time for ingredient cycling
	 */
	public abstract void render(GuiGraphics guiGraphics, int x, int y, 
	                            RecipeHolder<?> recipe, int mouseX, int mouseY, long gameTime);
	
	/**
	 * Get the width of this renderer's GUI.
	 */
	public abstract int getWidth();
	
	/**
	 * Get the height of this renderer's GUI.
	 */
	public abstract int getHeight();
	
	/**
	 * Check if the mouse is hovering over a specific area.
	 */
	protected boolean isHovering(int areaX, int areaY, int areaWidth, int areaHeight, 
	                            int mouseX, int mouseY) {
		return mouseX >= areaX && mouseX < areaX + areaWidth && 
		       mouseY >= areaY && mouseY < areaY + areaHeight;
	}
}
