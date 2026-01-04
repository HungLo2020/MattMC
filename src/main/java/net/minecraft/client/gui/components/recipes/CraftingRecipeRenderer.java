package net.minecraft.client.gui.components.recipes;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;

/**
 * Renderer for crafting recipes using JEI-style layout.
 */
public class CraftingRecipeRenderer extends RecipeRenderer {
	private static final ResourceLocation JEI_SLOT = 
		ResourceLocation.withDefaultNamespace("textures/gui/jei/slot.png");
	private static final ResourceLocation JEI_OUTPUT_SLOT = 
		ResourceLocation.withDefaultNamespace("textures/gui/jei/output_slot.png");
	private static final ResourceLocation JEI_ARROW = 
		ResourceLocation.withDefaultNamespace("textures/gui/jei/recipe_arrow.png");
	
	// Layout for 3x3 crafting grid in JEI style
	private static final int GRID_START_X = 20;
	private static final int GRID_START_Y = 20;
	private static final int SLOT_SIZE = 18;
	private static final int RESULT_X = 110;
	private static final int RESULT_Y = 38;
	private static final int ARROW_X = 80;
	private static final int ARROW_Y = 38;
	
	private final ContextMap contextMap;
	private final Font font;
	
	public CraftingRecipeRenderer(ContextMap contextMap, Font font) {
		this.contextMap = contextMap;
		this.font = font;
	}
	
	@Override
	public void render(GuiGraphics guiGraphics, int x, int y, 
	                  RecipeHolder<?> recipe, int mouseX, int mouseY, long gameTime) {
		// Render recipe from display
		if (!recipe.value().display().isEmpty()) {
			RecipeDisplay display = recipe.value().display().get(0);
			
			// Check if it's a shaped recipe display
			if (display instanceof ShapedCraftingRecipeDisplay shapedDisplay) {
				renderShapedRecipe(guiGraphics, x, y, shapedDisplay, gameTime);
			} else {
				// Handle shapeless recipes (3x3 grid, ingredients in order)
				renderShapelessRecipe(guiGraphics, x, y, display, gameTime);
			}
			
			// Render arrow
			guiGraphics.blit(JEI_ARROW, x + ARROW_X, y + ARROW_Y, 0, 0, 22, 16, 22, 16);
			
			// Render result slot background
			guiGraphics.blit(JEI_OUTPUT_SLOT, x + RESULT_X, y + RESULT_Y, 0, 0, 26, 26, 26, 26);
			
			// Render result item
			SlotDisplay resultDisplay = display.result();
			ItemStack result = resultDisplay.resolveForFirstStack(contextMap);
			if (!result.isEmpty()) {
				guiGraphics.renderItem(result, x + RESULT_X + 5, y + RESULT_Y + 5);
				guiGraphics.renderItemDecorations(font, result, x + RESULT_X + 5, y + RESULT_Y + 5);
			}
		}
	}
	
	private void renderShapedRecipe(GuiGraphics guiGraphics, int x, int y, 
	                               ShapedCraftingRecipeDisplay display, long gameTime) {
		int width = display.width();
		int height = display.height();
		List<SlotDisplay> ingredients = display.ingredients();
		
		// Center smaller recipes in the 3x3 grid
		int offsetX = (3 - width) / 2;
		int offsetY = (3 - height) / 2;
		
		// Render all 9 slot backgrounds first
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 3; col++) {
				int slotX = x + GRID_START_X + col * SLOT_SIZE;
				int slotY = y + GRID_START_Y + row * SLOT_SIZE;
				guiGraphics.blit(JEI_SLOT, slotX, slotY, 0, 0, 18, 18, 18, 18);
			}
		}
		
		// Render ingredients in the grid
		for (int i = 0; i < ingredients.size(); i++) {
			SlotDisplay slotDisplay = ingredients.get(i);
			
			// Calculate position in grid
			int gridX = i % width;
			int gridY = i / width;
			
			int slotX = x + GRID_START_X + (gridX + offsetX) * SLOT_SIZE;
			int slotY = y + GRID_START_Y + (gridY + offsetY) * SLOT_SIZE;
			
			// Render ingredient (slot background already drawn)
			renderSlotDisplay(guiGraphics, slotDisplay, slotX + 1, slotY + 1, gameTime);
		}
	}
	
	private void renderShapelessRecipe(GuiGraphics guiGraphics, int x, int y, 
	                                   RecipeDisplay display, long gameTime) {
		// For shapeless recipes, we can't directly access ingredients from generic RecipeDisplay
		// We'll just render the result and indicate it's shapeless
		// A proper implementation would need ShapelessCraftingRecipeDisplay type
		
		// Render all 9 slot backgrounds as empty for now
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 3; col++) {
				int slotX = x + GRID_START_X + col * SLOT_SIZE;
				int slotY = y + GRID_START_Y + row * SLOT_SIZE;
				guiGraphics.blit(JEI_SLOT, slotX, slotY, 0, 0, 18, 18, 18, 18);
			}
		}
		
		// TODO: If Minecraft adds ShapelessCraftingRecipeDisplay, render ingredients here
	}
	
	private void renderSlotDisplay(GuiGraphics guiGraphics, SlotDisplay slotDisplay, 
	                              int slotX, int slotY, long gameTime) {
		List<ItemStack> stacks = slotDisplay.resolveForStacks(contextMap);
		if (!stacks.isEmpty()) {
			// Cycle through options
			int index = (int)((gameTime / 30) % stacks.size());
			ItemStack item = stacks.get(index);
			if (!item.isEmpty()) {
				guiGraphics.renderItem(item, slotX, slotY);
				guiGraphics.renderItemDecorations(font, item, slotX, slotY);
			}
		}
	}
	
	@Override
	public int getWidth() {
		return 176;  // JEI standard width
	}
	
	@Override
	public int getHeight() {
		return 125;  // JEI standard height
	}
}
