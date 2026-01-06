package net.minecraft.client.recipe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeAccess;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.Level;

/**
 * Helper class for looking up recipes that produce a given item.
 * Provides caching for performance and groups recipes by type.
 */
public class RecipeLookupHelper {
	// Cache for performance - maps Item to list of recipes
	private static final Map<Item, List<RecipeHolder<?>>> recipeCache = new HashMap<>();
	private static boolean cacheDirty = true;
	
	/**
	 * Find all recipes that produce the given item.
	 * Returns a map of RecipeType to list of recipes.
	 * Excludes campfire cooking recipes as they have no GUI.
	 */
	public static Map<RecipeType<?>, List<RecipeHolder<?>>> findRecipesFor(Item item, Level level) {
		if (level == null || level.recipeAccess() == null) {
			//System.out.println("DEBUG RecipeLookupHelper: level or recipeAccess is null");
			return Map.of();
		}
		
		if (cacheDirty) {
			//System.out.println("DEBUG RecipeLookupHelper: Cache is dirty, rebuilding...");
			rebuildCache(level);
			cacheDirty = false;
		}
		
		List<RecipeHolder<?>> allRecipes = recipeCache.getOrDefault(item, List.of());
		//System.out.println("DEBUG RecipeLookupHelper: Found " + allRecipes.size() + " total recipes for item " + item);
		
		Map<RecipeType<?>, List<RecipeHolder<?>>> byType = new HashMap<>();
		
		for (RecipeHolder<?> recipe : allRecipes) {
			RecipeType<?> type = recipe.value().getType();
			// Skip campfire cooking as it has no GUI
			if (type != RecipeType.CAMPFIRE_COOKING) {
				byType.computeIfAbsent(type, k -> new ArrayList<>()).add(recipe);
			}
		}
		
		//System.out.println("DEBUG RecipeLookupHelper: Returning " + byType.size() + " recipe types");
		return byType;
	}
	
	/**
	 * Rebuild the recipe cache from the recipe manager.
	 */
	private static void rebuildCache(Level level) {
		recipeCache.clear();
		
		//System.out.println("DEBUG RecipeLookupHelper: Starting cache rebuild");
		
		// On client side, we need to get RecipeManager from Minecraft
		net.minecraft.world.item.crafting.RecipeManager recipeManager = null;
		
		// Try to get RecipeManager from the integrated server (singleplayer)
		net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
		if (minecraft != null && minecraft.getSingleplayerServer() != null) {
			recipeManager = minecraft.getSingleplayerServer().getRecipeManager();
			//System.out.println("DEBUG RecipeLookupHelper: Got RecipeManager from singleplayer server");
		}
		// For multiplayer or if singleplayer server is not available yet,
		// try getting from level's recipeAccess if it's a RecipeManager
		else if (level.recipeAccess() instanceof net.minecraft.world.item.crafting.RecipeManager manager) {
			recipeManager = manager;
			//System.out.println("DEBUG RecipeLookupHelper: Got RecipeManager from level.recipeAccess()");
		}
		
		if (recipeManager == null) {
			//System.out.println("DEBUG RecipeLookupHelper: Could not get RecipeManager - cache will be empty");
			return;
		}
		
		Collection<RecipeHolder<?>> allRecipes = recipeManager.getRecipes();
		//System.out.println("DEBUG RecipeLookupHelper: RecipeManager has " + allRecipes.size() + " total recipes");
		
		ContextMap contextMap = net.minecraft.world.item.crafting.display.SlotDisplayContext.fromLevel(level);
		
		int cached = 0;
		for (RecipeHolder<?> recipeHolder : allRecipes) {
			Recipe<?> recipe = recipeHolder.value();
			
			// Try to get the result item from recipe displays
			ItemStack result = getRecipeResult(recipe, contextMap);
			if (!result.isEmpty()) {
				Item item = result.getItem();
				recipeCache.computeIfAbsent(item, k -> new ArrayList<>()).add(recipeHolder);
				cached++;
			}
		}
		//System.out.println("DEBUG RecipeLookupHelper: Cached " + cached + " recipes for " + recipeCache.size() + " items");
	}
	
	/**
	 * Extract the result ItemStack from a recipe.
	 * Different recipe types may store results differently.
	 */
	private static ItemStack getRecipeResult(Recipe<?> recipe, ContextMap contextMap) {
		try {
			// Get result from recipe displays
			if (recipe.display() != null && !recipe.display().isEmpty()) {
				RecipeDisplay display = recipe.display().get(0);
				if (display != null && display.result() != null) {
					SlotDisplay resultDisplay = display.result();
					List<ItemStack> stacks = resultDisplay.resolveForStacks(contextMap);
					if (!stacks.isEmpty()) {
						return stacks.get(0);
					}
				}
			}
		} catch (Exception e) {
			// Silently fail for problematic recipes
		}
		
		return ItemStack.EMPTY;
	}
	
	/**
	 * Mark cache as dirty when recipes are updated.
	 * Should be called when resource packs are reloaded.
	 */
	public static void invalidateCache() {
		cacheDirty = true;
	}
}
