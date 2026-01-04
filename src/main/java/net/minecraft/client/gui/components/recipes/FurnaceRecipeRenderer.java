package net.minecraft.client.gui.components.recipes;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;

/**
 * Renderer for furnace-type recipes using JEI-style layout.
 */
public class FurnaceRecipeRenderer extends RecipeRenderer {
	private static final ResourceLocation JEI_SLOT = 
		ResourceLocation.withDefaultNamespace("textures/gui/jei/slot.png");
	private static final ResourceLocation JEI_OUTPUT_SLOT = 
		ResourceLocation.withDefaultNamespace("textures/gui/jei/output_slot.png");
	private static final ResourceLocation JEI_ARROW = 
		ResourceLocation.withDefaultNamespace("textures/gui/jei/recipe_arrow.png");
	private static final ResourceLocation JEI_FLAME = 
		ResourceLocation.withDefaultNamespace("textures/gui/jei/icons/flame.png");
	
	// Layout for furnace recipe in JEI style
	private static final int INPUT_X = 40;
	private static final int INPUT_Y = 30;
	private static final int FUEL_X = 40;
	private static final int FUEL_Y = 60;
	private static final int RESULT_X = 110;
	private static final int RESULT_Y = 42;
	private static final int ARROW_X = 70;
	private static final int ARROW_Y = 42;
	private static final int FLAME_X = 40;
	private static final int FLAME_Y = 52;
	
	private final ContextMap contextMap;
	private final Font font;
	
	public FurnaceRecipeRenderer(ContextMap contextMap, Font font) {
		this.contextMap = contextMap;
		this.font = font;
	}
	
	@Override
	public void render(GuiGraphics guiGraphics, int x, int y, 
	                  RecipeHolder<?> recipe, int mouseX, int mouseY, long gameTime) {
		// Render ingredients and result from display
		if (!recipe.value().display().isEmpty()) {
			RecipeDisplay display = recipe.value().display().get(0);
			
			// Render input slot background
			guiGraphics.blit(JEI_SLOT, x + INPUT_X, y + INPUT_Y, 0, 0, 18, 18, 18, 18);
			
			// Render input ingredient
			if (display instanceof FurnaceRecipeDisplay furnaceDisplay) {
				SlotDisplay ingredientDisplay = furnaceDisplay.ingredient();
				List<ItemStack> ingredients = ingredientDisplay.resolveForStacks(contextMap);
				if (!ingredients.isEmpty()) {
					int index = (int)((gameTime / 30) % ingredients.size());
					ItemStack item = ingredients.get(index);
					if (!item.isEmpty()) {
						guiGraphics.renderItem(item, x + INPUT_X + 1, y + INPUT_Y + 1);
						guiGraphics.renderItemDecorations(font, item, x + INPUT_X + 1, y + INPUT_Y + 1);
					}
				}
			}
			
			// Render flame icon to indicate smelting
			guiGraphics.blit(JEI_FLAME, x + FLAME_X + 1, y + FLAME_Y, 0, 0, 14, 14, 14, 14);
			
			// Render arrow
			guiGraphics.blit(JEI_ARROW, x + ARROW_X, y + ARROW_Y, 0, 0, 22, 16, 22, 16);
			
			// Render result slot background
			guiGraphics.blit(JEI_OUTPUT_SLOT, x + RESULT_X, y + RESULT_Y, 0, 0, 26, 26, 26, 26);
			
			// Render result
			SlotDisplay resultDisplay = display.result();
			ItemStack result = resultDisplay.resolveForFirstStack(contextMap);
			if (!result.isEmpty()) {
				guiGraphics.renderItem(result, x + RESULT_X + 5, y + RESULT_Y + 5);
				guiGraphics.renderItemDecorations(font, result, x + RESULT_X + 5, y + RESULT_Y + 5);
			}
		}
	}
	
	@Override
	public int getWidth() {
		return 176;
	}
	
	@Override
	public int getHeight() {
		return 125;
	}
}
