package net.minecraft.client.gui.screens.recipebook;

import java.util.List;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;

/**
 * Stub class for backward compatibility.
 * Recipe Book UI has been replaced by RecipeViewerScreen.
 * This class remains for backend recipe tracking only.
 */
public class RecipeCollection {
	private final List<RecipeDisplayEntry> recipes;

	public RecipeCollection(List<RecipeDisplayEntry> recipes) {
		this.recipes = recipes;
	}

	public List<RecipeDisplayEntry> getRecipes() {
		return this.recipes;
	}

	public boolean hasKnownDisplays() {
		return !this.recipes.isEmpty();
	}
}
