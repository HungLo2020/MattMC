package net.minecraft.client.gui.screens.recipebook;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.world.item.crafting.display.RecipeDisplay;

@Environment(EnvType.CLIENT)
public interface RecipeUpdateListener {
	void recipesUpdated();

	void fillGhostRecipe(RecipeDisplay recipeDisplay);
}
