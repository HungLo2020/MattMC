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
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;

/**
 * Renderer for smithing recipes using the smithing table GUI texture.
 */
public class SmithingRecipeRenderer extends RecipeRenderer {
	private static final ResourceLocation SMITHING_LOCATION = 
		ResourceLocation.withDefaultNamespace("textures/gui/container/smithing.png");
	private static final int GUI_WIDTH = 176;
	private static final int GUI_HEIGHT = 75; // Cut off player inventory slots (was 166, then 90)
	
	// Slot positions in the smithing table GUI
	private static final int TEMPLATE_X = 8;
	private static final int TEMPLATE_Y = 45;
	private static final int BASE_X = 26;
	private static final int BASE_Y = 45;
	private static final int ADDITION_X = 44;
	private static final int ADDITION_Y = 45;
	private static final int RESULT_X = 98;
	private static final int RESULT_Y = 45;
	
	private final ContextMap contextMap;
	private final Font font;
	
	public SmithingRecipeRenderer(ContextMap contextMap, Font font) {
		this.contextMap = contextMap;
		this.font = font;
	}
	
	@Override
	public void render(GuiGraphics guiGraphics, int x, int y, 
	                  RecipeHolder<?> recipe, int mouseX, int mouseY, long gameTime) {
		// Draw smithing table background (only top portion, cutting off player inventory)
		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, SMITHING_LOCATION, 
		                x, y, 0, 0, GUI_WIDTH, GUI_HEIGHT, 256, 256);
		
		// Render ingredients from display
		if (!recipe.value().display().isEmpty()) {
			RecipeDisplay display = recipe.value().display().get(0);
			
			if (display instanceof SmithingRecipeDisplay smithingDisplay) {
				// Render template
				renderSlotDisplay(guiGraphics, smithingDisplay.template(), 
				                x + TEMPLATE_X, y + TEMPLATE_Y, gameTime);
				
				// Render base
				renderSlotDisplay(guiGraphics, smithingDisplay.base(), 
				                x + BASE_X, y + BASE_Y, gameTime);
				
				// Render addition
				renderSlotDisplay(guiGraphics, smithingDisplay.addition(), 
				                x + ADDITION_X, y + ADDITION_Y, gameTime);
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
	
	private void renderSlotDisplay(GuiGraphics guiGraphics, SlotDisplay slotDisplay, 
	                              int slotX, int slotY, long gameTime) {
		//System.out.println("[SmithingRecipeRenderer] Rendering SlotDisplay: " + slotDisplay.getClass().getSimpleName());
		List<ItemStack> stacks = slotDisplay.resolveForStacks(contextMap);
		//System.out.println("[SmithingRecipeRenderer]   Resolved to " + stacks.size() + " stacks");
		if (!stacks.isEmpty()) {
			int index = (int)((gameTime / 30) % stacks.size());
			ItemStack item = stacks.get(index);
			if (!item.isEmpty()) {
				guiGraphics.renderItem(item, slotX, slotY);
				guiGraphics.renderItemDecorations(font, item, slotX, slotY);
			}
		} else {
			//System.out.println("[SmithingRecipeRenderer]   WARNING: No stacks resolved - slot will be empty!");
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
	
	@Override
	public ItemStack getHoveredItem(int x, int y, int mouseX, int mouseY, 
	                               RecipeHolder<?> recipe, long gameTime) {
		if (recipe.value().display().isEmpty()) {
			return ItemStack.EMPTY;
		}
		
		RecipeDisplay display = recipe.value().display().get(0);
		
		if (display instanceof SmithingRecipeDisplay smithingDisplay) {
			// Check template slot
			int templateX = x + TEMPLATE_X;
			int templateY = y + TEMPLATE_Y;
			if (isHovering(templateX, templateY, 16, 16, mouseX, mouseY)) {
				return getItemFromSlotDisplay(smithingDisplay.template(), gameTime);
			}
			
			// Check base slot
			int baseX = x + BASE_X;
			int baseY = y + BASE_Y;
			if (isHovering(baseX, baseY, 16, 16, mouseX, mouseY)) {
				return getItemFromSlotDisplay(smithingDisplay.base(), gameTime);
			}
			
			// Check addition slot
			int additionX = x + ADDITION_X;
			int additionY = y + ADDITION_Y;
			if (isHovering(additionX, additionY, 16, 16, mouseX, mouseY)) {
				return getItemFromSlotDisplay(smithingDisplay.addition(), gameTime);
			}
		}
		
		// Check result slot
		SlotDisplay resultDisplay = display.result();
		int resultX = x + RESULT_X;
		int resultY = y + RESULT_Y;
		if (isHovering(resultX, resultY, 16, 16, mouseX, mouseY)) {
			ItemStack result = resultDisplay.resolveForFirstStack(contextMap);
			if (!result.isEmpty()) {
				return result;
			}
		}
		
		return ItemStack.EMPTY;
	}
	
	private ItemStack getItemFromSlotDisplay(SlotDisplay slotDisplay, long gameTime) {
		List<ItemStack> stacks = slotDisplay.resolveForStacks(contextMap);
		if (!stacks.isEmpty()) {
			int index = (int)((gameTime / 30) % stacks.size());
			ItemStack item = stacks.get(index);
			if (!item.isEmpty()) {
				return item;
			}
		}
		return ItemStack.EMPTY;
	}
}
