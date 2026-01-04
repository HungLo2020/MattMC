package net.minecraft.client.gui.screens.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.recipes.CraftingRecipeRenderer;
import net.minecraft.client.gui.components.recipes.FurnaceRecipeRenderer;
import net.minecraft.client.gui.components.recipes.RecipeRenderer;
import net.minecraft.client.gui.components.recipes.SmithingRecipeRenderer;
import net.minecraft.client.gui.components.recipes.StonecutterRecipeRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

/**
 * Screen that displays recipe information in an overlay format.
 * Shows recipes using JEI-style GUI textures with tab support for multiple recipe types.
 */
@Environment(EnvType.CLIENT)
public class RecipeViewerScreen extends Screen {
	// JEI texture locations
	private static final ResourceLocation JEI_BACKGROUND = 
		ResourceLocation.withDefaultNamespace("textures/gui/jei/single_recipe_background.png");
	private static final ResourceLocation JEI_SLOT = 
		ResourceLocation.withDefaultNamespace("textures/gui/jei/slot.png");
	private static final ResourceLocation JEI_TAB_SELECTED = 
		ResourceLocation.withDefaultNamespace("textures/gui/jei/tab_selected.png");
	private static final ResourceLocation JEI_TAB_UNSELECTED = 
		ResourceLocation.withDefaultNamespace("textures/gui/jei/tab_unselected.png");
	private static final ResourceLocation JEI_ARROW_PREV = 
		ResourceLocation.withDefaultNamespace("textures/gui/jei/icons/arrow_previous.png");
	private static final ResourceLocation JEI_ARROW_NEXT = 
		ResourceLocation.withDefaultNamespace("textures/gui/jei/icons/arrow_next.png");
	
	private final Screen parentScreen;
	private final ItemStack targetItem;
	private final Map<RecipeType<?>, List<RecipeHolder<?>>> recipesByType;
	private final ContextMap contextMap;
	
	// Tab management
	private final List<RecipeType<?>> availableTabs;
	private int currentTabIndex = 0;
	
	// Recipe navigation (for multiple recipes of same type)
	private int currentRecipeIndex = 0;
	
	// Layout - JEI uses a consistent background size
	private int centerX;
	private int centerY;
	private static final int GUI_WIDTH = 176;  // Standard JEI recipe width
	private static final int GUI_HEIGHT = 125; // Standard JEI recipe height
	
	// Buttons
	private Button prevButton;
	private Button nextButton;
	
	// Animation
	private float fadeProgress = 0.0F;
	private static final float FADE_DURATION_TICKS = 3.0F;
	
	public RecipeViewerScreen(Screen parentScreen, ItemStack targetItem, 
	                         Map<RecipeType<?>, List<RecipeHolder<?>>> recipes,
	                         ContextMap contextMap) {
		super(Component.literal("Recipe Viewer"));
		this.parentScreen = parentScreen;
		this.targetItem = targetItem;
		this.recipesByType = recipes;
		this.availableTabs = new ArrayList<>(recipes.keySet());
		this.contextMap = contextMap;
	}
	
	@Override
	protected void init() {
		// Calculate centered position with fixed JEI dimensions
		this.centerX = (this.width - GUI_WIDTH) / 2;
		this.centerY = (this.height - GUI_HEIGHT) / 2;
		
		// Add navigation buttons if multiple recipes of current type
		updateNavigationButtons();
	}
	
	private void updateNavigationButtons() {
		// Clear existing buttons
		if (this.prevButton != null) {
			this.removeWidget(this.prevButton);
		}
		if (this.nextButton != null) {
			this.removeWidget(this.nextButton);
		}
		
		List<RecipeHolder<?>> currentRecipes = getCurrentRecipes();
		if (currentRecipes.size() > 1) {
			int buttonY = this.centerY + GUI_HEIGHT + 5;
			
			this.prevButton = this.addRenderableWidget(
				Button.builder(Component.literal("<"), button -> previousRecipe())
					.pos(this.centerX + GUI_WIDTH / 2 - 50, buttonY)
					.size(20, 20)
					.build()
			);
			
			this.nextButton = this.addRenderableWidget(
				Button.builder(Component.literal(">"), button -> nextRecipe())
					.pos(this.centerX + GUI_WIDTH / 2 + 30, buttonY)
					.size(20, 20)
					.build()
			);
		}
	}
	
	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		// Update fade animation
		if (this.fadeProgress < 1.0F) {
			this.fadeProgress = Math.min(this.fadeProgress + partialTick / FADE_DURATION_TICKS, 1.0F);
		}
		
		// Render semi-transparent background with fade
		int alpha = (int)(128 * this.fadeProgress);
		guiGraphics.fill(0, 0, this.width, this.height, (alpha << 24));
		
		// Get current recipe and render it
		RecipeType<?> currentType = availableTabs.get(currentTabIndex);
		List<RecipeHolder<?>> recipes = getCurrentRecipes();
		if (recipes.isEmpty()) {
			this.minecraft.setScreen(parentScreen);
			return;
		}
		
		RecipeHolder<?> recipe = recipes.get(currentRecipeIndex);
		
		// Render JEI-style background (64x64 texture scaled to GUI_WIDTH x GUI_HEIGHT)
		guiGraphics.blit(JEI_BACKGROUND, 
		                this.centerX, this.centerY, 
		                0, 0, 
		                GUI_WIDTH, GUI_HEIGHT, 
		                64, 64);
		
		// Render recipe using renderer
		RecipeRenderer renderer = getRendererForCurrentType();
		renderer.render(guiGraphics, this.centerX, this.centerY, recipe, mouseX, mouseY, 
		               this.minecraft.level.getGameTime());
		
		// Render tabs if multiple recipe types
		if (availableTabs.size() > 1) {
			renderTabs(guiGraphics, mouseX, mouseY);
		}
		
		// Render recipe counter if multiple recipes of same type
		if (getCurrentRecipes().size() > 1) {
			renderRecipeCounter(guiGraphics);
		}
		
		// Render buttons and other widgets
		super.render(guiGraphics, mouseX, mouseY, partialTick);
	}
	
	private void renderTabs(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		int tabWidth = 24;  // JEI tab texture is 24x24
		int tabHeight = 24;
		int tabY = this.centerY - tabHeight + 2;  // Overlap with main GUI
		int totalTabsWidth = availableTabs.size() * (tabWidth + 2); // Small gap between tabs
		int tabX = this.centerX + (GUI_WIDTH - totalTabsWidth) / 2;
		
		for (int i = 0; i < availableTabs.size(); i++) {
			RecipeType<?> type = availableTabs.get(i);
			boolean selected = (i == currentTabIndex);
			int x = tabX + i * (tabWidth + 2);
			
			// Draw JEI tab background
			ResourceLocation tabTexture = selected ? JEI_TAB_SELECTED : JEI_TAB_UNSELECTED;
			guiGraphics.blit(tabTexture, x, tabY, 0, 0, tabWidth, tabHeight, 24, 24);
			
			// Draw tab icon
			ItemStack icon = getIconForRecipeType(type);
			if (!icon.isEmpty()) {
				guiGraphics.renderItem(icon, x + 4, tabY + 4);
			}
			
			// Tooltip on hover
			if (isMouseOverTab(mouseX, mouseY, x, tabY, tabWidth, tabHeight)) {
				Component typeName = getRecipeTypeName(type);
				guiGraphics.setTooltipForNextFrame(this.font, typeName, mouseX, mouseY);
			}
		}
	}
	
	private boolean isMouseOverTab(int mouseX, int mouseY, int tabX, int tabY, int tabWidth, int tabHeight) {
		return mouseX >= tabX && mouseX < tabX + tabWidth && 
		       mouseY >= tabY && mouseY < tabY + tabHeight;
	}
	
	private void renderRecipeCounter(GuiGraphics guiGraphics) {
		int total = getCurrentRecipes().size();
		String text = String.format("Recipe %d of %d", currentRecipeIndex + 1, total);
		int textWidth = this.font.width(text);
		int textX = this.centerX + (GUI_WIDTH - textWidth) / 2;
		int textY = this.centerY - 15;  // Above the tabs
		
		// Draw background
		guiGraphics.fill(textX - 2, textY - 2, textX + textWidth + 2, textY + 10, 0x80000000);
		
		// Draw text
		guiGraphics.drawString(this.font, text, textX, textY, 0xFFFFFFFF, false);
	}
	
	@Override
	public boolean keyPressed(KeyEvent keyEvent) {
		// ESC closes the recipe viewer, not the parent screen
		if (keyEvent.key() == 256) { // ESC
			this.minecraft.setScreen(parentScreen);
			return true;
		}
		
		// Arrow keys for navigation
		if (keyEvent.key() == 262) { // Right arrow
			nextRecipe();
			return true;
		}
		if (keyEvent.key() == 263) { // Left arrow
			previousRecipe();
			return true;
		}
		
		// Number keys for tab selection
		if (keyEvent.key() >= 49 && keyEvent.key() <= 57) { // 1-9 keys
			int tabIndex = keyEvent.key() - 49;
			if (tabIndex < availableTabs.size()) {
				switchToTab(tabIndex);
				return true;
			}
		}
		
		return super.keyPressed(keyEvent);
	}
	
	@Override
	public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl) {
		double mouseX = mouseButtonEvent.x();
		double mouseY = mouseButtonEvent.y();
		
		// Check tab clicks
		if (availableTabs.size() > 1) {
			int tabWidth = 24;
			int tabHeight = 24;
			int tabY = this.centerY - tabHeight + 2;
			int totalTabsWidth = availableTabs.size() * (tabWidth + 2);
			int tabX = this.centerX + (GUI_WIDTH - totalTabsWidth) / 2;
			
			for (int i = 0; i < availableTabs.size(); i++) {
				int x = tabX + i * (tabWidth + 2);
				if (isMouseOverTab((int)mouseX, (int)mouseY, x, tabY, tabWidth, tabHeight)) {
					switchToTab(i);
					return true;
				}
			}
		}
		
		// Click outside recipe viewer closes it
		if (!isMouseOverRecipeArea(mouseX, mouseY)) {
			this.minecraft.setScreen(parentScreen);
			return true;
		}
		
		return super.mouseClicked(mouseButtonEvent, bl);
	}
	
	private boolean isMouseOverRecipeArea(double mouseX, double mouseY) {
		// Include tabs area and navigation buttons
		int minY = availableTabs.size() > 1 ? this.centerY - 30 : this.centerY;
		int maxY = getCurrentRecipes().size() > 1 ? this.centerY + GUI_HEIGHT + 35 : this.centerY + GUI_HEIGHT;
		
		return mouseX >= this.centerX && mouseX < this.centerX + GUI_WIDTH &&
		       mouseY >= minY && mouseY < maxY;
	}
	
	private void switchToTab(int tabIndex) {
		if (tabIndex != this.currentTabIndex) {
			this.currentTabIndex = tabIndex;
			this.currentRecipeIndex = 0; // Reset to first recipe of new type
			updateNavigationButtons();
		}
	}
	
	private void nextRecipe() {
		List<RecipeHolder<?>> recipes = getCurrentRecipes();
		if (recipes.size() > 1) {
			this.currentRecipeIndex = (this.currentRecipeIndex + 1) % recipes.size();
		}
	}
	
	private void previousRecipe() {
		List<RecipeHolder<?>> recipes = getCurrentRecipes();
		if (recipes.size() > 1) {
			this.currentRecipeIndex--;
			if (this.currentRecipeIndex < 0) {
				this.currentRecipeIndex = recipes.size() - 1;
			}
		}
	}
	
	private List<RecipeHolder<?>> getCurrentRecipes() {
		RecipeType<?> currentType = availableTabs.get(currentTabIndex);
		return recipesByType.getOrDefault(currentType, List.of());
	}
	
	private RecipeRenderer getRendererForCurrentType() {
		RecipeType<?> currentType = availableTabs.get(currentTabIndex);
		return getRendererForType(currentType);
	}
	
	private RecipeRenderer getRendererForType(RecipeType<?> type) {
		if (type == RecipeType.CRAFTING) {
			return new CraftingRecipeRenderer(contextMap, font);
		} else if (type == RecipeType.SMELTING || type == RecipeType.BLASTING || type == RecipeType.SMOKING) {
			return new FurnaceRecipeRenderer(contextMap, font);
		} else if (type == RecipeType.STONECUTTING) {
			return new StonecutterRecipeRenderer(contextMap, font);
		} else if (type == RecipeType.SMITHING) {
			return new SmithingRecipeRenderer(contextMap, font);
		}
		
		// Default fallback
		return new CraftingRecipeRenderer(contextMap, font);
	}
	
	private ItemStack getIconForRecipeType(RecipeType<?> type) {
		if (type == RecipeType.CRAFTING) return new ItemStack(Items.CRAFTING_TABLE);
		if (type == RecipeType.SMELTING) return new ItemStack(Items.FURNACE);
		if (type == RecipeType.BLASTING) return new ItemStack(Items.BLAST_FURNACE);
		if (type == RecipeType.SMOKING) return new ItemStack(Items.SMOKER);
		if (type == RecipeType.STONECUTTING) return new ItemStack(Items.STONECUTTER);
		if (type == RecipeType.SMITHING) return new ItemStack(Items.SMITHING_TABLE);
		return ItemStack.EMPTY;
	}
	
	private Component getRecipeTypeName(RecipeType<?> type) {
		if (type == RecipeType.CRAFTING) return Component.translatable("container.crafting");
		if (type == RecipeType.SMELTING) return Component.translatable("container.furnace");
		if (type == RecipeType.BLASTING) return Component.translatable("container.blast_furnace");
		if (type == RecipeType.SMOKING) return Component.translatable("container.smoker");
		if (type == RecipeType.STONECUTTING) return Component.translatable("container.stonecutter");
		if (type == RecipeType.SMITHING) return Component.translatable("container.upgrade");
		return Component.literal("Recipe");
	}
	
	@Override
	public boolean isPauseScreen() {
		return false; // Don't pause the game
	}
}
