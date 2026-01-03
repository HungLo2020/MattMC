package net.minecraft.client.gui.screens.inventory;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Set;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.screens.recipebook.CraftingRecipeBookComponent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class InventoryScreen extends AbstractRecipeBookScreen<InventoryMenu> {
	private static final ResourceLocation SCROLLER_SPRITE = ResourceLocation.withDefaultNamespace("container/creative_inventory/scroller");
	private static final int JEI_SLOT_SIZE = 18;
	
	private float xMouse;
	private float yMouse;
	private boolean buttonClicked;
	private final EffectsInInventory effects;
	
	// JEI-like item list fields
	private final List<ItemStack> allTabItems = Lists.newArrayList();
	private float jeiScrollOffs = 0.0F;
	private boolean jeiScrolling = false;
	private int jeiColumns = 0;
	private int jeiRows = 0;
	private int jeiSlotSize = JEI_SLOT_SIZE;
	private int jeiPanelX = 0;
	private int jeiPanelY = 0;
	private int jeiPanelWidth = 0;
	private int jeiPanelHeight = 0;

	public InventoryScreen(Player player) {
		super(player.inventoryMenu, new CraftingRecipeBookComponent(player.inventoryMenu), player.getInventory(), Component.translatable("container.crafting"));
		this.titleLabelX = 97;
		this.effects = new EffectsInInventory(this);
		// JEI item list will be initialized in init() when minecraft instance is available
	}

	@Override
	public void containerTick() {
		super.containerTick();
		// No longer switch to creative inventory - stay in survival inventory even in creative mode
	}

	@Override
	protected void init() {
		// No longer switch to creative inventory - stay in survival inventory even in creative mode
		super.init();
		
		// Ensure creative tabs are built before accessing items
		if (this.minecraft != null && this.minecraft.player != null && this.minecraft.player.connection != null) {
			CreativeModeTabs.tryRebuildTabContents(
				this.minecraft.player.connection.enabledFeatures(),
				this.minecraft.player.canUseGameMasterBlocks(),
				this.minecraft.player.level().registryAccess()
			);
		}
		
		// Now rebuild the JEI item list with the built tabs
		this.rebuildJeiItemList();
		this.calculateJeiPanelLayout();
	}
	
	private void rebuildJeiItemList() {
		this.allTabItems.clear();
		Set<ItemStack> seenItems = ItemStackLinkedSet.createTypeAndComponentsSet();
		
		// Iterate through all tabs in order
		for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
			// Skip non-category tabs (like search, inventory, hotbar)
			if (tab.getType() != CreativeModeTab.Type.CATEGORY) {
				continue;
			}
			
			// Add items from this tab
			for (ItemStack item : tab.getDisplayItems()) {
				if (!item.isEmpty() && !seenItems.contains(item)) {
					this.allTabItems.add(item.copy());
					seenItems.add(item); // Add original item, not copy
				}
			}
		}
	}
	
	private void calculateJeiPanelLayout() {
		// Calculate the JEI panel position and size based on screen size
		// Goal: Anchor to right side of screen, fill space between inventory and right edge
		int screenWidth = this.width;
		int screenHeight = this.height;
		
		// Panel is anchored to right side of screen with small margin
		int rightMargin = 8;
		
		// Calculate left edge - gap from inventory (larger for better separation)
		int gapFromInventory = 16;
		int panelLeftEdge = this.leftPos + this.imageWidth + gapFromInventory;
		
		// Panel width fills from left edge to right screen border
		this.jeiPanelWidth = screenWidth - panelLeftEdge - rightMargin;
		
		// Minimal top margin - align near top of screen
		int topMargin = 4;
		this.jeiPanelY = topMargin;
		
		// Use full available height from top to bottom with minimal margins
		int bottomMargin = 4;
		int availableHeight = screenHeight - topMargin - bottomMargin;
		
		// Position panel at right edge
		this.jeiPanelX = screenWidth - this.jeiPanelWidth - rightMargin;
		
		// Use standard Minecraft slot size (18x18)
		int slotSpacing = this.jeiSlotSize;
		
		// Calculate how many columns we can fit
		// Subtract space for scrollbar (14px) from width calculation
		int widthForSlots = this.jeiPanelWidth - 14;
		this.jeiColumns = Math.max(1, widthForSlots / slotSpacing);
		
		// Calculate rows using full available height
		// Account for small top/bottom padding inside panel (2px each = 4px total)
		int heightForSlots = availableHeight - 4;
		this.jeiRows = Math.max(1, heightForSlots / slotSpacing);
		
		// Limit columns to a reasonable number (like JEI does)
		this.jeiColumns = Math.min(this.jeiColumns, 9);
		
		// Update panel width to match actual columns used (+ scrollbar space)
		this.jeiPanelWidth = this.jeiColumns * slotSpacing + 18; // Add space for scrollbar
		// Recalculate X position to anchor to right edge with the final width
		this.jeiPanelX = screenWidth - this.jeiPanelWidth - rightMargin;
		
		// Panel height exactly matches rows needed
		this.jeiPanelHeight = this.jeiRows * slotSpacing + 4; // Exact height for rows + padding
	}

	@Override
	protected ScreenPosition getRecipeBookButtonPosition() {
		return new ScreenPosition(this.leftPos + 104, this.height / 2 - 22);
	}

	@Override
	protected void onRecipeBookButtonClick() {
		this.buttonClicked = true;
	}

	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int i, int j) {
		guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, -12566464, false);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int i, int j, float f) {
		this.effects.renderEffects(guiGraphics, i, j);
		super.render(guiGraphics, i, j, f);
		this.effects.renderTooltip(guiGraphics, i, j);
		// Render JEI panel item tooltips
		this.renderJeiTooltip(guiGraphics, i, j);
		this.xMouse = i;
		this.yMouse = j;
	}

	@Override
	public boolean showsActiveEffects() {
		return this.effects.canSeeEffects();
	}

	@Override
	protected boolean isBiggerResultSlot() {
		return false;
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float f, int i, int j) {
		int k = this.leftPos;
		int l = this.topPos;
		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, INVENTORY_LOCATION, k, l, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
		renderEntityInInventoryFollowsMouse(guiGraphics, k + 26, l + 8, k + 75, l + 78, 30, 0.0625F, this.xMouse, this.yMouse, this.minecraft.player);
		
		// Render JEI-like item list panel
		this.renderJeiPanel(guiGraphics, i, j, f);
	}
	
	private void renderJeiPanel(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		if (this.jeiColumns <= 0 || this.jeiRows <= 0 || this.allTabItems.isEmpty()) {
			return;
		}
		
		// Draw panel background
		int panelColor = 0xC0101010; // Semi-transparent dark background
		guiGraphics.fill(this.jeiPanelX, this.jeiPanelY, this.jeiPanelX + this.jeiPanelWidth, this.jeiPanelY + this.jeiPanelHeight, panelColor);
		
		// Draw border
		int borderColor = 0xFF8B8B8B;
		guiGraphics.fill(this.jeiPanelX, this.jeiPanelY, this.jeiPanelX + this.jeiPanelWidth, this.jeiPanelY + 1, borderColor);
		guiGraphics.fill(this.jeiPanelX, this.jeiPanelY + this.jeiPanelHeight - 1, this.jeiPanelX + this.jeiPanelWidth, this.jeiPanelY + this.jeiPanelHeight, borderColor);
		guiGraphics.fill(this.jeiPanelX, this.jeiPanelY, this.jeiPanelX + 1, this.jeiPanelY + this.jeiPanelHeight, borderColor);
		guiGraphics.fill(this.jeiPanelX + this.jeiPanelWidth - 1, this.jeiPanelY, this.jeiPanelX + this.jeiPanelWidth, this.jeiPanelY + this.jeiPanelHeight, borderColor);
		
		// Calculate scroll position
		int totalRows = (int)Math.ceil((double)this.allTabItems.size() / this.jeiColumns);
		int maxScroll = Math.max(0, totalRows - this.jeiRows);
		int scrollRow = (int)(this.jeiScrollOffs * maxScroll);
		
		// Render items
		int slotsPerPage = this.jeiColumns * this.jeiRows;
		int startIndex = scrollRow * this.jeiColumns;
		
		for (int row = 0; row < this.jeiRows; row++) {
			for (int col = 0; col < this.jeiColumns; col++) {
				int index = startIndex + row * this.jeiColumns + col;
				if (index >= this.allTabItems.size()) {
					break;
				}
				
				ItemStack itemStack = this.allTabItems.get(index);
				if (!itemStack.isEmpty()) {
					int x = this.jeiPanelX + 2 + col * this.jeiSlotSize;
					int y = this.jeiPanelY + 2 + row * this.jeiSlotSize;
					
					// Draw slot background
					guiGraphics.fill(x, y, x + 16, y + 16, 0xFF8B8B8B);
					guiGraphics.fill(x + 1, y + 1, x + 16, y + 16, 0xFF373737);
					
					// Render the item
					guiGraphics.renderItem(itemStack, x, y);
					guiGraphics.renderItemDecorations(this.font, itemStack, x, y);
				}
			}
		}
		
		// Draw scrollbar if needed
		if (maxScroll > 0) {
			int scrollbarX = this.jeiPanelX + this.jeiPanelWidth - 14;
			int scrollbarY = this.jeiPanelY + 2;
			int scrollbarHeight = this.jeiPanelHeight - 4;
			
			// Scrollbar track
			guiGraphics.fill(scrollbarX, scrollbarY, scrollbarX + 12, scrollbarY + scrollbarHeight, 0xFF8B8B8B);
			guiGraphics.fill(scrollbarX + 1, scrollbarY + 1, scrollbarX + 12, scrollbarY + scrollbarHeight, 0xFF373737);
			
			// Scrollbar thumb
			int thumbHeight = Math.max(15, scrollbarHeight * this.jeiRows / totalRows);
			int thumbY = scrollbarY + (int)((scrollbarHeight - thumbHeight) * this.jeiScrollOffs);
			ResourceLocation scrollerSprite = SCROLLER_SPRITE;
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, scrollerSprite, scrollbarX, thumbY, 12, 15);
		}
	}
	
	private void renderJeiTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		if (this.jeiColumns <= 0 || this.jeiRows <= 0 || this.allTabItems.isEmpty()) {
			return;
		}
		
		// Check if mouse is over JEI panel
		if (mouseX < this.jeiPanelX || mouseX >= this.jeiPanelX + this.jeiPanelWidth ||
			mouseY < this.jeiPanelY || mouseY >= this.jeiPanelY + this.jeiPanelHeight) {
			return;
		}
		
		// Calculate which item the mouse is over
		int totalRows = (int)Math.ceil((double)this.allTabItems.size() / this.jeiColumns);
		int maxScroll = Math.max(0, totalRows - this.jeiRows);
		int scrollRow = (int)(this.jeiScrollOffs * maxScroll);
		int startIndex = scrollRow * this.jeiColumns;
		
		for (int row = 0; row < this.jeiRows; row++) {
			for (int col = 0; col < this.jeiColumns; col++) {
				int index = startIndex + row * this.jeiColumns + col;
				if (index >= this.allTabItems.size()) {
					break;
				}
				
				int x = this.jeiPanelX + 2 + col * this.jeiSlotSize;
				int y = this.jeiPanelY + 2 + row * this.jeiSlotSize;
				
				if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
					ItemStack itemStack = this.allTabItems.get(index);
					if (!itemStack.isEmpty()) {
						guiGraphics.setTooltipForNextFrame(this.font, this.getTooltipFromContainerItem(itemStack), itemStack.getTooltipImage(), mouseX, mouseY);
					}
					return;
				}
			}
		}
	}
	
	@Override
	public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl) {
		if (mouseButtonEvent.button() == 0) {
			// Check JEI panel clicks
			if (this.handleJeiPanelClick(mouseButtonEvent)) {
				return true;
			}
		}
		return super.mouseClicked(mouseButtonEvent, bl);
	}
	
	private boolean handleJeiPanelClick(MouseButtonEvent mouseButtonEvent) {
		double mouseX = mouseButtonEvent.x();
		double mouseY = mouseButtonEvent.y();
		boolean isShiftDown = mouseButtonEvent.hasShiftDown();
		
		if (this.jeiColumns <= 0 || this.jeiRows <= 0 || this.allTabItems.isEmpty()) {
			return false;
		}
		
		// Check if click is on JEI scrollbar
		int totalRows = (int)Math.ceil((double)this.allTabItems.size() / this.jeiColumns);
		int maxScroll = Math.max(0, totalRows - this.jeiRows);
		
		if (maxScroll > 0) {
			int scrollbarX = this.jeiPanelX + this.jeiPanelWidth - 14;
			int scrollbarY = this.jeiPanelY + 2;
			int scrollbarHeight = this.jeiPanelHeight - 4;
			
			if (mouseX >= scrollbarX && mouseX < scrollbarX + 12 &&
				mouseY >= scrollbarY && mouseY < scrollbarY + scrollbarHeight) {
				this.jeiScrolling = true;
				return true;
			}
		}
		
		// Check if click is on an item
		if (mouseX < this.jeiPanelX || mouseX >= this.jeiPanelX + this.jeiPanelWidth - 14 ||
			mouseY < this.jeiPanelY || mouseY >= this.jeiPanelY + this.jeiPanelHeight) {
			return false;
		}
		
		int scrollRow = maxScroll > 0 ? (int)(this.jeiScrollOffs * maxScroll) : 0;
		int startIndex = scrollRow * this.jeiColumns;
		
		for (int row = 0; row < this.jeiRows; row++) {
			for (int col = 0; col < this.jeiColumns; col++) {
				int index = startIndex + row * this.jeiColumns + col;
				if (index >= this.allTabItems.size()) {
					break;
				}
				
				int x = this.jeiPanelX + 2 + col * this.jeiSlotSize;
				int y = this.jeiPanelY + 2 + row * this.jeiSlotSize;
				
				if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
					ItemStack itemStack = this.allTabItems.get(index);
					if (!itemStack.isEmpty() && this.minecraft != null && this.minecraft.player != null) {
						System.out.println("[JEI DEBUG] Item clicked: " + itemStack.getItem().toString());
						
						// Determine how many items to give based on shift key
						int count = isShiftDown ? itemStack.getMaxStackSize() : 1;
						ItemStack itemToAdd = itemStack.copyWithCount(count);
						System.out.println("[JEI DEBUG] Item count to add: " + count);
						
						// Find the best slot to place the item using smart stacking/placement logic
						// This will:
						// 1. First try to stack with existing items
						// 2. Then find first empty hotbar slot
						// 3. Finally find first empty inventory slot
						int targetSlot = this.findBestSlotForItem(itemToAdd);
						System.out.println("[JEI DEBUG] Target slot: " + targetSlot);
						
						if (targetSlot != -1) {
							// Use the creative mode packet to properly add the item server-side
							// This ensures persistence across saves and proper placement
							// Works in both creative and survival modes
							System.out.println("[JEI DEBUG] Calling handleCreativeModeItemAdd with slot " + targetSlot);
							this.minecraft.gameMode.handleCreativeModeItemAdd(itemToAdd, targetSlot);
							System.out.println("[JEI DEBUG] Item added successfully");
						} else {
							System.out.println("[JEI DEBUG] ERROR: No valid slot found for item!");
						}
						
						return true;
					}
				}
			}
		}
		
		return false;
	}
	
	/**
	 * Finds the best slot to place an item using smart stacking/placement logic.
	 * Returns the container slot index (9-45 for valid inventory slots) or -1 if no slot available.
	 * 
	 * IMPORTANT: This returns CONTAINER slot indices, not inventory indices:
	 * - Container slots 9-35: Main inventory
	 * - Container slots 36-44: Hotbar
	 * - Container slot 45: Offhand
	 * 
	 * We NEVER return crafting slots (0-4) or armor slots (5-8).
	 */
	private int findBestSlotForItem(ItemStack itemStack) {
		if (this.minecraft == null || this.minecraft.player == null) {
			System.out.println("[JEI DEBUG] findBestSlotForItem: minecraft or player is null");
			return -1;
		}
		
		// Get the player's inventory
		var inventory = this.minecraft.player.getInventory();
		
		// First, try to find an existing stack with remaining space
		// This returns an inventory index (0-35 or 40 for offhand)
		int slotWithSpace = inventory.getSlotWithRemainingSpace(itemStack);
		System.out.println("[JEI DEBUG] getSlotWithRemainingSpace returned: " + slotWithSpace);
		if (slotWithSpace != -1) {
			// Convert inventory index to container slot index
			int containerSlot = inventoryIndexToContainerSlot(slotWithSpace);
			System.out.println("[JEI DEBUG] Converted inventory index " + slotWithSpace + " to container slot " + containerSlot);
			if (containerSlot != -1 && containerSlot >= 9) { // Only use valid inventory slots (not crafting/armor)
				System.out.println("[JEI DEBUG] Using slot with space: " + containerSlot);
				return containerSlot;
			} else {
				System.out.println("[JEI DEBUG] Slot with space rejected (containerSlot=" + containerSlot + ")");
			}
		}
		
		// If no existing stack, find first empty slot
		// Prioritize hotbar (0-8) over main inventory (9-35)
		int freeSlot = inventory.getFreeSlot();
		System.out.println("[JEI DEBUG] getFreeSlot returned: " + freeSlot);
		if (freeSlot != -1) {
			// Convert inventory index to container slot index
			int containerSlot = inventoryIndexToContainerSlot(freeSlot);
			System.out.println("[JEI DEBUG] Converted inventory index " + freeSlot + " to container slot " + containerSlot);
			if (containerSlot != -1 && containerSlot >= 9) { // Only use valid inventory slots (not crafting/armor)
				System.out.println("[JEI DEBUG] Using free slot: " + containerSlot);
				return containerSlot;
			} else {
				System.out.println("[JEI DEBUG] Free slot rejected (containerSlot=" + containerSlot + ")");
			}
		}
		
		// No space available
		System.out.println("[JEI DEBUG] No space available, returning -1");
		return -1;
	}
	
	/**
	 * Converts an inventory index (0-35 for items, 40 for offhand) to a container slot index.
	 * 
	 * Inventory layout:
	 * - 0-8: Hotbar → Container 36-44
	 * - 9-35: Main inventory → Container 9-35
	 * - 40: Offhand → Container 45
	 */
	private int inventoryIndexToContainerSlot(int inventoryIndex) {
		System.out.println("[JEI DEBUG] inventoryIndexToContainerSlot called with index: " + inventoryIndex);
		if (inventoryIndex >= 0 && inventoryIndex <= 8) {
			// Hotbar: inventory 0-8 → container 36-44
			int result = inventoryIndex + 36;
			System.out.println("[JEI DEBUG] Hotbar conversion: " + inventoryIndex + " -> " + result);
			return result;
		} else if (inventoryIndex >= 9 && inventoryIndex <= 35) {
			// Main inventory: stays the same
			System.out.println("[JEI DEBUG] Main inventory conversion: " + inventoryIndex + " -> " + inventoryIndex);
			return inventoryIndex;
		} else if (inventoryIndex == 40) {
			// Offhand: inventory 40 → container 45
			System.out.println("[JEI DEBUG] Offhand conversion: 40 -> 45");
			return 45;
		}
		// Invalid index
		System.out.println("[JEI DEBUG] Invalid inventory index: " + inventoryIndex + ", returning -1");
		return -1;
	}
	
	@Override
	public boolean mouseScrolled(double d, double e, double f, double g) {
		if (super.mouseScrolled(d, e, f, g)) {
			return true;
		}
		
		// Check if scrolling in JEI panel
		if (d >= this.jeiPanelX && d < this.jeiPanelX + this.jeiPanelWidth &&
			e >= this.jeiPanelY && e < this.jeiPanelY + this.jeiPanelHeight) {
			if (!this.allTabItems.isEmpty() && this.jeiColumns > 0 && this.jeiRows > 0) {
				int totalRows = (int)Math.ceil((double)this.allTabItems.size() / this.jeiColumns);
				int maxScroll = Math.max(0, totalRows - this.jeiRows);
				if (maxScroll > 0) {
					this.jeiScrollOffs = Mth.clamp(this.jeiScrollOffs - (float)g / maxScroll, 0.0F, 1.0F);
					return true;
				}
			}
			return false;
		}
		
		return false;
	}
	
	@Override
	public boolean mouseDragged(MouseButtonEvent mouseButtonEvent, double d, double e) {
		if (this.jeiScrolling) {
			// Handle JEI scrollbar dragging
			if (this.jeiColumns <= 0) {
				return true; // Prevent division by zero
			}
			
			int scrollbarY = this.jeiPanelY + 2;
			int scrollbarHeight = this.jeiPanelHeight - 4;
			int totalRows = (int)Math.ceil((double)this.allTabItems.size() / this.jeiColumns);
			int thumbHeight = Math.max(15, scrollbarHeight * this.jeiRows / totalRows);
			int scrollableHeight = scrollbarHeight - thumbHeight;
			if (scrollableHeight > 0) {
				this.jeiScrollOffs = ((float)mouseButtonEvent.y() - scrollbarY - thumbHeight * 0.5F) / scrollableHeight;
				this.jeiScrollOffs = Mth.clamp(this.jeiScrollOffs, 0.0F, 1.0F);
			}
			return true;
		} else {
			return super.mouseDragged(mouseButtonEvent, d, e);
		}
	}
	
	@Override
	public void resize(Minecraft minecraft, int i, int j) {
		super.resize(minecraft, i, j);
		// Recalculate JEI panel layout when screen is resized (including GUI scale changes)
		this.calculateJeiPanelLayout();
	}

	public static void renderEntityInInventoryFollowsMouse(
		GuiGraphics guiGraphics, int i, int j, int k, int l, int m, float f, float g, float h, LivingEntity livingEntity
	) {
		float n = (i + k) / 2.0F;
		float o = (j + l) / 2.0F;
		guiGraphics.enableScissor(i, j, k, l);
		float p = (float)Math.atan((n - g) / 40.0F);
		float q = (float)Math.atan((o - h) / 40.0F);
		Quaternionf quaternionf = new Quaternionf().rotateZ((float) Math.PI);
		Quaternionf quaternionf2 = new Quaternionf().rotateX(q * 20.0F * (float) (Math.PI / 180.0));
		quaternionf.mul(quaternionf2);
		float r = livingEntity.yBodyRot;
		float s = livingEntity.getYRot();
		float t = livingEntity.getXRot();
		float u = livingEntity.yHeadRotO;
		float v = livingEntity.yHeadRot;
		livingEntity.yBodyRot = 180.0F + p * 20.0F;
		livingEntity.setYRot(180.0F + p * 40.0F);
		livingEntity.setXRot(-q * 20.0F);
		livingEntity.yHeadRot = livingEntity.getYRot();
		livingEntity.yHeadRotO = livingEntity.getYRot();
		float w = livingEntity.getScale();
		Vector3f vector3f = new Vector3f(0.0F, livingEntity.getBbHeight() / 2.0F + f * w, 0.0F);
		float x = m / w;
		renderEntityInInventory(guiGraphics, i, j, k, l, x, vector3f, quaternionf, quaternionf2, livingEntity);
		livingEntity.yBodyRot = r;
		livingEntity.setYRot(s);
		livingEntity.setXRot(t);
		livingEntity.yHeadRotO = u;
		livingEntity.yHeadRot = v;
		guiGraphics.disableScissor();
	}

	public static void renderEntityInInventory(
		GuiGraphics guiGraphics,
		int i,
		int j,
		int k,
		int l,
		float f,
		Vector3f vector3f,
		Quaternionf quaternionf,
		@Nullable Quaternionf quaternionf2,
		LivingEntity livingEntity
	) {
		EntityRenderDispatcher entityRenderDispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
		EntityRenderer<? super LivingEntity, ?> entityRenderer = entityRenderDispatcher.getRenderer(livingEntity);
		EntityRenderState entityRenderState = entityRenderer.createRenderState(livingEntity, 1.0F);
		entityRenderState.lightCoords = 15728880;
		entityRenderState.hitboxesRenderState = null;
		entityRenderState.shadowPieces.clear();
		entityRenderState.outlineColor = 0;
		guiGraphics.submitEntityRenderState(entityRenderState, f, vector3f, quaternionf, quaternionf2, i, j, k, l);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent mouseButtonEvent) {
		if (mouseButtonEvent.button() == 0) {
			this.jeiScrolling = false;
		}
		if (this.buttonClicked) {
			this.buttonClicked = false;
			return true;
		} else {
			return super.mouseReleased(mouseButtonEvent);
		}
	}
}
