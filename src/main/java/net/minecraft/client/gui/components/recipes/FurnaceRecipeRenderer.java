package net.minecraft.client.gui.components.recipes;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;

/**
 * Renderer for furnace-type recipes (furnace, blast furnace, smoker).
 * Uses the appropriate GUI texture for each type.
 */
public class FurnaceRecipeRenderer extends RecipeRenderer {
	private static final int GUI_WIDTH = 176;
	private static final int GUI_HEIGHT = 166;
	
	// Slot positions in the furnace GUI
	private static final int INPUT_X = 56;
	private static final int INPUT_Y = 17;
	private static final int FUEL_X = 56;
	private static final int FUEL_Y = 53;
	private static final int RESULT_X = 116;
	private static final int RESULT_Y = 35;
	
	private final ResourceLocation texture;
	private final ContextMap contextMap;
	private final Font font;
	
	public FurnaceRecipeRenderer(ResourceLocation texture, ContextMap contextMap, Font font) {
		this.texture = texture;
		this.contextMap = contextMap;
		this.font = font;
	}
	
	@Override
	public void render(GuiGraphics guiGraphics, int x, int y, 
	                  RecipeHolder<?> recipe, int mouseX, int mouseY, long gameTime) {
		// Draw furnace background
		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, texture, 
		                x, y, 0, 0, GUI_WIDTH, GUI_HEIGHT, 256, 256);
		
		// Render ingredients and result from display
		if (!recipe.value().display().isEmpty()) {
			RecipeDisplay display = recipe.value().display().get(0);
			
			// Render input ingredient
			if (display instanceof FurnaceRecipeDisplay furnaceDisplay) {
				SlotDisplay ingredientDisplay = furnaceDisplay.ingredient();
				System.out.println("[FurnaceRecipeRenderer] Rendering ingredient SlotDisplay: " + ingredientDisplay.getClass().getSimpleName());
				List<ItemStack> ingredients = ingredientDisplay.resolveForStacks(contextMap);
				System.out.println("[FurnaceRecipeRenderer]   Resolved to " + ingredients.size() + " stacks");
				if (!ingredients.isEmpty()) {
					int index = (int)((gameTime / 30) % ingredients.size());
					ItemStack item = ingredients.get(index);
					if (!item.isEmpty()) {
						guiGraphics.renderItem(item, x + INPUT_X, y + INPUT_Y);
						guiGraphics.renderItemDecorations(font, item, x + INPUT_X, y + INPUT_Y);
					}
				} else {
					System.out.println("[FurnaceRecipeRenderer]   WARNING: No ingredients resolved - input slot will be empty!");
				}
			}
			
			// Render fuel indicator (coal)
			ItemStack fuel = new ItemStack(Items.COAL);
			guiGraphics.renderItem(fuel, x + FUEL_X, y + FUEL_Y);
			
			// Render result
			SlotDisplay resultDisplay = display.result();
			ItemStack result = resultDisplay.resolveForFirstStack(contextMap);
			if (!result.isEmpty()) {
				guiGraphics.renderItem(result, x + RESULT_X, y + RESULT_Y);
				guiGraphics.renderItemDecorations(font, result, x + RESULT_X, y + RESULT_Y);
			}
		}
	}
	
	@Override
	public int getWidth() {
		return GUI_WIDTH;
	}
	
	@Override
	public int getHeight() {
		return GUI_HEIGHT;
	}
}
