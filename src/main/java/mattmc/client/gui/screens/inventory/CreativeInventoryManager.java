package mattmc.client.gui.screens.inventory;

import mattmc.client.renderer.window.WindowHandle;
import mattmc.client.util.CoordinateUtils;
import mattmc.world.item.Inventory;
import mattmc.world.item.Item;
import mattmc.world.item.ItemStack;
import mattmc.util.MathUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages creative mode item selection and scrolling.
 */
public class CreativeInventoryManager {
    private static final int CREATIVE_COLS = 9;
    private static final int CREATIVE_ROWS = 15;
    private static final float GUI_SCALE = 3.0f;
    // Creative inventory texture dimensions (for rendering and click detection)
    private static final float CREATIVE_TEXTURE_WIDTH = 256f;
    private static final float CREATIVE_TEXTURE_HEIGHT = 384f;
    
    private final List<Item> allItems = new ArrayList<>();
    private int scrollRow = 0;
    
    public CreativeInventoryManager() {
        initializeItems();
    }
    
    /**
     * Initialize the list of all items for the creative inventory.
     * Uses the creative tabs system to automatically populate items.
     */
    private void initializeItems() {
        allItems.clear();
        // Ensure creative mode tabs are initialized
        mattmc.world.item.CreativeModeTabs.init();
        // Get all unique items from creative tabs
        allItems.addAll(mattmc.world.item.CreativeTabs.getAllUniqueItems());
    }
    
    /**
     * Handle scroll wheel for creative inventory.
     * Scrolling down (negative yoffset) increases scroll row to show next items.
     * Scrolling up (positive yoffset) decreases scroll row to show previous items.
     */
    public void handleScroll(double yoffset) {
        int totalRows = (allItems.size() + CREATIVE_COLS - 1) / CREATIVE_COLS;
        int maxScrollRow = Math.max(0, totalRows - CREATIVE_ROWS);
        
        // Only allow scrolling if there are more items than can fit in the display
        if (maxScrollRow > 0) {
            // Scroll down (negative yoffset) = increase row to show later items
            // Scroll up (positive yoffset) = decrease row to show earlier items
            scrollRow += (int) -yoffset;
            scrollRow = MathUtils.clamp(scrollRow, 0, maxScrollRow);
        }
    }
    
    /**
     * Find which creative inventory item (if any) was clicked.
     * @param mouseXWin Mouse X in window coordinates
     * @param mouseYWin Mouse Y in window coordinates
     * @param window Window reference for coordinate conversion
     * @return Item index in allItems list, or -1 if no item was clicked
     */
    public int findClickedCreativeItem(double mouseXWin, double mouseYWin, WindowHandle window) {
        // Get mouse position in framebuffer coordinates
        CoordinateUtils.Point2D fbCoords = CoordinateUtils.windowToFramebuffer(
            window.handle(), mouseXWin, mouseYWin
        );
        
        // Calculate creative inventory position (must match InventoryRenderer)
        int w = window.width(), h = window.height();
        float contentWidth = CREATIVE_TEXTURE_WIDTH * GUI_SCALE;
        float contentHeight = CREATIVE_TEXTURE_HEIGHT * GUI_SCALE;
        // Position: right edge of screen, adjusted for visual alignment
        float guiX = w - contentWidth + 190f;
        float guiY = (h - contentHeight) / 2f + 30f;
        
        // Slot grid parameters
        float startX = guiX + 8f * GUI_SCALE;
        float startY = guiY + 18f * GUI_SCALE;
        float slotSpacing = 18f * GUI_SCALE;
        
        // Check which slot was clicked
        for (int row = 0; row < CREATIVE_ROWS; row++) {
            for (int col = 0; col < CREATIVE_COLS; col++) {
                float slotX = startX + col * slotSpacing;
                float slotY = startY + row * slotSpacing;
                
                // Check if mouse is within this slot
                if (fbCoords.x >= slotX && fbCoords.x < slotX + slotSpacing &&
                    fbCoords.y >= slotY && fbCoords.y < slotY + slotSpacing) {
                    int itemIndex = (scrollRow + row) * CREATIVE_COLS + col;
                    return itemIndex;
                }
            }
        }
        
        return -1;
    }
    
    /**
     * Handle clicking on a creative inventory item.
     * Attempts to merge with existing stacks first, then deposits into first available slot.
     */
    public void handleCreativeItemClick(Inventory inventory, int itemIndex) {
        if (itemIndex < 0 || itemIndex >= allItems.size()) {
            return;
        }
        
        Item item = allItems.get(itemIndex);
        ItemStack stackToAdd = new ItemStack(item, 1);
        
        // First, try to merge with existing stacks (hotbar first, then main inventory)
        // Try hotbar (slots 0-8)
        for (int i = 0; i < 9; i++) {
            ItemStack existing = inventory.getStack(i);
            if (existing != null && existing.getItem() == item && 
                existing.getCount() < item.getMaxStackSize()) {
                existing.grow(1);
                return;
            }
        }
        
        // Try main inventory (slots 9-35)
        for (int i = 9; i < 36; i++) {
            ItemStack existing = inventory.getStack(i);
            if (existing != null && existing.getItem() == item && 
                existing.getCount() < item.getMaxStackSize()) {
                existing.grow(1);
                return;
            }
        }
        
        // If no existing stacks to merge with, find first empty slot
        // Try hotbar first (slots 0-8)
        for (int i = 0; i < 9; i++) {
            ItemStack existing = inventory.getStack(i);
            if (existing == null) {
                inventory.setStack(i, stackToAdd);
                return;
            }
        }
        
        // Then try main inventory (slots 9-35)
        for (int i = 9; i < 36; i++) {
            ItemStack existing = inventory.getStack(i);
            if (existing == null) {
                inventory.setStack(i, stackToAdd);
                return;
            }
        }
        
        // If we get here, inventory is completely full and all stacks are at max capacity
        // Item cannot be added
    }
    
    public List<Item> getAllItems() {
        return allItems;
    }
    
    public int getScrollRow() {
        return scrollRow;
    }
    
    public Item getItemAt(int index) {
        if (index >= 0 && index < allItems.size()) {
            return allItems.get(index);
        }
        return null;
    }
}
