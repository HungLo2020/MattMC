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
 * Shows recipes using authentic Minecraft GUI textures with tab support for multiple recipe types.
 */
@Environment(EnvType.CLIENT)
public class RecipeViewerScreen extends Screen {
	private final Screen parentScreen;
	private final ItemStack targetItem;
	private final Map<RecipeType<?>, List<RecipeHolder<?>>> recipesByType;
	private final ContextMap contextMap;
	private final JeiPanel parentJeiPanel; // Store reference to parent's JEI panel
	
	// Tab management
	private final List<RecipeType<?>> availableTabs;
	private int currentTabIndex = 0;
	
	// Recipe navigation (for multiple recipes of same type)
	private int currentRecipeIndex = 0;
	
	// Hover tracking for recipe lookup
	private ItemStack lastHoveredItem = ItemStack.EMPTY;
	
	// Layout
	private int centerX;
	private int centerY;
	private int guiWidth;
	private int guiHeight;
	
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
		
		// Get JEI panel from parent if it's an AbstractContainerScreen
		if (parentScreen instanceof AbstractContainerScreen<?> containerScreen) {
			this.parentJeiPanel = containerScreen.jeiPanel;
		} else if (parentScreen instanceof RecipeViewerScreen recipeViewerScreen) {
			// If parent is another RecipeViewerScreen, get its JEI panel reference
			this.parentJeiPanel = recipeViewerScreen.parentJeiPanel;
		} else {
			this.parentJeiPanel = null;
		}
	}
	
	@Override
	protected void init() {
		// Get current renderer to determine GUI size
		RecipeRenderer renderer = getRendererForCurrentType();
		this.guiWidth = renderer.getWidth();
		this.guiHeight = renderer.getHeight();
		
		// Calculate centered position
		this.centerX = (this.width - this.guiWidth) / 2;
		this.centerY = (this.height - this.guiHeight) / 2;
		
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
			int buttonY = this.centerY + this.guiHeight + 10;
			
			// Position buttons further apart to not cover the recipe counter text
			this.prevButton = this.addRenderableWidget(
				Button.builder(Component.literal("<"), button -> previousRecipe())
					.pos(this.centerX + this.guiWidth / 2 - 70, buttonY)
					.size(20, 20)
					.build()
			);
			
			this.nextButton = this.addRenderableWidget(
				Button.builder(Component.literal(">"), button -> nextRecipe())
					.pos(this.centerX + this.guiWidth / 2 + 50, buttonY)
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
		System.out.println("[RecipeViewerScreen] Rendering recipe: " + recipe.id());
		System.out.println("[RecipeViewerScreen]   Recipe type: " + currentType);
		System.out.println("[RecipeViewerScreen]   Recipe displays: " + recipe.value().display().size());
		
		// Render recipe (no scaling for simplicity)
		RecipeRenderer renderer = getRendererForCurrentType();
		System.out.println("[RecipeViewerScreen]   Using renderer: " + renderer.getClass().getSimpleName());
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
		
		// Render JEI panel if available from parent
		if (this.parentJeiPanel != null) {
			this.parentJeiPanel.render(guiGraphics, mouseX, mouseY, partialTick);
		}
		
		// Render JEI panel tooltips first (has priority over recipe tooltips)
		// JEI panel will handle its own hover detection and tooltip rendering
		if (this.parentJeiPanel != null) {
			this.parentJeiPanel.renderTooltip(guiGraphics, mouseX, mouseY, this);
			
			// Check if JEI has a hovered item - if so, skip recipe viewer tooltip
			ItemStack jeiHoveredItem = this.parentJeiPanel.getHoveredItem();
			if (!jeiHoveredItem.isEmpty()) {
				// Store JEI hovered item for 'R' key functionality
				this.lastHoveredItem = jeiHoveredItem;
				return; // JEI tooltip is already rendered, don't render recipe tooltip
			}
		}
		
		// Render tooltips for hovered items in recipe viewer (only if not hovering over JEI)
		ItemStack hoveredItem = renderer.getHoveredItem(this.centerX, this.centerY, mouseX, mouseY, 
		                                               recipe, this.minecraft.level.getGameTime());
		this.lastHoveredItem = hoveredItem; // Store for recipe lookup with 'R' key
		if (!hoveredItem.isEmpty()) {
			guiGraphics.setTooltipForNextFrame(this.font, getTooltipFromItem(this.minecraft, hoveredItem), 
			                                   hoveredItem.getTooltipImage(), mouseX, mouseY);
		}
	}
	
	private void renderTabs(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		int tabWidth = 28;
		int tabHeight = 28;
		int tabY = this.centerY - tabHeight - 2;
		int totalTabsWidth = availableTabs.size() * tabWidth;
		int tabX = this.centerX + (this.guiWidth - totalTabsWidth) / 2;
		
		for (int i = 0; i < availableTabs.size(); i++) {
			RecipeType<?> type = availableTabs.get(i);
			boolean selected = (i == currentTabIndex);
			int x = tabX + i * tabWidth;
			
			// Draw tab background
			int color = selected ? 0xFFC6C6C6 : 0xFF8B8B8B;
			guiGraphics.fill(x, tabY, x + tabWidth, tabY + tabHeight, color);
			
			// Draw tab border
			guiGraphics.fill(x, tabY, x + tabWidth, tabY + 1, 0xFF373737);
			guiGraphics.fill(x, tabY, x + 1, tabY + tabHeight, 0xFF373737);
			guiGraphics.fill(x + tabWidth - 1, tabY, x + tabWidth, tabY + tabHeight, 0xFFFFFFFF);
			if (!selected) {
				guiGraphics.fill(x, tabY + tabHeight - 1, x + tabWidth, tabY + tabHeight, 0xFF373737);
			}
			
			// Draw tab icon
			ItemStack icon = getIconForRecipeType(type);
			if (!icon.isEmpty()) {
				guiGraphics.renderItem(icon, x + 6, tabY + 6);
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
		
		// Position between the navigation buttons at the bottom
		int buttonY = this.centerY + this.guiHeight + 10;
		int textX = this.centerX + (this.guiWidth - textWidth) / 2;
		int textY = buttonY + 2; // Align with buttons
		
		// Draw background
		guiGraphics.fill(textX - 2, textY - 2, textX + textWidth + 2, textY + 10, 0x80000000);
		
		// Draw text
		guiGraphics.drawString(this.font, text, textX, textY, 0xFFFFFFFF, false);
	}
	
	@Override
	public boolean keyPressed(KeyEvent keyEvent) {
		// Forward key events to JEI panel first (so 'R' key works on JEI items)
		if (this.parentJeiPanel != null && this.parentJeiPanel.keyPressed(keyEvent)) {
			return true;
		}
		
		// ESC or inventory key (E) closes the recipe viewer, not the parent screen
		if (keyEvent.key() == 256 || this.minecraft.options.keyInventory.matches(keyEvent)) { // ESC or E
			this.minecraft.setScreen(parentScreen);
			return true;
		}
		
		// Recipe key (R) opens recipes for hovered item in recipe viewer
		if (this.minecraft.options.keyRecipeViewer.matches(keyEvent)) {
			if (!this.lastHoveredItem.isEmpty()) {
				if (openRecipeViewerForItem(this.lastHoveredItem)) {
					return true;
				}
			}
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
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		// Forward scroll events to JEI panel if available
		if (this.parentJeiPanel != null && this.parentJeiPanel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}
	
	/**
	 * Open a new recipe viewer for the given item.
	 * Creates a nested RecipeViewerScreen with this screen as the parent.
	 */
	private boolean openRecipeViewerForItem(ItemStack item) {
		if (this.minecraft == null || this.minecraft.level == null) {
			return false;
		}
		
		// Find recipes for this item
		java.util.Map<RecipeType<?>, java.util.List<RecipeHolder<?>>> recipes = 
			net.minecraft.client.recipe.RecipeLookupHelper.findRecipesFor(item.getItem(), this.minecraft.level);
		
		if (recipes.isEmpty()) {
			return false;
		}
		
		// Create new RecipeViewerScreen with this screen as parent
		try {
			RecipeViewerScreen viewer = new RecipeViewerScreen(this, item, recipes, this.contextMap);
			this.minecraft.setScreen(viewer);
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}
	
	@Override
	public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl) {
		double mouseX = mouseButtonEvent.x();
		double mouseY = mouseButtonEvent.y();
		
		// Forward mouse clicks to JEI panel first (for buttons and item clicks)
		if (this.parentJeiPanel != null && this.parentJeiPanel.mouseClicked(mouseButtonEvent)) {
			return true;
		}
		
		// Check tab clicks
		if (availableTabs.size() > 1) {
			int tabWidth = 28;
			int tabHeight = 28;
			int tabY = this.centerY - tabHeight - 2;
			int totalTabsWidth = availableTabs.size() * tabWidth;
			int tabX = this.centerX + (this.guiWidth - totalTabsWidth) / 2;
			
			for (int i = 0; i < availableTabs.size(); i++) {
				int x = tabX + i * tabWidth;
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
		int maxY = getCurrentRecipes().size() > 1 ? this.centerY + this.guiHeight + 35 : this.centerY + this.guiHeight;
		
		return mouseX >= this.centerX && mouseX < this.centerX + this.guiWidth &&
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
		} else if (type == RecipeType.SMELTING) {
			return new FurnaceRecipeRenderer(
				ResourceLocation.withDefaultNamespace("textures/gui/container/furnace.png"), 
				contextMap, font);
		} else if (type == RecipeType.BLASTING) {
			return new FurnaceRecipeRenderer(
				ResourceLocation.withDefaultNamespace("textures/gui/container/blast_furnace.png"), 
				contextMap, font);
		} else if (type == RecipeType.SMOKING) {
			return new FurnaceRecipeRenderer(
				ResourceLocation.withDefaultNamespace("textures/gui/container/smoker.png"), 
				contextMap, font);
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
