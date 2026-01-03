package net.minecraft.client.gui.screens.inventory;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Set;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;

/**
 * JEI-style item browser panel that can be added to any inventory screen.
 * Displays all creative tab items in a scrollable grid with search functionality.
 */
@Environment(EnvType.CLIENT)
public class JeiPanel {
	private static final ResourceLocation SCROLLER_SPRITE = ResourceLocation.withDefaultNamespace("container/creative_inventory/scroller");
	private static final int JEI_SLOT_SIZE = 18;
	
	private final Minecraft minecraft;
	private final Font font;
	private final Screen parentScreen;
	
	// JEI item list data
	private final List<ItemStack> allTabItems = Lists.newArrayList();
	private final List<ItemStack> filteredTabItems = Lists.newArrayList();
	
	// Panel layout
	private int panelX = 0;
	private int panelY = 0;
	private int panelWidth = 0;
	private int panelHeight = 0;
	private int columns = 0;
	private int rows = 0;
	private final int slotSize = JEI_SLOT_SIZE;
	
	// Scrolling
	private float scrollOffs = 0.0F;
	private boolean scrolling = false;
	
	// Search bar
	private String searchText = "";
	private boolean searchFocused = false;
	private final int searchBarHeight = 20;
	
	public JeiPanel(Minecraft minecraft, Font font, Screen parentScreen) {
		this.minecraft = minecraft;
		this.font = font;
		this.parentScreen = parentScreen;
	}
	
	/**
	 * Initializes the JEI panel - should be called from screen init().
	 */
	public void init() {
		// Ensure creative tabs are built
		if (this.minecraft != null && this.minecraft.player != null && this.minecraft.player.connection != null) {
			CreativeModeTabs.tryRebuildTabContents(
				this.minecraft.player.connection.enabledFeatures(),
				this.minecraft.player.canUseGameMasterBlocks(),
				this.minecraft.player.level().registryAccess()
			);
		}
		
		this.rebuildItemList();
	}
	
	/**
	 * Rebuilds the complete item list from all creative tabs.
	 */
	private void rebuildItemList() {
		this.allTabItems.clear();
		Set<ItemStack> seenItems = ItemStackLinkedSet.createTypeAndComponentsSet();
		
		// Iterate through all tabs in order
		for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
			// Skip non-category tabs
			if (tab.getType() != CreativeModeTab.Type.CATEGORY) {
				continue;
			}
			
			// Add items from this tab
			for (ItemStack item : tab.getDisplayItems()) {
				if (!item.isEmpty() && !seenItems.contains(item)) {
					this.allTabItems.add(item.copy());
					seenItems.add(item);
				}
			}
		}
		
		this.updateFilteredItems();
	}
	
	/**
	 * Updates the filtered items list based on current search text.
	 */
	private void updateFilteredItems() {
		this.filteredTabItems.clear();
		String searchLower = this.searchText.toLowerCase();
		
		if (searchLower.isEmpty()) {
			this.filteredTabItems.addAll(this.allTabItems);
		} else {
			for (ItemStack item : this.allTabItems) {
				String itemName = item.getHoverName().getString().toLowerCase();
				if (itemName.contains(searchLower)) {
					this.filteredTabItems.add(item);
				}
			}
		}
		
		this.scrollOffs = 0.0F;
	}
	
	/**
	 * Calculates the panel layout based on available screen space.
	 * Should be called when screen is initialized or resized.
	 * 
	 * @param screenWidth Total screen width
	 * @param screenHeight Total screen height
	 * @param inventoryRightEdge X position of the right edge of the main inventory GUI
	 * @param inventoryTopEdge Y position of the top edge of the main inventory GUI
	 */
	public void calculateLayout(int screenWidth, int screenHeight, int inventoryRightEdge, int inventoryTopEdge) {
		// Panel anchored to right side of screen
		int rightMargin = 8;
		int gapFromInventory = 16;
		int panelLeftEdge = inventoryRightEdge + gapFromInventory;
		
		this.panelWidth = screenWidth - panelLeftEdge - rightMargin;
		if (this.panelWidth < this.slotSize + 20) {
			// Not enough space for panel
			this.columns = 0;
			this.rows = 0;
			return;
		}
		
		// Calculate columns
		int availableWidthForSlots = this.panelWidth - 4 - 14; // 4px padding, 14px scrollbar
		this.columns = Math.max(1, availableWidthForSlots / this.slotSize);
		
		// Calculate vertical layout
		int topMargin = 4;
		int bottomMargin = 4;
		int availableHeight = screenHeight - topMargin - bottomMargin;
		int heightForSlots = availableHeight - this.searchBarHeight - 4; // 4px gap between items and search
		
		this.rows = Math.max(1, heightForSlots / this.slotSize);
		
		// Set panel position
		this.panelX = screenWidth - this.panelWidth - rightMargin;
		this.panelY = topMargin;
		
		// Calculate exact panel height
		this.panelHeight = this.rows * this.slotSize + 4 + 4 + this.searchBarHeight;
	}
	
	/**
	 * Renders the JEI panel.
	 */
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		if (this.columns <= 0 || this.rows <= 0 || this.allTabItems.isEmpty()) {
			return;
		}
		
		// Render items
		this.renderItems(guiGraphics);
		
		// Render scrollbar
		this.renderScrollbar(guiGraphics);
		
		// Render search bar
		this.renderSearchBar(guiGraphics, mouseX, mouseY);
	}
	
	private void renderItems(GuiGraphics guiGraphics) {
		int totalRows = (int)Math.ceil((double)this.filteredTabItems.size() / this.columns);
		int maxScroll = Math.max(0, totalRows - this.rows);
		int scrollRow = (int)(this.scrollOffs * maxScroll);
		int startIndex = scrollRow * this.columns;
		
		for (int row = 0; row < this.rows; row++) {
			for (int col = 0; col < this.columns; col++) {
				int index = startIndex + row * this.columns + col;
				if (index >= this.filteredTabItems.size()) {
					break;
				}
				
				ItemStack itemStack = this.filteredTabItems.get(index);
				if (!itemStack.isEmpty()) {
					int x = this.panelX + 2 + col * this.slotSize;
					int y = this.panelY + 2 + row * this.slotSize;
					
					// Render item directly without slot background
					guiGraphics.renderItem(itemStack, x, y);
					guiGraphics.renderItemDecorations(this.font, itemStack, x, y);
				}
			}
		}
	}
	
	private void renderScrollbar(GuiGraphics guiGraphics) {
		int totalRows = (int)Math.ceil((double)this.filteredTabItems.size() / this.columns);
		int maxScroll = Math.max(0, totalRows - this.rows);
		
		if (maxScroll > 0) {
			int scrollbarX = this.panelX + this.panelWidth - 14;
			int scrollbarY = this.panelY + 2;
			int itemsAreaHeight = this.rows * this.slotSize + 4;
			int scrollbarHeight = itemsAreaHeight - 4;
			
			// Scrollbar track
			guiGraphics.fill(scrollbarX, scrollbarY, scrollbarX + 12, scrollbarY + scrollbarHeight, 0xFF8B8B8B);
			guiGraphics.fill(scrollbarX + 1, scrollbarY + 1, scrollbarX + 12, scrollbarY + scrollbarHeight, 0xFF373737);
			
			// Scrollbar thumb
			int thumbHeight = Math.max(15, scrollbarHeight * this.rows / totalRows);
			int thumbY = scrollbarY + (int)((scrollbarHeight - thumbHeight) * this.scrollOffs);
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER_SPRITE, scrollbarX, thumbY, 12, 15);
		}
	}
	
	private void renderSearchBar(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		int searchBarX = this.panelX + 2;
		int searchBarY = this.panelY + this.rows * this.slotSize + 4 + 4;
		int searchBarWidth = this.panelWidth - 4;
		
		// Background
		guiGraphics.fill(searchBarX, searchBarY, searchBarX + searchBarWidth, searchBarY + this.searchBarHeight, 0xFF1A1A1A);
		
		// Border
		int borderColor = this.searchFocused ? 0xFFFFFFFF : 0xFF8B8B8B;
		guiGraphics.fill(searchBarX, searchBarY, searchBarX + searchBarWidth, searchBarY + 1, borderColor);
		guiGraphics.fill(searchBarX, searchBarY + this.searchBarHeight - 1, searchBarX + searchBarWidth, searchBarY + this.searchBarHeight, borderColor);
		guiGraphics.fill(searchBarX, searchBarY, searchBarX + 1, searchBarY + this.searchBarHeight, borderColor);
		guiGraphics.fill(searchBarX + searchBarWidth - 1, searchBarY, searchBarX + searchBarWidth, searchBarY + this.searchBarHeight, borderColor);
		
		// Text
		if (this.searchText.isEmpty() && !this.searchFocused) {
			guiGraphics.drawString(this.font, "Search...", searchBarX + 4, searchBarY + 6, 0xFF666666, false);
		} else {
			guiGraphics.drawString(this.font, this.searchText, searchBarX + 4, searchBarY + 6, 0xFFFFFFFF, false);
			
			// Cursor
			if (this.searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
				int cursorX = searchBarX + 4 + this.font.width(this.searchText);
				guiGraphics.fill(cursorX, searchBarY + 4, cursorX + 1, searchBarY + this.searchBarHeight - 4, 0xFFFFFFFF);
			}
		}
	}
	
	/**
	 * Renders tooltip for hovered item.
	 */
	public void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY, Screen screen) {
		if (this.columns <= 0 || this.rows <= 0 || this.filteredTabItems.isEmpty()) {
			return;
		}
		
		if (mouseX < this.panelX || mouseX >= this.panelX + this.panelWidth ||
			mouseY < this.panelY || mouseY >= this.panelY + this.panelHeight) {
			return;
		}
		
		int totalRows = (int)Math.ceil((double)this.filteredTabItems.size() / this.columns);
		int maxScroll = Math.max(0, totalRows - this.rows);
		int scrollRow = (int)(this.scrollOffs * maxScroll);
		int startIndex = scrollRow * this.columns;
		
		for (int row = 0; row < this.rows; row++) {
			for (int col = 0; col < this.columns; col++) {
				int index = startIndex + row * this.columns + col;
				if (index >= this.filteredTabItems.size()) {
					break;
				}
				
				int x = this.panelX + 2 + col * this.slotSize;
				int y = this.panelY + 2 + row * this.slotSize;
				
				if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
					ItemStack itemStack = this.filteredTabItems.get(index);
					if (!itemStack.isEmpty() && screen instanceof AbstractContainerScreen) {
						AbstractContainerScreen<?> containerScreen = (AbstractContainerScreen<?>)screen;
						guiGraphics.setTooltipForNextFrame(this.font, containerScreen.getTooltipFromContainerItem(itemStack), itemStack.getTooltipImage(), mouseX, mouseY);
					}
					return;
				}
			}
		}
	}
	
	/**
	 * Handles mouse click events.
	 * @return true if click was handled by the panel
	 */
	public boolean mouseClicked(MouseButtonEvent mouseButtonEvent) {
		double mouseX = mouseButtonEvent.x();
		double mouseY = mouseButtonEvent.y();
		boolean isShiftDown = mouseButtonEvent.hasShiftDown();
		
		if (this.columns <= 0 || this.rows <= 0 || this.allTabItems.isEmpty()) {
			return false;
		}
		
		// Check search bar click
		int searchBarX = this.panelX + 2;
		int searchBarY = this.panelY + this.rows * this.slotSize + 4 + 4;
		int searchBarWidth = this.panelWidth - 4;
		
		if (mouseX >= searchBarX && mouseX < searchBarX + searchBarWidth &&
			mouseY >= searchBarY && mouseY < searchBarY + this.searchBarHeight) {
			this.searchFocused = true;
			return true;
		} else {
			this.searchFocused = false;
		}
		
		// Check scrollbar click
		int totalRows = (int)Math.ceil((double)this.filteredTabItems.size() / this.columns);
		int maxScroll = Math.max(0, totalRows - this.rows);
		
		if (maxScroll > 0) {
			int scrollbarX = this.panelX + this.panelWidth - 14;
			int scrollbarY = this.panelY + 2;
			int itemsAreaHeight = this.rows * this.slotSize + 4;
			int scrollbarHeight = itemsAreaHeight - 4;
			
			if (mouseX >= scrollbarX && mouseX < scrollbarX + 12 &&
				mouseY >= scrollbarY && mouseY < scrollbarY + scrollbarHeight) {
				this.scrolling = true;
				return true;
			}
		}
		
		// Check item click
		if (mouseX < this.panelX || mouseX >= this.panelX + this.panelWidth - 14 ||
			mouseY < this.panelY || mouseY >= this.panelY + this.rows * this.slotSize + 4) {
			return false;
		}
		
		int scrollRow = maxScroll > 0 ? (int)(this.scrollOffs * maxScroll) : 0;
		int startIndex = scrollRow * this.columns;
		
		for (int row = 0; row < this.rows; row++) {
			for (int col = 0; col < this.columns; col++) {
				int index = startIndex + row * this.columns + col;
				if (index >= this.filteredTabItems.size()) {
					break;
				}
				
				int x = this.panelX + 2 + col * this.slotSize;
				int y = this.panelY + 2 + row * this.slotSize;
				
				if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
					ItemStack itemStack = this.filteredTabItems.get(index);
					if (!itemStack.isEmpty() && this.minecraft != null && this.minecraft.player != null) {
						int count = isShiftDown ? itemStack.getMaxStackSize() : 1;
						ItemStack itemToAdd = itemStack.copyWithCount(count);
						
						int targetSlot = this.findBestSlotForItem(itemToAdd);
						if (targetSlot != -1) {
							this.minecraft.gameMode.handleCreativeModeItemAdd(itemToAdd, targetSlot);
						}
						
						return true;
					}
				}
			}
		}
		
		return false;
	}
	
	/**
	 * Handles mouse released events.
	 */
	public boolean mouseReleased(MouseButtonEvent mouseButtonEvent) {
		if (mouseButtonEvent.button() == 0) {
			this.scrolling = false;
		}
		return false;
	}
	
	/**
	 * Handles mouse scrolling.
	 */
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX >= this.panelX && mouseX < this.panelX + this.panelWidth &&
			mouseY >= this.panelY && mouseY < this.panelY + this.panelHeight) {
			if (!this.filteredTabItems.isEmpty() && this.columns > 0 && this.rows > 0) {
				int totalRows = (int)Math.ceil((double)this.filteredTabItems.size() / this.columns);
				int maxScroll = Math.max(0, totalRows - this.rows);
				if (maxScroll > 0) {
					this.scrollOffs = Mth.clamp(this.scrollOffs - (float)scrollY / maxScroll, 0.0F, 1.0F);
					return true;
				}
			}
			return false;
		}
		return false;
	}
	
	/**
	 * Handles mouse dragging (for scrollbar).
	 */
	public boolean mouseDragged(MouseButtonEvent mouseButtonEvent, double deltaX, double deltaY) {
		if (this.scrolling) {
			if (this.columns <= 0) {
				return true;
			}
			
			int scrollbarY = this.panelY + 2;
			int itemsAreaHeight = this.rows * this.slotSize + 4;
			int scrollbarHeight = itemsAreaHeight - 4;
			int totalRows = (int)Math.ceil((double)this.filteredTabItems.size() / this.columns);
			int thumbHeight = Math.max(15, scrollbarHeight * this.rows / totalRows);
			int scrollableHeight = scrollbarHeight - thumbHeight;
			if (scrollableHeight > 0) {
				this.scrollOffs = ((float)mouseButtonEvent.y() - scrollbarY - thumbHeight * 0.5F) / scrollableHeight;
				this.scrollOffs = Mth.clamp(this.scrollOffs, 0.0F, 1.0F);
			}
			return true;
		}
		return false;
	}
	
	/**
	 * Handles character typed events (for search bar).
	 */
	public boolean charTyped(CharacterEvent characterEvent) {
		if (this.searchFocused) {
			this.searchText += characterEvent.codepointAsString();
			this.updateFilteredItems();
			return true;
		}
		return false;
	}
	
	/**
	 * Handles key press events (for search bar).
	 */
	public boolean keyPressed(KeyEvent keyEvent) {
		if (this.searchFocused) {
			int keyCode = keyEvent.key();
			int modifiers = keyEvent.modifiers();
			
			if (keyCode == 259) { // Backspace
				if (!this.searchText.isEmpty()) {
					this.searchText = this.searchText.substring(0, this.searchText.length() - 1);
					this.updateFilteredItems();
				}
				return true;
			} else if (keyCode == 261) { // Delete
				if (!this.searchText.isEmpty()) {
					this.searchText = this.searchText.substring(0, this.searchText.length() - 1);
					this.updateFilteredItems();
				}
				return true;
			} else if (keyCode == 256) { // Escape
				this.searchText = "";
				this.updateFilteredItems();
				this.searchFocused = false;
				return true;
			} else if (keyCode == 257 || keyCode == 335) { // Enter
				this.searchFocused = false;
				return true;
			} else if (keyCode == 65 && (modifiers & 2) != 0) { // Ctrl+A
				return true;
			} else if (keyCode == 67 && (modifiers & 2) != 0) { // Ctrl+C
				if (this.minecraft != null) {
					this.minecraft.keyboardHandler.setClipboard(this.searchText);
				}
				return true;
			} else if (keyCode == 86 && (modifiers & 2) != 0) { // Ctrl+V
				if (this.minecraft != null) {
					String clipboard = this.minecraft.keyboardHandler.getClipboard();
					this.searchText += clipboard;
					this.updateFilteredItems();
				}
				return true;
			}
			// Consume all keys when focused
			return true;
		}
		return false;
	}
	
	private int findBestSlotForItem(ItemStack itemStack) {
		if (this.minecraft == null || this.minecraft.player == null) {
			return -1;
		}
		
		Inventory inventory = this.minecraft.player.getInventory();
		
		// Try to find existing stack with space
		int slotWithSpace = inventory.getSlotWithRemainingSpace(itemStack);
		if (slotWithSpace != -1) {
			int containerSlot = inventoryIndexToContainerSlot(slotWithSpace);
			if (containerSlot != -1) {
				return containerSlot;
			}
		}
		
		// Find first empty slot
		int freeSlot = inventory.getFreeSlot();
		if (freeSlot != -1) {
			int containerSlot = inventoryIndexToContainerSlot(freeSlot);
			if (containerSlot != -1) {
				return containerSlot;
			}
		}
		
		return -1;
	}
	
	/**
	 * Converts a player inventory index to the correct container slot index.
	 * This dynamically finds where the player's inventory is in the current container.
	 */
	private int inventoryIndexToContainerSlot(int inventoryIndex) {
		if (this.minecraft == null || this.minecraft.player == null) {
			return -1;
		}
		
		// Get the currently open container
		var containerMenu = this.minecraft.player.containerMenu;
		if (containerMenu == null) {
			return -1;
		}
		
		Inventory playerInventory = this.minecraft.player.getInventory();
		
		// Find which slot in the container corresponds to this inventory index
		// by checking which slots belong to the player's inventory
		int foundSlot = -1;
		for (int i = 0; i < containerMenu.slots.size(); i++) {
			var slot = containerMenu.slots.get(i);
			// Check if this slot belongs to the player's inventory
			if (slot.container == playerInventory) {
				int slotIndex = slot.getContainerSlot();
				// slot.getContainerSlot() gives the index within the inventory (0-40)
				// We need to match it to our inventoryIndex
				if (slotIndex == inventoryIndex) {
					// Only return the first match
					if (foundSlot == -1) {
						foundSlot = i;
					}
				}
			}
		}
		
		return foundSlot;
	}
}
