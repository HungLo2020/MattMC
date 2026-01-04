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
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;

/**
 * Renderer for crafting recipes using the crafting table GUI texture.
 */
public class CraftingRecipeRenderer extends RecipeRenderer {
	private static final ResourceLocation CRAFTING_TABLE_LOCATION = 
		ResourceLocation.withDefaultNamespace("textures/gui/container/crafting_table.png");
	private static final int GUI_WIDTH = 176;
	private static final int GUI_HEIGHT = 166;
	
	// Slot positions in the crafting table GUI
	private static final int GRID_START_X = 30;
	private static final int GRID_START_Y = 17;
	private static final int SLOT_SIZE = 18;
	private static final int RESULT_X = 124;
	private static final int RESULT_Y = 35;
	
	private final ContextMap contextMap;
	private final Font font;
	
	public CraftingRecipeRenderer(ContextMap contextMap, Font font) {
		this.contextMap = contextMap;
		this.font = font;
	}
	
	@Override
	public void render(GuiGraphics guiGraphics, int x, int y, 
	                  RecipeHolder<?> recipe, int mouseX, int mouseY, long gameTime) {
		// Draw crafting table background
		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, CRAFTING_TABLE_LOCATION, 
		                x, y, 0, 0, GUI_WIDTH, GUI_HEIGHT, 256, 256);
		
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
			
			// Render result
			SlotDisplay resultDisplay = display.result();
			ItemStack result = resultDisplay.resolveForFirstStack(contextMap);
			if (!result.isEmpty()) {
				guiGraphics.renderItem(result, x + RESULT_X, y + RESULT_Y);
				guiGraphics.renderItemDecorations(font, result, x + RESULT_X, y + RESULT_Y);
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
		
		// Render ingredients in the grid
		for (int i = 0; i < ingredients.size(); i++) {
			SlotDisplay slotDisplay = ingredients.get(i);
			
			// Calculate position in grid
			int gridX = i % width;
			int gridY = i / width;
			
			int slotX = x + GRID_START_X + (gridX + offsetX) * SLOT_SIZE;
			int slotY = y + GRID_START_Y + (gridY + offsetY) * SLOT_SIZE;
			
			// Render ingredient
			renderSlotDisplay(guiGraphics, slotDisplay, slotX, slotY, gameTime);
		}
	}
	
	private void renderShapelessRecipe(GuiGraphics guiGraphics, int x, int y, 
	                                   RecipeDisplay display, long gameTime) {
		// For shapeless recipes, get ingredients and render in a 3x3 grid
		if (display instanceof ShapelessCraftingRecipeDisplay shapelessDisplay) {
			List<SlotDisplay> ingredients = shapelessDisplay.ingredients();
			System.out.println("[CraftingRecipeRenderer] renderShapelessRecipe - " + ingredients.size() + " ingredients");
			
			// Render ingredients in a 3x3 grid layout
			for (int i = 0; i < ingredients.size() && i < 9; i++) {
				SlotDisplay slotDisplay = ingredients.get(i);
				System.out.println("[CraftingRecipeRenderer]   Ingredient " + i + " type: " + slotDisplay.getClass().getSimpleName());
				
				// Calculate position in 3x3 grid
				int gridX = i % 3;
				int gridY = i / 3;
				
				int slotX = x + GRID_START_X + gridX * SLOT_SIZE;
				int slotY = y + GRID_START_Y + gridY * SLOT_SIZE;
				
				// Render ingredient
				renderSlotDisplay(guiGraphics, slotDisplay, slotX, slotY, gameTime);
			}
		}
	}
	
	private void renderSlotDisplay(GuiGraphics guiGraphics, SlotDisplay slotDisplay, 
	                              int slotX, int slotY, long gameTime) {
		System.out.println("[CraftingRecipeRenderer] Rendering SlotDisplay: " + slotDisplay.getClass().getSimpleName());
		System.out.println("[CraftingRecipeRenderer]   contextMap: " + (contextMap != null ? "present" : "NULL"));
		
		List<ItemStack> stacks = slotDisplay.resolveForStacks(contextMap);
		System.out.println("[CraftingRecipeRenderer]   Resolved to " + stacks.size() + " stacks");
		
		if (!stacks.isEmpty()) {
			// Cycle through options
			int index = (int)((gameTime / 30) % stacks.size());
			ItemStack item = stacks.get(index);
			System.out.println("[CraftingRecipeRenderer]   Displaying item at index " + index + ": " + item);
			if (!item.isEmpty()) {
				guiGraphics.renderItem(item, slotX, slotY);
				guiGraphics.renderItemDecorations(font, item, slotX, slotY);
			}
		} else {
			System.out.println("[CraftingRecipeRenderer]   WARNING: No stacks resolved - slot will be empty!");
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
