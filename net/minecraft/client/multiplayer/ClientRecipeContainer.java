package net.minecraft.client.multiplayer;

import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.RecipeAccess;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.item.crafting.SelectableRecipe.SingleInputSet;

@Environment(EnvType.CLIENT)
public class ClientRecipeContainer implements RecipeAccess {
	private final Map<ResourceKey<RecipePropertySet>, RecipePropertySet> itemSets;
	private final SingleInputSet<StonecutterRecipe> stonecutterRecipes;

	public ClientRecipeContainer(Map<ResourceKey<RecipePropertySet>, RecipePropertySet> map, SingleInputSet<StonecutterRecipe> singleInputSet) {
		this.itemSets = map;
		this.stonecutterRecipes = singleInputSet;
	}

	public RecipePropertySet propertySet(ResourceKey<RecipePropertySet> resourceKey) {
		return (RecipePropertySet)this.itemSets.getOrDefault(resourceKey, RecipePropertySet.EMPTY);
	}

	public SingleInputSet<StonecutterRecipe> stonecutterRecipes() {
		return this.stonecutterRecipes;
	}
}
