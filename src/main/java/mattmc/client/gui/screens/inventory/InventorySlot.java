package mattmc.client.gui.screens.inventory;

/**
 * Represents a single inventory slot with its position and size.
 */
public class InventorySlot {
    public final float x, y, width, height; // Relative to 176x166 GUI coordinate system
    public final int inventoryIndex; // Index in player inventory (0-35, or -1 for non-inventory slots)
    
    public InventorySlot(float x, float y, float width, float height, int inventoryIndex) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.inventoryIndex = inventoryIndex;
    }
    
    public boolean contains(float px, float py) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }
}
