package net.minecraft.client.gui.screens.recipebook;

import net.minecraft.world.item.crafting.display.RecipeDisplay;

/**
 * Stub interface for backward compatibility.
 * Recipe Book UI has been replaced by RecipeViewerScreen.
 * This interface remains for packet handling compatibility only.
 */
public interface RecipeUpdateListener {
	void recipesUpdated();
	
	// Stub method for packet handling
	default void fillGhostRecipe(RecipeDisplay recipeDisplay) {
		// No-op: ghost recipe filling is not used anymore
	}
}
