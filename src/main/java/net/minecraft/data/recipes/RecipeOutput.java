package net.minecraft.data.recipes;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import org.jetbrains.annotations.Nullable;

public interface RecipeOutput {
	void accept(ResourceKey<Recipe<?>> resourceKey, Recipe<?> recipe, @Nullable AdvancementHolder advancementHolder);

	Advancement.Builder advancement();

	void includeRootAdvancement();
}
