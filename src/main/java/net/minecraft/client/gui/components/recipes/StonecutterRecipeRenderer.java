package net.minecraft.client.gui.components.recipes;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;

/**
 * Renderer for stonecutter recipes using the stonecutter GUI texture.
 */
public class StonecutterRecipeRenderer extends RecipeRenderer {
	private static final ResourceLocation STONECUTTER_LOCATION = 
		ResourceLocation.withDefaultNamespace("textures/gui/container/stonecutter.png");
	private static final int GUI_WIDTH = 176;
	private static final int GUI_HEIGHT = 75; // Cut off player inventory slots (was 166, then 90)
	
	// Slot positions in the stonecutter GUI
	private static final int INPUT_X = 20;
	private static final int INPUT_Y = 33;
	private static final int RESULT_X = 143;
	private static final int RESULT_Y = 33;
	
	private final ContextMap contextMap;
	private final Font font;
	
	public StonecutterRecipeRenderer(ContextMap contextMap, Font font) {
		this.contextMap = contextMap;
		this.font = font;
	}
	
	@Override
	public void render(GuiGraphics guiGraphics, int x, int y, 
	                  RecipeHolder<?> recipe, int mouseX, int mouseY, long gameTime) {
		// Draw stonecutter background (only top portion, cutting off player inventory)
		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, STONECUTTER_LOCATION, 
		                x, y, 0, 0, GUI_WIDTH, GUI_HEIGHT, 256, 256);
		
		// Render ingredients and result from display
		if (!recipe.value().display().isEmpty()) {
			RecipeDisplay display = recipe.value().display().get(0);
			
			// Render input ingredient
			if (display instanceof StonecutterRecipeDisplay stonecutterDisplay) {
				SlotDisplay ingredientDisplay = stonecutterDisplay.input();
				System.out.println("[StonecutterRecipeRenderer] Rendering ingredient SlotDisplay: " + ingredientDisplay.getClass().getSimpleName());
				List<ItemStack> ingredients = ingredientDisplay.resolveForStacks(contextMap);
				System.out.println("[StonecutterRecipeRenderer]   Resolved to " + ingredients.size() + " stacks");
				if (!ingredients.isEmpty()) {
					int index = (int)((gameTime / 30) % ingredients.size());
					ItemStack item = ingredients.get(index);
					if (!item.isEmpty()) {
						guiGraphics.renderItem(item, x + INPUT_X, y + INPUT_Y);
						guiGraphics.renderItemDecorations(font, item, x + INPUT_X, y + INPUT_Y);
					}
				} else {
					System.out.println("[StonecutterRecipeRenderer]   WARNING: No ingredients resolved - input slot will be empty!");
				}
			}
			
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
