package net.minecraft.client.gui.screens.recipebook;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.item.crafting.ExtendedRecipeBookCategory;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;

/**
 * Stub enum for backward compatibility.
 * Recipe Book UI has been replaced by RecipeViewerScreen.
 * This enum remains for backend recipe categorization only.
 */
public enum SearchRecipeBookCategory {
	CRAFTING((recipeDisplayEntry) -> true),
	FURNACE((recipeDisplayEntry) -> true),
	BLAST_FURNACE((recipeDisplayEntry) -> true),
	SMOKER((recipeDisplayEntry) -> true);

	private final Predicate<RecipeDisplayEntry> filter;

	private SearchRecipeBookCategory(Predicate<RecipeDisplayEntry> predicate) {
		this.filter = predicate;
	}

	public boolean matches(RecipeDisplayEntry recipeDisplayEntry) {
		return this.filter.test(recipeDisplayEntry);
	}
	
	// Stub method for compatibility
	public List<ExtendedRecipeBookCategory> includedCategories() {
		return List.of();
	}
}
